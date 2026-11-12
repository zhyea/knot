// @vitest-environment jsdom
import {describe, it, expect, vi, beforeEach, afterEach} from "vitest";
import {enableAutoUnmount, flushPromises, mount, type VueWrapper} from "@vue/test-utils";
import AsyncTaskProfileFormDrawer from "@/components/model/async/AsyncTaskProfileFormDrawer.vue";

/**
 * 轮询参数区的两条口径（2026-11-02）：
 *
 * 1. **超时从库到界面都是分钟**（2026-11-02 全链路改口径，列名 timeout_minutes）。
 *    「不改就保存」的原值必须钉住：多算一次 ×60000 就会把 30 分钟写成 30 小时。
 * 2. **六个轮询字段都带说明气泡**。图标或文案任一丢失，口径就又回到「只有运维知道」，
 *    而这种退化不会有任何报错。
 */

vi.mock("@/composables/useEnumOptions", () => ({
  useEnumOptions: () => ({optionsOf: () => []})
}));

vi.mock("@/api/asyncTaskProfiles", () => ({
  dryRunAsyncTaskProfile: vi.fn(),
  getAsyncTaskProfile: vi.fn(),
  saveAsyncTaskProfile: vi.fn()
}));

import {getAsyncTaskProfile, saveAsyncTaskProfile} from "@/api/asyncTaskProfiles";

const JsonEditorStub = {name: "JsonCodeEditor", props: ["modelValue"], template: '<div class="json-stub"/>'};

/** `<script setup>` 的绑定挂在内部实例的 setupState 上，读气泡文案只能从这里拿（模板外的常量不对外暴露） */
type SetupState = {$: {setupState: {pollTips: Record<string, string>}}};

const PROFILE_CODE = "KLING_TASK";

/** 详情形态按接口原样给全：抽屉里 `Object.assign(form, blankForm(), detail)` 会整体覆盖 */
function detail(overrides: Record<string, unknown> = {}) {
  return {
    profileCode: PROFILE_CODE,
    protocol: "VIDEO_GENERATIONS",
    queryMethod: "GET",
    queryPath: "/v1/videos/{task_id}",
    taskIdPath: "data.task_id",
    statusPath: "data.task_status",
    statusSucceeded: "['succeed'].contains(status)",
    statusFailed: "status == 'failed'",
    statusRunning: "",
    resultPath: "data.task_result.videos",
    resultUrlField: "url",
    errorMessagePath: "",
    usagePath: "",
    resultExpiresDays: 30,
    pollIntervalMs: 5000,
    pollBackoffBaseMs: 1000,
    pollBackoffMaxMs: 30000,
    maxAttempts: 360,
    timeoutMinutes: 60,
    status: 1,
    remark: "",
    ...overrides
  };
}

async function openEdit(overrides: Record<string, unknown> = {}): Promise<VueWrapper> {
  vi.mocked(getAsyncTaskProfile).mockResolvedValue(detail(overrides));
  const wrapper = mount(AsyncTaskProfileFormDrawer, {
    props: {modelValue: true, profileCode: PROFILE_CODE},
    global: {stubs: {JsonCodeEditor: JsonEditorStub}}
  });
  // 抽屉在 watch(immediate) 里拉详情，内容随 modelValue 渲染，两步都要等
  await flushPromises();
  return wrapper;
}

function formItemOf(wrapper: VueWrapper, label: string) {
  const item = wrapper.findAll(".el-form-item").find((node) => {
    const labelNode = node.find(".el-form-item__label");
    return labelNode.exists() && labelNode.text().includes(label);
  });
  if (!item) {
    throw new Error(`找不到表单项：${label}`);
  }
  return item;
}

function timeoutInput(wrapper: VueWrapper) {
  const input = formItemOf(wrapper, "任务超时").find("input");
  if (!input.exists()) {
    throw new Error("任务超时没有输入框");
  }
  return input;
}

function saveButton(wrapper: VueWrapper) {
  const button = wrapper.findAll(".el-drawer__footer .el-button").find((node) => node.text().includes("保存"));
  if (!button) {
    throw new Error("找不到保存按钮");
  }
  return button;
}

async function save(wrapper: VueWrapper) {
  await saveButton(wrapper).trigger("click");
  await flushPromises();
}

enableAutoUnmount(afterEach);

describe("异步任务档案抽屉 · 轮询参数", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(saveAsyncTaskProfile).mockResolvedValue({});
  });

  it("超时按分钟显示：库里 60 分钟就显示 60", async () => {
    const wrapper = await openEdit();

    expect(formItemOf(wrapper, "任务超时").text()).toContain("任务超时(分钟)");
    expect(timeoutInput(wrapper).element.value).toBe("60");
  });

  it("碰都不碰超时就保存，payload 里仍是分钟原值", async () => {
    const wrapper = await openEdit();

    await save(wrapper);

    expect(saveAsyncTaskProfile).toHaveBeenCalledWith(expect.objectContaining({timeoutMinutes: 60}));
  });

  it("编辑态回显的 usagePath 不输入直接保存，payload 里不被 blankForm 默认值冲掉", async () => {
    const wrapper = await openEdit({usagePath: "data.usage"});

    await save(wrapper);

    expect(saveAsyncTaskProfile).toHaveBeenCalledWith(expect.objectContaining({usagePath: "data.usage"}));
  });

  it("填 45 分钟，payload 就是 45（不再有任何毫秒换算）", async () => {
    const wrapper = await openEdit();

    const input = timeoutInput(wrapper);
    await input.setValue("45");
    await input.trigger("change");
    await save(wrapper);

    expect(saveAsyncTaskProfile).toHaveBeenCalledWith(expect.objectContaining({timeoutMinutes: 45}));
  });

  it("六个轮询字段各有说明气泡，文案都不为空", async () => {
    const wrapper = await openEdit();

    const labels = ["首次间隔(ms)", "退避基数(ms)", "退避上限(ms)", "轮询次数上限", "任务超时(分钟)", "产物有效期(天)"];
    for (const label of labels) {
      // 图标必须在 label 内、且被 EP 认成 tooltip 的 trigger：只数图标会漏掉「图标在、气泡没接上」
      const icon = formItemOf(wrapper, label).find(".profile-form__label-icon");
      expect(icon.exists()).toBe(true);
      expect(icon.classes()).toContain("el-tooltip__trigger");
    }
    expect(wrapper.findAll(".profile-form__label-icon")).toHaveLength(labels.length);

    const tips = (wrapper.vm as unknown as SetupState).$.setupState.pollTips;
    expect(Object.keys(tips)).toHaveLength(labels.length);
    for (const text of Object.values(tips)) {
      expect(text.trim().length).toBeGreaterThan(0);
    }
  });
});

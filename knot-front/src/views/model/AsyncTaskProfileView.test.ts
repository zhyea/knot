// @vitest-environment jsdom
import {describe, it, expect, vi, beforeEach, afterEach} from "vitest";
import {enableAutoUnmount, flushPromises, mount, type VueWrapper} from "@vue/test-utils";
import AsyncTaskProfileView from "@/views/model/AsyncTaskProfileView.vue";
import {listAsyncTaskProfiles} from "@/api/asyncTaskProfiles";
import {listAsyncTaskProfileOperationLogs} from "@/api/operationLogs";

/**
 * 异步任务档案列表页的三处固定要求（2026-11-27）：
 *
 * 1. 「这是什么」不再占一个按钮位，只留问号图标，说明挂在气泡里；
 * 2. 操作列是图标按钮（编辑 / 操作记录 / 删除），不再是一串文字链接；
 * 3. 操作记录按**主键 id** 检索——档案的引用键是 profileCode，但日志表按 id 精确匹配，
 *    拿编码去查会串到同前缀的另一条档案（KLING_TASK / KLING_TASK2）。
 */

const hoisted = vi.hoisted(() => ({replace: vi.fn()}));

vi.mock("vue-router", () => ({
  useRoute: () => ({query: {}}),
  useRouter: () => ({replace: hoisted.replace})
}));

vi.mock("@/composables/useEnumOptions", () => ({
  useEnumOptions: () => ({optionsOf: () => [], labelOf: () => ""})
}));

vi.mock("@/api/asyncTaskProfiles", () => ({
  listAsyncTaskProfiles: vi.fn(),
  deleteAsyncTaskProfile: vi.fn(),
  getAsyncTaskProfile: vi.fn(),
  saveAsyncTaskProfile: vi.fn(),
  dryRunAsyncTaskProfile: vi.fn()
}));

vi.mock("@/api/operationLogs", () => ({
  listAsyncTaskProfileOperationLogs: vi.fn()
}));

const PROFILE_ID = 7;
const PROFILE_CODE = "KLING_TASK";

function row(overrides: Record<string, unknown> = {}) {
  return {
    id: PROFILE_ID,
    profileCode: PROFILE_CODE,
    protocol: "VIDEO_GENERATIONS",
    queryMethod: "GET",
    queryPath: "/v1/videos/{task_id}",
    pollIntervalMs: 5000,
    timeoutMinutes: 30,
    status: 1,
    bindingCount: 0,
    updatedAt: "2026-11-27T09:00:00",
    remark: "",
    ...overrides
  };
}

async function mountView(): Promise<VueWrapper> {
  vi.mocked(listAsyncTaskProfiles).mockResolvedValue({list: [row()], total: 1} as never);
  const wrapper = mount(AsyncTaskProfileView, {attachTo: document.body});
  await flushPromises();
  return wrapper;
}

function actionButton(wrapper: VueWrapper, label: string) {
  const button = wrapper.find(`[aria-label="${label}"]`);
  if (!button.exists()) {
    throw new Error(`操作列缺少「${label}」按钮`);
  }
  return button;
}

enableAutoUnmount(afterEach);

describe("异步任务档案列表", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(listAsyncTaskProfileOperationLogs).mockResolvedValue([]);
  });

  it("「这是什么」只剩问号图标，且图标接上了气泡", async () => {
    const wrapper = await mountView();

    const icon = wrapper.find(".profile-help-icon");
    expect(icon.exists()).toBe(true);
    // 只数图标会漏掉「图标在、气泡没接上」：EP 会给 trigger 打 el-tooltip__trigger
    expect(icon.classes()).toContain("el-tooltip__trigger");
    // 文案不能再以按钮形式占位
    expect(wrapper.text()).not.toContain("这是什么");
  });

  it("操作列是三个图标按钮：编辑 / 操作记录 / 删除", async () => {
    const wrapper = await mountView();

    expect(wrapper.find(".row-actions").exists()).toBe(true);
    actionButton(wrapper, "编辑");
    actionButton(wrapper, "操作记录");
    actionButton(wrapper, "删除");
    // 文字链接形态已退场
    expect(wrapper.text()).not.toContain("编辑\n");
  });

  it("点「操作记录」按主键 id 拉日志，标题带档案编码", async () => {
    const wrapper = await mountView();

    await actionButton(wrapper, "操作记录").trigger("click");
    await flushPromises();

    expect(listAsyncTaskProfileOperationLogs).toHaveBeenCalledWith(PROFILE_ID);
    expect(document.body.textContent).toContain(`异步任务档案操作记录 - ${PROFILE_CODE}`);
  });

  it("被模型引用时删除按钮置灰，但操作记录仍可点", async () => {
    vi.mocked(listAsyncTaskProfiles).mockResolvedValue({
      list: [row({bindingCount: 2})],
      total: 1
    } as never);
    const wrapper = mount(AsyncTaskProfileView, {attachTo: document.body});
    await flushPromises();

    const disabled = actionButton(wrapper, "删除");
    expect(disabled.attributes("disabled")).toBeDefined();
    expect(actionButton(wrapper, "操作记录").attributes("disabled")).toBeUndefined();
  });
});

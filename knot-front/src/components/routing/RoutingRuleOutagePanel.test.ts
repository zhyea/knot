// @vitest-environment jsdom
import {describe, it, expect, vi, beforeEach, afterEach} from "vitest";
import {enableAutoUnmount, mount, flushPromises, type VueWrapper} from "@vue/test-utils";
import {ElMessage} from "element-plus";
import RoutingRuleOutagePanel from "@/components/routing/RoutingRuleOutagePanel.vue";
import type {Dict} from "@/types";

/**
 * `RoutingRuleOutagePanel`（B4 摘除管理面）直测。
 *
 * <p>它是从 `RoutingRuleFormDrawer` 抽出来的**跨抽象层**组件：管的是网关运行时健康态，
 * 不碰规则表单，也不参与提交链路——`ruleCode` 与「可手动摘除的候选」都由父组件传入。</p>
 *
 * <p>关键契约：**刷新时机不属于本组件状态机**，面板不自挂 `onMounted` 拉取，
 * 由父组件在打开抽屉/换规则时调暴露出来的 `refresh()`。这条被用例显式钉住
 * （挂载后不请求），否则有人加一句 `onMounted(refresh)` 就会让抽屉每次打开多打一次接口。</p>
 */

const RULE_CODE = "R_GPT";

// el-select 的下拉 teleport 到 body，不 unmount 会残留到后续用例
enableAutoUnmount(afterEach);

vi.mock("@/api/gatewayOutage", () => ({
  listGatewayOutages: vi.fn(),
  releaseGatewayOutage: vi.fn(),
  ejectGatewayOutage: vi.fn()
}));

import {ejectGatewayOutage, listGatewayOutages, releaseGatewayOutage} from "@/api/gatewayOutage";

function outageRow(overrides: Dict = {}): Dict {
  return {
    ruleCode: RULE_CODE,
    modelCode: "m-1",
    failures: 3,
    slows: 1,
    windowEndAt: Date.now(),
    ejectedUntil: 0,
    probeUntil: 0,
    cooldownMs: 60000,
    reason: "FAILURE",
    ...overrides
  };
}

function mountPanel(props: Dict = {}) {
  return mount(RoutingRuleOutagePanel, {
    props: {ruleCode: RULE_CODE, modelCodes: ["m-1", "m-2"], ...props}
  });
}

/** 面板不自拉，直测必须显式走一次父组件会调的那个入口 */
async function open(wrapper: VueWrapper): Promise<void> {
  await (wrapper.vm as unknown as { refresh: () => Promise<void> }).refresh();
  await flushPromises();
}

function rowsOf(wrapper: VueWrapper) {
  return wrapper.findAll(".outage-table .el-table__body tbody tr");
}

function cooldownOf(wrapper: VueWrapper, index: number): string {
  return rowsOf(wrapper)[index].findAll("td")[4].text();
}

function warned(): string[] {
  return vi.mocked(ElMessage.warning).mock.calls.map((call) => String(call[0]));
}

function successOf(): string {
  return String(vi.mocked(ElMessage.success).mock.calls.at(-1)?.[0] ?? "");
}

beforeEach(() => {
  vi.clearAllMocks();
  vi.spyOn(ElMessage, "warning").mockImplementation(() => ({}) as never);
  vi.spyOn(ElMessage, "success").mockImplementation(() => ({}) as never);
  vi.spyOn(ElMessage, "error").mockImplementation(() => ({}) as never);
  vi.mocked(listGatewayOutages).mockResolvedValue([outageRow()] as never);
  vi.mocked(releaseGatewayOutage).mockResolvedValue(undefined as never);
  vi.mocked(ejectGatewayOutage).mockResolvedValue(undefined as never);
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe("RoutingRuleOutagePanel 摘除管理面", () => {
  // ==================== 刷新时机（契约） ====================

  it("挂载本身不发请求——刷新时机由父组件决定", async () => {
    const wrapper = mountPanel();
    await flushPromises();

    expect(listGatewayOutages).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain("当前无摘除目标");
  });

  it("refresh 按 ruleCode 拉取并渲染", async () => {
    const wrapper = mountPanel();
    await open(wrapper);

    expect(listGatewayOutages).toHaveBeenCalledWith({ruleCode: RULE_CODE});
    expect(rowsOf(wrapper)).toHaveLength(1);
    expect(rowsOf(wrapper)[0].text()).toContain("m-1");
  });

  it("ruleCode 为空时 refresh 不发请求（新建态复用时不会误打接口）", async () => {
    const wrapper = mountPanel({ruleCode: ""});
    await open(wrapper);

    expect(listGatewayOutages).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain("当前无摘除目标");
  });

  it("网关不可达时静默降级为空态，不抛错", async () => {
    vi.mocked(listGatewayOutages).mockRejectedValue(new Error("gateway down"));
    const wrapper = mountPanel();
    await open(wrapper);

    expect(wrapper.text()).toContain("当前无摘除目标");
    expect(wrapper.find(".outage-table").exists()).toBe(false);
  });

  it("「刷新」按钮重新拉取", async () => {
    const wrapper = mountPanel();
    await open(wrapper);
    expect(listGatewayOutages).toHaveBeenCalledTimes(1);

    await wrapper.find(".section-head .el-button").trigger("click");
    await flushPromises();

    expect(listGatewayOutages).toHaveBeenCalledTimes(2);
  });

  // ==================== 状态与冷却 ====================

  it("按 ejectedUntil 区分「摘除中 / 探活中」，冷却按状态取对应截止时间", async () => {
    const now = Date.now();
    vi.mocked(listGatewayOutages).mockResolvedValue([
      // 摘除中：看 ejectedUntil（此行的 probeUntil 更早，若误取 min 会显示 30s）
      outageRow({modelCode: "m-1", ejectedUntil: now + 120_000, probeUntil: now + 30_000}),
      // 探活中：看 probeUntil（ejectedUntil 已过期；取 min 会恒 ≤ 0，这一列永远「—」）
      outageRow({modelCode: "m-3", ejectedUntil: 0, probeUntil: now + 90_000}),
      outageRow({modelCode: "m-4", ejectedUntil: 0, probeUntil: 0})
    ] as never);

    const wrapper = mountPanel();
    await open(wrapper);

    expect(rowsOf(wrapper)).toHaveLength(3);
    expect(rowsOf(wrapper)[0].text()).toContain("摘除中");
    expect(cooldownOf(wrapper, 0)).toMatch(/2m0s/);
    expect(rowsOf(wrapper)[1].text()).toContain("探活中");
    expect(cooldownOf(wrapper, 1)).toMatch(/1m3\ds/);
    expect(rowsOf(wrapper)[2].text()).toContain("探活中");
    expect(cooldownOf(wrapper, 2)).toBe("—");
  });

  it("窗口计数分别展示失败与慢请求次数", async () => {
    vi.mocked(listGatewayOutages).mockResolvedValue([outageRow({failures: 7, slows: 2})] as never);
    const wrapper = mountPanel();
    await open(wrapper);

    expect(rowsOf(wrapper)[0].text()).toContain("失败 7");
    expect(rowsOf(wrapper)[0].text()).toContain("慢 2");
  });

  // ==================== 恢复 ====================

  it("恢复：按 ruleCode + modelCode 调接口，提示后重新拉取", async () => {
    vi.mocked(listGatewayOutages).mockResolvedValue([
      outageRow({modelCode: "m-1", ejectedUntil: Date.now() + 60_000, probeUntil: Date.now() + 60_000})
    ] as never);
    const wrapper = mountPanel();
    await open(wrapper);

    await wrapper.find(".outage-table .el-button").trigger("click");
    await flushPromises();

    expect(releaseGatewayOutage).toHaveBeenCalledWith({ruleCode: RULE_CODE, modelCode: "m-1"});
    expect(successOf()).toBe("已恢复 m-1");
    expect(listGatewayOutages).toHaveBeenCalledTimes(2);
  });

  it("恢复失败：已由全局拦截器提示，不重拉列表也不卡住按钮", async () => {
    vi.mocked(releaseGatewayOutage).mockRejectedValue(new Error("boom"));
    const wrapper = mountPanel();
    await open(wrapper);

    const button = wrapper.find(".outage-table .el-button");
    await button.trigger("click");
    await flushPromises();

    expect(listGatewayOutages).toHaveBeenCalledTimes(1);
    expect(successOf()).toBe("");
    // _acting 必须复位，否则这一行的「恢复」按钮会永远转圈
    expect(wrapper.find(".outage-table .el-button").classes().join(" ")).not.toContain("is-loading");
  });

  // ==================== 手动摘除 ====================

  it("手动摘除：模型 code 为空时提示，不调接口", async () => {
    const wrapper = mountPanel();
    await open(wrapper);

    await wrapper.findAll(".outage-eject-form .el-button")[0].trigger("click");
    await flushPromises();

    expect(warned()).toContain("请选择或输入模型 code");
    expect(ejectGatewayOutage).not.toHaveBeenCalled();
  });

  it("手动摘除成功：带默认冷却 60 秒、空原因传 undefined，随后清空输入并重拉", async () => {
    const wrapper = mountPanel();
    await open(wrapper);

    // 直接把值写到 select 的 v-model（jsdom 里 el-select 的交互路径不稳定）
    const vm = wrapper.vm as unknown as { ejectForm: Dict };
    vm.ejectForm.modelCode = "m-2";
    await flushPromises();

    await wrapper.findAll(".outage-eject-form .el-button")[0].trigger("click");
    await flushPromises();

    expect(ejectGatewayOutage).toHaveBeenCalledWith({
      ruleCode: RULE_CODE,
      modelCode: "m-2",
      cooldownSec: 60,
      reason: undefined
    });
    expect(successOf()).toBe("已手动摘除 m-2");
    expect(vm.ejectForm.modelCode).toBe("");
    expect(listGatewayOutages).toHaveBeenCalledTimes(2);
  });

  it("手动摘除失败：保留已填内容，不重拉列表", async () => {
    vi.mocked(ejectGatewayOutage).mockRejectedValue(new Error("boom"));
    const wrapper = mountPanel();
    await open(wrapper);

    const vm = wrapper.vm as unknown as { ejectForm: Dict };
    vm.ejectForm.modelCode = "m-2";
    vm.ejectForm.reason = "人工维护";
    await flushPromises();

    await wrapper.findAll(".outage-eject-form .el-button")[0].trigger("click");
    await flushPromises();

    // 失败清空等于让用户重填一遍，必须保留
    expect(vm.ejectForm.modelCode).toBe("m-2");
    expect(vm.ejectForm.reason).toBe("人工维护");
    expect(listGatewayOutages).toHaveBeenCalledTimes(1);
  });

  it("手动摘除候选只用传入的 modelCodes，不由面板自行推导", async () => {
    const wrapper = mountPanel({modelCodes: ["m-9"]});
    await open(wrapper);

    await wrapper.find(".outage-eject-form .el-select").trigger("click");
    await flushPromises();

    const labels = Array.from(document.querySelectorAll(".el-select-dropdown__item")).map((node) => node.textContent);
    expect(labels).toContain("m-9");
    expect(labels).not.toContain("m-1");
  });
});

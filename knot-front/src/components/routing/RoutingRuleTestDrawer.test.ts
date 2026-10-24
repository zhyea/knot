// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach } from "vitest";
import { nextTick } from "vue";
import { mount, flushPromises, type VueWrapper } from "@vue/test-utils";
import { ElDrawer } from "element-plus";
import RoutingRuleTestDrawer from "@/components/routing/RoutingRuleTestDrawer.vue";

/**
 * 抽屉的「模板 ↔ 状态」接线测试。
 *
 * 背景：3.6 把两个状态机抽成 `useBillingVerdict` / `useStreamBuffer` 后，它们各自有
 * 23 + 26 例 composable 测试，但**抽屉这一侧一条测试也没有**——即「composable 的输出
 * 有没有真的接到模板上」从未被验证。composable 测试全绿、抽屉忘了解构某个字段、
 * 或者模板绑错了名字，都不会被任何门禁发现（`noUnusedLocals` 只能抓"解构了没用"，
 * 抓不到"该用的没解构"）。
 *
 * 因此本文件**刻意 mock 掉两个 composable**，由测试自己持有 ref：
 * 这样"给 composable 一个状态 → 断言 DOM 变化"才能成立，否则状态藏在真实 composable
 * 内部无法从外部驱动。
 *
 * 断言的落点是业务可见行为（何时出现哪个 tab、发送/停止按钮切换、状态条数值、
 * 关闭时的清理），不复刻模板细节。
 */

const h = vi.hoisted(() => ({
  stream: {} as Record<string, any>,
  verdict: {} as Record<string, any>,
  caps: {} as Record<string, any>
}));

vi.mock("@/composables/useStreamBuffer", async () => {
  const { ref } = await import("vue");
  h.stream = {
    streamBody: ref(""),
    receivedBytes: ref(0),
    streamStartedAt: ref<number | null>(null),
    streamBlocks: ref<unknown[]>([]),
    streamContent: ref(""),
    streamReasoning: ref(""),
    elapsedTick: ref(0),
    isStreaming: ref(false),
    beginStream: vi.fn(),
    endStream: vi.fn(),
    handleStreamEvent: vi.fn(),
    flushStreamText: vi.fn(),
    flushBlocks: vi.fn(),
    recoverKnotExtendFromStreamBlocks: vi.fn(),
    finishStream: vi.fn(),
    appendStreamError: vi.fn(),
    abortStreaming: vi.fn(),
    disposeStreamResources: vi.fn()
  };
  return { useStreamBuffer: () => h.stream };
});

vi.mock("@/composables/useBillingVerdict", async () => {
  const { ref } = await import("vue");
  h.verdict = {
    knotExtend: ref(null),
    verdictResult: ref(null),
    verdictLoading: ref(false),
    verdictSkipped: ref(false),
    verdictError: ref(""),
    verdictActive: ref(false),
    resetVerdictState: vi.fn(),
    captureKnotExtend: vi.fn(),
    captureKnotExtendFromStreamData: vi.fn(),
    servingModelCode: vi.fn(() => "m-served"),
    triggerBillingVerdict: vi.fn()
  };
  return { useBillingVerdict: () => h.verdict };
});

vi.mock("@/composables/useDebugCapabilities", () => ({
  useDebugCapabilities: () => ({
    loadOptions: h.caps.loadOptions,
    canonicalOf: (code: string) => code,
    gatewayPathOf: (code: string) => `/v1/${String(code).toLowerCase()}`,
    hintOf: () => ""
  })
}));

vi.mock("@/composables/useEnumOptions", () => ({
  useEnumOptions: () => ({ labelOf: () => "-" })
}));

vi.mock("@/composables/useCopyToClipboard", () => ({
  useCopyToClipboard: () => ({ copy: vi.fn().mockResolvedValue(true) })
}));

vi.mock("@/api/routing", () => ({
  listRoutingRuleDebugProtocols: vi.fn().mockResolvedValue({
    targets: [{ targetType: "MODEL", targetId: 1, protocols: ["CHAT_COMPLETIONS"] }]
  }),
  listTestRequestPresetOptions: vi.fn().mockResolvedValue({ list: [] }),
  getTestRequestPreset: vi.fn().mockResolvedValue(null),
  testRoutingRule: vi.fn(),
  testRoutingRuleStream: vi.fn(),
  testRoutingRuleBillingVerdict: vi.fn()
}));

import {
  listRoutingRuleDebugProtocols,
  listTestRequestPresetOptions,
  testRoutingRule
} from "@/api/routing";

const TARGETS = [{ targetType: "MODEL", targetId: 1, targetCode: "m1", modelType: "TOKEN" }];

/** 打开抽屉必须走 setProps：抽屉的初始化挂在 watch(modelValue) 上，没有 immediate */
async function openDrawer(props: Record<string, unknown> = {}): Promise<VueWrapper> {
  const wrapper = mount(RoutingRuleTestDrawer, {
    props: {
      modelValue: false,
      ruleId: 1,
      ruleName: "规则A",
      secretKey: "sk-test",
      targets: TARGETS,
      ...props
    },
    global: {
      stubs: { JsonCodeEditor: true, ShellCodeBlock: true, RoutingBillingVerdictPanel: true }
    }
  });
  await wrapper.setProps({ modelValue: true });
  await flushPromises();
  return wrapper;
}

function tabLabels(wrapper: VueWrapper): string[] {
  return wrapper.findAll(".el-tabs__item").map((item) => item.text());
}

function toolbarButton(wrapper: VueWrapper, modifier: string) {
  const found = wrapper.find(`.request-toolbar .el-button--${modifier}`);
  return found.exists() ? found : null;
}

describe("RoutingRuleTestDrawer 抽屉编排（模板 ↔ 状态接线）", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    h.stream.isStreaming.value = false;
    h.stream.streamBlocks.value = [];
    h.stream.receivedBytes.value = 0;
    h.stream.streamContent.value = "";
    h.stream.streamReasoning.value = "";
    h.stream.streamStartedAt.value = null;
    h.verdict.verdictActive.value = false;
    h.verdict.verdictResult.value = null;
    h.caps.loadOptions = vi.fn().mockResolvedValue(undefined);
  });

  it("标题带规则名；无规则名时只显示固定标题", async () => {
    const named = await openDrawer();
    expect(named.findComponent(ElDrawer).props("title")).toBe("路由规则测试 — 规则A");

    const anonymous = await openDrawer({ ruleName: "   " });
    expect(anonymous.findComponent(ElDrawer).props("title")).toBe("路由规则测试");
  });

  it("打开抽屉触发三个初始化请求，并复位计费校验开关", async () => {
    const wrapper = await openDrawer();

    expect(h.caps.loadOptions).toHaveBeenCalledTimes(1);
    expect(listTestRequestPresetOptions).toHaveBeenCalledTimes(1);
    expect(listRoutingRuleDebugProtocols).toHaveBeenCalledWith(1);
    expect(h.verdict.resetVerdictState).toHaveBeenCalled();

    // 默认勾选「执行计费规则校验」
    const checkbox = wrapper.find(".test-billing-verify-toggle input[type='checkbox']");
    expect((checkbox.element as HTMLInputElement).checked).toBe(true);
  });

  it("消费者已默认返回消费详情时，提示语与标签切换", async () => {
    const local = await openDrawer({ consumerReturnUsageDetail: true });
    expect(local.text()).toContain("消费者已默认返回消费详情");
    expect(local.text()).toContain("勾选后响应自动发起 JEV 双盲计费校验");

    const remote = await openDrawer({ consumerReturnUsageDetail: false });
    expect(remote.text()).not.toContain("消费者已默认返回消费详情");
    expect(remote.text()).toContain("本次请求自动附带 knot_extend");
  });

  it("没有规则 id 时发送按钮禁用（避免对着空规则的 URL 打请求）", async () => {
    const wrapper = await openDrawer({ ruleId: null });
    const send = toolbarButton(wrapper, "primary");
    expect(send).not.toBeNull();
    expect(send!.attributes("disabled")).toBeDefined();
  });

  it("流式进行中：发送按钮换成停止按钮，状态条展示区块数与已接收字节", async () => {
    const wrapper = await openDrawer();
    expect(toolbarButton(wrapper, "danger")).toBeNull();

    h.stream.isStreaming.value = true;
    h.stream.streamBlocks.value = [{ id: 1 }, { id: 2 }];
    h.stream.receivedBytes.value = 2048;
    await nextTick();

    expect(toolbarButton(wrapper, "primary")).toBeNull();
    const stop = toolbarButton(wrapper, "danger");
    expect(stop).not.toBeNull();

    const status = wrapper.find(".stream-status");
    expect(status.exists()).toBe(true);
    expect(status.text()).toContain("区块 2");
    // 2048B → 2.0 KB（receivedBytesLabel 的 1KB 阈值与一位小数口径）
    expect(status.text()).toContain("2.0 KB");

    await stop!.trigger("click");
    expect(h.stream.abortStreaming).toHaveBeenCalledTimes(1);
  });

  it("密钥为空时点发送直接早退，不发请求", async () => {
    const wrapper = await openDrawer({ secretKey: "" });
    await toolbarButton(wrapper, "primary")!.trigger("click");
    await flushPromises();

    expect(testRoutingRule).not.toHaveBeenCalled();
  });

  it("非流式执行成功后：结果区出现，并自动发起计费裁判", async () => {
    vi.mocked(testRoutingRule).mockResolvedValue({
      status: 200,
      headers: { "x-knot-model": "m1" },
      data: { id: "chatcmpl-1", usage: { total_tokens: 12 } }
    } as never);

    const wrapper = await openDrawer();
    expect(wrapper.text()).toContain("执行请求后在这里查看响应结果");

    await toolbarButton(wrapper, "primary")!.trigger("click");
    await flushPromises();

    expect(testRoutingRule).toHaveBeenCalledTimes(1);
    // 整包响应体交给被告提取
    expect(h.verdict.captureKnotExtend).toHaveBeenCalledWith({
      id: "chatcmpl-1",
      usage: { total_tokens: 12 }
    });
    // 开关默认开启 → 非流式分支执行完必须触发裁判第二步
    expect(h.verdict.triggerBillingVerdict).toHaveBeenCalledTimes(1);
    // 空态换成结果 tabs
    expect(wrapper.text()).not.toContain("执行请求后在这里查看响应结果");
    expect(tabLabels(wrapper)).toContain("Body");
  });

  it("计费校验开关关闭时不发起裁判", async () => {
    vi.mocked(testRoutingRule).mockResolvedValue({
      status: 200,
      headers: {},
      data: { id: "x" }
    } as never);

    const wrapper = await openDrawer();
    await wrapper.find(".test-billing-verify-toggle input[type='checkbox']").setValue(false);
    await toolbarButton(wrapper, "primary")!.trigger("click");
    await flushPromises();

    expect(testRoutingRule).toHaveBeenCalledTimes(1);
    expect(h.verdict.triggerBillingVerdict).not.toHaveBeenCalled();
  });

  it("「计费校验」tab 只在开关开且裁判态激活时出现", async () => {
    vi.mocked(testRoutingRule).mockResolvedValue({
      status: 200,
      headers: {},
      data: { id: "x" }
    } as never);

    const wrapper = await openDrawer();
    await toolbarButton(wrapper, "primary")!.trigger("click");
    await flushPromises();

    // 开关开但无裁判态（未激活）→ 不出现
    expect(tabLabels(wrapper)).not.toContain("计费校验");

    h.verdict.verdictActive.value = true;
    await nextTick();
    expect(tabLabels(wrapper)).toContain("计费校验");
  });

  it("「流式响应」tab 随流式状态出现", async () => {
    vi.mocked(testRoutingRule).mockResolvedValue({
      status: 200,
      headers: {},
      data: { id: "x" }
    } as never);

    const wrapper = await openDrawer();
    await toolbarButton(wrapper, "primary")!.trigger("click");
    await flushPromises();
    expect(tabLabels(wrapper)).not.toContain("流式响应");

    h.stream.isStreaming.value = true;
    await nextTick();
    expect(tabLabels(wrapper)).toContain("流式响应");
  });

  it("关闭抽屉：中止在途流、释放计时器、清空流式与裁判状态", async () => {
    const wrapper = await openDrawer();
    h.stream.streamBlocks.value = [{ id: 1 }];
    h.stream.receivedBytes.value = 512;
    h.stream.streamContent.value = "abc";

    const afterOpen = vi.mocked(h.verdict.resetVerdictState).mock.calls.length;
    wrapper.findComponent(ElDrawer).vm.$emit("closed");
    await nextTick();

    expect(h.stream.disposeStreamResources).toHaveBeenCalledTimes(1);
    expect(h.stream.streamBlocks.value).toEqual([]);
    expect(h.stream.receivedBytes.value).toBe(0);
    expect(h.stream.streamContent.value).toBe("");
    expect(h.verdict.resetVerdictState.mock.calls.length).toBeGreaterThan(afterOpen);
  });

  it("组件卸载同样释放流资源（不关抽屉直接切路由的情况）", async () => {
    const wrapper = await openDrawer();
    wrapper.unmount();
    expect(h.stream.disposeStreamResources).toHaveBeenCalledTimes(1);
  });
});

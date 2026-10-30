// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach } from "vitest";
import { nextTick } from "vue";
import { mount, flushPromises, enableAutoUnmount, type VueWrapper } from "@vue/test-utils";
import { afterEach } from "vitest";
import { ElDrawer, ElOption, ElSelect } from "element-plus";
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
  async: {} as Record<string, any>,
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

vi.mock("@/composables/useAsyncTaskPolling", async () => {
  const { ref } = await import("vue");
  h.async = {
    taskCode: ref<string | null>(null),
    taskStatus: ref(""),
    pollCount: ref(0),
    timeline: ref<unknown[]>([]),
    result: ref<unknown>(null),
    error: ref<unknown>(null),
    localTimeout: ref(false),
    stopped: ref(false),
    polling: ref(false),
    lastError: ref(""),
    elapsedLabel: ref("0.0s"),
    start: vi.fn(),
    stop: vi.fn(),
    pollNow: vi.fn(),
    resetAsyncState: vi.fn(),
    disposeAsyncPolling: vi.fn()
  };
  // 捕获得抽屉注入的 ctx：终态回调只有从外部触发，「终态 → 计费校验」才测得出来
  return { useAsyncTaskPolling: (ctx: unknown) => { h.async.ctx = ctx; return h.async; } };
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
  queryGatewayTask: vi.fn(),
  testRoutingRule: vi.fn(),
  testRoutingRuleStream: vi.fn(),
  testRoutingRuleBillingVerdict: vi.fn()
}));

import {
  listRoutingRuleDebugProtocols,
  listTestRequestPresetOptions,
  testRoutingRule,
  testRoutingRuleStream
} from "@/api/routing";

const TARGETS = [{ targetType: "MODEL", targetId: 1, targetCode: "m1", modelType: "TOKEN" }];

// 每条用例都挂一个抽屉：不卸载会累积到后续用例（曾让 fake timers 的用例卡住 5.9s 超时）
enableAutoUnmount(afterEach);

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

/** 当前激活的结果页标题（Body / 概览 / Header / 流式响应） */
function activeTabLabel(wrapper: VueWrapper): string {
  return wrapper.find(".debug-tabs--response .el-tabs__item.is-active").text();
}

/** 抽屉里第 index 个 el-select（0=调试目标、1=接口协议） */
function selectOf(wrapper: VueWrapper, index: number) {
  const all = wrapper.findAllComponents(ElSelect);
  if (!all[index]) {
    throw new Error(`找不到第 ${index} 个 el-select`);
  }
  return all[index];
}

/** 协议下拉当前渲染出的选项值（读 el-option 组件实例——展开态 DOM 会 teleport 到 body） */
function protocolOptionLabels(wrapper: VueWrapper): string[] {
  return selectOf(wrapper, 1)
    .findAllComponents(ElOption)
    .map((node) => String(node.props("value")));
}

/** 通过 JsonCodeEditor 的 v-model 写入请求模板（stub 上 emit 即驱动真实 composable） */
async function setTemplate(wrapper: VueWrapper, text: string) {
  const editor = wrapper.findComponent({ name: "JsonCodeEditor" });
  if (!editor.exists()) {
    throw new Error("请求模板编辑器未渲染（先切到「预设请求」页）");
  }
  editor.vm.$emit("update:modelValue", text);
  await flushPromises();
}

/** 点击结果区的某个 tab 标题 */
async function openTab(wrapper: VueWrapper, label: string) {
  const tab = wrapper
    .findAll(".debug-tabs--response .el-tabs__item")
    .find((node) => node.text() === label);
  if (!tab) {
    throw new Error(`找不到结果页签：${label}`);
  }
  await tab.trigger("click");
  await flushPromises();
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
    h.verdict.knotExtend.value = null;
    h.async.taskCode.value = null;
    h.async.taskStatus.value = "";
    h.async.pollCount.value = 0;
    h.async.timeline.value = [];
    h.async.result.value = null;
    h.async.error.value = null;
    h.async.localTimeout.value = false;
    h.async.stopped.value = false;
    h.async.polling.value = false;
    h.async.lastError.value = "";
    h.caps.loadOptions = vi.fn().mockResolvedValue(undefined);
    // ⚠ `clearAllMocks` 只清调用记录，**不清 mockResolvedValue**：
    // 上一条用例换过的返回数据会静默延续到下一条（曾让「单目标只有自己的协议」拿到并集）。
    vi.mocked(listRoutingRuleDebugProtocols).mockResolvedValue({
      targets: [{ targetType: "MODEL", targetId: 1, protocols: ["CHAT_COMPLETIONS"] }]
    } as never);
    vi.mocked(testRoutingRule).mockReset();
    vi.mocked(testRoutingRuleStream).mockReset();
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

  it("缺消费者密钥：发送按钮禁用 + 界面写明原因（不再让人去填一个不存在的输入框）", async () => {
    const wrapper = await openDrawer({ secretKey: "" });

    const send = toolbarButton(wrapper, "primary");
    expect(send).not.toBeNull();
    expect(send!.attributes("disabled")).toBeDefined();

    // 原因必须可见：旧实现只有一句「请填写 API Key」，而界面上根本没有可填的地方
    const tip = wrapper.find(".secret-missing-tip");
    expect(tip.exists()).toBe(true);
    expect(tip.text()).toContain("未绑定消费者");
    expect(tip.text()).toContain("「消费者」页");

    // 即便按钮被禁用，程序化触发也不能真的打请求（双保险）
    await send!.trigger("click");
    await flushPromises();
    expect(testRoutingRule).not.toHaveBeenCalled();
  });

  it("密钥就绪时不显示缺密钥提示", async () => {
    const wrapper = await openDrawer();
    expect(wrapper.find(".secret-missing-tip").exists()).toBe(false);
    expect(toolbarButton(wrapper, "primary")!.attributes("disabled")).toBeUndefined();
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

  // ==================== 失败路径与降级（内容级，非接线） ====================

  it("非流式失败：结果区替换空态、tab 落到 Body、错误响应头回填 Header 页", async () => {
    vi.mocked(testRoutingRule).mockRejectedValue({
      response: {
        status: 502,
        headers: { "x-knot-trace": "trace-9" },
        data: { message: "上游不可达" }
      }
    });

    const wrapper = await openDrawer();
    await toolbarButton(wrapper, "primary")!.trigger("click");
    await flushPromises();

    // 不能停在空态：失败也必须进结果区
    expect(wrapper.text()).not.toContain("执行请求后在这里查看响应结果");
    expect(activeTabLabel(wrapper)).toBe("Body");
    // 失败响应的 header 同样要能在「Header」页看到
    expect(wrapper.text()).toContain("trace-9");
  });

  it("流式被用户中止：标为 STOPPED、不弹网络错误，且 knot_extend 已到手时仍跑裁判", async () => {
    h.verdict.knotExtend.value = { usage: { total_tokens: 1 } };
    vi.mocked(testRoutingRuleStream).mockResolvedValue({
      status: 200,
      headers: {},
      read: vi.fn().mockRejectedValue(Object.assign(new Error("aborted"), { name: "AbortError" }))
    } as never);

    const wrapper = await openDrawer();
    await setTemplate(wrapper, '{"stream":true}');
    await toolbarButton(wrapper, "primary")!.trigger("click");
    await flushPromises();

    // 主动停止不是失败：保留已收内容、状态落 STOPPED（不是 ERROR）
    expect(h.stream.finishStream).toHaveBeenCalledWith("STOPPED", "");
    expect(h.stream.appendStreamError).not.toHaveBeenCalled();
    // [DONE] 前已拿到 knot_extend → 仍可判（流式第二个参数 true）
    expect(h.verdict.triggerBillingVerdict).toHaveBeenCalledWith(expect.anything(), true);
  });

  it("流式遇到真实网络错误：切回 Body 并把错误写进流式区块，不误判成用户中止", async () => {
    vi.mocked(testRoutingRuleStream).mockResolvedValue({
      status: 200,
      headers: {},
      read: vi.fn().mockRejectedValue(new TypeError("Failed to fetch"))
    } as never);

    const wrapper = await openDrawer();
    await setTemplate(wrapper, '{"stream":true}');
    await toolbarButton(wrapper, "primary")!.trigger("click");
    await flushPromises();

    expect(h.stream.finishStream).not.toHaveBeenCalledWith("STOPPED", "");
    expect(h.stream.appendStreamError).toHaveBeenCalledTimes(1);
    expect(activeTabLabel(wrapper)).toBe("Body");
  });

  it("请求模板不是合法 JSON 时早退：不发请求，并把原文留在响应体里方便对照", async () => {
    const wrapper = await openDrawer();
    await setTemplate(wrapper, "{oops");
    await toolbarButton(wrapper, "primary")!.trigger("click");
    await flushPromises();

    expect(testRoutingRule).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain("请求模板不是合法 JSON");
    // 原文留在响应体里（Body 页由 ShellCodeBlock 承载，文本不在 wrapper.text() 里）
    const codes = wrapper
      .findAllComponents({ name: "ShellCodeBlock" })
      .map((node) => String(node.props("code") ?? ""));
    expect(codes.some((code) => code.includes("{oops"))).toBe(true);
  });

  it("协议载入失败：协议下拉降级为空且禁用，不做本地兜底", async () => {
    vi.mocked(listRoutingRuleDebugProtocols).mockRejectedValue(new Error("boom"));

    const wrapper = await openDrawer();
    const protocol = selectOf(wrapper, 1);

    expect(protocol.findAll(".el-select__wrapper.is-disabled, .is-disabled").length).toBeGreaterThan(0);
    expect(protocol.text()).not.toContain("CHAT_COMPLETIONS");
  });

  it("「全部目标」取协议并集，锁定单个目标后只留该目标自己的协议", async () => {
    vi.mocked(listRoutingRuleDebugProtocols).mockResolvedValue({
      targets: [
        { targetType: "MODEL", targetId: 1, protocols: ["CHAT_COMPLETIONS"] },
        { targetType: "MODEL", targetId: 2, protocols: ["RESPONSES"] }
      ]
    } as never);

    const TWO_TARGETS = [
      { targetType: "MODEL", targetId: 1, targetCode: "m1", targetName: "模型一", primary: true },
      { targetType: "MODEL", targetId: 2, targetCode: "m2", targetName: "模型二" }
    ];
    const wrapper = await openDrawer({ targets: TWO_TARGETS });

    expect(protocolOptionLabels(wrapper)).toEqual(["CHAT_COMPLETIONS", "RESPONSES"]);

    // 切到次目标：只剩它自己的协议（并集必须随选择重算，不能沿用全部目标的结果）
    selectOf(wrapper, 0).vm.$emit("update:modelValue", "MODEL:2");
    await flushPromises();
    expect(protocolOptionLabels(wrapper)).toEqual(["RESPONSES"]);
  });

  it("切到没有协议的目标：已选协议被清空而不是保留上一个目标的协议", async () => {
    const TWO_TARGETS = [
      { targetType: "MODEL", targetId: 1, targetCode: "m1", primary: true },
      { targetType: "MODEL", targetId: 2, targetCode: "m2" }
    ];
    // 接口只给 target 1 协议，target 2 无协议
    const wrapper = await openDrawer({ targets: TWO_TARGETS });
    expect(protocolOptionLabels(wrapper)).toEqual(["CHAT_COMPLETIONS"]);
    expect(selectOf(wrapper, 1).props("modelValue")).toBe("CHAT_COMPLETIONS");

    selectOf(wrapper, 0).vm.$emit("update:modelValue", "MODEL:2");
    await flushPromises();

    // ⚠ 必须断言**状态里的 protocol**。只断言「下拉选项为空」抓不到：选项是派生视图，
    // 状态没清时选项一样是空的（探针实证：删掉 watch 里的 syncCurrentProtocol，那种断言照样绿，
    // 而真实后果是拿着上一个目标的协议发请求）。
    expect(selectOf(wrapper, 1).props("modelValue")).toBe("");
    expect(protocolOptionLabels(wrapper)).toEqual([]);
  });

  // ==================== 概览页取值（displayContent / displayReasoning） ====================

  it("概览页：完整内容取 content、思考过程单独取 reasoning_content", async () => {
    vi.mocked(testRoutingRule).mockResolvedValue({
      status: 200,
      headers: {},
      data: { choices: [{ message: { content: "回答正文", reasoning_content: "思考正文" } }] }
    } as never);

    const wrapper = await openDrawer();
    await toolbarButton(wrapper, "primary")!.trigger("click");
    await flushPromises();
    await openTab(wrapper, "概览");

    const text = wrapper.text();
    expect(text).toContain("回答正文");
    expect(text).toContain("思考正文");
  });

  it("概览页：没有 content 时用 legacy completions 的 choices[].text 兜底", async () => {
    vi.mocked(testRoutingRule).mockResolvedValue({
      status: 200,
      headers: {},
      data: { choices: [{ text: "legacy 正文" }] }
    } as never);

    const wrapper = await openDrawer();
    await toolbarButton(wrapper, "primary")!.trigger("click");
    await flushPromises();
    await openTab(wrapper, "概览");

    expect(wrapper.text()).toContain("legacy 正文");
  });

  it("概览页：content 与 legacy text 同时存在时优先 content（兜底顺序不能反）", async () => {
    vi.mocked(testRoutingRule).mockResolvedValue({
      status: 200,
      headers: {},
      data: { choices: [{ message: { content: "新式正文" }, text: "旧式正文" }] }
    } as never);

    const wrapper = await openDrawer();
    await toolbarButton(wrapper, "primary")!.trigger("click");
    await flushPromises();
    await openTab(wrapper, "概览");

    const text = wrapper.text();
    expect(text).toContain("新式正文");
    expect(text).not.toContain("旧式正文");
  });

  it("概览页：流式拼装内容优先于响应体反解", async () => {
    vi.mocked(testRoutingRule).mockResolvedValue({
      status: 200,
      headers: {},
      data: { choices: [{ message: { content: "非流式正文" } }] }
    } as never);

    const wrapper = await openDrawer();
    await toolbarButton(wrapper, "primary")!.trigger("click");
    await flushPromises();

    h.stream.streamContent.value = "流式正文";
    await nextTick();
    await openTab(wrapper, "概览");

    const text = wrapper.text();
    expect(text).toContain("流式正文");
    expect(text).not.toContain("非流式正文");
  });

  it("请求区外壳：.test-form 挂在折叠面板上（无表单项的 el-form 空壳已移除）", async () => {
    const wrapper = await openDrawer();
    expect(wrapper.find(".el-collapse.test-form").exists()).toBe(true);
    expect(wrapper.find("form").exists()).toBe(false);
  });

  // ==================== 异步任务（202 → 轮询 → 终态） ====================

  /** 202 回执：三字段就是全部寻址信息 */
  const ACCEPTED_BODY = {
    task_id: "tc-abc",
    status: "queued",
    query_url: "/openai/v1/tasks/tc-abc"
  };

  it("⭐ 202 回执：出现「异步任务」tab 并开始轮询，且此刻不发起计费校验", async () => {
    vi.mocked(testRoutingRule).mockResolvedValue({
      status: 202,
      headers: {},
      data: ACCEPTED_BODY
    } as never);

    const wrapper = await openDrawer();
    await toolbarButton(wrapper, "primary")!.trigger("click");
    await flushPromises();

    expect(tabLabels(wrapper)).toContain("异步任务");
    // 自动停在异步页：结果还没出来，停在 Body 会让人以为「就这样了」
    expect(activeTabLabel(wrapper)).toBe("异步任务");
    expect(h.async.start).toHaveBeenCalledWith({
      taskCode: "tc-abc",
      queryUrl: "/openai/v1/tasks/tc-abc"
    });
    // 提交响应早于结算，此刻没有 knot_extend；硬跑校验只会得到「无被告」
    expect(h.verdict.triggerBillingVerdict).not.toHaveBeenCalled();
  });

  it("200 响应不出现异步 tab（同步链路不受影响）", async () => {
    vi.mocked(testRoutingRule).mockResolvedValue({
      status: 200,
      headers: {},
      data: { id: "chatcmpl-1" }
    } as never);

    const wrapper = await openDrawer();
    await toolbarButton(wrapper, "primary")!.trigger("click");
    await flushPromises();

    expect(tabLabels(wrapper)).not.toContain("异步任务");
    expect(h.async.start).not.toHaveBeenCalled();
  });

  it("⭐ 终态 completed：证据来自终态响应本身，裁判按异步口径发起", async () => {
    vi.mocked(testRoutingRule).mockResolvedValue({
      status: 202,
      headers: {},
      data: ACCEPTED_BODY
    } as never);

    const wrapper = await openDrawer();
    await toolbarButton(wrapper, "primary")!.trigger("click");
    await flushPromises();

    const terminalBody = {
      task_id: "tc-abc",
      status: "completed",
      result: { result_url: "https://x/a.mp4", result_urls: ["https://x/a.mp4"] },
      knot_extend: { billing: { totalCost: "0.5" } },
      upstream_body: { data: { task_result: { videos: [{ duration: "5" }] } } }
    };
    h.async.ctx.onTerminal({ status: "completed", body: terminalBody });
    await flushPromises();

    // 终态响应体整体交给被告提取（与同步链路同一套）
    expect(h.verdict.captureKnotExtend).toHaveBeenCalledWith(terminalBody);
    // 关键：upstreamBody 来自 upstream_body、taskStatus 来自 status——视频分档只认这两样
    expect(h.verdict.triggerBillingVerdict).toHaveBeenCalledWith(
      {},
      false,
      {
        upstreamBody: { data: { task_result: { videos: [{ duration: "5" }] } } },
        taskStatus: "completed"
      }
    );
  });

  it("终态 failed：写上失败原因，但不发起计费校验（本就不计费）", async () => {
    vi.mocked(testRoutingRule).mockResolvedValue({
      status: 202,
      headers: {},
      data: ACCEPTED_BODY
    } as never);

    const wrapper = await openDrawer();
    await toolbarButton(wrapper, "primary")!.trigger("click");
    await flushPromises();

    h.async.ctx.onTerminal({
      status: "failed",
      body: { status: "failed", error: { code: "UPSTREAM_TASK_FAILED", message: "boom" } }
    });
    // 真实 composable 会把 status / error 写进自己的 ref；这里同步驱动桩状态
    h.async.taskStatus.value = "failed";
    h.async.error.value = { code: "UPSTREAM_TASK_FAILED", message: "boom" };
    await flushPromises();

    expect(h.verdict.triggerBillingVerdict).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain("UPSTREAM_TASK_FAILED");
    // 失败页必须写清「不计费」：否则用户会以为失败也要付钱
    expect(wrapper.text()).toContain("本任务不计费");
  });

  it("关闭抽屉：停止轮询并释放资源", async () => {
    vi.mocked(testRoutingRule).mockResolvedValue({
      status: 202,
      headers: {},
      data: ACCEPTED_BODY
    } as never);

    const wrapper = await openDrawer();
    await toolbarButton(wrapper, "primary")!.trigger("click");
    await flushPromises();

    wrapper.findComponent(ElDrawer).vm.$emit("closed");
    await nextTick();

    expect(h.async.disposeAsyncPolling).toHaveBeenCalled();
    expect(h.async.resetAsyncState).toHaveBeenCalled();
  });

  it("视频协议却按同步返回：概览页给出「未生成任务句柄」的指路文案", async () => {
    vi.mocked(listRoutingRuleDebugProtocols).mockResolvedValue({
      targets: [{ targetType: "MODEL", targetId: 1, protocols: ["VIDEO_GENERATIONS"] }]
    } as never);
    vi.mocked(testRoutingRule).mockResolvedValue({
      status: 200,
      headers: {},
      data: { data: [{ status: "succeed" }] }
    } as never);

    const wrapper = await openDrawer();
    await toolbarButton(wrapper, "primary")!.trigger("click");
    await flushPromises();
    await openTab(wrapper, "概览");

    expect(wrapper.find(".async-handle-missing").exists()).toBe(true);
    expect(wrapper.text()).toContain("未生成任务句柄");
    expect(wrapper.text()).toContain("async_profile_code");
  });

  it("非视频协议不给「未生成句柄」提示（不猜别的协议该不该异步）", async () => {
    vi.mocked(testRoutingRule).mockResolvedValue({
      status: 200,
      headers: {},
      data: { id: "x" }
    } as never);

    const wrapper = await openDrawer();
    await toolbarButton(wrapper, "primary")!.trigger("click");
    await flushPromises();
    await openTab(wrapper, "概览");

    expect(wrapper.find(".async-handle-missing").exists()).toBe(false);
  });

  it("复制流式区块：按钮瞬时反馈成「已复制」，1.2s 后复位", async () => {
    vi.useFakeTimers();
    try {
      vi.mocked(testRoutingRule).mockResolvedValue({
        status: 200,
        headers: {},
        data: { id: "x" }
      } as never);

      const wrapper = await openDrawer();
      await toolbarButton(wrapper, "primary")!.trigger("click");
      await flushPromises();

      h.stream.isStreaming.value = true;
      h.stream.streamBlocks.value = [{ id: 7, data: "data: hi" }];
      await nextTick();

      await openTab(wrapper, "流式响应");
      const copy = wrapper.find(".stream-block__copy");
      expect(copy.exists()).toBe(true);
      expect(copy.attributes("aria-label")).toBe("复制");

      await copy.trigger("click");
      await flushPromises();
      expect(wrapper.find(".stream-block__copy").attributes("aria-label")).toBe("已复制");

      vi.advanceTimersByTime(1300);
      await nextTick();
      expect(wrapper.find(".stream-block__copy").attributes("aria-label")).toBe("复制");
    } finally {
      vi.useRealTimers();
    }
  });
});

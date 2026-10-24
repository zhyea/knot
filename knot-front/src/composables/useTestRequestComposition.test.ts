import { describe, it, expect, vi, beforeEach } from "vitest";
import { computed, nextTick, ref } from "vue";

const warningSpy = vi.hoisted(() => vi.fn());

// 本 composable 只用到 ElMessage.warning（重名提示），不需要任何组件
vi.mock("element-plus", () => ({ ElMessage: { warning: warningSpy } }));

import { useTestRequestComposition } from "@/composables/useTestRequestComposition";
import type { TestRequestCompositionContext } from "@/composables/useTestRequestComposition";

/**
 * 请求构造侧的直接单测。
 *
 * 为什么在抽屉已有 12 例接线测试之后还要单独测它：接线测试只覆盖「抽屉把输出接到模板」，
 * 覆盖不到**输出的内容**。而这里有两处最容易悄悄错、且错了不会报错的逻辑：
 *   1. 请求头合成（表格权威 / 必填头补默认 / 临时测试头去重）——错了就是"请求能发出去但行为不对"；
 *   2. curl 预览的单引号转义——错了是"复制出来的命令执行失败"。
 */
function makeCtx(overrides: Partial<TestRequestCompositionContext> = {}): TestRequestCompositionContext {
  const protocol = overrides.protocol ?? computed(() => "CHAT_COMPLETIONS");
  return {
    secretKey: () => "sk-demo",
    ruleCode: () => "rule-abc",
    targetKey: () => "__ALL__",
    protocol,
    billingVerifyEnabled: ref(true),
    consumerReturnUsageDetail: () => false,
    canonicalOf: (code) => String(code ?? ""),
    gatewayPathOf: (code) => `/v1/${String(code ?? "").toLowerCase()}`,
    listPresetOptions: vi.fn().mockResolvedValue({ list: [], total: 0 }),
    loadPresetDetail: vi.fn().mockResolvedValue(null),
    ...overrides
  } as TestRequestCompositionContext;
}

describe("useTestRequestComposition 请求构造", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("requestUrl = 网关基址 + 后端下发的调试路径", () => {
    const api = useTestRequestComposition(makeCtx());
    // 基址去尾斜杠，路径来自 gatewayPathOf(canonicalOf(protocol))
    expect(api.requestUrl.value).toBe("http://127.0.0.1:9090/v1/chat_completions");
  });

  it("resetHeaderRows 生成三行默认头：密钥进 Authorization，规则编码进 Rule", () => {
    const api = useTestRequestComposition(makeCtx());
    api.resetHeaderRows();
    expect(api.headerRows.value).toEqual([
      { name: "Authorization", value: "Bearer sk-demo" },
      { name: "Rule", value: "rule-abc" },
      { name: "Content-Type", value: "application/json" }
    ]);
  });

  it("密钥 / 规则编码为空时用占位符，而不是留空导致请求头值为空串", () => {
    const api = useTestRequestComposition(
      makeCtx({ secretKey: () => "   ", ruleCode: () => "" })
    );
    api.resetHeaderRows();
    expect(api.headerRows.value[0].value).toBe("Bearer sk-your-routing-secret-key");
    expect(api.headerRows.value[1].value).toBe("your-rule-code");
  });

  it("buildRequestHeaders：表格是权威——删掉的行就不发送", () => {
    const api = useTestRequestComposition(makeCtx());
    api.resetHeaderRows();
    api.removeHeaderRow(api.headerRows.value[2]); // 删掉 Content-Type
    const headers = api.buildRequestHeaders("application/json");
    // 表格里没有 Content-Type 时会补默认，但值来自默认而非残留
    expect(headers["Content-Type"]).toBe("application/json");
    expect(headers.Authorization).toBe("Bearer sk-demo");
    expect(headers.Rule).toBe("rule-abc");
  });

  it("buildRequestHeaders：表格填了同名头则以表格为准，不被默认值覆盖", () => {
    const api = useTestRequestComposition(makeCtx());
    api.resetHeaderRows();
    api.headerRows.value[2].value = "application/json; charset=utf-8";
    const headers = api.buildRequestHeaders("text/event-stream");
    expect(headers["Content-Type"]).toBe("application/json; charset=utf-8");
    // 表格里没有 Accept → 补传入的 accept
    expect(headers.Accept).toBe("text/event-stream");
  });

  it("临时测试头：开关开 + 消费者未默认返回 + 表格没有 → 自动补且标记为 temporary", () => {
    const api = useTestRequestComposition(makeCtx());
    api.resetHeaderRows();
    const headers = api.buildRequestHeaders("application/json");
    expect(headers["X-Knot-Test-Return-Usage"]).toBe("true");
    expect(api.displayHeaderRows.value.some((row) => row.temporary)).toBe(true);
  });

  it("临时测试头：消费者已默认返回时不补（重复头无意义）", () => {
    const api = useTestRequestComposition(makeCtx({ consumerReturnUsageDetail: () => true }));
    api.resetHeaderRows();
    expect(api.buildRequestHeaders("application/json")["X-Knot-Test-Return-Usage"]).toBeUndefined();
  });

  it("临时测试头：开关关闭时不补", () => {
    const api = useTestRequestComposition(makeCtx({ billingVerifyEnabled: ref(false) }));
    api.resetHeaderRows();
    expect(api.buildRequestHeaders("application/json")["X-Knot-Test-Return-Usage"]).toBeUndefined();
  });

  it("临时测试头：表格里手动填了同名头则不覆盖", () => {
    const api = useTestRequestComposition(makeCtx());
    api.resetHeaderRows();
    api.headerRows.value.push({ name: "x-knot-test-return-usage", value: "false" });
    const headers = api.buildRequestHeaders("application/json");
    // 大小写不敏感：表格填的小写版本生效，不再追加一行
    expect(headers["x-knot-test-return-usage"]).toBe("false");
    expect(headers["X-Knot-Test-Return-Usage"]).toBeUndefined();
  });

  it("validateHeaderRows：重名（大小写不敏感）报错，空名称行跳过", () => {
    const api = useTestRequestComposition(makeCtx());
    api.resetHeaderRows();
    expect(api.validateHeaderRows()).toBeNull();

    api.addHeaderRow(); // 空名称行
    expect(api.validateHeaderRows()).toBeNull();

    api.headerRows.value.push({ name: "authorization", value: "dup" });
    expect(api.validateHeaderRows()).toBe("请求头「authorization」重名，请合并为一行");
  });

  it("normalizeHeaderRow：trim 掉首尾空格，重名时给提示（不阻断编辑）", async () => {
    const api = useTestRequestComposition(makeCtx());
    api.resetHeaderRows();
    const row = { name: "  Rule  ", value: "x" };
    api.normalizeHeaderRow(row);
    expect(row.name).toBe("Rule");
    await nextTick();
    expect(warningSpy).toHaveBeenCalledTimes(1);
    expect(String(warningSpy.mock.calls[0][0])).toContain("Rule");
  });

  it("curlPreview：含 URL / 头部 / 请求体，且单引号被转义（否则复制出来执行会断）", () => {
    const api = useTestRequestComposition(makeCtx());
    api.resetHeaderRows();
    api.currentTemplateText.value = JSON.stringify({ prompt: "it's fine" });

    const curl = api.curlPreview.value;
    expect(curl).toContain("curl -X POST 'http://127.0.0.1:9090/v1/chat_completions'");
    expect(curl).toContain("-H 'Authorization: Bearer sk-demo'");
    // 请求体里的单引号必须转成 '\'' 才能安全嵌进单引号字符串
    expect(curl).toContain("'\\''");
    expect(curl).toContain("-d '");
  });

  it("模板按 target+protocol 分槽：切目标不会串台", () => {
    const targetKey = ref("__ALL__");
    const protocol = ref("CHAT_COMPLETIONS");
    const api = useTestRequestComposition(
      makeCtx({ targetKey: () => targetKey.value, protocol: computed(() => protocol.value) })
    );

    api.currentTemplateText.value = '{"a":1}';
    targetKey.value = "MODEL:1";
    api.currentTemplateText.value = '{"b":2}';

    expect(api.currentTemplateText.value).toBe('{"b":2}');
    targetKey.value = "__ALL__";
    expect(api.currentTemplateText.value).toBe('{"a":1}');
  });

  it("filteredPresetOptions：无协议时为空，有协议时按 protocolCode 过滤", async () => {
    const protocol = ref("");
    const api = useTestRequestComposition(makeCtx({ protocol: computed(() => protocol.value) }));
    await api.loadPresetOptions();
    expect(api.filteredPresetOptions.value).toEqual([]);

    protocol.value = "CHAT_COMPLETIONS";
    await nextTick();
    expect(api.filteredPresetOptions.value).toEqual([]);
  });

  it("无可用预设时 ensureTemplateForCurrentSelection 兜底为空对象文本", () => {
    const api = useTestRequestComposition(makeCtx());
    api.ensureTemplateForCurrentSelection();
    // "{}" 是合法 JSON，保证 runTest 不会在解析阶段就早退
    expect(api.currentTemplateText.value).toBe("{}");
    expect(api.parsedTemplateBody.value.error).toBeFalsy();
  });

  it("载入预设时丢弃过期结果：加载期间切了别的预设，旧响应不覆盖新选择", async () => {
    let resolveDetail: (v: unknown) => void = () => {};
    const slowDetail = new Promise((resolve) => {
      resolveDetail = resolve;
    });
    const api = useTestRequestComposition(
      makeCtx({
        listPresetOptions: vi.fn().mockResolvedValue({
          list: [
            { value: 1, label: "P1", meta: { protocolCode: "CHAT_COMPLETIONS" } },
            { value: 2, label: "P2", meta: { protocolCode: "CHAT_COMPLETIONS" } }
          ],
          total: 2
        }),
        loadPresetDetail: vi.fn().mockImplementation((id) =>
          String(id) === "1" ? slowDetail : Promise.resolve({ requestBody: '{"from":"p2"}' })
        )
      })
    );

    await api.loadPresetOptions();
    const slow = api.onPresetChange(1); // 慢请求发起后立刻切走
    await api.onPresetChange(2);
    resolveDetail({ requestBody: '{"from":"p1"}' }); // 慢请求现在才回来
    await slow;

    // 过期响应的结果被丢弃，模板仍是 P2 的
    expect(api.currentTemplateText.value).toContain("p2");
  });
});

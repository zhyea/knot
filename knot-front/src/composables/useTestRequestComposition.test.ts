import { describe, it, expect, vi, beforeEach } from "vitest";
import { computed, nextTick, ref, type Ref } from "vue";

const warningSpy = vi.hoisted(() => vi.fn());

// 本 composable 只用到 ElMessage.warning（重名提示），不需要任何组件
vi.mock("element-plus", () => ({ ElMessage: { warning: warningSpy } }));

import { useTestRequestComposition } from "@/composables/useTestRequestComposition";
import type {
  TestPresetOption,
  TestRequestCompositionContext
} from "@/composables/useTestRequestComposition";

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
    listPresetOptions: vi.fn().mockResolvedValue([]),
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
        listPresetOptions: vi.fn().mockResolvedValue([
          { id: 1, name: "P1", protocolCode: "CHAT_COMPLETIONS", modelFamily: null },
          { id: 2, name: "P2", protocolCode: "CHAT_COMPLETIONS", modelFamily: null }
        ]),
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

  /**
   * 预设 ↔ 模板的会话边界（2026-11-25 回归）。
   *
   * <p>实测缺陷：抽屉是长期存活的单实例，先打开 gaterouter-qwen-image-2.0（预设 674「qwen图像生成」），
   * 关闭后打开 gaterouter-seedream-4.5，选中框仍显示「qwen图像生成」、模板仍是 Qwen 的
   * input.messages 结构，而 Seedream 的预设列表里只有 10「文生图」。两条规则的调试目标都是 ALL、
   * 协议都是 IMAGE_GENERATIONS，所以槽位 key 相同，旧内容被直接复用。</p>
   *
   * <p>这组用例钉住的是「预设列表落地」这一个校正点：选中项必须属于当前协议的可选项集，
   * 不满足时选中态与模板必须一起作废。</p>
   */
  describe("预设与模板的会话边界", () => {
    const PROTOCOL = "IMAGE_GENERATIONS";

    /** detail 加载是 fire-and-forget：断言前先让宏任务跑一轮 */
    const settle = () => new Promise<void>((resolve) => setTimeout(resolve, 0));

    const QWEN_PRESET: TestPresetOption = {
      id: 674, name: "qwen图像生成", protocolCode: PROTOCOL, modelFamily: "qwen"
    };
    const COMMON_PRESET: TestPresetOption = {
      id: 10, name: "文生图", protocolCode: PROTOCOL, modelFamily: null
    };

    /** 两条规则共用协议与调试目标，仅预设作用域不同的调试上下文 */
    function ruleSwitchCtx(
      ruleCode: Ref<string>,
      presets: Record<string, TestPresetOption[]>,
      bodies: Record<string, string>
    ): TestRequestCompositionContext {
      return makeCtx({
        ruleCode: () => ruleCode.value,
        protocol: computed(() => PROTOCOL),
        listPresetOptions: vi.fn(async () => presets[ruleCode.value] ?? []),
        loadPresetDetail: vi.fn(async (id) => ({ requestBody: bodies[String(id)] ?? "{}" }))
      });
    }

    it("⭐ 换规则：旧预设不在新列表时，选中项与模板一起换掉（qwen 模板串到 seedream）", async () => {
      const ruleCode = ref("gaterouter-qwen-image-2.0");
      const api = useTestRequestComposition(
        ruleSwitchCtx(
          ruleCode,
          {
            "gaterouter-qwen-image-2.0": [QWEN_PRESET, COMMON_PRESET],
            "gaterouter-seedream-4.5": [COMMON_PRESET]
          },
          {
            "674": '{"input":{"messages":[{"role":"user","content":"qwen"}]}}',
            "10": '{"prompt":"a cat","size":"2K"}'
          }
        )
      );

      await api.loadPresetOptions();
      await settle();
      expect(api.selectedPresetId.value).toBe(674);
      expect(api.currentTemplateText.value).toContain("qwen");

      ruleCode.value = "gaterouter-seedream-4.5";
      await api.loadPresetOptions();
      await settle();

      // 674 不属于 Seedream：选中项必须落到它自己的第一条预设，正文不能还是 Qwen 的
      expect(api.selectedPresetId.value).toBe(10);
      expect(api.currentTemplateText.value).toContain("a cat");
      expect(api.currentTemplateText.value).not.toContain("qwen");
    });

    it("换规则不共用模板槽位：A 的草稿不落到 B，切回 A 仍能恢复", async () => {
      const ruleCode = ref("rule-a");
      const api = useTestRequestComposition(
        ruleSwitchCtx(
          ruleCode,
          { "rule-a": [COMMON_PRESET], "rule-b": [COMMON_PRESET] },
          { "10": '{"prompt":"seeded"}' }
        )
      );

      await api.loadPresetOptions();
      await settle();
      api.currentTemplateText.value = '{"prompt":"A 手改的草稿"}';

      ruleCode.value = "rule-b";
      await api.loadPresetOptions();
      await settle();
      expect(api.currentTemplateText.value).not.toContain("A 手改的草稿");

      // 切回 A：该规则的草稿还在（槽位按规则隔离，不是一律清空）
      ruleCode.value = "rule-a";
      await api.loadPresetOptions();
      await settle();
      expect(api.currentTemplateText.value).toContain("A 手改的草稿");
    });

    it("切目标后已选预设不在新目标列表：换选中并换模板（同一根因的另一条路径）", async () => {
      const targetKey = ref("MODEL:1");
      const api = useTestRequestComposition(
        makeCtx({
          targetKey: () => targetKey.value,
          protocol: computed(() => PROTOCOL),
          listPresetOptions: vi.fn(async () =>
            targetKey.value === "MODEL:1"
              ? [{ id: 2, name: "目标一预设", protocolCode: PROTOCOL, modelFamily: null }]
              : [{ id: 3, name: "目标二预设", protocolCode: PROTOCOL, modelFamily: null }]
          ),
          loadPresetDetail: vi.fn(async (id) => ({
            requestBody: String(id) === "2" ? '{"prompt":"p2"}' : '{"prompt":"p3"}'
          }))
        })
      );

      await api.loadPresetOptions();
      await settle();
      expect(api.selectedPresetId.value).toBe(2);

      targetKey.value = "MODEL:2";
      await api.loadPresetOptions();
      await settle();

      expect(api.selectedPresetId.value).toBe(3);
      expect(api.currentTemplateText.value).toContain("p3");
    });

    it("切目标后选中项仍有效但槽位为空：按选中项补载，不重置成第一条", async () => {
      const targetKey = ref("MODEL:1");
      const shared: TestPresetOption[] = [
        COMMON_PRESET,
        { id: 11, name: "通用第二例", protocolCode: PROTOCOL, modelFamily: null }
      ];
      const api = useTestRequestComposition(
        makeCtx({
          targetKey: () => targetKey.value,
          protocol: computed(() => PROTOCOL),
          listPresetOptions: vi.fn(async () => shared),
          loadPresetDetail: vi.fn(async (id) => ({
            requestBody: String(id) === "11" ? '{"prompt":"second"}' : '{"prompt":"first"}'
          }))
        })
      );

      await api.loadPresetOptions();
      await api.onPresetChange(11);
      expect(api.currentTemplateText.value).toContain("second");

      // 新目标的槽位是空的，但选中项（通用预设 11）依然合法 → 补载 11，不能顺手改成第一条 10
      targetKey.value = "MODEL:2";
      await api.loadPresetOptions();
      await settle();

      expect(api.selectedPresetId.value).toBe(11);
      expect(api.currentTemplateText.value).toContain("second");
    });

    it("切回旧目标：本目标的正文必须保留，只把失效的选中项改回与正文配对的那条", async () => {
      const targetKey = ref("MODEL:1");
      const api = useTestRequestComposition(
        makeCtx({
          targetKey: () => targetKey.value,
          protocol: computed(() => PROTOCOL),
          listPresetOptions: vi.fn(async () =>
            targetKey.value === "MODEL:1"
              ? [{ id: 2, name: "目标一预设", protocolCode: PROTOCOL, modelFamily: null }]
              : [{ id: 3, name: "目标二预设", protocolCode: PROTOCOL, modelFamily: null }]
          ),
          loadPresetDetail: vi.fn(async (id) => ({
            requestBody: String(id) === "2" ? '{"prompt":"p2"}' : '{"prompt":"p3"}'
          }))
        })
      );

      await api.loadPresetOptions();
      await settle();
      targetKey.value = "MODEL:2";
      await api.loadPresetOptions();
      await settle();
      expect(api.currentTemplateText.value).toContain("p3");

      // 切回目标一：槽位里的 p2 属于本上下文，不能因为选中项（3）失效就被清空重建
      targetKey.value = "MODEL:1";
      await api.loadPresetOptions();
      await settle();

      expect(api.currentTemplateText.value).toContain("p2");
      expect(api.selectedPresetId.value).toBe(2);
    });

    it("预设详情迟到：写入回到发起时的槽位，不污染切换后的目标槽位", async () => {
      const targetKey = ref("MODEL:1");
      let resolveSlow: (value: unknown) => void = () => {};
      const slow = new Promise((resolve) => {
        resolveSlow = resolve;
      });
      const api = useTestRequestComposition(
        makeCtx({
          targetKey: () => targetKey.value,
          protocol: computed(() => PROTOCOL),
          listPresetOptions: vi.fn(async () => [QWEN_PRESET]),
          loadPresetDetail: vi.fn(() => slow)
        })
      );

      // 列表落地 → 自动选 674 并开始载入请求体（挂起）
      await api.loadPresetOptions();
      const pending = api.onPresetChange(674);

      targetKey.value = "MODEL:2";
      resolveSlow({ requestBody: '{"input":{"messages":[{"content":"qwen"}]}}' });
      await pending;
      await settle();

      // 迟到的正文只能回到 MODEL:1 的槽位；新目标的槽位必须还是空的，
      // 否则「切目标后新槽位可写」会变成把旧目标的请求体带过去
      expect(api.currentTemplateText.value).toBe("");
    });

    it("并发加载：先发起但后返回的旧列表不覆盖后发起的结果", async () => {
      let resolveStale: (value: TestPresetOption[]) => void = () => {};
      const stale = new Promise<TestPresetOption[]>((resolve) => {
        resolveStale = resolve;
      });
      let calls = 0;
      const api = useTestRequestComposition(
        makeCtx({
          protocol: computed(() => PROTOCOL),
          listPresetOptions: vi.fn(() => {
            calls += 1;
            return calls === 1 ? stale : Promise.resolve([COMMON_PRESET]);
          }),
          loadPresetDetail: vi.fn(async () => ({ requestBody: '{"prompt":"fresh"}' }))
        })
      );

      const pending = api.loadPresetOptions();
      await api.loadPresetOptions();
      await settle();
      expect(api.selectedPresetId.value).toBe(10);

      // 打开抽屉时目标从空切到「全部目标」会并发触发第二次加载，旧结果必须按代次丢弃
      resolveStale([QWEN_PRESET]);
      await pending;
      await settle();

      expect(api.filteredPresetOptions.value.map((preset) => preset.id)).toEqual([10]);
      expect(api.currentTemplateText.value).toContain("fresh");
    });
  });

  it("filteredPresetOptions：后端已按作用域筛好，前端只按当前协议显示", async () => {
    const api = useTestRequestComposition(
      makeCtx({
        protocol: computed(() => "CHAT_COMPLETIONS"),
        listPresetOptions: vi.fn().mockResolvedValue([
          { id: 1, name: "当前协议-有族", protocolCode: "CHAT_COMPLETIONS", modelFamily: "openai" },
          { id: 2, name: "其他协议-有族", protocolCode: "RESPONSES", modelFamily: "openai" },
          { id: 3, name: "当前协议-通用", protocolCode: "CHAT_COMPLETIONS", modelFamily: null }
        ])
      })
    );

    await api.loadPresetOptions();
    expect(api.filteredPresetOptions.value.map((preset) => preset.id)).toEqual([1, 3]);
  });
});

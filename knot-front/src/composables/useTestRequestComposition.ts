import {computed, reactive, ref, type ComputedRef, type Ref} from "vue";
import {ElMessage} from "element-plus";
import type {Dict} from "@/types";
import {formatJson, formatJsonText, stringifyJson} from "@/utils/format";
import {safeParseTemplate, stripModelField} from "@/utils/routingTestPayload";

/** 网关基址：请求 URL 与 curl 预览共用同一份，避免两处各读一次 env */
const GATEWAY_BASE_URL = import.meta.env.VITE_GATEWAY_BASE_URL || "http://127.0.0.1:9090";

/**
 * 网关基址（已去尾斜杠）。
 *
 * <p>⭐ 异步任务轮询必须走这里：202 回执里的 {@code query_url} 是<b>相对路径</b>
 * （后端按端点前缀拼出，见 `AsyncTaskAcceptedResponse`），只有网关基址能把它补成可请求的地址。
 * 自己再读一次 env 就会得到第二份基址——改端口时只改一处，另一处静默指向旧地址。</p>
 */
export function gatewayBaseUrl(): string {
  const base = GATEWAY_BASE_URL.trim();
  return base.endsWith("/") ? base.slice(0, -1) : base;
}

/**
 * 管理面测试头：勾选「测试时临时开启返回消费详情」后随请求发往网关。
 * 与后端 `GatewayHeaders` 同款常量——两端必须一致，改一处即改另一处。
 */
export const TEST_RETURN_USAGE_HEADER = "X-Knot-Test-Return-Usage";

/** 预设请求下拉项：options 契约不含 requestBody（大字段），选中后按 id 懒加载 */
export interface TestPresetOption {
  id: number;
  name: string;
  protocolCode: string;
  /** 模型族 code；null = 通用用例，任何调试目标都可选用 */
  modelFamily: string | null;
}

/** 请求头表格行：抽屉打开时按当前密钥 / 规则编码生成默认三行，之后以表格为准 */
export interface HeaderRow {
  name: string;
  value: string;
}

export interface HeaderDisplayRow extends HeaderRow {
  temporary?: boolean;
}

/**
 * 请求构造的上下文：抽屉把「当前选择」与两个接口注入进来。
 *
 * <p>为什么走注入而不是直接 import api：本 composable 要能脱离 HTTP 单测——
 * curl 预览与「表格即权威」的请求头合成是两个最容易悄悄错的地方
 * （占位符没替换、必填头缺失、临时测试头重复），必须能单独钉住。</p>
 */
export interface TestRequestCompositionContext {
  /** 当前 API Key（生成默认 Authorization 头） */
  secretKey: () => string | null | undefined;
  /** 当前规则编码（生成默认 Rule 头） */
  ruleCode: () => string | null | undefined;
  /** 当前调试目标 key（模板按 target+protocol 分槽存放） */
  targetKey: () => string | null | undefined;
  /** 当前生效协议 code（归一后） */
  protocol: ComputedRef<string>;
  /** 「执行计费规则校验」开关：打开时请求头补临时测试头 */
  billingVerifyEnabled: Ref<boolean>;
  /** 规则绑定消费者是否已默认返回消费详情：已开启则无需临时头 */
  consumerReturnUsageDetail: () => boolean;
  /** 协议 code 归一（后端能力接口下发的 canonicalCode） */
  canonicalOf: (code: unknown) => string;
  /** 协议 → 网关调试路径（后端能力接口下发） */
  gatewayPathOf: (code: unknown) => string;
  /** 预设下拉项加载：后端调试上下文已按作用域筛好，前端只按协议显示 */
  listPresetOptions: () => Promise<TestPresetOption[]>;
  /** 按 id 懒加载预设详情（含 requestBody） */
  loadPresetDetail: (id: number | string) => Promise<Dict | null>;
}

/**
 * 路由规则「测试请求」的构造侧：协议路径 / URL / 请求头表格 / 请求模板 / 预设。
 *
 * <p>只管<b>发出去的东西长什么样</b>，不发请求、不判响应——发请求与错误分类留在抽屉，
 * 那是编排职责（3.6 定的边界：只抽状态，不抽编排）。</p>
 */
export function useTestRequestComposition(ctx: TestRequestCompositionContext) {
  // ===================== 协议路径与 URL =====================

  /** 网关调试路径由后端能力接口下发（与 RoutingRuleService.buildGatewayTestPath 同源） */
  function protocolPath(protocol: unknown): string {
    return ctx.gatewayPathOf(ctx.canonicalOf(protocol)) || "";
  }

  function normalizeBaseUrl(): string {
    return gatewayBaseUrl();
  }

  const requestUrl = computed(() => `${normalizeBaseUrl()}${protocolPath(ctx.protocol.value)}`);

  // ===================== 请求头表格（Headers tab 即权威） =====================

  const headerRows = ref<HeaderRow[]>([]);

  /** 表格里是否已存在同名头（HTTP 头大小写不敏感） */
  function hasHeaderName(name: string): boolean {
    const lower = name.toLowerCase();
    return headerRows.value.some((row) => row.name.trim().toLowerCase() === lower);
  }

  /** 是否需要补临时测试头：开关开 + 消费者未默认返回 + 表格里没手动填过 */
  function needsTemporaryUsageHeader(): boolean {
    return (
      ctx.billingVerifyEnabled.value &&
      !ctx.consumerReturnUsageDetail() &&
      !hasHeaderName(TEST_RETURN_USAGE_HEADER)
    );
  }

  const displayHeaderRows = computed<HeaderDisplayRow[]>(() => {
    const rows: HeaderDisplayRow[] = [...headerRows.value];
    if (needsTemporaryUsageHeader()) {
      rows.push({name: TEST_RETURN_USAGE_HEADER, value: "true", temporary: true});
    }
    return rows;
  });

  function resolveRuleHeaderValue(): string {
    return ctx.ruleCode()?.trim() || "your-rule-code";
  }

  /** 打开抽屉（或重置）时生成默认请求头：Authorization / Rule / Content-Type */
  function resetHeaderRows(): void {
    headerRows.value = [
      {
        name: "Authorization",
        value: `Bearer ${ctx.secretKey()?.trim() || "sk-your-routing-secret-key"}`
      },
      {name: "Rule", value: resolveRuleHeaderValue()},
      {name: "Content-Type", value: "application/json"}
    ];
  }

  function addHeaderRow(): void {
    headerRows.value.push({name: "", value: ""});
  }

  function removeHeaderRow(row: unknown): void {
    const index = headerRows.value.findIndex((item) => item === row);
    if (index >= 0) {
      headerRows.value.splice(index, 1);
    }
  }

  /** 名称失焦规范化：trim；重名即时警告（不阻断编辑，发送前还会硬校验） */
  function normalizeHeaderRow(row: unknown): void {
    if (!row || typeof row !== "object" || !("name" in row) || typeof row.name !== "string") {
      return;
    }
    const headerRow = row as HeaderRow;
    headerRow.name = headerRow.name.trim();
    if (!headerRow.name) {
      return;
    }
    const duplicated = headerRows.value.some(
      (item) =>
        item !== headerRow && item.name.trim().toLowerCase() === headerRow.name.toLowerCase()
    );
    if (duplicated) {
      ElMessage.warning(`请求头「${headerRow.name}」已存在，重名只有最后一条会生效`);
    }
  }

  /** 发送前校验：名称 trim 后不可重名（大小写不敏感），空名称行直接跳过 */
  function validateHeaderRows(): string | null {
    const seen = new Set<string>();
    for (const row of headerRows.value) {
      const name = row.name.trim();
      if (!name) {
        continue;
      }
      const lower = name.toLowerCase();
      if (seen.has(lower)) {
        return `请求头「${name}」重名，请合并为一行`;
      }
      seen.add(lower);
    }
    return null;
  }

  /**
   * 组装实际请求头：表格为权威（删除的行即不发送）；
   * 协议必需头 Content-Type / Accept 仅在表格缺失时补默认，表格填了则以表格为准。
   * 勾选临时开启时补测试头（表格已手动填同名头则不覆盖，保持表格权威）。
   */
  function buildRequestHeaders(accept: string): Record<string, string> {
    const headers: Record<string, string> = {};
    for (const row of headerRows.value) {
      const name = row.name.trim();
      if (name) {
        headers[name] = row.value;
      }
    }
    if (!hasHeaderName("Accept")) {
      headers.Accept = accept;
    }
    if (!hasHeaderName("Content-Type")) {
      headers["Content-Type"] = "application/json";
    }
    if (needsTemporaryUsageHeader()) {
      headers[TEST_RETURN_USAGE_HEADER] = "true";
    }
    return headers;
  }

  // ===================== 请求模板（按 规则 + target + protocol 分槽） =====================

  const templateStore = reactive<Dict>({});

  /**
   * 模板槽位：规则 :: 调试目标 :: 协议。
   *
   * <p>⭐ 规则必须进键。抽屉在列表页是<b>单实例长期存活</b>：`destroy-on-close` 只销毁抽屉内容，
   * 不销毁组件与 composable 状态。此前键里只有 target+protocol，而两条规则的调试目标默认都是
   * 「全部目标」、协议都是 IMAGE_GENERATIONS，槽位完全相同 → 上一条规则的请求体被下一条直接复用
   * （实测：先开 qwen 图像规则再开 seedream，选中框仍显示「qwen图像生成」、正文仍是 input.messages）。</p>
   *
   * <p>为什么用规则编码而不是 ruleId：ctx 已为 Rule 头提供规则编码，再挂一个只用于分槽的 id 访问器
   * 就是同一事实的第二个来源；编码唯一且随抽屉打开注入，够用。</p>
   */
  const activeTemplateKey = computed(
    () => `${ctx.ruleCode() || "default"}::${ctx.targetKey() || "default"}::${ctx.protocol.value || "default"}`
  );

  const currentTemplateText = computed({
    get(): string {
      return templateStore[activeTemplateKey.value] || "";
    },
    set(value: string) {
      templateStore[activeTemplateKey.value] = value;
    }
  });

  const parsedTemplateBody = computed(() => safeParseTemplate(currentTemplateText.value));

  // ===================== 预设请求 =====================

  const presetOptions = ref<TestPresetOption[]>([]);
  const selectedPresetId = ref<number | string | null>(null);

  /**
   * 预设列表加载代次：只让最后一次发起的加载落地。
   *
   * <p>打开抽屉会并发触发两次加载——抽屉 watch 里的 debug-context 一次，目标从空切到
   * 「全部目标」时 targetKey watch 再一次。先发起的请求拿到的是上一作用域的旧列表（甚至空表），
   * 它若后到，会把刚播种好的选中项与模板一起覆盖掉。</p>
   */
  let presetLoadSeq = 0;

  const filteredPresetOptions = computed(() => {
    const protocol = ctx.protocol.value;
    if (!protocol) {
      return [];
    }
    // 模型族 / 目标作用域的筛选已在后端 debug-context 完成；前端只按当前协议显示
    return presetOptions.value.filter((preset) => preset.protocolCode === protocol);
  });

  async function loadPresetOptions(): Promise<void> {
    const seq = ++presetLoadSeq;
    let options: TestPresetOption[] = [];
    try {
      options = await ctx.listPresetOptions();
    } catch {
      options = [];
    }
    if (seq !== presetLoadSeq) {
      // 已有更晚的加载发起：本次列表属于旧作用域，整批丢弃
      return;
    }
    presetOptions.value = options;
    // 列表落地是唯一校正点：换了规则 / 目标 / 协议后的选中项与模板都在这里对齐
    reconcilePresetSelection();
  }

  async function onPresetChange(presetId: number | string | null): Promise<void> {
    // 同步选中态：用户交互由 v-model 赋值，但程序化调用需在此显式设置
    selectedPresetId.value = presetId;
    if (!presetId) {
      return;
    }
    const preset = presetOptions.value.find((item) => String(item.id) === String(presetId));
    if (!preset) {
      return;
    }
    // 载入期间规则 / 目标可能切换：写入必须回到发起时的槽位，否则旧规则的请求体会落进新槽位
    const slotKey = activeTemplateKey.value;
    // options 契约不承载 requestBody（大字段），选中后按 id 懒加载预设详情
    let requestBody = "{}";
    try {
      const detail = await ctx.loadPresetDetail(preset.id);
      // 加载期间用户又切了别的预设：丢弃过期结果，避免覆盖新选择
      if (String(selectedPresetId.value) !== String(presetId)) {
        return;
      }
      requestBody = detail?.requestBody || "{}";
    } catch {
      requestBody = "{}";
    }
    const parsed = safeParseTemplate(requestBody);
    const base = parsed.error ? {} : parsed.value;
    templateStore[slotKey] = formatJson(stripModelField(base));
  }

  /**
   * 选中态与模板的一致性校正（**唯一入口**：预设列表每次落地都走这里）。
   *
   * <p>三条规则，按优先级：</p>
   * <ol>
   *   <li>选中项仍属于本作用域且本槽位没有正文 → 按它补载（换目标 / 换协议后槽位是空的）；</li>
   *   <li>本槽位已有正文 → 绝不覆盖，只把失效的选中项改掉。槽位键含规则，所以这里的正文
   *       只可能来自本上下文（预设播种或用户编辑）；反过来，选中项失效恰恰说明它来自别处。</li>
   *   <li>无正文、无可用选中项 → 播种第一条预设的请求体；没有预设可选用时留 "{}"，
   *       保证 runTest 不会在解析阶段早退。</li>
   * </ol>
   *
   * <p>为什么不「换规则就清空所有槽位」：预设是全局实体（同一 id 的 requestBody 与规则无关），
   * 本作用域仍可选用的选中项应当保留；跨规则草稿由槽位键隔离，不需要靠清空来解决串台。</p>
   */
  function reconcilePresetSelection(): void {
    const slotKey = activeTemplateKey.value;
    const options = filteredPresetOptions.value;
    const current = selectedPresetId.value;
    const hasSelection = current != null && current !== "";
    const selectionValid = hasSelection
      && options.some((preset) => String(preset.id) === String(current));
    const text = (templateStore[slotKey] || "").trim();
    const hasBody = !!text && text !== "{}";

    if (selectionValid) {
      if (hasBody) {
        return;
      }
      void onPresetChange(current);
      return;
    }

    // 选中项失效（换了规则 / 目标 / 协议）：先清掉，避免选中框显示一个下拉里不存在的预设
    // （Element Plus 会沿用 cachedOptions 里的旧 label，看起来像「选中了 qwen 预设」）
    if (hasSelection) {
      selectedPresetId.value = null;
    }

    if (hasBody) {
      // 本上下文的正文不能因为选中项失效就丢：只补一个合法选中项，正文保持不动
      selectedPresetId.value = options[0]?.id ?? null;
      return;
    }

    const first = options[0];
    if (!first) {
      if (!text) {
        templateStore[slotKey] = "{}";
      }
      return;
    }
    void onPresetChange(first.id);
  }

  /**
   * 协议切换 / 首次进入时确保模板与选中项一致。
   * 空槽位播种、选中项失效、正文为空白占位三种情况都由 {@link reconcilePresetSelection} 处理。
   */
  function ensureTemplateForCurrentSelection(): void {
    if (!ctx.protocol.value || !activeTemplateKey.value) {
      return;
    }
    reconcilePresetSelection();
  }

  function resetCurrentTemplate(): void {
    if (!ctx.protocol.value) {
      return;
    }
    selectedPresetId.value = null;
    templateStore[activeTemplateKey.value] = "{}";
  }

  // ===================== curl 预览 =====================

  function buildCurlCommand(): string {
    const json = parsedTemplateBody.value.error
      ? currentTemplateText.value || "{}"
      : stringifyJson(parsedTemplateBody.value.value || {});
    const escapedJson = json.replace(/'/g, "'\\''");
    // 请求头与「Headers」表格同源：用户在表格里的增删改即时反映到 curl 预览
    const headerLines = Object.entries(buildRequestHeaders("application/json"))
      .map(([name, value]) => `  -H '${name}: ${value.replace(/'/g, "'\\''")}'`)
      .join(" \\\n");
    return (
      `curl -X POST '${requestUrl.value}' \\\n` +
      `${headerLines} \\\n` +
      `  -d '${escapedJson}'`
    );
  }

  const curlPreview = computed(() => buildCurlCommand());

  /** 响应体格式化：非流式整包用，空值给空串（调用方负责占位符） */
  function formatBody(body: unknown): string {
    if (!body) return "";
    return formatJsonText(body, 2, String(body));
  }

  return {
    // 协议与 URL
    requestUrl,
    // 请求头表格
    headerRows,
    displayHeaderRows,
    resetHeaderRows,
    addHeaderRow,
    removeHeaderRow,
    normalizeHeaderRow,
    validateHeaderRows,
    buildRequestHeaders,
    // 模板
    currentTemplateText,
    parsedTemplateBody,
    ensureTemplateForCurrentSelection,
    resetCurrentTemplate,
    // 预设
    selectedPresetId,
    filteredPresetOptions,
    loadPresetOptions,
    onPresetChange,
    // curl 与格式化
    curlPreview,
    formatBody
  };
}

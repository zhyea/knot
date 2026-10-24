import {computed, reactive, ref, type ComputedRef, type Ref} from "vue";
import {ElMessage} from "element-plus";
import type {Dict, OptionItem, OptionPage} from "@/types";
import {formatJson, formatJsonText, stringifyJson} from "@/utils/format";
import {safeParseTemplate, stripModelField} from "@/utils/routingTestPayload";

/** 网关基址：请求 URL 与 curl 预览共用同一份，避免两处各读一次 env */
const GATEWAY_BASE_URL = import.meta.env.VITE_GATEWAY_BASE_URL || "http://127.0.0.1:9090";

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
}

/** 预设项 meta（对应后端 OptionItem<TestRequestPresetOptionMeta>；后端可为 null） */
interface TestPresetOptionMeta {
  protocolCode?: string | null;
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
  /** 预设下拉项加载 */
  listPresetOptions: () => Promise<OptionPage<OptionItem<TestPresetOptionMeta>>>;
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
    const base = GATEWAY_BASE_URL.trim();
    return base.endsWith("/") ? base.slice(0, -1) : base;
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

  // ===================== 请求模板（按 target+protocol 分槽） =====================

  const templateStore = reactive<Dict>({});

  const activeTemplateKey = computed(
    () => `${ctx.targetKey() || "default"}::${ctx.protocol.value || "default"}`
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

  const filteredPresetOptions = computed(() => {
    const protocol = ctx.protocol.value;
    if (!protocol) {
      return [];
    }
    return presetOptions.value.filter((preset) => preset.protocolCode === protocol);
  });

  async function loadPresetOptions(): Promise<void> {
    try {
      const page = await ctx.listPresetOptions();
      // 统一 OptionPage 契约：value=id，label=name，接口协议走 meta.protocolCode；
      // requestBody 选中时按 id 懒加载
      presetOptions.value = (page?.list ?? []).map((item) => ({
        id: Number(item.value),
        name: item.label,
        protocolCode: item.meta?.protocolCode ?? ""
      }));
    } catch {
      presetOptions.value = [];
    }
    // 预设加载完成后，若当前未选中任何预设，默认选中过滤后的第一个（协议已确定时生效）
    autoSelectFirstPreset();
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
    templateStore[activeTemplateKey.value] = formatJson(stripModelField(base));
  }

  /** 为该 (target, protocol) 首次生成模板：有可选项时默认选中第一个预设并载入请求体，否则留空白默认 */
  function seedTemplateForProtocol(): void {
    const first = filteredPresetOptions.value[0];
    if (first) {
      void onPresetChange(first.id);
    } else {
      selectedPresetId.value = null;
      // 无预设可用时的空白默认请求体；具体结构由「预设请求」用例（kb_test_request_presets）提供
      templateStore[activeTemplateKey.value] = "{}";
    }
  }

  /**
   * 预设请求下拉「默认选中第一个」：未选中且存在可选项时，自动选中过滤后的第一个。
   * 仅当模板仍为空白占位（未加载过预设 / 用户尚未编辑）才覆盖模板，避免清掉用户已输入的内容。
   */
  function autoSelectFirstPreset(): void {
    if (selectedPresetId.value != null) {
      return;
    }
    const first = filteredPresetOptions.value[0];
    if (!first) {
      return;
    }
    const current = templateStore[activeTemplateKey.value] || "";
    const isBlank = !current.trim() || current.trim() === "{}";
    if (isBlank) {
      void onPresetChange(first.id);
    } else {
      selectedPresetId.value = first.id;
    }
  }

  /** 协议切换 / 首次进入时确保有模板：没有就播种，有空白占位就补选预设 */
  function ensureTemplateForCurrentSelection(): void {
    const protocol = ctx.protocol.value;
    if (!protocol || !activeTemplateKey.value) {
      return;
    }
    if (!templateStore[activeTemplateKey.value]) {
      seedTemplateForProtocol();
    } else if (selectedPresetId.value == null && filteredPresetOptions.value.length) {
      autoSelectFirstPreset();
    }
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

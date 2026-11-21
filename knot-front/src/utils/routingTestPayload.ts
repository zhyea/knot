import type {Dict} from "@/types";
import type {SseEvent} from "@/utils/sse";
import {highlightJsonHtml, parseJsonResult} from "@/utils/format";

/**
 * 路由测试抽屉的负载解析原语（改进清单 3.6「先抽纯逻辑」第二步）。
 *
 * <p>这批函数只做「从上游响应里把内容掏出来」和「把事件拼成文本」，<b>不持有任何 Vue 状态</b>，
 * 因此可以脱离组件单测。抽屉侧只剩状态机（节流刷新、abort、计时器）。</p>
 *
 * <p>为什么要深搜而不是按协议硬编码路径：网关测试链路是<b>透传调试</b>，同一个协议在不同供应商手上
 * 形态仍有差异（`choices[].delta.content` / `choices[].message.content` / `choices[].text` /
 * Anthropic content blocks / Responses `output_text`），逐个协议写死路径会在遇到新变体时静默丢内容。</p>
 */

/** 解析单个 SSE data（或裸 JSON）：`data:` 前缀可有可无。 */
export function parseStreamJson(value: string): { value: unknown; error: unknown } {
  const text = value.trim();
  const jsonText = text.startsWith("data:") ? text.slice("data:".length).trim() : text;
  return parseJsonResult(jsonText, null);
}

/** 兼容 Responses 等协议把事件包在 response 子对象中的返回形态。 */
export function findObjectField(payload: unknown, field: string): unknown {
  if (typeof payload === "string") {
    const parsed = parseJsonResult(payload, null);
    return parsed.error ? null : findObjectField(parsed.value, field);
  }
  if (!payload || typeof payload !== "object") {
    return null;
  }
  if (Array.isArray(payload)) {
    for (const item of payload) {
      const found = findObjectField(item, field);
      if (found != null) {
        return found;
      }
    }
    return null;
  }
  const dict = payload as Dict;
  if (Object.prototype.hasOwnProperty.call(dict, field)) {
    return dict[field] ?? null;
  }
  for (const value of Object.values(dict)) {
    const found = findObjectField(value, field);
    if (found != null) {
      return found;
    }
  }
  return null;
}

/** 在「含 anchorField 的那一层」取同级的 field（`knot_extend` 与 `usage` 是并列兄弟）。 */
export function findSiblingField(payload: unknown, anchorField: string, field: string): unknown {
  if (!payload || typeof payload !== "object") {
    return null;
  }
  if (Array.isArray(payload)) {
    for (const item of payload) {
      const found = findSiblingField(item, anchorField, field);
      if (found != null) {
        return found;
      }
    }
    return null;
  }
  const dict = payload as Dict;
  if (Object.prototype.hasOwnProperty.call(dict, anchorField)) {
    return dict[field] ?? null;
  }
  for (const value of Object.values(dict)) {
    const found = findSiblingField(value, anchorField, field);
    if (found != null) {
      return found;
    }
  }
  return null;
}

/**
 * 从 content 类字段值提取文本：字符串原样返回；块数组（Anthropic content blocks /
 * Responses API output_text 等形态）拼接各块 text。
 */
export function extractTextValue(value: unknown): string {
  if (typeof value === "string") {
    return value;
  }
  if (Array.isArray(value)) {
    return value
      .map((block) => {
        if (typeof block === "string") {
          return block;
        }
        if (block && typeof block === "object") {
          const text = (block as Dict).text;
          return typeof text === "string" ? text : "";
        }
        return "";
      })
      .join("");
  }
  return "";
}

/**
 * 从上游响应（单个 SSE data 或完整响应体）深搜指定字段并拼接：
 * 兼容 chat 流式 `choices[].delta.content`、缓冲 `choices[].message.content`、
 * completions `choices[].text`、Responses/Anthropic 的 content 块数组及顶层 `content`。
 * 仅取文本值，避免误抓无关同名字段造成的噪音（这里 content / reasoning_content 语义稳定）。
 */
export function extractField(node: unknown, key: string): string {
  if (node == null || typeof node !== "object") {
    return "";
  }
  const results: string[] = [];
  const visit = (current: unknown) => {
    if (current == null || typeof current !== "object") {
      return;
    }
    if (Array.isArray(current)) {
      current.forEach(visit);
      return;
    }
    for (const [k, v] of Object.entries(current)) {
      if (k === key) {
        const text = extractTextValue(v);
        if (text) {
          results.push(text);
        } else if (v && typeof v === "object" && !Array.isArray(v)) {
          // 对象形态的 content（少数厂商）：继续下钻找同名字段
          visit(v);
        }
      } else if (v && typeof v === "object") {
        visit(v);
      }
    }
  };
  visit(node);
  return results.join("");
}

/** 客户端无需传 model：请求体的 model 由网关按路由目标的上游模型覆盖，这里一律剔除。 */
export function stripModelField(body: unknown): Dict {
  if (!body || typeof body !== "object" || Array.isArray(body)) {
    return {};
  }
  const {model: _ignored, ...rest} = body as Dict;
  return rest;
}

/** 模板文本 → 请求体对象；空文本给 `{}`，非法 JSON 由 `error` 表达（调用方据此提示而不是发空体）。 */
export function safeParseTemplate(text: unknown): { value: unknown; error: unknown } {
  return parseJsonResult(text, {});
}

/** 区块内代码高亮：复用 JSON 高亮（内部已做 HTML 转义，无 XSS 风险）。 */
export function highlightBlock(data: string): string {
  if (!data) {
    return '<span class="sh-muted">（空）</span>';
  }
  return highlightJsonHtml(data);
}

/** SSE 事件 → 可展示文本：`message` 是默认事件名，不写 event 行（与 wire format 一致）。 */
export function formatUpstreamEvent(event: SseEvent): string {
  const eventLine = event.event && event.event !== "message" ? `event: ${event.event}\n` : "";
  return `${eventLine}data: ${event.data}\n\n`;
}

/** fetch 中止是「用户主动停止」，不是故障，调用方据此不弹网络错误。 */
export function isAbortError(error: unknown): boolean {
  return (error as { name?: string })?.name === "AbortError";
}

/** 202 回执里的两个必需字段（异步任务的全部寻址信息） */
export interface AsyncTaskAcceptedHandle {
  /** knot 的任务句柄，对外字段名 `task_id` */
  taskCode: string;
  /** 轮询地址（相对路径），对外字段名 `query_url` */
  queryUrl: string;
}

/**
 * 从一个响应里认出「这是异步任务提交回执」，并取出句柄与轮询地址。
 *
 * <p>⭐ <b>判据只有「HTTP 202 + 两个字段都在」</b>，不猜上游、不看协议、不看模型类型：</p>
 * <ul>
 *   <li>不按协议判（例如「协议是 VIDEO 就当异步」）：同一协议既有同步也有异步实现，
 *       协议只说明端点路径，说明不了这条调用怎么回。按协议猜会把同步视频响应误当回执，
 *       于是抽屉对着一份真结果去轮询一个不存在的任务。</li>
 *   <li>不按字段名宽松匹配（`taskId` / `queryUrl` / 上游自己的 `task_id`）：契约就是蛇形三字段
 *       （见后端 `AsyncTaskAcceptedResponse`），宽容匹配等于给「网关拼错键名」这类事故
 *       留下一条永远发现不了的路径。</li>
 * </ul>
 *
 * @param status 实际 HTTP 状态码（XHR 通道原值）
 * @param body   已解析的响应体
 * @returns 两个字段都非空时返回句柄；否则 null（调用方按同步链路继续）
 */
export function parseAsyncTaskAccepted(
  status: number | null | undefined,
  body: unknown
): AsyncTaskAcceptedHandle | null {
  if (status !== 202) {
    return null;
  }
  if (!body || typeof body !== "object" || Array.isArray(body)) {
    return null;
  }
  const dict = body as Dict;
  const taskCode = typeof dict.task_id === "string" ? dict.task_id.trim() : "";
  const queryUrl = typeof dict.query_url === "string" ? dict.query_url.trim() : "";
  if (!taskCode || !queryUrl) {
    return null;
  }
  return {taskCode, queryUrl};
}

/**
 * 下载文件名可用的扩展名白名单。
 *
 * <p>不从 URL 里<b>猜</b>扩展名：签名地址常带 `?v=1.2` 或路径尾段是 `.../download`，
 * 按「最后一个点之后」取会把版本号当扩展名，存成 `knot-xxx-2` 这种没法双击打开的文件。
 * 只认这张表，认不出就不带扩展名——浏览器会按 `Content-Type` 自己补。</p>
 */
const DOWNLOAD_EXTENSIONS = [
  ".mp4", ".webm", ".mov", ".m4v", ".mkv", ".avi",
  ".gif", ".png", ".jpg", ".jpeg", ".webp",
  ".mp3", ".wav", ".m4a"
];

/**
 * 产物地址 → 下载文件名：`knot-<任务句柄>-<序号><扩展名>`。
 *
 * <p>句柄缺失（老任务 / 手工拼的响应）时退成 `knot-result`，序号仅在多产物时出现——
 * 单产物带上 `-1` 只是噪音。任务句柄本身是后端生成的，但仍做一次清洗：
 * 它会成为文件名，斜杠与空格在 Windows 上会被替换，写进 `download` 只会得到怪名字。</p>
 */
export function resultDownloadName(url: string, taskCode?: string | null, index = 0): string {
  const rawCode = typeof taskCode === "string" ? taskCode.trim() : "";
  const code = (rawCode || "result").replace(/[^\w.-]+/g, "_");
  const ordinal = index > 0 ? `-${index + 1}` : "";
  return `knot-${code}${ordinal}${downloadExtensionOf(url)}`;
}

/** 图片结果的来源：上游签发的地址 / base64 内联 */
export type ImageResultSource = "url" | "base64";

/** 一张可展示的图片：`src` 可直接放进 `<img>` */
export interface ImageResult {
  src: string;
  source: ImageResultSource;
}

/** 图片格式白名单：既用于下载名，也用于异步产物缩略图的判定 */
const IMAGE_EXTENSIONS = [".png", ".jpg", ".jpeg", ".webp", ".gif", ".bmp", ".avif", ".svg"];

/** 图片语义键：这些键的值就是图片本身（地址或 base64），其余 URL 字段不参与；顺序即结果优先序 */
const IMAGE_KEYS = ["image", "images", "image_url", "image_urls", "b64_json"];

/**
 * 从响应体里收集图片结果。
 *
 * <p>网关测试链路是透传调试，同一语义在不同供应商手上形态不同：OpenAI `data[].url` /
 * `data[].b64_json`、Qwen 多模态 `output.choices[].message.content[].image`、
 * 以及 `images` / `image_url(s)` 这类直给数组。因此分两路取：先认 OpenAI 的 `data` 数组，
 * 再按图片语义键深搜；**不做泛化 `url` 深搜**——那会把响应里任意链接误当成图。</p>
 *
 * @param body 响应体（对象，或 JSON 文本）
 * @returns 按出现顺序去重后的图片列表；认不出给空数组
 */
export function collectImageResults(body: unknown): ImageResult[] {
  const payload = typeof body === "string" ? parseJsonResult(body, null).value : body;
  if (!payload || typeof payload !== "object") {
    return [];
  }
  const results: ImageResult[] = [];
  const seen = new Set<string>();
  const push = (value: unknown, base64 = false) => {
    // `images` / `image_urls` 的数组元素可能是对象（`{url}`），一律摊平成候选值
    if (value && typeof value === "object" && !Array.isArray(value)) {
      const dict = value as Dict;
      push(dict.url);
      push(dict.image);
      push(dict.b64_json, true);
      return;
    }
    if (typeof value !== "string") {
      return;
    }
    const src = normalizeImageSource(value, base64);
    if (!src || seen.has(src)) {
      return;
    }
    seen.add(src);
    results.push({src, source: src.startsWith("data:image/") ? "base64" : "url"});
  };

  // 一、OpenAI images 规范：data: [{url} | {b64_json} | {image}]
  const data = (payload as Dict).data;
  if (Array.isArray(data)) {
    data.forEach((item) => push(item));
  }
  // 二、其余供应商形态：按图片语义键深搜
  IMAGE_KEYS.forEach((key) => collectImageByKey(payload, key, push));
  return results;
}

/** 深搜指定图片语义键；数组值逐项摊平（`images: ["..."]`） */
function collectImageByKey(node: unknown, key: string, push: (value: unknown, base64?: boolean) => void): void {
  if (!node || typeof node !== "object") {
    return;
  }
  if (Array.isArray(node)) {
    node.forEach((item) => collectImageByKey(item, key, push));
    return;
  }
  for (const [name, value] of Object.entries(node as Dict)) {
    if (name === key) {
      if (Array.isArray(value)) {
        value.forEach((item) => push(item, key === "b64_json"));
      } else {
        push(value, key === "b64_json");
      }
    } else if (value && typeof value === "object") {
      collectImageByKey(value, key, push);
    }
  }
}

/**
 * 候选值 → 可展示地址：http(s) 原样、`data:image/` 原样、裸 base64 补 PNG 前缀。
 *
 * @param value 候选值
 * @param base64 该值来自 `b64_json`（允许裸 base64）
 * @returns 合法地址；其余（相对路径、普通文本）返回空串
 */
function normalizeImageSource(value: string, base64: boolean): string {
  const text = value.trim();
  if (!text) {
    return "";
  }
  if (base64) {
    return text.startsWith("data:") ? text : `data:image/png;base64,${text}`;
  }
  if (/^data:image\//i.test(text) || /^https?:\/\//i.test(text)) {
    return text;
  }
  return "";
}

/** 地址是否指向图片（data URL 或白名单扩展名）：异步产物据此决定渲染缩略图还是纯链接 */
export function isImageSource(value: unknown): boolean {
  if (typeof value !== "string" || !value) {
    return false;
  }
  if (/^data:image\//i.test(value)) {
    return true;
  }
  let pathname = "";
  try {
    pathname = new URL(value, "http://localhost").pathname.toLowerCase();
  } catch {
    return false;
  }
  return IMAGE_EXTENSIONS.some((extension) => pathname.endsWith(extension));
}

/**
 * 图片下载文件名：`knot-image[-序号]<扩展名>`。
 *
 * <p>扩展名优先取 data URL 的 MIME，其次取地址白名单；都认不出给 `.png`
 * （图片总是要能被双击打开，缺扩展名比猜错更难用）。</p>
 */
export function imageDownloadName(item: ImageResult, index = 0): string {
  const ordinal = index > 0 ? `-${index + 1}` : "";
  return `knot-image${ordinal}${imageExtensionOf(item)}`;
}

function imageExtensionOf(item: ImageResult): string {
  if (item.source === "base64") {
    const mime = /^data:image\/([a-z0-9.+-]+);/i.exec(item.src)?.[1]?.toLowerCase() || "";
    if (mime === "jpeg") {
      return ".jpg";
    }
    return IMAGE_EXTENSIONS.includes(`.${mime}`) ? `.${mime}` : ".png";
  }
  let pathname = "";
  try {
    pathname = new URL(item.src, "http://localhost").pathname.toLowerCase();
  } catch {
    return ".png";
  }
  const matched = IMAGE_EXTENSIONS.find((extension) => pathname.endsWith(extension));
  return matched || ".png";
}

/** 白名单内的扩展名；地址非法或认不出返回空串（不猜） */
function downloadExtensionOf(url: string): string {
  if (!url) {
    return "";
  }
  let pathname = "";
  try {
    pathname = new URL(url, "http://localhost").pathname.toLowerCase();
  } catch {
    return "";
  }
  const match = /(\.[a-z0-9]{2,5})$/.exec(pathname);
  if (!match) {
    return "";
  }
  return DOWNLOAD_EXTENSIONS.includes(match[1]) ? match[1] : "";
}

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

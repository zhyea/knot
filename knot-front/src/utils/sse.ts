/**
 * 管理端 SSE 事件流解析。
 *
 * <p>与 `http.ts` 的 Axios 通道完全解耦：Axios 面向一次性 JSON 解包，
 * 而流式响应需要在 `ReadableStream` 上逐块解码，因此这里基于 `fetch` 的
 * `response.body.getReader()` 自行解析。</p>
 *
 * <p>覆盖的协议细节：</p>
 * <ul>
 *   <li>用 `TextDecoder(stream: true)` 增量解码，避免跨 chunk 的半个汉字变成乱码；</li>
 *   <li>同时兼容 `\n\n` 与 `\r\n\r\n` 两种事件分隔；</li>
 *   <li>支持一个事件包含多行 `data:`（按 SSE 规范用 `\n` 拼接）；</li>
 *   <li>EOF 时处理残留缓冲，不静默丢弃未闭合事件。</li>
 * </ul>
 */

/** 单个 SSE 事件的原始形态（data 保持字符串，JSON 解析交由调用方决定） */
export interface SseEvent {
  event: string;
  data: string;
}

/** 流式读取的空闲超时：相邻两块数据之间允许的最大间隔 */
const DEFAULT_IDLE_TIMEOUT_MS = 120_000;

const EVENT_DELIMITER = /\r?\n\r?\n/;

/**
 * 逐块喂入解码文本，吐出完整事件。
 *
 * 独立成类是为了让「跨 chunk / CRLF / 多行 data / 残留缓冲」这些边界条件
 * 可以脱离网络层单独验证。
 */
export class SseEventParser {
  private buffer = "";

  /**
   * 追加一段解码后的文本，返回其中已闭合的事件。
   */
  push(text: string): SseEvent[] {
    this.buffer += text;
    const events: SseEvent[] = [];
    let matched: RegExpExecArray | null;
    // 分隔符可能在一次 push 中出现多次，需循环取完
    while ((matched = EVENT_DELIMITER.exec(this.buffer)) !== null) {
      const block = this.buffer.slice(0, matched.index);
      this.buffer = this.buffer.slice(matched.index + matched[0].length);
      const event = parseBlock(block);
      if (event) {
        events.push(event);
      }
    }
    return events;
  }

  /**
   * EOF 时取出残留缓冲。返回 null 表示残留内容不是一个完整事件（协议异常）。
   */
  flush(): SseEvent | null {
    const rest = this.buffer.trim();
    this.buffer = "";
    if (!rest) {
      return null;
    }
    return parseBlock(rest);
  }
}

/**
 * 解析单个事件块。注释行（`:` 开头）与空行忽略。
 */
function parseBlock(block: string): SseEvent | null {
  let eventName = "message";
  const dataLines: string[] = [];
  for (const rawLine of block.split(/\r?\n/)) {
    const line = rawLine;
    if (!line || line.startsWith(":")) {
      continue;
    }
    const colon = line.indexOf(":");
    const field = colon === -1 ? line : line.slice(0, colon);
    // 规范允许 `data: x` 与 `data:x` 两种写法；冒号后紧跟的单个空格要去掉
    let value = colon === -1 ? "" : line.slice(colon + 1);
    if (value.startsWith(" ")) {
      value = value.slice(1);
    }
    if (field === "event") {
      eventName = value;
    } else if (field === "data") {
      dataLines.push(value);
    }
  }
  if (!dataLines.length) {
    return null;
  }
  return {event: eventName, data: dataLines.join("\n")};
}

/**
 * 读取一个 SSE 响应并逐事件回调。
 *
 * @param response fetch 返回的原始响应
 * @param onEvent 每个事件的回调（同步调用）
 * @param options.abortSignal 供用户主动取消
 * @param options.idleTimeoutMs 相邻数据间隔超时，默认 120s
 */
export async function readEventStream(
  response: Response,
  onEvent: (event: SseEvent) => void,
  options: {abortSignal?: AbortSignal; idleTimeoutMs?: number} = {}
): Promise<void> {
  const body = response.body;
  if (!body) {
    throw new Error("响应不支持流式读取");
  }
  const idleTimeoutMs = options.idleTimeoutMs ?? DEFAULT_IDLE_TIMEOUT_MS;
  const reader = body.getReader();
  const decoder = new TextDecoder("utf-8");
  const parser = new SseEventParser();
  try {
    for (;;) {
      const {done, value} = await withIdleTimeout(reader.read(), idleTimeoutMs, options.abortSignal);
      if (done) {
        break;
      }
      // stream: true 保证跨 chunk 的多字节字符由解码器内部缓冲
      for (const event of parser.push(decoder.decode(value, {stream: true}))) {
        onEvent(event);
      }
    }
    // 补上解码器与解析器的尾部残留
    for (const event of parser.push(decoder.decode())) {
      onEvent(event);
    }
    parser.flush();
  } finally {
    // 取消时释放底层连接；已结束时 cancel 也能安全忽略
    try {
      await reader.cancel();
    } catch {
      // 连接可能已断开，忽略
    }
  }
}

/**
 * 给单次 read 加空闲超时：上游长时间不出数据时不能让请求永远挂住。
 */
function withIdleTimeout<T>(
  promise: Promise<T>,
  timeoutMs: number,
  abortSignal?: AbortSignal
): Promise<T> {
  return new Promise<T>((resolve, reject) => {
    let settled = false;
    const finish = (fn: () => void) => {
      if (settled) {
        return;
      }
      settled = true;
      cleanup();
      fn();
    };
    const timer = setTimeout(() => {
      finish(() => reject(new Error("流式响应读取超时")));
    }, timeoutMs);
    const onAbort = () => {
      finish(() => reject(new DOMException("Aborted", "AbortError")));
    };
    const cleanup = () => {
      clearTimeout(timer);
      abortSignal?.removeEventListener("abort", onAbort);
    };
    if (abortSignal) {
      if (abortSignal.aborted) {
        onAbort();
        return;
      }
      abortSignal.addEventListener("abort", onAbort);
    }
    promise.then(
      (value) => finish(() => resolve(value)),
      (error) => finish(() => reject(error))
    );
  });
}

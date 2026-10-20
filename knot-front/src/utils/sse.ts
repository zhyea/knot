/**
 * 管理端 SSE 事件流解析。
 *
 * <p>与 `http.ts` 的 Axios 通道完全解耦：Axios 面向一次性 JSON 解包，
 * 而流式响应需要逐块增量解析，因此读取循环基于「解码文本块来源」抽象
 * （{@link TextChunkSource}），XHR 与 fetch 两类传输适配同一套逻辑。</p>
 *
 * <p>覆盖的协议细节：</p>
 * <ul>
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
 * 解码文本块来源：屏蔽底层传输差异（XHR onprogress / fetch ReadableStream），
 * 由 readEventStream 统一完成 SSE 解析、空闲超时与中止。
 */
export interface TextChunkSource {
  /** 取下一段解码文本；返回 null 表示 EOF；抛错表示传输失败 */
  next(): Promise<string | null>;
  /** 中止底层传输（用户主动停止或读取结束） */
  cancel(): void;
}

/**
 * 读取一个 SSE 流并逐事件回调。
 *
 * @param source 解码文本块来源（XHR / fetch 适配器）
 * @param onEvent 每个事件的回调（同步调用）
 * @param options.abortSignal 供用户主动取消
 * @param options.idleTimeoutMs 相邻数据间隔超时，默认 120s
 */
export async function readEventStream(
  source: TextChunkSource,
  onEvent: (event: SseEvent) => void,
  options: {abortSignal?: AbortSignal; idleTimeoutMs?: number} = {}
): Promise<void> {
  const idleTimeoutMs = options.idleTimeoutMs ?? DEFAULT_IDLE_TIMEOUT_MS;
  const parser = new SseEventParser();
  try {
    for (;;) {
      const chunk = await withIdleTimeout(source.next(), idleTimeoutMs, options.abortSignal);
      if (chunk == null) {
        break;
      }
      for (const event of parser.push(chunk)) {
        onEvent(event);
      }
    }
    // EOF 可能没有以空行结束，仍需把残留的完整事件交给调用方。
    const trailingEvent = parser.flush();
    if (trailingEvent) {
      onEvent(trailingEvent);
    }
  } finally {
    // 取消时释放底层连接；已结束时 cancel 也能安全忽略
    source.cancel();
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

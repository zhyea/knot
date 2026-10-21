import { describe, it, expect } from "vitest";
import {
  SseEventParser,
  readEventStream,
  type SseEvent,
  type TextChunkSource
} from "@/utils/sse";

// p1-2 点名的高危交互之一：流式 abort。这里逐块喂入与中止逻辑已脱离网络层（XHR/fetch），
// 是「停止按钮失效」缺陷的根因区（句柄 read() 内 abort 监听缺失会导致 read() 永不返回）。
// 本文件锁住 SseEventParser 的边界解析与 readEventStream 的 abort / 空闲超时 / EOF 行为。

describe("SseEventParser 边界解析", () => {
  it("单个事件以 \\n\\n 分隔", () => {
    const parser = new SseEventParser();
    const events = parser.push("data: hello\n\n");
    expect(events).toEqual<SseEvent[]>([{ event: "message", data: "hello" }]);
  });

  it("一次 push 多个事件全部吐出", () => {
    const parser = new SseEventParser();
    const events = parser.push("data: a\n\ndata: b\n\n");
    expect(events).toHaveLength(2);
    expect(events[0].data).toBe("a");
    expect(events[1].data).toBe("b");
  });

  it("多行 data 用 \\n 拼接", () => {
    const parser = new SseEventParser();
    const events = parser.push("data: line1\ndata: line2\n\n");
    expect(events).toEqual<SseEvent[]>([{ event: "message", data: "line1\nline2" }]);
  });

  it("兼容 \\r\\n\\r\\n 分隔符与 event 字段", () => {
    const parser = new SseEventParser();
    const events = parser.push("event: ping\r\ndata: 1\r\n\r\nevent: pong\r\ndata: 2\r\n\r\n");
    expect(events).toEqual<SseEvent[]>([
      { event: "ping", data: "1" },
      { event: "pong", data: "2" }
    ]);
  });

  it("注释行（: 开头）被忽略", () => {
    const parser = new SseEventParser();
    const events = parser.push(": this is a comment\ndata: real\n\n");
    expect(events).toEqual<SseEvent[]>([{ event: "message", data: "real" }]);
  });

  it("空 data 不算事件", () => {
    const parser = new SseEventParser();
    const events = parser.push("\n\n");
    expect(events).toHaveLength(0);
    expect(parser.flush()).toBeNull();
  });

  it("跨多次 push 的不完整事件缓冲，直到闭合才吐出", () => {
    const parser = new SseEventParser();
    expect(parser.push("data: x\n")).toHaveLength(0); // 无分隔符，留在缓冲
    const events = parser.push("data: y\n\n"); // 闭合
    expect(events).toHaveLength(1);
    expect(events[0].data).toBe("x\ny");
  });

  it("EOF 残留缓冲若是完整事件也要交出", () => {
    const parser = new SseEventParser();
    parser.push("data: tail\n"); // 末尾不以空行结束
    expect(parser.flush()).toEqual<SseEvent | null>({ event: "message", data: "tail" });
  });

  it("EOF 残留为空时 flush 返回 null", () => {
    const parser = new SseEventParser();
    parser.push("data: done\n\n");
    expect(parser.flush()).toBeNull();
  });
});

function makeSource(chunks: (string | null)[]): { source: TextChunkSource; cancelled: { value: boolean } } {
  let i = 0;
  const cancelled = { value: false };
  const source: TextChunkSource = {
    next: () => Promise.resolve(chunks[i++] ?? null),
    cancel: () => {
      cancelled.value = true;
    }
  };
  return { source, cancelled };
}

describe("readEventStream 读取控制", () => {
  it("正常结束回调所有事件并取消底层连接（finally）", async () => {
    const events: SseEvent[] = [];
    const { source, cancelled } = makeSource(["data: a\n\ndata: b\n\n", null]);
    await readEventStream(source, (event) => events.push(event));
    expect(events.map((e) => e.data)).toEqual(["a", "b"]);
    expect(cancelled.value).toBe(true);
  });

  it("EOF 没有空行结尾时仍交出残留事件", async () => {
    const events: SseEvent[] = [];
    const { source } = makeSource(["data: last\n"]); // 无尾随 \n\n
    await readEventStream(source, (event) => events.push(event));
    expect(events).toEqual<SseEvent[]>([{ event: "message", data: "last" }]);
  });

  it("用户中止时抛出 AbortError 且取消底层连接", async () => {
    const controller = new AbortController();
    const cancelled = { value: false };
    const source: TextChunkSource = {
      next: () => new Promise<string | null>(() => {}), // 永不 resolve
      cancel: () => {
        cancelled.value = true;
      }
    };
    const promise = readEventStream(source, () => {}, {
      abortSignal: controller.signal,
      idleTimeoutMs: 1000
    });
    controller.abort();
    await expect(promise).rejects.toThrow();
    // 中止拒绝必须是 AbortError（名称），而不是被静默吞掉或走超时分支
    try {
      await promise;
    } catch (error) {
      expect((error as DOMException).name).toBe("AbortError");
    }
    expect(cancelled.value).toBe(true);
  });

  it("相邻数据间隔超时抛出读取超时错误", async () => {
    const source: TextChunkSource = {
      next: () => new Promise<string | null>(() => {}), // 永不 resolve
      cancel: () => {}
    };
    const promise = readEventStream(source, () => {}, { idleTimeoutMs: 50 });
    await expect(promise).rejects.toThrow("读取超时");
  });
});

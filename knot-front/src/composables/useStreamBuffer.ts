import {computed, ref, type Ref} from "vue";
import {extractField, formatUpstreamEvent} from "@/utils/routingTestPayload";
import {parseJsonResult, stringifyJson} from "@/utils/format";
import {RoutingTestRunState} from "@/constants/status";
import type {SseEvent} from "@/utils/sse";
import type {Dict} from "@/types";

/** 一次路由测试的结果（抽屉与流式缓冲共用） */
export interface RoutingTestResult {
  curl?: string;
  status: string;
  httpStatus: number | string | null;
  modelCode: string | null;
  protocol: string | null;
  errorMessage: string;
  responseBody: string;
}

/** 流式区块：每个 SSE 事件独立成块，逐块动态追加到「流式响应」页 */
export interface StreamBlock {
  id: number;
  event?: string;
  data: string;
}

/** 区块节流刷入间隔（ms）：逐事件直接改响应式数组会让 v-html 高频重排 */
const BLOCK_FLUSH_INTERVAL = 60;
/** 文本节流刷入间隔（ms）：逐 token 直接改响应式属性会让高亮组件高频重算 */
const TEXT_FLUSH_INTERVAL = 50;
/** 「已用时」刷新间隔（ms） */
const ELAPSED_INTERVAL = 1000;

/**
 * 流式缓冲的上下文：抽屉把「被写回的结果」「被告提取动作」与「DOM 滚动」注入进来。
 *
 * <p>三者都必须注入而非 import：前两者是裁判侧的协作对象，<b>后者是 DOM 依赖</b>——
 * 若本 composable 直接摸元素 ref，就只能在真实 DOM 环境里测，等于不可测。</p>
 */
export interface StreamBufferContext {
  /** 测试结果 ref：流式终态与 SSE error 事件写回它 */
  testResult: Ref<RoutingTestResult | null>;
  /** 被告负载 ref；流结束时据此判断是否已拿到被告 */
  knotExtend: Ref<Dict | null>;
  /** 从 SSE data 提取被告（由裁判 composable 提供） */
  captureKnotExtendFromStreamData: (data: string) => void;
  /** 追加区块后滚到底部（DOM 动作，由抽屉注入） */
  onScrollToBottom: () => void;
}

/**
 * 流式测试的缓冲与节流状态机。
 *
 * <p>只负责「收到的字节如何攒、何时刷进界面、怎么固化成终态」，<b>不做编排</b>——
 * 发请求、判错误类型、触发裁判仍是抽屉的职责。</p>
 *
 * <p>两条节流（文本 50ms / 区块 60ms）是性能设计：流式下逐 token 直接改响应式属性
 * 会让高亮组件高频重算。因此「已收到」与「已刷入」是两套状态，结束时必须先 flush。</p>
 */
export function useStreamBuffer(ctx: StreamBufferContext) {
  /** 正在进行的流式请求；非 null 时「发送请求」切换为「停止请求」 */
  const streamAbortController = ref<AbortController | null>(null);
  /** 流式累计接收的响应文本（原始顺序，不做中途格式化） */
  const streamBody = ref("");
  /** 已接收字节数，用于「正在接收」指示 */
  const receivedBytes = ref(0);
  /** 流开始时间戳，用于计算已用时 */
  const streamStartedAt = ref<number | null>(null);
  /** 流式区块列表 */
  const streamBlocks = ref<StreamBlock[]>([]);
  /** 流式正文累计（概览页展示用） */
  const streamContent = ref("");
  /** 流式推理内容累计 */
  const streamReasoning = ref("");
  /** 驱动「已用时」每秒重算的计数器 */
  const elapsedTick = ref(0);

  /** 文本节流计时器 */
  let flushTimer: ReturnType<typeof setTimeout> | null = null;
  /** 已收到但尚未刷入界面的片段 */
  let pendingText = "";
  /** 「已用时」计时器 */
  let elapsedTimer: ReturnType<typeof setInterval> | null = null;
  /** 已生成尚未刷入界面的区块 */
  let pendingBlocks: StreamBlock[] = [];
  /** 区块 id 序列 */
  let blockSeq = 0;
  /** 区块追加节流计时器 */
  let blockFlushTimer: ReturnType<typeof setTimeout> | null = null;
  /** 待刷入的正文 / 推理片段 */
  let pendingContent = "";
  let pendingReasoning = "";

  const isStreaming = computed(() => streamAbortController.value !== null);

  /** 攒下文本片段并安排节流刷新；同时按 UTF-8 字节数累加已接收量 */
  function appendStreamText(text: string): void {
    if (!text) {
      return;
    }
    pendingText += text;
    receivedBytes.value += new TextEncoder().encode(text).length;
    if (flushTimer) {
      return;
    }
    flushTimer = setTimeout(() => {
      flushTimer = null;
      flushStreamText();
    }, TEXT_FLUSH_INTERVAL);
  }

  /** 把攒下的文本刷进 streamBody（结束路径必须显式调用，否则丢最后一批） */
  function flushStreamText(): void {
    if (!pendingText) {
      return;
    }
    streamBody.value += pendingText;
    pendingText = "";
  }

  /** 安排区块节流刷入 */
  function scheduleBlockFlush(): void {
    if (blockFlushTimer) {
      return;
    }
    blockFlushTimer = setTimeout(() => {
      blockFlushTimer = null;
      flushBlocks();
    }, BLOCK_FLUSH_INTERVAL);
  }

  /** 把攒下的区块与正文/推理片段刷进界面 */
  function flushBlocks(): void {
    if (!pendingBlocks.length) {
      return;
    }
    if (pendingContent) {
      streamContent.value += pendingContent;
      pendingContent = "";
    }
    if (pendingReasoning) {
      streamReasoning.value += pendingReasoning;
      pendingReasoning = "";
    }
    streamBlocks.value = [...streamBlocks.value, ...pendingBlocks];
    pendingBlocks = [];
    ctx.onScrollToBottom();
  }

  function clearBlockTimers(): void {
    if (blockFlushTimer) {
      clearTimeout(blockFlushTimer);
      blockFlushTimer = null;
    }
    pendingBlocks = [];
    pendingContent = "";
    pendingReasoning = "";
  }

  /** SSE 事件是上游原生事件：独立成块留档，并反解 content / reasoning_content 供概览页累加 */
  function handleStreamEvent(event: SseEvent): void {
    const dataText = event.data ?? "";
    const parsed = parseJsonResult(dataText, null);
    if (event.event === "error" && ctx.testResult.value) {
      const errorData = parsed.value && typeof parsed.value === "object" ? (parsed.value as Dict) : {};
      const errorMessage = String(errorData.errorMessage || errorData.message || "网关请求失败");
      ctx.testResult.value = {
        ...ctx.testResult.value,
        status: RoutingTestRunState.ERROR,
        httpStatus: errorData.httpStatus ?? ctx.testResult.value.httpStatus,
        errorMessage,
        responseBody: String(errorData.responseBody || dataText)
      };
    }
    if (!parsed.error && parsed.value && typeof parsed.value === "object") {
      pendingContent += extractField(parsed.value, "content");
      pendingReasoning += extractField(parsed.value, "reasoning_content");
    }
    ctx.captureKnotExtendFromStreamData(dataText);
    pendingBlocks.push({id: blockSeq++, event: event.event, data: dataText});
    scheduleBlockFlush();
    appendStreamText(formatUpstreamEvent(event));
  }

  /** 区块是实际收到内容的留档；流结束时再核对一次，避免事件状态未及时进入裁判输入。 */
  function recoverKnotExtendFromStreamBlocks(): void {
    if (ctx.knotExtend.value) {
      return;
    }
    for (const block of streamBlocks.value) {
      ctx.captureKnotExtendFromStreamData(block.data);
      if (ctx.knotExtend.value) {
        return;
      }
    }
  }

  /** 结束流式态：把已收到的内容固化到 responseBody，并置终态 */
  function finishStream(status: string, errorMessage: string): void {
    if (ctx.testResult.value) {
      ctx.testResult.value = {
        ...ctx.testResult.value,
        status,
        errorMessage,
        responseBody: streamBody.value
      };
    }
  }

  /** 首个事件前就失败时：同时填充 Body 与「流式响应」页，保留已收到的上游片段。 */
  function appendStreamError(result: RoutingTestResult): void {
    const errorText = result.responseBody || result.errorMessage || "请求失败";
    const errorData = stringifyJson({
      status: result.status,
      httpStatus: result.httpStatus,
      errorMessage: result.errorMessage,
      responseBody: errorText
    });
    streamBlocks.value = [...streamBlocks.value, {id: blockSeq++, event: "error", data: errorData}];
    streamBody.value = streamBody.value ? `${streamBody.value}\n\n[error] ${errorText}` : errorText;
    result.responseBody = streamBody.value;
  }

  /** 中止在途流（用户点「停止」或关闭抽屉） */
  function abortStreaming(): void {
    if (streamAbortController.value) {
      streamAbortController.value.abort();
    }
  }

  /**
   * 开始一次流式调用：复位所有缓冲并启动「已用时」计时器。
   *
   * <p>不负责建 controller / 置 loading / 切 tab——那是编排层的事。</p>
   */
  function beginStream(controller: AbortController): void {
    streamAbortController.value = controller;
    streamStartedAt.value = Date.now();
    receivedBytes.value = 0;
    streamBody.value = "";
    streamBlocks.value = [];
    pendingText = "";
    pendingBlocks = [];
    blockSeq = 0;
    streamContent.value = "";
    streamReasoning.value = "";
    clearBlockTimers();
    if (elapsedTimer) {
      clearInterval(elapsedTimer);
    }
    elapsedTimer = setInterval(() => {
      elapsedTick.value += 1;
    }, ELAPSED_INTERVAL);
  }

  /**
   * 结束一次流式调用（对应编排层的 finally）：兜底刷掉最后一批待刷数据并释放计时器。
   *
   * <p><b>必须放在 finally</b>——正常结束、用户中止、请求失败三条路径都要走，
   * 漏清会让计时器对着已销毁的组件写响应式状态。</p>
   */
  function endStream(): void {
    flushStreamText();
    flushBlocks();
    if (flushTimer) {
      clearTimeout(flushTimer);
      flushTimer = null;
    }
    clearBlockTimers();
    if (elapsedTimer) {
      clearInterval(elapsedTimer);
      elapsedTimer = null;
    }
    pendingText = "";
    streamAbortController.value = null;
    streamStartedAt.value = null;
  }

  /**
   * 释放流式占用的资源：在途流 + 三个计时器 + 待刷缓冲。
   *
   * <p>只做资源释放，不碰展示数据——数据复位是抽屉 `onClosed` 的职责。</p>
   */
  function disposeStreamResources(): void {
    abortStreaming();
    if (flushTimer) {
      clearTimeout(flushTimer);
      flushTimer = null;
    }
    clearBlockTimers();
    if (elapsedTimer) {
      clearInterval(elapsedTimer);
      elapsedTimer = null;
    }
    pendingText = "";
  }

  return {
    streamAbortController,
    streamBody,
    receivedBytes,
    streamStartedAt,
    streamBlocks,
    streamContent,
    streamReasoning,
    elapsedTick,
    isStreaming,
    beginStream,
    endStream,
    handleStreamEvent,
    flushStreamText,
    flushBlocks,
    recoverKnotExtendFromStreamBlocks,
    finishStream,
    appendStreamError,
    abortStreaming,
    disposeStreamResources
  };
}

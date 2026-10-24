import {stringifyJson} from "@/utils/format";

/**
 * 路由测试抽屉的错误归一（改进清单 3.6 第一步：先抽纯逻辑）。
 *
 * <p>抽屉里同时存在两种失败形态，必须归一成同一个结果结构，否则「错误页」的渲染要分叉：</p>
 * <ul>
 *   <li><b>axios 失败</b>：响应体在 {@code error.response.data}，可能是统一响应包装
 *       {@code {success:false, message, data}}，也可能直接是业务对象；</li>
 *   <li><b>fetch 失败</b>：网络层直接抛 {@code TypeError: Failed to fetch}，没有 response，
 *       HTTP 状态无从得知——这种必须翻译成可操作的中文提示，不能把浏览器原文丢给用户。</li>
 * </ul>
 *
 * <p>本模块<b>不依赖 Vue 状态</b>：调用方把「当前协议」作为参数传入，因此可脱离组件单测。</p>
 */
export interface RoutingTestErrorResult {
  status: "ERROR";
  httpStatus: number | null;
  modelCode: string | null;
  protocol: string | null;
  errorMessage: string;
  responseBody: string;
}

/** 归一失败形态：只声明本模块实际读取的字段，其余一律不碰 */
interface ErrorLike {
  message?: unknown;
  response?: {
    status?: number | null;
    data?: unknown;
  };
}

/** 统一响应包装的判断依据：`success === false` 时真实负载在 `data` 里 */
interface WrappedPayload {
  success?: unknown;
  message?: unknown;
  data?: Record<string, unknown>;
}

export function normalizeErrorResult(error: unknown, activeProtocol: string | null): RoutingTestErrorResult {
  const err = (error ?? {}) as ErrorLike;
  const status = err.response?.status ?? null;
  const data = err.response?.data;

  if (data && typeof data === "object") {
    const payload = data as WrappedPayload & Record<string, unknown>;
    if (payload.success === false) {
      // 统一响应包装：业务字段在 data 内，外层 message 才是给人看的
      return {
        status: "ERROR",
        httpStatus: status,
        modelCode: (payload.data?.modelCode as string | undefined) ?? null,
        protocol: (payload.data?.protocol as string | undefined) ?? activeProtocol,
        errorMessage: firstText(payload.message) || "请求失败",
        responseBody: serializeBody(payload.data ?? payload)
      };
    }
    return {
      status: "ERROR",
      httpStatus: status,
      modelCode: (payload.modelCode as string | undefined) ?? null,
      protocol: (payload.protocol as string | undefined) ?? activeProtocol,
      // 三层兜底：message → error（可能是对象也可能是字符串）→ axios 自带 message
      errorMessage: firstText(payload.message) || errorTextOf(payload.error) || firstText(err.message) || "请求失败",
      responseBody: serializeBody(payload)
    };
  }

  return {
    status: "ERROR",
    httpStatus: status,
    modelCode: null,
    protocol: activeProtocol || null,
    errorMessage: normalizeNetworkErrorMessage(err.message),
    // fetch 网络失败没有 response body；仍把错误写入 Body，避免调试面板看起来像空响应。
    responseBody: normalizeNetworkErrorMessage(err.message)
  };
}

/** 将 fetch 无响应错误转换成用户可操作的提示，避免只显示浏览器的英文 TypeError。 */
export function normalizeNetworkErrorMessage(message: unknown): string {
  const raw = String(message || "").trim();
  if (!raw || /failed to fetch|networkerror|load failed/i.test(raw)) {
    return "无法连接 gateway 服务，请确认 gateway 已启动且地址可访问";
  }
  return `网络错误：${raw}`;
}

/** 响应体 → 文本：空值保持空串（不是 "null"），字符串原样透出，其余走 JSON 序列化。 */
export function serializeBody(body: unknown): string {
  if (body == null || body === "") return "";
  if (typeof body === "string") return body;
  return stringifyJson(body);
}

function firstText(value: unknown): string {
  return typeof value === "string" ? value : "";
}

function errorTextOf(value: unknown): string {
  if (typeof value === "string") return value;
  if (value && typeof value === "object") {
    const message = (value as {message?: unknown}).message;
    return typeof message === "string" ? message : "";
  }
  return "";
}

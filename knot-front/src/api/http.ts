import axios, {type AxiosResponse, type AxiosRequestConfig} from "axios";
import {ElMessage} from "element-plus";
import {useAuth} from "@/composables/useAuth";
import {touchIdleActivity} from "@/composables/idleActivity";
import router from "../router";

declare module "axios" {
  interface AxiosRequestConfig {
    /** 跳过「请求即视为活跃」的空闲计时刷新 */
    skipIdleTouch?: boolean;
    /** 失败时不弹 ElMessage，由调用方自行处理 */
    silentError?: boolean;
    /** 请求发出时实际使用的登录令牌，用于忽略旧会话的 401 */
    authToken?: string;
  }
}

/** 业务失败（HTTP 200 但 `success === false`）时抛出的错误形态 */
export interface ApiBusinessError extends Error {
  response?: AxiosResponse;
  config?: AxiosRequestConfig;
  code?: number | string;
}

const http = axios.create({
  baseURL: "",
  timeout: 30000
});

// 请求拦截器：自动携带 token
http.interceptors.request.use((config) => {
  const {token} = useAuth();
  if (token.value) {
    // 保存请求发出时的 token。响应可能在用户重新登录后才返回，
    // 不能让旧会话的 401 清理新会话。
    config.authToken = token.value;
    config.headers.Authorization = `Bearer ${token.value}`;
    if (!config.skipIdleTouch) {
      touchIdleActivity();
    }
  }
  return config;
});

// 响应拦截器：处理 401 和业务错误
http.interceptors.response.use(
  (response) => {
    const body = response.data;
    const silentError = response.config?.silentError;

    if (body && typeof body.success === "boolean" && body.success === false) {
      if (!silentError) {
        ElMessage.error(body.message || "请求失败");
      }
      const businessError = new Error(body.message || "请求失败") as ApiBusinessError;
      businessError.response = response;
      businessError.config = response.config;
      businessError.code = body.code;
      return Promise.reject(businessError);
    }
    return response;
  },
  (error) => {
    const requestUrl = error.config?.url || "";
    const isLoginRequest = requestUrl.includes("/api/auth/login");

    // 401 只允许当前会话对应的请求触发退出。登录前发出的无 token 请求、
    // 以及旧会话延迟返回的 401，都不能清理刚建立的新会话。
    if (error.response?.status === 401 && !isLoginRequest) {
      const {token, clearAllAuthState} = useAuth();
      const requestToken = error.config?.authToken;
      const currentToken = token.value;
      const belongsToCurrentSession =
        !!currentToken && !!requestToken && requestToken === currentToken;

      if (belongsToCurrentSession) {
        // JWT 无服务端会话，收到当前 token 的 401 时直接清理本地状态，
        // 避免再次调用 logout 造成并发请求和二次 401。
        clearAllAuthState();
        if (router.currentRoute.value.path !== "/login") {
          router.push("/login");
        }
        ElMessage.error("登录已过期，请重新登录");
        return Promise.reject(new Error("登录已过期"));
      }
      // 旧会话的响应不能影响当前会话，也不能向调用方伪报“当前登录已过期”。
      return Promise.reject(error);
    }

    const silentError = error.config?.silentError;
    if (!silentError) {
      const msg =
        error.response?.data?.message ||
        error.response?.data?.error ||
        error.message ||
        "网络错误";
      ElMessage.error(msg);
    }
    return Promise.reject(error);
  }
);

/**
 * 解包 Spring `ApiResponse`：`{ success, message, data }` -> `data`
 * 非 `ApiResponse` 的响应则原样返回 `body`
 */
export function unwrapData(response: AxiosResponse): unknown {
  const body = response.data;
  if (body && typeof body.success === "boolean") {
    return body.data;
  }
  return body;
}

export function get<T = any>(url: string, config: AxiosRequestConfig = {}): Promise<T> {
  return http.get(url, config).then(unwrapData as (response: AxiosResponse) => T);
}

export function postQuery<T = any>(
  url: string,
  data?: unknown,
  config: AxiosRequestConfig = {}
): Promise<T> {
  return http.post(url, data, config).then(unwrapData as (response: AxiosResponse) => T);
}

export function post<T = any>(
  url: string,
  data?: unknown,
  config: AxiosRequestConfig = {}
): Promise<T> {
  return http.post(url, data, config).then(unwrapData as (response: AxiosResponse) => T);
}

export function put<T = any>(
  url: string,
  data?: unknown,
  config: AxiosRequestConfig = {}
): Promise<T> {
  return http.put(url, data, config).then(unwrapData as (response: AxiosResponse) => T);
}

export function del<T = any>(url: string, config: AxiosRequestConfig = {}): Promise<T> {
  return http.delete(url, config).then(unwrapData as (response: AxiosResponse) => T);
}

/**
 * 以 `fetch` 发起 SSE 流式 POST 请求，返回原始 `Response` 供 `readEventStream` 增量读取。
 *
 * <p>不复用 Axios 实例：Axios 面向一次性 JSON 解包（`unwrapData` + 30s 超时），
 * 与「长连接 + ReadableStream」模型冲突。此处独立处理鉴权头与 401。</p>
 *
 * <p>注意：不设置总时长超时（流式响应本就可能很长），超时由调用方通过
 * `readEventStream` 的空闲超时与 `AbortSignal` 控制。</p>
 */
export async function postEventStream(
  url: string,
  data: unknown,
  signal?: AbortSignal
): Promise<Response> {
  const {token, clearAllAuthState} = useAuth();
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    Accept: "text/event-stream"
  };
  if (token.value) {
    headers.Authorization = `Bearer ${token.value}`;
    touchIdleActivity();
  }

  const response = await fetch(url, {
    method: "POST",
    headers,
    body: JSON.stringify(data ?? {}),
    signal
  });

  if (response.ok) {
    return response;
  }

  // 与 Axios 拦截器保持一致：只有当前会话的 401 才清理登录态
  if (response.status === 401) {
    clearAllAuthState();
    if (router.currentRoute.value.path !== "/login") {
      router.push("/login");
    }
    ElMessage.error("登录已过期，请重新登录");
    throw new Error("登录已过期");
  }

  // 非 2xx：把 ApiResponse 结构转成与 Axios 一致的错误形态，便于复用 normalizeErrorResult
  const raw = await response.text();
  let body: unknown = raw;
  try {
    body = raw ? JSON.parse(raw) : null;
  } catch {
    // 非 JSON 响应（如容器网关的纯文本错误）保留原文
  }
  const message =
    (body && typeof body === "object" && "message" in body
      ? String((body as {message?: unknown}).message ?? "")
      : "") || `请求失败（HTTP ${response.status}）`;
  ElMessage.error(message);
  throw Object.assign(new Error(message), {
    response: {status: response.status, data: body}
  });
}

export default http;

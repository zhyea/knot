import axios, {type AxiosResponse, type AxiosRequestConfig} from "axios";
import {ElMessage} from "element-plus";
import {useAuth} from "@/composables/useAuth";
import {touchIdleActivity} from "@/composables/idleActivity";
import router from "../router";
import {readEventStream, type SseEvent} from "../utils/sse";

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
 * 解析 XHR `getAllResponseHeaders()` 的原始文本为键值对。
 *
 * <p>fetch 的 `Headers` 对象按规范会把所有头名统一小写，丢失服务端原始大小写；
 * 调试抽屉的「响应 Header」tab 要求展示原值（如 {@code X-Knot-Model}），
 * 因此网关直连请求走 XHR 通道，由本函数保留原始头名。</p>
 */
function parseRawResponseHeaders(raw: string): Record<string, string> {
  const headers: Record<string, string> = {};
  for (const line of raw.split(/\r?\n/)) {
    const index = line.indexOf(":");
    // 首行状态行（如 "HTTP/1.1 200 OK"）无冒号，直接跳过
    if (index <= 0) {
      continue;
    }
    const name = line.slice(0, index).trim();
    if (name) {
      headers[name] = line.slice(index + 1).trim();
    }
  }
  return headers;
}

/** 网关直连流式请求句柄：响应头原值 + SSE 增量读取 */
export interface GatewayEventStream {
  status: number;
  /** 原始大小写的响应头（跨域时仍受 CORS 暴露头白名单约束） */
  headers: Record<string, string>;
  /** 增量消费 SSE 事件直到 EOF；abortSignal 用于用户主动停止 */
  read(
    onEvent: (event: SseEvent) => void,
    options?: {abortSignal?: AbortSignal; idleTimeoutMs?: number}
  ): Promise<void>;
}

/**
 * 以 XHR 发起 SSE 流式 POST 请求，返回句柄供调用方增量读取事件。
 *
 * <p>不复用 Axios 实例：Axios 面向一次性 JSON 解包（`unwrapData` + 30s 超时），
 * 与「长连接 + 增量推送」模型冲突。此处独立处理鉴权头与 401。</p>
 *
 * <p>选 XHR 而非 fetch 的唯一理由：`Headers` 对象会把响应头名统一小写，
 * 调试面板需要原值展示；XHR 的 `getAllResponseHeaders()` 保留服务端原始大小写。</p>
 *
 * <p>注意：不设置总时长超时（流式响应本就可能很长），超时由调用方通过
 * `readEventStream` 的空闲超时与 `AbortSignal` 控制。</p>
 *
 * <p>⚠ 实测（Chrome headless，跨域 + 同源各测一遍）：`getAllResponseHeaders()`
 * 返回的头名一律被浏览器小写化，<b>同源也不例外</b>。因此本函数的 XHR 通道
 * 换不来「响应头原值」，调试面板展示的 `X-Knot-Model` 等实际是小写形式。
 * 保留 XHR 的理由只剩「不经过 Axios 的 30s 超时与 ApiResponse 解包」。</p>
 */
export async function postEventStream(
  url: string,
  data: unknown,
  signal?: AbortSignal,
  requestHeaders: Record<string, string> = {}
): Promise<GatewayEventStream> {
  const {token, clearAllAuthState} = useAuth();
  const usesAdminAuth = !requestHeaders.Authorization;
  return new Promise<GatewayEventStream>((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open("POST", url);
    for (const [name, value] of Object.entries(requestHeaders)) {
      xhr.setRequestHeader(name, value);
    }
    if (token.value) {
      touchIdleActivity();
    }

    // 增量文本队列：onprogress 只推送新增部分，read() 按需拉取
    const queue: string[] = [];
    let wake: (() => void) | null = null;
    let consumed = 0;
    let closed = false;
    let failure: Error | null = null;
    let abandoned = false;
    let settled = false;
    let handleAbortBound = false;

    const notify = () => {
      if (wake) {
        const resume = wake;
        wake = null;
        resume();
      }
    };
    const enqueuePending = () => {
      const text = xhr.responseText || "";
      if (text.length > consumed) {
        queue.push(text.slice(consumed));
        consumed = text.length;
        notify();
      }
    };
    const finish = () => {
      closed = true;
      notify();
    };
    const breakWith = (error: Error) => {
      failure = error;
      finish();
    };
    const cleanupSignal = () => signal?.removeEventListener("abort", onAbort);
    const onAbort = () => xhr.abort();
    signal?.addEventListener("abort", onAbort);

    const buildHandle = (status: number, headers: Record<string, string>): GatewayEventStream => ({
      status,
      headers,
      read: (onEvent, options = {}) => {
        // 句柄交付后调用方的 signal 仍须生效：抽屉的「停止」是在拿到句柄之后触发的，
        // 若此前的 cleanupSignal() 已解绑，abort 将完全落空、流永不结束。
        // 句柄只登记一次监听，重复 read 也不会叠加。
        if (signal && !handleAbortBound) {
          handleAbortBound = true;
          signal.addEventListener("abort", onAbort);
        }
        return readEventStream(
          {
            next: async () => {
              for (;;) {
                if (queue.length) {
                  return queue.shift() as string;
                }
                if (failure) {
                  throw failure;
                }
                if (closed) {
                  return null;
                }
                await new Promise<void>((resume) => {
                  wake = resume;
                });
                // 被取消后优雅收尾：不再抛出，避免未处理的 Promise 拒绝
                if (abandoned) {
                  return null;
                }
              }
            },
            cancel: () => {
              abandoned = true;
              cleanupSignal();
              xhr.abort();
            }
          },
          onEvent,
          options
        );
      }
    });

    xhr.onreadystatechange = () => {
      // readyState 2 = 响应头到手：先交还句柄，正文经 onprogress 增量推送
      if (xhr.readyState !== 2 || settled) {
        return;
      }
      const headers = parseRawResponseHeaders(xhr.getAllResponseHeaders());
      if (xhr.status >= 200 && xhr.status < 300) {
        settled = true;
        cleanupSignal();
        resolve(buildHandle(xhr.status, headers));
        enqueuePending();
      }
    };
    xhr.onprogress = () => enqueuePending();
    xhr.onload = () => {
      if (settled) {
        finish();
        return;
      }
      // 未在 readyState 2 交还：2xx 兜底整体交付，非 2xx 按错误拒绝
      const headers = parseRawResponseHeaders(xhr.getAllResponseHeaders());
      const raw = xhr.responseText;
      let body: unknown = raw;
      try {
        body = raw ? JSON.parse(raw) : null;
      } catch {
        // 非 JSON 响应（如容器网关的纯文本错误）保留原文
      }
      settled = true;
      cleanupSignal();
      if (xhr.status >= 200 && xhr.status < 300) {
        resolve(buildHandle(xhr.status, headers));
        enqueuePending();
        finish();
        return;
      }
      // 与 Axios 拦截器保持一致：只有当前会话的 401 才清理登录态
      if (xhr.status === 401 && usesAdminAuth && token.value) {
        clearAllAuthState();
        if (router.currentRoute.value.path !== "/login") {
          router.push("/login");
        }
        ElMessage.error("登录已过期，请重新登录");
        reject(new Error("登录已过期"));
        return;
      }
      // 非 2xx：把 ApiResponse 结构转成与 Axios 一致的错误形态，便于复用 normalizeErrorResult
      const message =
        (body && typeof body === "object" && "message" in body
          ? String((body as {message?: unknown}).message ?? "")
          : "") || `请求失败（HTTP ${xhr.status}）`;
      if (usesAdminAuth) {
        ElMessage.error(message);
      }
      reject(Object.assign(new Error(message), {
        response: {status: xhr.status, data: body, headers}
      }));
    };
    xhr.onerror = () => {
      if (!settled) {
        settled = true;
        cleanupSignal();
        reject(new Error("网络请求失败"));
      }
      breakWith(new Error("网关连接失败"));
    };
    xhr.onabort = () => {
      // 响应头到手前被中止（如用户连接期即点停止）：必须拒绝，否则调用方永远挂起
      if (!settled) {
        settled = true;
        cleanupSignal();
        reject(new DOMException("Aborted", "AbortError"));
      }
      breakWith(new DOMException("Aborted", "AbortError"));
    };
    xhr.send(JSON.stringify(data ?? {}));
  });
}

/**
 * 以消费者 API Key 直接向网关发送一次性 JSON POST 请求。
 * 管理端 Axios 实例会自动覆盖 Authorization 为登录态 JWT，因此网关调试
 * 请求必须使用独立的请求链路。
 *
 * <p>请求头由调用方完整组装（调试抽屉「Headers」表格即权威）：此处不再内置
 * Authorization / Rule / Content-Type 等默认头，避免用户在表格里删改后默认头
 * 仍旧静默发出、预览与实际请求不一致。</p>
 *
 * <p>选 XHR 而非 fetch 的唯一理由：`Headers` 对象会把响应头名统一小写，
 * 调试面板的「响应 Header」tab 需要展示原值。</p>
 *
 * @param signal 可选中止信号。异步任务轮询在关抽屉时必须能停掉在途请求——
 *               否则「已停止」只是界面文案，网关上仍有一串查询在跑
 */
export async function postGatewayJson<T = unknown>(
  url: string,
  data: unknown,
  requestHeaders: Record<string, string>,
  signal?: AbortSignal
): Promise<{status: number; data: T; headers: Record<string, string>}> {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open("POST", url);
    if (signal) {
      if (signal.aborted) {
        reject(new DOMException("Aborted", "AbortError"));
        return;
      }
      signal.addEventListener("abort", () => xhr.abort(), {once: true});
    }
    // 中止不是失败：抛 AbortError 让调用方按「用户主动停止」处理（同 SSE 通道口径）
    xhr.onabort = () => reject(new DOMException("Aborted", "AbortError"));
    for (const [name, value] of Object.entries(requestHeaders)) {
      xhr.setRequestHeader(name, value);
    }
    xhr.onload = () => {
      const raw = xhr.responseText;
      let body: unknown = raw;
      try {
        body = raw ? JSON.parse(raw) : null;
      } catch {
        // 非 JSON 响应保留原文，便于调试网关返回的纯文本错误。
      }

      // 采集响应头原值，供调试面板「响应 Header」tab 展示
      const headers = parseRawResponseHeaders(xhr.getAllResponseHeaders());

      if (xhr.status >= 200 && xhr.status < 300) {
        resolve({status: xhr.status, data: body as T, headers});
        return;
      }

      const message =
        (body && typeof body === "object" && "message" in body
          ? String((body as {message?: unknown}).message ?? "")
          : "") ||
        (body && typeof body === "object" && "error" in body
          ? String((body as {error?: unknown}).error ?? "")
          : "") ||
        `请求失败（HTTP ${xhr.status}）`;
      reject(Object.assign(new Error(message), {
        response: {status: xhr.status, data: body, headers}
      }));
    };
    xhr.onerror = () => {
      reject(new Error("网络请求失败"));
    };
    xhr.send(JSON.stringify(data ?? {}));
  });
}

export default http;

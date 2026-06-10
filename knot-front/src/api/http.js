import axios from "axios";
import { ElMessage } from "element-plus";
import { useAuth } from "../composables/useAuth";
import { touchIdleActivity } from "../composables/idleActivity";
import router from "../router";

const http = axios.create({
  baseURL: "",
  timeout: 30000
});

// 请求拦截器：自动携带 token
http.interceptors.request.use((config) => {
  const { token } = useAuth();
  if (token.value) {
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
      const businessError = new Error(body.message || "请求失败");
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

    // 401 未授权时，清理 token 并跳转到登录页
    if (error.response?.status === 401 && !isLoginRequest) {
      const { logout } = useAuth();
      logout();
      router.push("/login");
      ElMessage.error("登录已过期，请重新登录");
      return Promise.reject(new Error("登录已过期"));
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
export function unwrapData(response) {
  const body = response.data;
  if (body && typeof body.success === "boolean") {
    return body.data;
  }
  return body;
}

export function get(url, config) {
  return http.get(url, config).then(unwrapData);
}

export function postQuery(url, data, config) {
  return http.post(url, data, config).then(unwrapData);
}

export function post(url, data, config) {
  return http.post(url, data, config).then(unwrapData);
}

export function put(url, data, config) {
  return http.put(url, data, config).then(unwrapData);
}

export function del(url, config) {
  return http.delete(url, config).then(unwrapData);
}

export default http;

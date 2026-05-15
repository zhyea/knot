import axios from "axios";
import { ElMessage } from "element-plus";
import { useAuth } from "../composables/useAuth";
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
  }
  return config;
});

// 响应拦截器：处理 401 和业务错误
http.interceptors.response.use(
  (response) => {
    const body = response.data;
    // 如果请求配置了 silentError，则不显示错误提示，由调用方处理
    const silentError = response.config?.silentError;
    
    if (body && typeof body.success === "boolean" && body.success === false) {
      if (!silentError) {
        ElMessage.error(body.message || "请求失败");
      }
      return Promise.reject(new Error(body.message || "请求失败"));
    }
    return response;
  },
  (error) => {
    // 401 未授权，清除 token 并跳转到登录页
    if (error.response?.status === 401) {
      const { logout } = useAuth();
      logout();
      router.push('/login');
      ElMessage.error('登录已过期，请重新登录');
      return Promise.reject(new Error('登录已过期'));
    }
    
    // 如果请求配置了 silentError，则不显示错误提示
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
 * 解包 Spring `ApiResponse`：`{ success, message, data }` → `data`
 * 非 ApiResponse 的响应原样返回 data 字段或整个 body
 */
export function unwrapData(response) {
  const body = response.data;
  if (body && typeof body.success === "boolean") {
    return body.data;
  }
  return body;
}

/**
 * 查询接口统一使用 POST，参数放入请求体
 * @param {string} url
 * @param {object} data 请求体参数（原 get 的 params）
 */
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

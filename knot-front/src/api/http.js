import axios from "axios";
import { ElMessage } from "element-plus";

const http = axios.create({
  baseURL: "",
  timeout: 30000
});

http.interceptors.response.use(
  (response) => {
    const body = response.data;
    if (body && typeof body.success === "boolean" && body.success === false) {
      ElMessage.error(body.message || "请求失败");
      return Promise.reject(new Error(body.message || "请求失败"));
    }
    return response;
  },
  (error) => {
    const msg =
      error.response?.data?.message ||
      error.response?.data?.error ||
      error.message ||
      "网络错误";
    ElMessage.error(msg);
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

export function get(url, config) {
  return http.get(url, config).then(unwrapData);
}

export function post(url, data, config) {
  return http.post(url, data, config).then(unwrapData);
}

export function put(url, data, config) {
  return http.put(url, data, config).then(unwrapData);
}

export default http;

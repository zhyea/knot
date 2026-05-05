import { postQuery, post, put, del } from "./http";

export function listEnumConfigs(params) {
  return postQuery("/api/system/enums/list", params);
}

export function listEnumsByCategory(category) {
  return postQuery(`/api/system/enums/${category}`);
}

export function listEnumCategories() {
  return postQuery("/api/system/enums/categories");
}

export function createEnumConfig(payload) {
  return post("/api/system/enums", payload);
}

export function updateEnumConfig(id, payload) {
  return put(`/api/system/enums/${id}`, payload);
}

export function deleteEnumConfig(id) {
  return del(`/api/system/enums/${id}`);
}

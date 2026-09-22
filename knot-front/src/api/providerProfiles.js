import { postQuery, post, put, get, del } from "./http";

export function listProviderProfiles(params) {
  return postQuery("/api/provider-profiles/list", params);
}

export function getProviderProfile(id) {
  return get(`/api/provider-profiles/${id}`);
}

export function checkProviderProfileCode(code, excludeId) {
  return get("/api/provider-profiles/check-code", {
    params: { code, excludeId: excludeId ?? undefined }
  });
}

export function createProviderProfile(payload) {
  return post("/api/provider-profiles", payload);
}

export function updateProviderProfile(id, payload) {
  return put(`/api/provider-profiles/${id}`, payload);
}

export function deleteProviderProfile(id) {
  return del(`/api/provider-profiles/${id}`);
}

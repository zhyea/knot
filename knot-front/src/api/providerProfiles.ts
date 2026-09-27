import {postQuery, post, put, get, del} from "./http";
import type {Dict} from "@/types";

export function listProviderProfiles(params: Dict) {
  return postQuery("/api/provider-profiles/list", params);
}

export function getProviderProfile(id: number | string) {
  return get(`/api/provider-profiles/${id}`);
}

export function checkProviderProfileCode(code: string, excludeId: number | string | null) {
  return get("/api/provider-profiles/check-code", {
    params: {code, excludeId: excludeId ?? undefined}
  });
}

export function createProviderProfile(payload: Dict) {
  return post("/api/provider-profiles", payload);
}

export function updateProviderProfile(id: number | string, payload: Dict) {
  return put(`/api/provider-profiles/${id}`, payload);
}

export function deleteProviderProfile(id: number | string) {
  return del(`/api/provider-profiles/${id}`);
}

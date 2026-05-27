import { get, put } from "./http";

export function getMySettings(config) {
  return get("/api/user-settings/me", config);
}

export function saveMySettings(settings, config) {
  return put("/api/user-settings/me", { settings }, config);
}

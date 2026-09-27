import { post } from "./http";
import type { Dict } from "@/types";

export function login(data: Dict) {
  return post("/api/auth/login", data, { silentError: true });
}

export function forcePasswordChange(data: Dict) {
  return post("/api/auth/force-password-change", data, { silentError: true, skipIdleTouch: true });
}

export function logout() {
  return post("/api/auth/logout", null, { skipIdleTouch: true });
}

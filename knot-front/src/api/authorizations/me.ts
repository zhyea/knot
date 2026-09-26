import { get } from "../http";
import type { AxiosRequestConfig } from "axios";

export function getMyAuthorizations(config: AxiosRequestConfig) {
  return get("/api/me/authorizations", config);
}

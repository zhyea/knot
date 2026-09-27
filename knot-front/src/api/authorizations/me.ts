import {get} from "@/api/http";
import type {AxiosRequestConfig} from "axios";

export function getMyAuthorizations(config: AxiosRequestConfig) {
  return get("/api/me/authorizations", config);
}

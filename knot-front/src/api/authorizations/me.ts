import {get} from "@/api/http";
import type {AxiosRequestConfig} from "axios";
import type {Dict} from "@/types";

export function getMyAuthorizations(config: AxiosRequestConfig) {
  return get<Dict>("/api/me/authorizations", config);
}

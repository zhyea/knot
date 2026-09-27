import {get, put} from "./http";
import type {AxiosRequestConfig} from "axios";
import type {Dict} from "@/types";

export function getMySettings(config: AxiosRequestConfig) {
  return get("/api/user-settings/me", config);
}

export function saveMySettings(settings: Dict, config: AxiosRequestConfig) {
  return put("/api/user-settings/me", {settings}, config);
}

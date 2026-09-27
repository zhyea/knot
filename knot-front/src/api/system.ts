import {postQuery} from "./http";
import type {Dict} from "@/types";

export function listOperationLogs(params: Dict) {
  return postQuery("/api/system/operation-logs", params);
}

export function getOperationLogDetail(id: number | string) {
  return postQuery(`/api/system/operation-logs/${id}`);
}

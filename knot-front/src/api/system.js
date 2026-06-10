import { postQuery } from "./http";

export function listOperationLogs(params) {
  return postQuery("/api/system/operation-logs", params);
}

export function getOperationLogDetail(id) {
  return postQuery(`/api/system/operation-logs/${id}`);
}

import { postQuery } from "./http";

export function listSystemRoles(params) {
  return postQuery("/api/system/roles", params);
}

export function listOperationLogs(params) {
  return postQuery("/api/system/operation-logs", params);
}

export function getOperationLogDetail(id) {
  return postQuery(`/api/system/operation-logs/${id}`);
}


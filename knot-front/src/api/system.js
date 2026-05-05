import { postQuery, post, put } from "./http";

export function listSystemRoles(params) {
  return postQuery("/api/system/roles", params);
}

export function listUsers(params) {
  return postQuery("/api/system/users", params);
}

export function createUser(payload) {
  return post("/api/system/users", payload);
}

export function updateUserStatus(id, payload) {
  return put(`/api/system/users/${id}/status`, payload);
}

export function listOperationLogs(params) {
  return postQuery("/api/system/operation-logs", params);
}

export function getOperationLogDetail(id) {
  return postQuery(`/api/system/operation-logs/${id}`);
}

export function listNodes(params) {
  return postQuery("/api/system/nodes", params);
}

export function createBackupTask() {
  return post("/api/system/backups");
}

export function restoreBackup(id) {
  return post(`/api/system/backups/${id}/restore`);
}

import { get, post, put } from "./http";

export function listSystemRoles() {
  return get("/api/system/roles");
}

export function listUsers() {
  return get("/api/system/users");
}

export function createUser(payload) {
  return post("/api/system/users", payload);
}

export function updateUserStatus(id, payload) {
  return put(`/api/system/users/${id}/status`, payload);
}

export function listOperationLogs() {
  return get("/api/system/operation-logs");
}

export function getOperationLogDetail(id) {
  return get(`/api/system/operation-logs/${id}`);
}

export function listNodes() {
  return get("/api/system/nodes");
}

export function createBackupTask() {
  return post("/api/system/backups");
}

export function restoreBackup(id) {
  return post(`/api/system/backups/${id}/restore`);
}

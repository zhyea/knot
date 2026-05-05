import { postQuery, post } from "./http";

export function listNotifyTemplates(params) {
  return postQuery("/api/notifications/templates/list", params);
}

export function createNotifyTemplate(payload) {
  return post("/api/notifications/templates", payload);
}

export function sendNotification(payload) {
  return post("/api/notifications/send", payload);
}

export function createNotifyPolicy(payload) {
  return post("/api/notifications/policies", payload);
}

import { get, post } from "./http";

export function listNotifyTemplates() {
  return get("/api/notifications/templates");
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

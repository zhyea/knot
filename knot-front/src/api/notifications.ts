import {postQuery, post} from "./http";
import type {Dict} from "@/types";

export function listNotifyTemplates(params: Dict) {
  return postQuery("/api/notifications/templates/list", params);
}

export function createNotifyTemplate(payload: Dict) {
  return post("/api/notifications/templates", payload);
}

export function createNotifyPolicy(payload: Dict) {
  return post("/api/notifications/policies", payload);
}

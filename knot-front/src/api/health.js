import { get } from "./http";

export function getHealth() {
  return get("/api/health");
}

import { postQuery } from "./http";

export function getHealth() {
  return postQuery("/api/health");
}

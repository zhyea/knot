import { get } from "../http";

export function getMyAuthorizations(config) {
  return get("/api/me/authorizations", config);
}

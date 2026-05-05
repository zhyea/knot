import { postQuery } from "./http";

export function listModuleCatalog(params) {
  return postQuery("/api/modules", params);
}

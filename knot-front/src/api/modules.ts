import { postQuery } from "./http";

export function listModuleCatalog(params: Record<string, unknown> = {}) {
  return postQuery("/api/modules", params);
}

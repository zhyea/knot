import { get } from "./http";

export function listModuleCatalog() {
  return get("/api/modules");
}

import type { Dict, Row } from "../types";

export function mergeOptionList(
  existingList: Row[],
  incomingList: Row[],
  valueKey = "id"
): Row[] {
  const map = new Map<unknown, Row>();
  for (const item of existingList || []) {
    if (item?.[valueKey] != null) {
      map.set(item[valueKey], item);
    }
  }
  for (const item of incomingList || []) {
    if (item?.[valueKey] != null) {
      map.set(item[valueKey], item);
    }
  }
  return Array.from(map.values());
}

export function normalizeOptionList(data: unknown): Row[] {
  if (Array.isArray((data as Dict)?.list)) {
    return (data as Dict).list as Row[];
  }
  return Array.isArray(data) ? data : [];
}

export function resolveSelectedOption(
  value: unknown,
  options: Row[],
  fallback: Row | null = null,
  valueKey = "id"
): Row[] {
  if (value == null || value === "") {
    return [];
  }
  const selected = (options || []).find((item) => item?.[valueKey] === value);
  if (selected) {
    return [selected];
  }
  if (fallback?.[valueKey] != null) {
    return [fallback];
  }
  return [{ [valueKey]: value }];
}

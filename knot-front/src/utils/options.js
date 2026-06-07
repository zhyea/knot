export function mergeOptionList(existingList, incomingList, valueKey = "id") {
  const map = new Map();
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

export function normalizeOptionList(data) {
  if (Array.isArray(data?.list)) {
    return data.list;
  }
  return Array.isArray(data) ? data : [];
}

export function resolveSelectedOption(value, options, fallback = null, valueKey = "id") {
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

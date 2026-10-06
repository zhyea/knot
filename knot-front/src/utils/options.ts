import type {Dict, Row} from "@/types";
import type {OptionPage, OptionQuery} from "@/api/options";

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

/** 新 options 契约固定使用 value/label 作为取值与展示字段。 */
export const OPTION_VALUE_KEY = "value";

/** 新 options 契约的 label 字段。 */
export const OPTION_LABEL_KEY = "label";

/**
 * 把 options 接口包装成 RemoteEntitySelect 的 loadFunction。
 *
 * <p>统一行为：把查询参数透传给 options 接口、把返回项并入累积列表（按 value 去重）、
 * 返回整个 OptionPage（组件据此渲染 list，并读取 missingValues 上抛「已选项不存在或无权限」）。
 * extra（编辑态已选项等）由调用方通过 selectedOptions 传入，不在本函数处理。</p>
 */
export function toOptionsLoader<T extends OptionQuery>(
  fetcher: (query: T) => Promise<OptionPage>,
  accumulator?: {value: Row[]}
) {
  return async (params: Dict = {}): Promise<OptionPage> => {
    const page = await fetcher(params as T);
    if (accumulator) {
      accumulator.value = mergeOptionList(accumulator.value, page.list, OPTION_VALUE_KEY);
    }
    return page;
  };
}

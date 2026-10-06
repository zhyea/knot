import type {Dict, Row} from "@/types";
import type {OptionPage, OptionQuery} from "@/api/options";

/** 新 options 契约固定使用 value 作为取值字段（mergeOptionList 去重键）。 */
export const OPTION_VALUE_KEY = "value";

export function mergeOptionList(
  existingList: Row[],
  incomingList: Row[],
  valueKey = "id"
): Row[] {
  const map = new Map<string, Row>();
  for (const item of existingList || []) {
    if (item && item[valueKey] != null) {
      map.set(String(item[valueKey]), item);
    }
  }
  for (const item of incomingList || []) {
    if (item && item[valueKey] != null) {
      map.set(String(item[valueKey]), item);
    }
  }
  return Array.from(map.values());
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

/**
 * 把 options 接口包装成 RemoteEntitySelect 的 loadFunction。
 *
 * <p>统一行为：把查询参数透传给 options 接口、把返回项并入累积列表（按 value 去重，
 * 供调用方在别处复用同一份候选）、把整个 OptionPage 交给组件（组件据此渲染 list，
 * 并读取 missingValues 展示 / 阻止非法提交）。</p>
 *
 * <p>⚠ {@code accumulator} 是**传出**侧用法：调用方传一个 `{value: Row[]}` 容器，
 * 每次加载后候选会被合并进去（ModelFormDrawer / ModelPoolFormDrawer / RoutingRuleFormDrawer
 * 共 7 处依赖它做表格列与联动的取值）。不要因为「直接 grep 函数名找不到调用」就当死代码删掉。</p>
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

/** 下拉单页条数，与后端 OptionQuery 默认 pageSize 对齐（服务端上限 50）。 */
export const OPTION_PAGE_SIZE = 20;

/**
 * 从 options 接口返回体（OptionPage）抽出行列表；兼容旧 PageResult（同样带 list 字段）
 * 与裸数组两种形态。仅取 list，敏感字段隔离由后端保证，前端无需再过滤。
 */
export function normalizeOptionList(result: unknown): Row[] {
  const r = result as Dict;
  if (Array.isArray(r?.list)) {
    return r.list as Row[];
  }
  return Array.isArray(r) ? r : [];
}

/** 缺失项（已删除 / 无权限）占位标签前缀，后端 missingValues 的值拼在后面。 */
export const MISSING_OPTION_LABEL_PREFIX = "已选项不存在或无权限（";

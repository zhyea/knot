import {displayText} from "@/utils/format";

/** 模块目录条目（`/api/modules` 返回，能力清单只有 code/name 是必填） */
export interface DashboardModuleItem {
  code: string;
  name: string;
  capabilities?: string[];
}

/** 排序后用于「资源概览」右侧的模块条目 */
export interface RankedModule extends DashboardModuleItem {
  capabilityCount: number;
  capabilityPreview: string[];
  remainingCapabilityCount: number;
}

/** 汇总会取能力密度最高的前 6 个模块；再多右侧就滚成一条长列表，失去「概览」意义 */
export const MODULE_LIMIT = 6;
/** 每张模块卡最多展示 3 个能力标签，其余折成「+N」 */
const CAPABILITY_PREVIEW = 3;

/**
 * 模块名兜底表：`/api/modules` 没返回中文名时用。
 *
 * <p>⚠ 这是**展示兜底**不是业务字典，别拿它做匹配或判断。新增模块优先在
 * `kb_module_catalog` 里配名字，这里补只为了让老库不至于显示成裸 code。</p>
 */
export const FALLBACK_MODULE_NAMES: Record<string, string> = {
  system: "系统管理",
  model: "供应商模型",
  routing: "路由管理",
  billing: "计费管理",
  app: "应用管理",
  user: "用户管理",
  department: "部门管理",
  enum: "枚举管理",
  provider: "供应商",
  "logical-model": "统一模型",
  "model-pool": "模型池"
};

/** code → 中文名：接口下发的那份优先 */
export function buildModuleNameMap(catalog: DashboardModuleItem[]): Record<string, string> {
  const pairs = (catalog || []).map((item) => [item.code, item.name] as const);
  return Object.fromEntries(pairs);
}

/** 按能力密度排序并截前 N 个；排序不稳定时按原始顺序兜底（sort 是稳定的，无需补 key） */
export function rankModules(catalog: DashboardModuleItem[], limit: number = MODULE_LIMIT): RankedModule[] {
  return [...(catalog || [])]
    .map((module) => {
      const capabilities = Array.isArray(module.capabilities) ? module.capabilities : [];
      return {
        ...module,
        capabilityCount: capabilities.length,
        capabilityPreview: capabilities.slice(0, CAPABILITY_PREVIEW),
        remainingCapabilityCount: Math.max(capabilities.length - CAPABILITY_PREVIEW, 0)
      };
    })
    .sort((a, b) => b.capabilityCount - a.capabilityCount)
    .slice(0, limit);
}

/** 模块 code → 展示名：接口名 → 兜底表 → 原样回显 code（禁出现空字符串） */
export function resolveModuleName(code: string, nameMap: Record<string, string>): string {
  return displayText(nameMap[code] || FALLBACK_MODULE_NAMES[code] || code);
}

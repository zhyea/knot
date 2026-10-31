import type {Row} from "@/types";

/**
 * 总览页四张指标卡的取值口径。
 *
 * <p>⭐ 这里只放**纯计算**：输入「资源总数 + 最近操作」，输出页面要展示的每一项。
 * 不碰 ref、不碰接口，因此可以用一条断言钉住全部比例口径（见同名 .test.ts）。</p>
 */
export interface DashboardTotals {
  apps: number;
  providers: number;
  logicalModels: number;
  routingRules: number;
}

export type MetricTone = "blue" | "teal" | "green" | "orange";

/** 卡片右侧那组小柱子：每根代表一个指标，当前这张卡对应的那根高亮 */
export interface MetricBar {
  key: string;
  active: boolean;
  height: number;
}

export interface MetricItem {
  key: keyof DashboardTotals;
  label: string;
  value: number;
  moduleKey: string;
  route: string;
  tone: MetricTone;
  /** 图标**名**而不是组件：本模块保持纯函数，映射放在 components/dashboard/dashboardIcons */
  iconName: string;
  activityCount: number;
  /** 概览条宽度百分比，带下限（0 值也要留一道可见的底） */
  ratio: number;
  helperText: string;
  distribution: MetricBar[];
}

interface MetricDef {
  key: keyof DashboardTotals;
  label: string;
  moduleKey: string;
  route: string;
  tone: MetricTone;
  iconName: string;
}

/** 比例下限：值为 0 的指标仍要留一道可见的底色，否则四根柱子只剩三根 */
const RATIO_FLOOR = 8;
/** 柱高下限：与比例同因不同量（柱子按容器高度百分比算） */
const BAR_FLOOR = 22;

const METRIC_DEFS: readonly MetricDef[] = [
  {key: "apps", label: "应用", moduleKey: "app", route: "/apps", tone: "blue", iconName: "Grid"},
  {
    key: "providers",
    label: "供应商账户",
    moduleKey: "provider-account",
    route: "/model-management/providers",
    tone: "teal",
    iconName: "Connection"
  },
  {
    key: "logicalModels",
    label: "统一模型",
    moduleKey: "logical-model",
    route: "/model-management/logical-models",
    tone: "green",
    iconName: "Cpu"
  },
  {key: "routingRules", label: "路由规则", moduleKey: "routing", route: "/routing/rules", tone: "orange", iconName: "Share"}
];

/**
 * 列表接口的三种返回形态 → 一个总数。
 *
 * <p>三种都要认：直接返回数组（部分内部接口）、标准分页 `{total}`、以及
 * `{list}` 但没有 total 的老接口。认不出时返回 0 而不是猜。</p>
 */
export function normalizeTotal(result: unknown): number {
  if (Array.isArray(result)) {
    return result.length;
  }
  const row = (result ?? {}) as Row;
  const total = Number(row.total);
  if (!Number.isNaN(total) && total >= 0) {
    return total;
  }
  return Array.isArray(row.list) ? row.list.length : 0;
}

/** 最近操作按模块计数：卡片下方那句「最近 N 条相关操作」的唯一数据源 */
export function buildActivityMap(logs: Row[]): Record<string, number> {
  const map: Record<string, number> = {};
  for (const log of logs || []) {
    const key = log?.module || "";
    if (!key) {
      continue;
    }
    map[key] = (map[key] || 0) + 1;
  }
  return map;
}

/** 四张指标卡：数值、占比、活动文案与柱组 */
export function buildMetrics(totals: DashboardTotals, activityMap: Record<string, number>): MetricItem[] {
  const rows = METRIC_DEFS.map((def) => ({def, value: totals[def.key] || 0}));
  // 分母兜底为 1：四个数全是 0 时不能除以 0，否则比例是 NaN
  const maxValue = Math.max(...rows.map((item) => item.value), 1);

  return rows.map(({def, value}) => {
    const activityCount = activityMap[def.moduleKey] || 0;
    return {
      ...def,
      value,
      activityCount,
      ratio: Math.max(RATIO_FLOOR, Math.round((value / maxValue) * 100)),
      helperText: activityCount > 0 ? `最近 ${activityCount} 条相关操作` : "最近无相关操作",
      distribution: rows.map((source) => ({
        key: source.def.key,
        active: source.def.key === def.key,
        height: Math.max(BAR_FLOOR, Math.round((source.value / maxValue) * 100))
      }))
    };
  });
}

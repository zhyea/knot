import {reactive, ref} from "vue";
import {listApps} from "@/api/apps";
import {getHealth} from "@/api/health";
import {listLogicalModels} from "@/api/logicalModels";
import {listModuleCatalog} from "@/api/modules";
import {listOperationLogs} from "@/api/operationLogs";
import {listProviders} from "@/api/providers";
import {listRoutingRules} from "@/api/routing";
import {formatDateTime} from "@/utils/format";
import {normalizeTotal} from "@/utils/dashboardMetrics";
import type {DashboardTotals} from "@/utils/dashboardMetrics";
import type {DashboardModuleItem} from "@/utils/dashboardModules";
import type {Row} from "@/types";

export interface DashboardHealth {
  status: string;
  serverTime: string;
  dbTime: string;
}

/** 最近操作只取这么多条：卡片高度是固定的，多了会滚成一条窄条 */
const RECENT_LOG_LIMIT = 6;

/**
 * 总览页取数：7 个接口各自独立降级，**一个抖动不拖垮整页**。
 *
 * <p>⭐ 降级而不抛的理由：总览页是运维第一眼看的页面，接口全挂时它更该显示出来
 * （哪怕数字是 0），而不是白屏。代价是「挂了」与「确实为 0」在界面上无法区分，
 * 所以失败必须登记到 {@link failedSections} 并在页头提示——这条不能省。</p>
 */
export function useDashboard() {
  const health = reactive<DashboardHealth>({status: "", serverTime: "", dbTime: ""});
  const statTotals = reactive<DashboardTotals>({apps: 0, providers: 0, logicalModels: 0, routingRules: 0});
  const loading = ref(false);
  /** 本轮成功刷新的时刻；加载失败时不写，否则「更新时间」会撒谎 */
  const lastUpdatedAt = ref("");
  const moduleCatalog = ref<DashboardModuleItem[]>([]);
  const recentLogs = ref<Row[]>([]);
  /** 本轮加载失败的区块名（业务语言，直接进页头提示） */
  const failedSections = ref<string[]>([]);

  /**
   * 单区块降级：失败时返回 fallback **并登记失败区块**。
   *
   * <p>{@code label} 会出现在页头的失败提示里，因此必须写成业务语言
   * （「应用总数」而不是接口路径）。</p>
   */
  async function withFallback<T, F>(label: string, factory: () => Promise<T>, fallback: F): Promise<T | F> {
    try {
      return await factory();
    } catch (error) {
      if (!failedSections.value.includes(label)) {
        failedSections.value.push(label);
      }
      console.warn(`[dashboard] 区块「${label}」加载失败，已按空值展示`, error);
      return fallback;
    }
  }

  async function loadDashboard(): Promise<void> {
    loading.value = true;
    failedSections.value = [];
    try {
      const [
        healthResult,
        moduleResult,
        appsResult,
        providersResult,
        logicalModelsResult,
        routingRulesResult,
        logsResult
      ] = await Promise.all([
        withFallback("健康状态", () => getHealth(), null),
        withFallback("功能模块", () => listModuleCatalog(), []),
        withFallback("应用总数", () => listApps({pageNum: 1, pageSize: 1}), {total: 0, list: []}),
        withFallback("供应商总数", () => listProviders({pageNum: 1, pageSize: 1}), {total: 0, list: []}),
        withFallback("逻辑模型总数", () => listLogicalModels({pageNum: 1, pageSize: 1}), {total: 0, list: []}),
        withFallback("路由规则总数", () => listRoutingRules({pageNum: 1, pageSize: 1}), {total: 0, list: []}),
        withFallback("最近操作", () => listOperationLogs({pageNum: 1, pageSize: RECENT_LOG_LIMIT}), {list: []})
      ]);

      health.status = healthResult?.status || "";
      health.serverTime = formatDateTime(healthResult?.serverTime);
      health.dbTime = formatDateTime(healthResult?.dbTime);

      moduleCatalog.value = Array.isArray(moduleResult) ? moduleResult : [];
      statTotals.apps = normalizeTotal(appsResult);
      statTotals.providers = normalizeTotal(providersResult);
      statTotals.logicalModels = normalizeTotal(logicalModelsResult);
      statTotals.routingRules = normalizeTotal(routingRulesResult);
      recentLogs.value = Array.isArray(logsResult?.list) ? logsResult.list.slice(0, RECENT_LOG_LIMIT) : [];
      lastUpdatedAt.value = new Date().toISOString();
    } catch (error) {
      // 走到这里说明不是某个区块挂了（那 7 个都由 withFallback 接住），而是汇总逻辑本身出错。
      // 仍然不向上抛：调用点是 `onMounted(() => loadDashboard())` 和刷新按钮，没人接 Promise，
      // 抛出去就是 unhandled rejection（同 useEnums 的判例）。留痕由 console.error 负责。
      console.error("[dashboard] 总览数据汇总失败", error);
    } finally {
      loading.value = false;
    }
  }

  return {health, statTotals, loading, lastUpdatedAt, moduleCatalog, recentLogs, failedSections, loadDashboard};
}

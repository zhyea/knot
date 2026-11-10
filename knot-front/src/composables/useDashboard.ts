import {reactive, ref} from "vue";
import {useAuth} from "@/composables/useAuth";
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

/** 汇总逻辑自身异常（非单区块失败）时登记的名字，保证「看着空」一定有提示 */
const SUMMARY_SECTION = "总览数据";

/**
 * 总览页取数：8 个来源各自独立降级，**一个抖动不拖垮整页**。
 *
 * <p>⭐ 降级而不抛的理由：总览页是运维第一眼看的页面，接口全挂时它更该显示出来
 * （哪怕数字是 0），而不是白屏。代价是「挂了」与「确实为 0」在界面上无法区分，
 * 所以失败必须登记到 {@link failedSections} 并在页头提示——这条不能省。</p>
 *
 * <p>⭐ {@link loading} 初值为 true、{@link initialized} 初值为 false：宿主据此
 * **先加载、后渲染数据区**。否则首帧会先把 0 / 空列表铺出来（数据到达前那一眼就是
 * 「加载不完整」），再闪一层蒙版。</p>
 */
export function useDashboard() {
  const {loadAuthorizations} = useAuth();
  const health = reactive<DashboardHealth>({status: "", serverTime: "", dbTime: ""});
  const statTotals = reactive<DashboardTotals>({apps: 0, providers: 0, logicalModels: 0, routingRules: 0});
  const loading = ref(true);
  /** 首轮加载是否已结束：false 时宿主不渲染数据区，只显示加载蒙版 */
  const initialized = ref(false);
  /** 本轮成功刷新的时刻；加载失败时不写，否则「更新时间」会撒谎 */
  const lastUpdatedAt = ref("");
  const moduleCatalog = ref<DashboardModuleItem[]>([]);
  const recentLogs = ref<Row[]>([]);
  /** 本轮加载失败的区块名（业务语言，直接进页头提示） */
  const failedSections = ref<string[]>([]);
  /**
   * 加载轮次。连续刷新（进入页面 + 手点刷新、组件重挂载）时只认最后一次：
   * 慢请求后到就把新数据覆盖回旧值，正是「越刷新越不完整」的来源。
   */
  let loadSequence = 0;

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
    const sequence = ++loadSequence;
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
        withFallback("最近操作", () => listOperationLogs({pageNum: 1, pageSize: RECENT_LOG_LIMIT}), {list: []}),
        // 快捷入口来自授权菜单。登录后授权是异步拉取的（不阻塞登录跳转），
        // 不等它就会先渲染「暂无可访问入口」，这正是登录后首次进首页的缺块。
        withFallback("可访问菜单", () => loadAuthorizations(), null)
      ]);

      // 已有更新的加载在跑：本次结果作废，别覆盖新数据，也别提前撤掉蒙版
      if (sequence !== loadSequence) {
        return;
      }

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
      // 走到这里说明不是某个区块挂了（那 8 个都由 withFallback 接住），而是汇总逻辑本身出错。
      // 仍然不向上抛：调用点是 `onMounted(() => loadDashboard())` 和刷新按钮，没人接 Promise，
      // 抛出去就是 unhandled rejection（同 useEnums 的判例）。留痕由 console.error 负责。
      // ⭐ 但必须登记失败：否则界面只显示半截数据、连页头提示都没有，就成了无声的「加载不完整」。
      console.error("[dashboard] 总览数据汇总失败", error);
      if (sequence === loadSequence && !failedSections.value.includes(SUMMARY_SECTION)) {
        failedSections.value.push(SUMMARY_SECTION);
      }
    } finally {
      // 只有最新一轮才允许结束蒙版 / 放行数据区
      if (sequence === loadSequence) {
        loading.value = false;
        initialized.value = true;
      }
    }
  }

  return {health, statTotals, loading, initialized, lastUpdatedAt, moduleCatalog, recentLogs, failedSections, loadDashboard};
}

<template>
  <!--
    ⭐ 先加载、后渲染：数据区在 initialized 之前根本不存在，蒙版下面不会露出
    一屏 0 / 空列表。刷新时数据区保留旧值，由蒙版盖住，避免高度塌陷。
  -->
  <div
    v-loading="loading"
    :element-loading-text="loadingText"
    class="dashboard-page"
    :class="{'dashboard-page--booting': !initialized}"
  >
    <DashboardHero :updated-at-text="lastUpdatedAtText" @refresh="loadDashboard" />

    <template v-if="initialized">
      <el-alert
        v-if="failedSections.length"
        type="warning"
        show-icon
        :closable="false"
        :title="`部分数据加载失败：${failedSections.join('、')}`"
        description="上述区块已按 0 / 空展示，数值不代表真实情况。请点击右上角刷新重试，或检查对应服务是否可用。"
      />

      <DashboardMetricGrid :metrics="metrics" @select="goToRoute" />

      <section class="dashboard-main">
        <DashboardOverviewPanel :metrics="metrics" :modules="rankedModules" :module-count="moduleCatalog.length" />

        <div class="dashboard-side">
          <DashboardRuntimePanel
            :status="health.status"
            :server-time="health.serverTime"
            :db-time="health.dbTime"
            :module-count="moduleCatalog.length"
          />
          <DashboardQuickLinks :links="quickLinks" @select="goToRoute" />
          <DashboardRecentActivity
            :logs="recentLogs"
            :module-name-map="moduleNameMap"
            @open-all="goToRoute('/system/logs')"
          />
        </div>
      </section>
    </template>
  </div>
</template>

<script setup lang="ts">
import {computed, onMounted} from "vue";
import {useRouter} from "vue-router";
import {useAuth} from "@/composables/useAuth";
import {useTimezone} from "@/composables/useTimezone";
import {useDashboard} from "@/composables/useDashboard";
import {buildActivityMap, buildMetrics} from "@/utils/dashboardMetrics";
import {buildModuleNameMap, rankModules} from "@/utils/dashboardModules";
import {collectQuickLinks} from "@/utils/dashboardQuickLinks";
import {EMPTY_PLACEHOLDER, formatDateTimeInTimezone} from "@/utils/format";
import {resolveDashboardIcon} from "@/components/dashboard/dashboardIcons";
import DashboardHero from "@/components/dashboard/DashboardHero.vue";
import DashboardMetricGrid from "@/components/dashboard/DashboardMetricGrid.vue";
import DashboardOverviewPanel from "@/components/dashboard/DashboardOverviewPanel.vue";
import DashboardRuntimePanel from "@/components/dashboard/DashboardRuntimePanel.vue";
import DashboardQuickLinks from "@/components/dashboard/DashboardQuickLinks.vue";
import DashboardRecentActivity from "@/components/dashboard/DashboardRecentActivity.vue";

/**
 * 控制台总览：**只做布局与拼装**。
 *
 * <p>取数与降级在 {@link useDashboard}，比例/排序/收集在 utils/dashboard*，
 * 展示在 components/dashboard/*。这里剩下的每个 computed 都是「把两份数据拼起来」。</p>
 */
const router = useRouter();
const {modules: authorizedModules} = useAuth();
const {current: currentTimezone} = useTimezone();
const {health, statTotals, loading, initialized, lastUpdatedAt, moduleCatalog, recentLogs, failedSections, loadDashboard} =
  useDashboard();

/** 首次加载与「刷新已有数据」是两种等待语义，文案分别说清 */
const loadingText = computed(() => (initialized.value ? "正在刷新看板数据…" : "正在加载看板数据…"));
const lastUpdatedAtText = computed(() =>
  lastUpdatedAt.value ? formatDateTimeInTimezone(lastUpdatedAt.value, currentTimezone.value) : EMPTY_PLACEHOLDER
);
const moduleNameMap = computed(() => buildModuleNameMap(moduleCatalog.value));
const metrics = computed(() => buildMetrics(statTotals, buildActivityMap(recentLogs.value)));
const rankedModules = computed(() => rankModules(moduleCatalog.value));
const quickLinks = computed(() => collectQuickLinks(authorizedModules.value || [], resolveDashboardIcon));

function goToRoute(path: string) {
  if (!path) {
    return;
  }
  router.push(path);
}

// loadDashboard 内部已兜住异常（见 useDashboard 的 catch），这里不需要再接 Promise。
// 挂载即开加载：loading 初值已是 true，所以「进入页面」到「数据就绪」之间只有蒙版，没有半成品。
onMounted(() => {
  loadDashboard();
});
</script>

<style scoped>
.dashboard-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  width: 100%;
  min-height: calc(100vh - var(--layout-header-height, 60px));
  padding: 20px;
  box-sizing: border-box;
}

.dashboard-main {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(340px, 1fr);
  gap: 16px;
  align-items: stretch;
  flex: 1 1 auto;
  min-height: 0;
}

.dashboard-side {
  display: grid;
  grid-template-rows: auto auto minmax(0, 1fr);
  gap: 16px;
  min-height: 0;
}

@media (max-width: 1180px) {
  /* 单列堆叠时不再强撑高度，保持自然高度 */
  .dashboard-page {
    min-height: 0;
  }

  /* 例外：首轮加载数据区还没挂载，若不撑高蒙版就只剩 Hero 那一条 */
  .dashboard-page--booting {
    min-height: calc(100vh - var(--layout-header-height, 60px));
  }

  .dashboard-main {
    flex: 0 0 auto;
    grid-template-columns: minmax(0, 1fr);
  }

  .dashboard-side {
    grid-template-rows: auto;
  }
}

@media (max-width: 768px) {
  .dashboard-page {
    padding: 16px;
  }
}
</style>

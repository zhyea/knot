<template>
  <div v-loading="loading" class="dashboard-page">
    <section class="dashboard-hero">
      <div class="dashboard-hero__copy">
        <p class="dashboard-hero__eyebrow">Knot AI Gateway</p>
        <h1 class="dashboard-hero__title">控制台总览</h1>
        <p class="dashboard-hero__desc">
          统一查看应用接入、模型资源、路由配置和最近操作，减少在多个页面之间来回切换。
        </p>
      </div>
      <div class="dashboard-hero__actions">
        <div class="refresh-pill">
          <span class="refresh-pill__label">最近刷新</span>
          <strong class="refresh-pill__value">{{ lastUpdatedAt || "--" }}</strong>
        </div>
        <el-button :icon="RefreshRight" type="primary" plain @click="loadDashboard">刷新</el-button>
      </div>
    </section>

    <section class="metric-grid">
      <article
        v-for="metric in metrics"
        :key="metric.key"
        class="metric-card"
        :class="`metric-card--${metric.tone}`"
        @click="goToRoute(metric.route)"
      >
        <div class="metric-card__meta">
          <span class="metric-card__label">{{ metric.label }}</span>
          <el-icon class="metric-card__icon"><component :is="metric.icon" /></el-icon>
        </div>
        <div class="metric-card__value">{{ formatCount(metric.value) }}</div>
        <div class="metric-card__hint" :class="{ 'metric-card__hint--active': metric.activityCount > 0 }">
          {{ metric.helperText }}
        </div>
        <div class="metric-card__bars" aria-hidden="true">
          <span
            v-for="bar in metric.distribution"
            :key="bar.key"
            class="metric-card__bar"
            :class="{ 'metric-card__bar--active': bar.active }"
            :style="{ height: `${bar.height}%` }"
          />
        </div>
      </article>
    </section>

    <section class="dashboard-main">
      <div class="dashboard-panel dashboard-panel--overview">
        <div class="panel-header">
          <div>
            <h2 class="panel-title">资源概览</h2>
            <p class="panel-subtitle">按当前系统资源数量和模块能力密度汇总。</p>
          </div>
          <span class="panel-badge">模块 {{ formatCount(moduleCatalog.length) }}</span>
        </div>

        <div class="overview-layout">
          <div class="overview-chart">
            <div v-for="metric in metrics" :key="metric.key" class="overview-row">
              <div class="overview-row__head">
                <span class="overview-row__label">{{ metric.label }}</span>
                <strong class="overview-row__value">{{ formatCount(metric.value) }}</strong>
              </div>
              <div class="overview-row__track">
                <span
                  class="overview-row__fill"
                  :class="`overview-row__fill--${metric.tone}`"
                  :style="{ width: `${metric.ratio}%` }"
                />
              </div>
              <div class="overview-row__meta">{{ metric.helperText }}</div>
            </div>
          </div>

          <div class="module-overview">
            <div v-for="module in rankedModules" :key="module.code" class="module-overview__item">
              <div class="module-overview__head">
                <div>
                  <div class="module-overview__name">{{ module.name }}</div>
                  <div class="module-overview__code">{{ module.code }}</div>
                </div>
                <span class="module-overview__count">{{ module.capabilityCount }} 项能力</span>
              </div>
              <div class="module-overview__caps">
                <el-tag
                  v-for="capability in module.capabilityPreview"
                  :key="capability"
                  size="small"
                  effect="plain"
                >
                  {{ capability }}
                </el-tag>
                <span v-if="module.remainingCapabilityCount > 0" class="module-overview__more">
                  +{{ module.remainingCapabilityCount }}
                </span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="dashboard-side">
        <div class="dashboard-panel">
          <div class="panel-header">
            <div>
              <h2 class="panel-title">运行状态</h2>
              <p class="panel-subtitle">查看服务和数据源的当前时间戳。</p>
            </div>
          </div>

          <div class="status-hero">
            <span class="status-hero__label">服务状态</span>
            <span class="status-hero__value" :class="healthStatusClass">
              {{ health.status || "--" }}
            </span>
          </div>

          <div class="status-list">
            <div class="status-list__item">
              <span class="status-list__label">服务时间</span>
              <strong class="status-list__value">{{ health.serverTime || "--" }}</strong>
            </div>
            <div class="status-list__item">
              <span class="status-list__label">数据库时间</span>
              <strong class="status-list__value">{{ health.dbTime || "--" }}</strong>
            </div>
            <div class="status-list__item">
              <span class="status-list__label">模块目录</span>
              <strong class="status-list__value">{{ formatCount(moduleCatalog.length) }}</strong>
            </div>
          </div>
        </div>

        <div class="dashboard-panel">
          <div class="panel-header">
            <div>
              <h2 class="panel-title">快捷入口</h2>
              <p class="panel-subtitle">优先展示你当前可访问的页面。</p>
            </div>
          </div>

          <div class="quick-links">
            <button
              v-for="link in quickLinks"
              :key="link.path"
              class="quick-links__item"
              type="button"
              @click="goToRoute(link.path)"
            >
              <el-icon class="quick-links__icon"><component :is="link.icon" /></el-icon>
              <span class="quick-links__text">{{ link.label }}</span>
            </button>
            <el-empty v-if="!quickLinks.length" description="暂无可访问入口" :image-size="72" />
          </div>
        </div>

        <div class="dashboard-panel">
          <div class="panel-header">
            <div>
              <h2 class="panel-title">最近操作</h2>
              <p class="panel-subtitle">展示最新的操作记录摘要。</p>
            </div>
            <el-button link type="primary" @click="goToRoute('/system/logs')">查看全部</el-button>
          </div>

          <div v-if="recentLogs.length" class="activity-list">
            <button
              v-for="log in recentLogs"
              :key="log.id"
              class="activity-list__item"
              type="button"
              @click="goToRoute('/system/logs')"
            >
              <div class="activity-list__main">
                <div class="activity-list__title">
                  <span class="activity-list__module">{{ resolveModuleName(log.module) }}</span>
                  <span class="activity-list__operation">{{ log.operation || "--" }}</span>
                </div>
                <div class="activity-list__entity">{{ log.entityName || log.entityType || "--" }}</div>
              </div>
              <div class="activity-list__meta">
                <el-tag :type="log.status === 'SUCCESS' ? 'success' : 'danger'" size="small">
                  {{ log.status || "--" }}
                </el-tag>
                <span class="activity-list__time">{{ formatDateTime(log.createdAt) }}</span>
              </div>
            </button>
          </div>
          <el-empty v-else description="暂无操作记录" :image-size="72" />
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import {
  Bell,
  Connection,
  Cpu,
  Grid,
  Lock,
  Money,
  OfficeBuilding,
  Odometer,
  RefreshRight,
  Setting,
  Share,
  Tools,
  User
} from "@element-plus/icons-vue";
import { listApps } from "../api/apps";
import { getHealth } from "../api/health";
import { listLogicalModels } from "../api/logicalModels";
import { listModuleCatalog } from "../api/modules";
import { listOperationLogs } from "../api/operationLogs";
import { listProviders } from "../api/providers";
import { listRoutingRules } from "../api/routing";
import { useAuth } from "../composables/useAuth";
import { formatDateTime } from "../utils/format";

const router = useRouter();
const { modules: authorizedModules } = useAuth();

const health = reactive({
  status: "",
  serverTime: "",
  dbTime: ""
});

const statTotals = reactive({
  apps: 0,
  providers: 0,
  logicalModels: 0,
  routingRules: 0
});

const loading = ref(false);
const lastUpdatedAt = ref("");
const moduleCatalog = ref([]);
const recentLogs = ref([]);

const iconMap = {
  Bell,
  Box: Cpu,
  Coin: Money,
  Connection,
  Cpu,
  Document: Bell,
  Grid,
  Lock,
  OfficeBuilding,
  Odometer,
  Setting,
  Share,
  Tools,
  User
};

const fallbackModuleNameMap = {
  system: "系统管理",
  model: "供应商模型",
  routing: "路由管理",
  billing: "计费管理",
  app: "应用管理",
  user: "用户管理",
  department: "部门管理",
  enum: "枚举管理",
  provider: "供应商管理",
  "logical-model": "模型广场"
};

const recentActivityMap = computed(() => {
  const map = {};
  for (const log of recentLogs.value) {
    const key = log?.module || "";
    if (!key) continue;
    map[key] = (map[key] || 0) + 1;
  }
  return map;
});

const moduleNameMap = computed(() => {
  const pairs = (moduleCatalog.value || []).map((item) => [item.code, item.name]);
  return Object.fromEntries(pairs);
});

const metrics = computed(() => {
  const metricDefs = [
    {
      key: "apps",
      label: "应用",
      value: statTotals.apps,
      moduleKey: "app",
      route: "/apps",
      tone: "blue",
      icon: Grid
    },
    {
      key: "providers",
      label: "供应商",
      value: statTotals.providers,
      moduleKey: "provider",
      route: "/providers",
      tone: "teal",
      icon: Connection
    },
    {
      key: "logicalModels",
      label: "统一模型",
      value: statTotals.logicalModels,
      moduleKey: "logical-model",
      route: "/model-management/logical-models",
      tone: "green",
      icon: Cpu
    },
    {
      key: "routingRules",
      label: "路由规则",
      value: statTotals.routingRules,
      moduleKey: "routing",
      route: "/routing/rules",
      tone: "orange",
      icon: Share
    }
  ];

  const maxValue = Math.max(...metricDefs.map((item) => item.value || 0), 1);

  return metricDefs.map((item) => {
    const activityCount = recentActivityMap.value[item.moduleKey] || 0;
    return {
      ...item,
      activityCount,
      ratio: Math.max(8, Math.round(((item.value || 0) / maxValue) * 100)),
      helperText: activityCount > 0 ? `最近 ${activityCount} 条相关操作` : "最近无相关操作",
      distribution: metricDefs.map((source) => ({
        key: source.key,
        active: source.key === item.key,
        height: Math.max(22, Math.round(((source.value || 0) / maxValue) * 100))
      }))
    };
  });
});

const rankedModules = computed(() =>
  [...(moduleCatalog.value || [])]
    .map((module) => {
      const capabilities = Array.isArray(module.capabilities) ? module.capabilities : [];
      return {
        ...module,
        capabilityCount: capabilities.length,
        capabilityPreview: capabilities.slice(0, 3),
        remainingCapabilityCount: Math.max(capabilities.length - 3, 0)
      };
    })
    .sort((a, b) => b.capabilityCount - a.capabilityCount)
    .slice(0, 6)
);

const quickLinks = computed(() => {
  const links = [];

  function collectMenus(menus, parentIcon) {
    for (const menu of menus || []) {
      const children = Array.isArray(menu.children) ? menu.children : [];
      if (children.length) {
        collectMenus(children, menu.icon || parentIcon);
        continue;
      }
      if (!menu.routePath) {
        continue;
      }
      links.push({
        path: menu.routePath,
        label: menu.menuName,
        icon: resolveIcon(menu.icon || parentIcon)
      });
    }
  }

  for (const module of authorizedModules.value || []) {
    collectMenus(module.menus, module.icon);
  }

  const unique = [];
  const seen = new Set();
  for (const link of links) {
    if (seen.has(link.path)) {
      continue;
    }
    seen.add(link.path);
    unique.push(link);
  }

  return unique.slice(0, 6);
});

const healthStatusClass = computed(() => ({
  "status-hero__value--up": ["UP", "OK"].includes(`${health.status}`.toUpperCase()),
  "status-hero__value--down": health.status && !["UP", "OK"].includes(`${health.status}`.toUpperCase())
}));

function resolveIcon(iconName) {
  return iconMap[iconName] || Setting;
}

function resolveModuleName(code) {
  return moduleNameMap.value[code] || fallbackModuleNameMap[code] || code || "--";
}

function normalizeTotal(result) {
  if (Array.isArray(result)) {
    return result.length;
  }
  const total = Number(result?.total);
  if (!Number.isNaN(total) && total >= 0) {
    return total;
  }
  return Array.isArray(result?.list) ? result.list.length : 0;
}

async function withFallback(factory, fallback) {
  try {
    return await factory();
  } catch {
    return fallback;
  }
}

function formatCount(value) {
  return new Intl.NumberFormat("zh-CN").format(Number(value || 0));
}

function goToRoute(path) {
  if (!path) {
    return;
  }
  router.push(path);
}

async function loadDashboard() {
  loading.value = true;
  try {
    const [healthResult, moduleResult, appsResult, providersResult, logicalModelsResult, routingRulesResult, logsResult] =
      await Promise.all([
        withFallback(() => getHealth(), null),
        withFallback(() => listModuleCatalog(), []),
        withFallback(() => listApps({ pageNum: 1, pageSize: 1 }), { total: 0, list: [] }),
        withFallback(() => listProviders({ pageNum: 1, pageSize: 1 }), { total: 0, list: [] }),
        withFallback(() => listLogicalModels({ pageNum: 1, pageSize: 1 }), { total: 0, list: [] }),
        withFallback(() => listRoutingRules({ pageNum: 1, pageSize: 1 }), { total: 0, list: [] }),
        withFallback(() => listOperationLogs({ pageNum: 1, pageSize: 6 }), { list: [] })
      ]);

    health.status = healthResult?.status || "";
    health.serverTime = formatDateTime(healthResult?.serverTime);
    health.dbTime = formatDateTime(healthResult?.dbTime);

    moduleCatalog.value = Array.isArray(moduleResult) ? moduleResult : [];
    statTotals.apps = normalizeTotal(appsResult);
    statTotals.providers = normalizeTotal(providersResult);
    statTotals.logicalModels = normalizeTotal(logicalModelsResult);
    statTotals.routingRules = normalizeTotal(routingRulesResult);
    recentLogs.value = Array.isArray(logsResult?.list) ? logsResult.list.slice(0, 6) : [];
    lastUpdatedAt.value = formatDateTime(new Date().toISOString());
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  loadDashboard();
});
</script>

<style scoped>
.dashboard-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  max-width: 1440px;
  padding: 20px;
  box-sizing: border-box;
}

.dashboard-hero {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.dashboard-hero__copy {
  min-width: 0;
}

.dashboard-hero__eyebrow {
  margin: 0 0 8px;
  font-size: 12px;
  color: #909399;
  text-transform: uppercase;
}

.dashboard-hero__title {
  margin: 0;
  font-size: 28px;
  line-height: 1.2;
  color: #1f2937;
}

.dashboard-hero__desc {
  margin: 10px 0 0;
  max-width: 720px;
  font-size: 14px;
  line-height: 1.7;
  color: #606266;
}

.dashboard-hero__actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.refresh-pill {
  display: inline-flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  min-width: 180px;
  padding: 10px 14px;
  border: 1px solid var(--knot-border, #ebeef5);
  background: #fff;
}

.refresh-pill__label {
  font-size: 12px;
  color: #909399;
}

.refresh-pill__value {
  font-size: 14px;
  color: #303133;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.metric-card {
  position: relative;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  grid-template-areas:
    "meta bars"
    "value bars"
    "hint bars";
  gap: 10px 16px;
  min-height: 148px;
  padding: 18px 20px;
  border: 1px solid var(--knot-border, #ebeef5);
  background: #fff;
  cursor: pointer;
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease,
    transform 0.2s ease;
}

.metric-card:hover {
  border-color: #c6d2e1;
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.06);
  transform: translateY(-1px);
}

.metric-card__meta {
  grid-area: meta;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-width: 0;
}

.metric-card__label {
  font-size: 15px;
  font-weight: 600;
  color: #7a8a9e;
}

.metric-card__icon {
  width: 34px;
  height: 34px;
  border: 1px solid #e7edf5;
  font-size: 16px;
  color: #4b88d8;
  background: #f7fbff;
}

.metric-card__value {
  grid-area: value;
  font-size: 34px;
  line-height: 1;
  font-weight: 700;
  color: #1f2937;
}

.metric-card__hint {
  grid-area: hint;
  align-self: end;
  font-size: 13px;
  color: #909399;
}

.metric-card__hint--active {
  color: #14b8a6;
}

.metric-card__bars {
  grid-area: bars;
  align-self: stretch;
  display: flex;
  align-items: flex-end;
  gap: 8px;
  min-width: 112px;
  padding-top: 8px;
}

.metric-card__bar {
  flex: 1 1 0;
  min-width: 12px;
  background: #cfe2fb;
}

.metric-card__bar--active {
  background: #4a94e8;
}

.metric-card--teal .metric-card__icon {
  color: #1296a8;
  background: #f0fbfc;
}

.metric-card--teal .metric-card__bar--active,
.overview-row__fill--teal {
  background: #23b3c9;
}

.metric-card--green .metric-card__icon {
  color: #2f9e6d;
  background: #f1fbf6;
}

.metric-card--green .metric-card__bar--active,
.overview-row__fill--green {
  background: #56b881;
}

.metric-card--orange .metric-card__icon {
  color: #d97706;
  background: #fff7ed;
}

.metric-card--orange .metric-card__bar--active,
.overview-row__fill--orange {
  background: #f59e0b;
}

.overview-row__fill--blue {
  background: #4a94e8;
}

.dashboard-main {
  display: grid;
  grid-template-columns: minmax(0, 1.8fr) minmax(320px, 1fr);
  gap: 16px;
  align-items: start;
}

.dashboard-side {
  display: grid;
  gap: 16px;
}

.dashboard-panel {
  border: 1px solid var(--knot-border, #ebeef5);
  background: #fff;
  padding: 18px 20px;
}

.dashboard-panel--overview {
  min-height: 520px;
}

.panel-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 18px;
}

.panel-title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #1f2937;
}

.panel-subtitle {
  margin: 8px 0 0;
  font-size: 13px;
  line-height: 1.6;
  color: #909399;
}

.panel-badge {
  display: inline-flex;
  align-items: center;
  height: 30px;
  padding: 0 12px;
  background: #f5f7fa;
  color: #606266;
  font-size: 13px;
}

.overview-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(280px, 1fr);
  gap: 24px;
  align-items: start;
}

.overview-chart {
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding-right: 8px;
}

.overview-row {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.overview-row__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.overview-row__label {
  font-size: 14px;
  color: #606266;
}

.overview-row__value {
  font-size: 18px;
  color: #1f2937;
}

.overview-row__track {
  position: relative;
  height: 12px;
  overflow: hidden;
  background: #eef3f8;
}

.overview-row__fill {
  display: block;
  height: 100%;
}

.overview-row__meta {
  font-size: 12px;
  color: #909399;
}

.module-overview {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.module-overview__item {
  padding: 14px;
  border: 1px solid #edf2f7;
  background: #fbfcfe;
}

.module-overview__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.module-overview__name {
  font-size: 15px;
  font-weight: 600;
  color: #1f2937;
}

.module-overview__code {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
}

.module-overview__count {
  font-size: 12px;
  color: #4b88d8;
}

.module-overview__caps {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.module-overview__more {
  display: inline-flex;
  align-items: center;
  font-size: 12px;
  color: #909399;
}

.status-hero {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 16px 18px;
  background: #f8fafc;
}

.status-hero__label {
  font-size: 13px;
  color: #909399;
}

.status-hero__value {
  font-size: 28px;
  font-weight: 700;
  color: #d97706;
}

.status-hero__value--up {
  color: #16a34a;
}

.status-hero__value--down {
  color: #dc2626;
}

.status-list {
  display: grid;
  gap: 12px;
  margin-top: 16px;
}

.status-list__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.status-list__label {
  font-size: 13px;
  color: #909399;
}

.status-list__value {
  font-size: 13px;
  color: #303133;
  text-align: right;
}

.quick-links {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.quick-links__item {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 52px;
  padding: 0 14px;
  border: 1px solid #e8eef5;
  background: #fff;
  cursor: pointer;
  text-align: left;
  transition:
    border-color 0.2s ease,
    background 0.2s ease;
}

.quick-links__item:hover {
  border-color: #bfd3eb;
  background: #f8fbff;
}

.quick-links__icon {
  flex: 0 0 auto;
  font-size: 16px;
  color: #4a94e8;
}

.quick-links__text {
  min-width: 0;
  font-size: 13px;
  color: #303133;
}

.activity-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.activity-list__item {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 0;
  border-top: 1px solid #f0f2f5;
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.activity-list__item:first-child {
  padding-top: 0;
  border-top: none;
}

.activity-list__main,
.activity-list__meta {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
}

.activity-list__main {
  flex: 1;
}

.activity-list__title {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.activity-list__module {
  font-size: 13px;
  font-weight: 600;
  color: #1f2937;
}

.activity-list__operation,
.activity-list__entity,
.activity-list__time {
  font-size: 12px;
  color: #909399;
}

.activity-list__meta {
  align-items: flex-end;
}

@media (max-width: 1280px) {
  .metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .dashboard-main,
  .overview-layout {
    grid-template-columns: minmax(0, 1fr);
  }

  .dashboard-panel--overview {
    min-height: 0;
  }
}

@media (max-width: 768px) {
  .dashboard-page {
    padding: 16px;
  }

  .dashboard-hero,
  .dashboard-hero__actions {
    flex-direction: column;
    align-items: stretch;
  }

  .metric-grid,
  .quick-links {
    grid-template-columns: minmax(0, 1fr);
  }

  .metric-card {
    grid-template-columns: minmax(0, 1fr);
    grid-template-areas:
      "meta"
      "value"
      "hint"
      "bars";
  }

  .metric-card__bars {
    min-width: 0;
    height: 72px;
  }

  .activity-list__item {
    flex-direction: column;
  }

  .activity-list__meta {
    align-items: flex-start;
  }
}
</style>

<template>
  <el-container class="layout">
    <el-aside
      :width="asideWidthCss"
      class="aside"
      :class="{ 'aside--collapsed': asideCollapsed, 'aside--resizing': asideResizing }"
    >
      <el-container class="aside-shell" direction="vertical">
        <el-header class="aside-header">
          <el-tooltip :content="t('app.brand')" placement="right" :disabled="!asideCollapsed">
            <span class="aside-logo">{{ asideCollapsed ? "K" : t("app.brand") }}</span>
          </el-tooltip>
        </el-header>

        <el-main class="aside-nav">
          <el-scrollbar class="aside-scrollbar">
            <el-menu
              class="aside-menu"
              :collapse="asideCollapsed"
              :collapse-transition="true"
              :default-active="activePath"
              :default-openeds="openeds"
              router
            >
              <el-menu-item index="/">
                <el-icon><Odometer /></el-icon>
                <span>{{ t("menu.dashboard") }}</span>
              </el-menu-item>

              <el-sub-menu index="/system">
                <template #title>
                  <el-icon><Setting /></el-icon>
                  <span>{{ t("menu.system") }}</span>
                </template>
                <el-menu-item index="/system/users">{{ t("menu.users") }}</el-menu-item>
                <el-menu-item index="/system/departments">{{ t("menu.departments") }}</el-menu-item>
                <el-menu-item index="/system/roles">{{ t("menu.roles") }}</el-menu-item>
                <el-menu-item index="/system/logs">{{ t("menu.logs") }}</el-menu-item>
                <el-menu-item index="/system/scheduled-tasks">{{ t("menu.scheduledTasks") }}</el-menu-item>
                <el-menu-item index="/system/enums">{{ t("menu.enums") }}</el-menu-item>
                <el-menu-item index="/system/settings">{{ t("menu.settings") }}</el-menu-item>
                <el-menu-item index="/system/plugins">{{ t("menu.plugins") }}</el-menu-item>
              </el-sub-menu>

              <el-menu-item index="/providers">
                <el-icon><Connection /></el-icon>
                <span>{{ t("menu.providers") }}</span>
              </el-menu-item>

              <el-sub-menu index="/model-management">
                <template #title>
                  <el-icon><Cpu /></el-icon>
                  <span>{{ t("menu.modelManagement") }}</span>
                </template>
                <el-menu-item index="/model-management/models">{{ t("menu.models") }}</el-menu-item>
                <el-menu-item index="/model-management/model-pools">{{ t("menu.modelPools") }}</el-menu-item>
                <el-menu-item index="/model-management/logical-models">{{ t("menu.logicalModels") }}</el-menu-item>
                <el-menu-item index="/model-management/external-models">{{ t("menu.externalModels") }}</el-menu-item>
              </el-sub-menu>

              <el-menu-item index="/apps">
                <el-icon><Monitor /></el-icon>
                <span>{{ t("menu.apps") }}</span>
              </el-menu-item>

              <el-sub-menu index="/routing">
                <template #title>
                  <el-icon><Guide /></el-icon>
                  <span>{{ t("menu.routing") }}</span>
                </template>
                <el-menu-item index="/routing/rules">{{ t("menu.routingRules") }}</el-menu-item>
                <el-menu-item index="/routing/consumers">{{ t("menu.routingConsumers") }}</el-menu-item>
              </el-sub-menu>

              <el-sub-menu index="/billing">
                <template #title>
                  <el-icon><Money /></el-icon>
                  <span>{{ t("menu.billing") }}</span>
                </template>
                <el-menu-item index="/billing/rules">{{ t("menu.billingRules") }}</el-menu-item>
                <el-menu-item index="/billing/reconciliation">{{ t("menu.reconciliation") }}</el-menu-item>
              </el-sub-menu>

              <el-sub-menu index="/security">
                <template #title>
                  <el-icon><Lock /></el-icon>
                  <span>{{ t("menu.security") }}</span>
                </template>
                <el-menu-item index="/security/policy">{{ t("menu.securityPolicy") }}</el-menu-item>
                <el-menu-item index="/security/alerts">{{ t("menu.securityAlerts") }}</el-menu-item>
                <el-menu-item index="/security/cache">{{ t("menu.securityCache") }}</el-menu-item>
              </el-sub-menu>

              <el-sub-menu index="/notifications">
                <template #title>
                  <el-icon><Bell /></el-icon>
                  <span>{{ t("menu.notifications") }}</span>
                </template>
                <el-menu-item index="/notifications/templates">{{ t("menu.notifyTemplates") }}</el-menu-item>
                <el-menu-item index="/notifications/send">{{ t("menu.notifySend") }}</el-menu-item>
                <el-menu-item index="/notifications/policy">{{ t("menu.notifyPolicy") }}</el-menu-item>
              </el-sub-menu>
            </el-menu>
          </el-scrollbar>
        </el-main>
      </el-container>

      <div
        v-show="!asideCollapsed"
        class="aside-resizer"
        :title="t('header.resizeSidebar')"
        @mousedown.prevent="onAsideResizeStart"
      />
    </el-aside>

    <el-container class="layout-right">
      <el-header class="header">
        <div class="header-title">
          <el-tooltip :content="asideCollapsed ? t('header.expandSidebar') : t('header.collapseSidebar')" placement="bottom">
            <el-button
              type="primary"
              :icon="asideCollapsed ? Expand : Fold"
              class="header-collapse"
              size="small"
              plain
              @click="toggleAsideCollapsed"
            />
          </el-tooltip>
          <span>{{ pageTitle }}</span>
        </div>

        <div class="header-right">
          <el-dropdown @command="handleCommand">
            <span class="user-info">
              <el-icon><User /></el-icon>
              <span>{{ user?.realName || user?.username || t("common.user") }}</span>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout">{{ t("common.logout") }}</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="main">
        <el-scrollbar class="main-scrollbar">
          <router-view />
        </el-scrollbar>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import {
  Bell,
  Connection,
  Cpu,
  Expand,
  Fold,
  Guide,
  Lock,
  Money,
  Monitor,
  Odometer,
  Setting,
  User
} from "@element-plus/icons-vue";
import { useAuth } from "../composables/useAuth";
import { useLocale } from "../composables/useLocale";
import { getStorageItem, setStorageItem } from "../utils/storage";
import { ref } from "vue";

const LS_ASIDE_WIDTH = "knot.sidebar.widthPx";
const LS_ASIDE_COLLAPSED = "knot.sidebar.collapsed";
const ASIDE_MIN = 180;
const ASIDE_MAX = 420;
const ASIDE_DEFAULT = 220;
const ASIDE_COLLAPSED_PX = 64;

const route = useRoute();
const router = useRouter();
const { user, logout } = useAuth();
const { t } = useLocale();

const activePath = computed(() => route.path);
const pageTitle = computed(() => {
  if (route.meta?.titleKey) {
    return t(route.meta.titleKey);
  }
  return route.meta?.title || t("app.title");
});
const asideCollapsed = ref(false);
const asideWidth = ref(ASIDE_DEFAULT);
const asideResizing = ref(false);
let asideMoveHandler = null;
let asideUpHandler = null;

const asideWidthCss = computed(() =>
  asideCollapsed.value ? `${ASIDE_COLLAPSED_PX}px` : `${asideWidth.value}px`
);

const openeds = computed(() => {
  const match = route.path.match(/^\/([^/]+)/);
  return match ? [`/${match[1]}`] : [];
});

function clearResizeListeners() {
  if (asideMoveHandler) {
    document.removeEventListener("mousemove", asideMoveHandler);
    asideMoveHandler = null;
  }
  if (asideUpHandler) {
    document.removeEventListener("mouseup", asideUpHandler);
    asideUpHandler = null;
  }
}

function resetResizeState() {
  asideResizing.value = false;
  document.body.style.userSelect = "";
  document.body.style.cursor = "";
  clearResizeListeners();
}

onMounted(() => {
  const width = parseInt(getStorageItem(LS_ASIDE_WIDTH) || "", 10);
  if (!Number.isNaN(width) && width >= ASIDE_MIN && width <= ASIDE_MAX) {
    asideWidth.value = width;
  }
  asideCollapsed.value = getStorageItem(LS_ASIDE_COLLAPSED) === "1";
});

watch(asideWidth, (value) => {
  setStorageItem(LS_ASIDE_WIDTH, String(value));
});

watch(asideCollapsed, (value) => {
  setStorageItem(LS_ASIDE_COLLAPSED, value ? "1" : "0");
});

onBeforeUnmount(() => {
  resetResizeState();
});

function toggleAsideCollapsed() {
  asideCollapsed.value = !asideCollapsed.value;
}

function onAsideResizeStart(downEvent) {
  if (asideCollapsed.value) {
    return;
  }

  asideResizing.value = true;
  const startX = downEvent.clientX;
  const startWidth = asideWidth.value;
  document.body.style.userSelect = "none";
  document.body.style.cursor = "col-resize";

  clearResizeListeners();

  asideMoveHandler = (moveEvent) => {
    const delta = moveEvent.clientX - startX;
    asideWidth.value = Math.min(ASIDE_MAX, Math.max(ASIDE_MIN, startWidth + delta));
  };

  asideUpHandler = () => {
    resetResizeState();
  };

  document.addEventListener("mousemove", asideMoveHandler);
  document.addEventListener("mouseup", asideUpHandler);
}

async function handleCommand(command) {
  if (command === "logout") {
    await logout();
    router.push("/login");
  }
}
</script>

<style scoped>
.layout {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  --layout-header-height: var(--el-header-height, 60px);
}

.layout-right {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  min-width: 0;
}

.aside {
  flex-shrink: 0;
  align-self: stretch;
  position: relative;
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
  background: var(--knot-surface, #fff);
  border-right: 1px solid var(--knot-border, #ebeef5);
  transition: width 0.2s ease;
}

.aside--resizing {
  transition: none;
}

.aside--collapsed .aside-logo {
  font-size: 16px;
}

.aside-shell {
  flex: 1;
  min-height: 0;
  height: 100%;
}

.aside-header {
  flex-shrink: 0;
  height: var(--layout-header-height);
  box-sizing: border-box;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 12px;
  font-weight: 600;
  border-bottom: 1px solid var(--knot-border, #ebeef5);
  background: var(--knot-surface, #fff);
}

.aside-logo {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 14px;
}

.aside-nav {
  flex: 1;
  min-height: 0;
  padding: 0 !important;
}

.aside-scrollbar {
  height: 100%;
}

.aside-scrollbar :deep(.el-scrollbar__wrap) {
  overflow-x: hidden;
}

.aside-scrollbar :deep(.el-scrollbar__view) {
  min-height: 100%;
}

.aside-menu {
  border-right: none;
  background: var(--knot-surface, #fff);
  --el-menu-bg-color: var(--knot-surface, #fff);
  --el-menu-hover-bg-color: var(--el-color-primary-light-9);
  --el-menu-active-color: var(--el-color-primary);
}

.aside-resizer {
  position: absolute;
  top: 0;
  right: 0;
  width: 6px;
  height: 100%;
  cursor: col-resize;
  z-index: 12;
}

.aside-resizer:hover {
  background: var(--el-color-primary-light-8);
}

.header {
  height: var(--layout-header-height);
  box-sizing: border-box;
  background: var(--knot-surface, #fff);
  border-bottom: 1px solid var(--knot-border, #ebeef5);
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.header-title {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  font-weight: 400;
  font-size: 15px;
  color: #303133;
}

.header-collapse {
  flex: 0 0 auto;
}

.header-right {
  display: flex;
  align-items: center;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  font-weight: 400;
  font-size: 14px;
}

.main {
  background: var(--knot-bg, #f5f7fa);
  padding: 0;
  min-height: 0;
  flex: 1;
}

.main-scrollbar {
  height: 100%;
}

.main-scrollbar :deep(.el-scrollbar__view) {
  min-height: 100%;
}
</style>

import { createRouter, createWebHistory } from "vue-router";
import NestedView from "../layouts/NestedView.vue";
import { useAuth } from "../composables/useAuth";

const routes = [
  { path: "/login", name: "login", component: () => import("@/views/LoginView.vue") },
  { path: "/", name: "dashboard", component: () => import("@/views/DashboardView.vue") },

  // 系统管理
  {
    path: "/system",
    component: NestedView,
    children: [
      { path: "users", name: "system-users", component: () => import("@/views/system/UserManageView.vue") },
      { path: "roles", name: "system-roles", component: () => import("@/views/system/RoleManageView.vue") },
      { path: "logs", name: "system-logs", component: () => import("@/views/system/OperationLogView.vue") },
      { path: "scheduled-tasks", name: "system-scheduled-tasks", component: () => import("@/views/system/ScheduledTaskView.vue") },
      { path: "enums", name: "system-enums", component: () => import("@/views/system/EnumManageView.vue") },
      { path: "settings", name: "system-settings", component: () => import("@/views/system/UserSettingsView.vue") },
      { path: "plugins", name: "system-plugins", component: () => import("@/views/PluginManageView.vue") }
    ]
  },

  // 供应商管理
  { path: "/providers", name: "providers", component: () => import("@/views/ProviderManageView.vue") },

  // 模型管理
  {
    path: "/model-management",
    component: NestedView,
    children: [
      { path: "models", name: "model-management-models", component: () => import("@/views/ModelManageView.vue") },
      { path: "model-pools", name: "model-management-model-pools", component: () => import("@/views/ModelPoolManageView.vue") },
      { path: "logical-models", name: "model-management-logical-models", component: () => import("@/views/LogicalModelMarketplaceView.vue") },
      { path: "external-models", name: "model-management-external-models", component: () => import("@/views/ExternalModelManageView.vue") }
    ]
  },
  { path: "/logical-models", redirect: "/model-management/logical-models" },
  { path: "/models", redirect: "/model-management/models" },
  { path: "/model-pools", redirect: "/model-management/model-pools" },

  // 应用管理
  { path: "/apps", name: "apps", component: () => import("@/views/AppManageView.vue") },

  // 路由规则
  {
    path: "/routing",
    component: NestedView,
    children: [
      { path: "consumers", name: "routing-consumers", component: () => import("@/views/routing/RoutingConsumerView.vue") },
      { path: "rules", name: "routing-rules", component: () => import("@/views/routing/RoutingRuleView.vue") }
    ]
  },

  // 计费成本
  {
    path: "/billing",
    component: NestedView,
    children: [
      { path: "rules", name: "billing-rules", component: () => import("@/views/billing/BillingRuleView.vue") },
      { path: "reconciliation", name: "billing-reconciliation", component: () => import("@/views/billing/ReconciliationView.vue") }
    ]
  },

  // 安全监控
  {
    path: "/security",
    component: NestedView,
    children: [
      { path: "policy", name: "security-policy", component: () => import("@/views/security/SecurityPolicyView.vue") },
      { path: "alerts", name: "security-alerts", component: () => import("@/views/security/AlertManageView.vue") },
      { path: "cache", name: "security-cache", component: () => import("@/views/security/CacheManageView.vue") }
    ]
  },

  // 插件管理
  { path: "/plugins", redirect: "/system/plugins" },

  // 通知管理
  {
    path: "/notifications",
    component: NestedView,
    children: [
      { path: "templates", name: "notifications-templates", component: () => import("@/views/notifications/NotifyTemplateView.vue") },
      { path: "send", name: "notifications-send", component: () => import("@/views/notifications/NotifySendView.vue") },
      { path: "policy", name: "notifications-policy", component: () => import("@/views/notifications/NotifyPolicyView.vue") }
    ]
  },

  { path: "/:pathMatch(.*)*", name: "not-found", component: () => import("@/views/NotFoundView.vue") }
];

const router = createRouter({
  history: createWebHistory(),
  routes
});

// 路由守卫
router.beforeEach((to, from, next) => {
  const { isLoggedIn } = useAuth();
  
  // 白名单：不需要登录的页面
  const whiteList = ['/login'];
  
  if (whiteList.includes(to.path)) {
    // 如果已登录，访问登录页则跳转到首页
    if (isLoggedIn.value) {
      next('/');
    } else {
      next();
    }
  } else {
    // 其他页面需要登录
    if (isLoggedIn.value) {
      next();
    } else {
      next('/login');
    }
  }
});

export default router;

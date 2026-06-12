import { createRouter, createWebHistory } from "vue-router";
import NestedView from "../layouts/NestedView.vue";
import { useAuth } from "../composables/useAuth";

const routes = [
  {
    path: "/login",
    name: "login",
    component: () => import("@/views/LoginView.vue"),
    meta: { titleKey: "route.login" }
  },
  {
    path: "/",
    name: "dashboard",
    component: () => import("@/views/DashboardView.vue"),
    meta: { titleKey: "route.dashboard" }
  },
  {
    path: "/system",
    component: NestedView,
    children: [
      { path: "users", name: "system-users", component: () => import("@/views/system/UserManageView.vue"), meta: { titleKey: "route.systemUsers" } },
      { path: "departments", name: "system-departments", component: () => import("@/views/system/DepartmentManageView.vue"), meta: { titleKey: "route.systemDepartments" } },
      { path: "roles", redirect: { name: "system-role-authorizations" } },
      { path: "role-authorizations", name: "system-role-authorizations", component: () => import("@/views/system/RoleAuthorizationManageView.vue"), meta: { titleKey: "route.systemRoleAuthorizations" } },
      { path: "authorization-resources", name: "system-authorization-resources", component: () => import("@/views/system/AuthorizationResourceManageView.vue"), meta: { titleKey: "route.systemAuthorizationResources" } },
      { path: "logs", name: "system-logs", component: () => import("@/views/system/OperationLogView.vue"), meta: { titleKey: "route.systemLogs" } },
      { path: "scheduled-tasks", name: "system-scheduled-tasks", component: () => import("@/views/system/ScheduledTaskView.vue"), meta: { titleKey: "route.systemScheduledTasks" } },
      { path: "enums", name: "system-enums", component: () => import("@/views/system/EnumManageView.vue"), meta: { titleKey: "route.systemEnums" } },
      { path: "settings", name: "system-settings", component: () => import("@/views/system/UserSettingsView.vue"), meta: { titleKey: "route.systemSettings" } },
      { path: "plugins", name: "system-plugins", component: () => import("@/views/PluginManageView.vue"), meta: { titleKey: "route.systemPlugins" } }
    ]
  },
  {
    path: "/model-management",
    component: NestedView,
    children: [
      { path: "model-pools", name: "model-management-model-pools", component: () => import("@/views/ModelPoolManageView.vue"), meta: { titleKey: "route.modelManagementModelPools" } },
      { path: "models", name: "model-management-models", component: () => import("@/views/ModelManageView.vue"), meta: { titleKey: "route.modelManagementModels" } },
      { path: "providers", name: "model-management-providers", component: () => import("@/views/ProviderManageView.vue"), meta: { titleKey: "route.providers" } },
      { path: "logical-models", name: "model-management-logical-models", component: () => import("@/views/LogicalModelMarketplaceView.vue"), meta: { titleKey: "route.modelManagementLogicalModels" } },
      { path: "external-models", name: "model-management-external-models", component: () => import("@/views/ExternalModelManageView.vue"), meta: { titleKey: "route.modelManagementExternalModels" } }
    ]
  },
  { path: "/providers", redirect: "/model-management/providers" },
  { path: "/logical-models", redirect: "/model-management/logical-models" },
  { path: "/models", redirect: "/model-management/models" },
  { path: "/model-pools", redirect: "/model-management/model-pools" },
  {
    path: "/apps",
    name: "apps",
    component: () => import("@/views/AppManageView.vue"),
    meta: { titleKey: "route.apps" }
  },
  {
    path: "/routing",
    component: NestedView,
    children: [
      { path: "consumers", name: "routing-consumers", component: () => import("@/views/routing/RoutingConsumerView.vue"), meta: { titleKey: "route.routingConsumers" } },
      { path: "rules", name: "routing-rules", component: () => import("@/views/routing/RoutingRuleView.vue"), meta: { titleKey: "route.routingRules" } }
    ]
  },
  {
    path: "/billing",
    component: NestedView,
    children: [
      { path: "rules", name: "billing-rules", component: () => import("@/views/billing/BillingRuleView.vue"), meta: { titleKey: "route.billingRules" } },
      { path: "reconciliation", name: "billing-reconciliation", component: () => import("@/views/billing/ReconciliationView.vue"), meta: { titleKey: "route.billingReconciliation" } }
    ]
  },
  {
    path: "/security",
    component: NestedView,
    children: [
      { path: "policy", name: "security-policy", component: () => import("@/views/security/SecurityPolicyView.vue"), meta: { titleKey: "route.securityPolicy" } },
      { path: "alerts", name: "security-alerts", component: () => import("@/views/security/AlertManageView.vue"), meta: { titleKey: "route.securityAlerts" } },
      { path: "cache", name: "security-cache", component: () => import("@/views/security/CacheManageView.vue"), meta: { titleKey: "route.securityCache" } }
    ]
  },
  { path: "/plugins", redirect: "/system/plugins" },
  {
    path: "/notifications",
    component: NestedView,
    children: [
      { path: "templates", name: "notifications-templates", component: () => import("@/views/notifications/NotifyTemplateView.vue"), meta: { titleKey: "route.notificationsTemplates" } },
      { path: "send", name: "notifications-send", component: () => import("@/views/notifications/NotifySendView.vue"), meta: { titleKey: "route.notificationsSend" } },
      { path: "policy", name: "notifications-policy", component: () => import("@/views/notifications/NotifyPolicyView.vue"), meta: { titleKey: "route.notificationsPolicy" } }
    ]
  },
  {
    path: "/:pathMatch(.*)*",
    name: "not-found",
    component: () => import("@/views/NotFoundView.vue"),
    meta: { titleKey: "route.notFound" }
  }
];

const router = createRouter({
  history: createWebHistory(),
  routes
});

router.beforeEach((to, from, next) => {
  const { isLoggedIn } = useAuth();
  const whiteList = ["/login"];

  if (whiteList.includes(to.path)) {
    if (isLoggedIn.value) {
      next("/");
    } else {
      next();
    }
    return;
  }

  if (isLoggedIn.value) {
    next();
  } else {
    next("/login");
  }
});

export default router;

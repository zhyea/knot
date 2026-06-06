import { createRouter, createWebHistory } from "vue-router";
import NestedView from "../layouts/NestedView.vue";
import { useAuth } from "../composables/useAuth";

const routes = [
  {
    path: "/login",
    name: "login",
    component: () => import("@/views/LoginView.vue"),
    meta: { hiddenTagView: true, title: "登录" }
  },
  {
    path: "/",
    name: "dashboard",
    component: () => import("@/views/DashboardView.vue"),
    meta: { title: "总览", affix: true, cacheName: "DashboardView" }
  },
  {
    path: "/system",
    component: NestedView,
    children: [
      {
        path: "users",
        name: "system-users",
        component: () => import("@/views/system/UserManageView.vue"),
        meta: { title: "用户管理", cacheName: "UserManageView" }
      },
      {
        path: "departments",
        name: "system-departments",
        component: () => import("@/views/system/DepartmentManageView.vue"),
        meta: { title: "部门管理", cacheName: "DepartmentManageView" }
      },
      {
        path: "roles",
        name: "system-roles",
        component: () => import("@/views/system/RoleManageView.vue"),
        meta: { title: "角色权限", cacheName: "RoleManageView" }
      },
      {
        path: "logs",
        name: "system-logs",
        component: () => import("@/views/system/OperationLogView.vue"),
        meta: { title: "操作日志", cacheName: "OperationLogView" }
      },
      {
        path: "scheduled-tasks",
        name: "system-scheduled-tasks",
        component: () => import("@/views/system/ScheduledTaskView.vue"),
        meta: { title: "定时任务", cacheName: "ScheduledTaskView" }
      },
      {
        path: "enums",
        name: "system-enums",
        component: () => import("@/views/system/EnumManageView.vue"),
        meta: { title: "枚举管理", cacheName: "EnumManageView" }
      },
      {
        path: "settings",
        name: "system-settings",
        component: () => import("@/views/system/UserSettingsView.vue"),
        meta: { title: "用户设置", cacheName: "UserSettingsView" }
      },
      {
        path: "plugins",
        name: "system-plugins",
        component: () => import("@/views/PluginManageView.vue"),
        meta: { title: "插件管理", cacheName: "PluginManageView" }
      }
    ]
  },
  {
    path: "/providers",
    name: "providers",
    component: () => import("@/views/ProviderManageView.vue"),
    meta: { title: "供应商管理", cacheName: "ProviderManageView" }
  },
  {
    path: "/model-management",
    component: NestedView,
    children: [
      {
        path: "models",
        name: "model-management-models",
        component: () => import("@/views/ModelManageView.vue"),
        meta: { title: "供应商模型", cacheName: "ModelManageView" }
      },
      {
        path: "model-pools",
        name: "model-management-model-pools",
        component: () => import("@/views/ModelPoolManageView.vue"),
        meta: { title: "模型池", cacheName: "ModelPoolManageView" }
      },
      {
        path: "logical-models",
        name: "model-management-logical-models",
        component: () => import("@/views/LogicalModelMarketplaceView.vue"),
        meta: { title: "模型广场", cacheName: "LogicalModelMarketplaceView" }
      },
      {
        path: "external-models",
        name: "model-management-external-models",
        component: () => import("@/views/ExternalModelManageView.vue"),
        meta: { title: "外部模型", cacheName: "ExternalModelManageView" }
      }
    ]
  },
  { path: "/logical-models", redirect: "/model-management/logical-models" },
  { path: "/models", redirect: "/model-management/models" },
  { path: "/model-pools", redirect: "/model-management/model-pools" },
  {
    path: "/apps",
    name: "apps",
    component: () => import("@/views/AppManageView.vue"),
    meta: { title: "应用管理", cacheName: "AppManageView" }
  },
  {
    path: "/routing",
    component: NestedView,
    children: [
      {
        path: "consumers",
        name: "routing-consumers",
        component: () => import("@/views/routing/RoutingConsumerView.vue"),
        meta: { title: "消费者", cacheName: "RoutingConsumerView" }
      },
      {
        path: "rules",
        name: "routing-rules",
        component: () => import("@/views/routing/RoutingRuleView.vue"),
        meta: { title: "规则列表", cacheName: "RoutingRuleView" }
      }
    ]
  },
  {
    path: "/billing",
    component: NestedView,
    children: [
      {
        path: "rules",
        name: "billing-rules",
        component: () => import("@/views/billing/BillingRuleView.vue"),
        meta: { title: "计费规则", cacheName: "BillingRuleView" }
      },
      {
        path: "reconciliation",
        name: "billing-reconciliation",
        component: () => import("@/views/billing/ReconciliationView.vue"),
        meta: { title: "对账管理", cacheName: "ReconciliationView" }
      }
    ]
  },
  {
    path: "/security",
    component: NestedView,
    children: [
      {
        path: "policy",
        name: "security-policy",
        component: () => import("@/views/security/SecurityPolicyView.vue"),
        meta: { title: "安全策略", cacheName: "SecurityPolicyView" }
      },
      {
        path: "alerts",
        name: "security-alerts",
        component: () => import("@/views/security/AlertManageView.vue"),
        meta: { title: "告警管理", cacheName: "AlertManageView" }
      },
      {
        path: "cache",
        name: "security-cache",
        component: () => import("@/views/security/CacheManageView.vue"),
        meta: { title: "缓存管理", cacheName: "CacheManageView" }
      }
    ]
  },
  { path: "/plugins", redirect: "/system/plugins" },
  {
    path: "/notifications",
    component: NestedView,
    children: [
      {
        path: "templates",
        name: "notifications-templates",
        component: () => import("@/views/notifications/NotifyTemplateView.vue"),
        meta: { title: "通知模板", cacheName: "NotifyTemplateView" }
      },
      {
        path: "send",
        name: "notifications-send",
        component: () => import("@/views/notifications/NotifySendView.vue"),
        meta: { title: "通知发送", cacheName: "NotifySendView" }
      },
      {
        path: "policy",
        name: "notifications-policy",
        component: () => import("@/views/notifications/NotifyPolicyView.vue"),
        meta: { title: "通知策略", cacheName: "NotifyPolicyView" }
      }
    ]
  },
  {
    path: "/:pathMatch(.*)*",
    name: "not-found",
    component: () => import("@/views/NotFoundView.vue"),
    meta: { hiddenTagView: true, title: "页面不存在" }
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

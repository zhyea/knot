import { createRouter, createWebHistory } from "vue-router";

const routes = [
  { path: "/", name: "dashboard", component: () => import("@/views/DashboardView.vue") },
  { path: "/system", name: "system", component: () => import("@/views/SystemManageView.vue") },
  { path: "/providers", name: "providers", component: () => import("@/views/ProviderManageView.vue") },
  { path: "/models", name: "models", component: () => import("@/views/ModelManageView.vue") },
  { path: "/apps", name: "apps", component: () => import("@/views/AppManageView.vue") },
  { path: "/routing", name: "routing", component: () => import("@/views/RouteRuleView.vue") },
  { path: "/billing", name: "billing", component: () => import("@/views/BillingView.vue") },
  { path: "/security", name: "security", component: () => import("@/views/SecurityMonitorView.vue") },
  { path: "/plugins", name: "plugins", component: () => import("@/views/PluginManageView.vue") },
  { path: "/notifications", name: "notifications", component: () => import("@/views/NotificationManageView.vue") },
  { path: "/release", name: "release", component: () => import("@/views/GrayReleaseView.vue") },
  { path: "/:pathMatch(.*)*", name: "not-found", component: () => import("@/views/NotFoundView.vue") }
];

const router = createRouter({
  history: createWebHistory(),
  routes
});

export default router;

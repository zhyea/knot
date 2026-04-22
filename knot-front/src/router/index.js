import { createRouter, createWebHistory } from "vue-router";
import DashboardView from "../views/DashboardView.vue";
import SystemManageView from "../views/SystemManageView.vue";
import ProviderManageView from "../views/ProviderManageView.vue";
import ModelManageView from "../views/ModelManageView.vue";
import AppManageView from "../views/AppManageView.vue";
import RouteRuleView from "../views/RouteRuleView.vue";
import BillingView from "../views/BillingView.vue";
import SecurityMonitorView from "../views/SecurityMonitorView.vue";
import PluginManageView from "../views/PluginManageView.vue";
import NotificationManageView from "../views/NotificationManageView.vue";
import GrayReleaseView from "../views/GrayReleaseView.vue";

const routes = [
  { path: "/", name: "dashboard", component: DashboardView },
  { path: "/system", name: "system", component: SystemManageView },
  { path: "/providers", name: "providers", component: ProviderManageView },
  { path: "/models", name: "models", component: ModelManageView },
  { path: "/apps", name: "apps", component: AppManageView },
  { path: "/routing", name: "routing", component: RouteRuleView },
  { path: "/billing", name: "billing", component: BillingView },
  { path: "/security", name: "security", component: SecurityMonitorView },
  { path: "/plugins", name: "plugins", component: PluginManageView },
  { path: "/notifications", name: "notifications", component: NotificationManageView },
  { path: "/release", name: "release", component: GrayReleaseView }
];

const router = createRouter({
  history: createWebHistory(),
  routes
});

export default router;

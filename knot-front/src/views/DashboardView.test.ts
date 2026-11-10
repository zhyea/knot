// @vitest-environment jsdom
import {describe, it, expect, vi, beforeEach, afterEach} from "vitest";
import {mount, flushPromises, enableAutoUnmount} from "@vue/test-utils";
import {listApps} from "@/api/apps";
import {getHealth} from "@/api/health";
import {listLogicalModels} from "@/api/logicalModels";
import {listModuleCatalog} from "@/api/modules";
import {listOperationLogs} from "@/api/operationLogs";
import {listProviders} from "@/api/providers";
import {listRoutingRules} from "@/api/routing";
import DashboardView from "@/views/DashboardView.vue";

/**
 * 总览页的**集成**固化（2026-10-31 拆分前零测试）。
 *
 * <p>拆分把这一页拆成「1 个宿主 + 1 个 composable + 3 个 utils + 6 个组件」，
 * 各层自己都有单测；这里只钉**拼装是否接对**：数据到卡片的链路、失败提示、
 * 点击跳转。刻意不重复断言比例算法（那在 utils/dashboardMetrics.test.ts）。</p>
 */
const hoisted = vi.hoisted(() => ({push: vi.fn()}));

vi.mock("vue-router", () => ({useRouter: () => ({push: hoisted.push})}));
vi.mock("@/composables/useAuth", async () => {
  const {ref} = await import("vue");
  return {
    useAuth: () => ({
      modules: ref([{icon: "Grid", menus: [{menuName: "应用管理", routePath: "/apps", children: []}]}]),
      // 总览页会等授权菜单一起就绪，再放行数据区（快捷入口的数据来源）
      loadAuthorizations: () => Promise.resolve({permissions: [], modules: []})
    })
  };
});
vi.mock("@/composables/useTimezone", async () => {
  const {ref} = await import("vue");
  return {useTimezone: () => ({current: ref("Asia/Shanghai")})};
});
vi.mock("@/api/apps", () => ({listApps: vi.fn()}));
vi.mock("@/api/health", () => ({getHealth: vi.fn()}));
vi.mock("@/api/logicalModels", () => ({listLogicalModels: vi.fn()}));
vi.mock("@/api/modules", () => ({listModuleCatalog: vi.fn()}));
vi.mock("@/api/operationLogs", () => ({listOperationLogs: vi.fn()}));
vi.mock("@/api/providers", () => ({listProviders: vi.fn()}));
vi.mock("@/api/routing", () => ({listRoutingRules: vi.fn()}));

const mockApps = vi.mocked(listApps);
const mockHealth = vi.mocked(getHealth);
const mockModels = vi.mocked(listLogicalModels);
const mockModules = vi.mocked(listModuleCatalog);
const mockLogs = vi.mocked(listOperationLogs);
const mockProviders = vi.mocked(listProviders);
const mockRules = vi.mocked(listRoutingRules);

function logs(count: number) {
  return Array.from({length: count}, (_, i) => ({
    id: i + 1,
    module: "app",
    operation: `op-${i}`,
    entityName: `entity-${i}`,
    status: "SUCCESS",
    createdAt: "2026-10-31T12:00:00Z"
  }));
}

async function mountPage() {
  const wrapper = mount(DashboardView);
  await flushPromises();
  return wrapper;
}

describe("DashboardView 拼装", () => {
  enableAutoUnmount(afterEach);

  beforeEach(() => {
    hoisted.push.mockReset();
    mockHealth.mockResolvedValue({status: "UP", serverTime: "2026-10-31T12:00:00Z", dbTime: "2026-10-31T12:00:01Z"});
    mockModules.mockResolvedValue([
      {code: "app", name: "应用管理", capabilities: ["read", "write", "delete", "admin"]}
    ]);
    mockApps.mockResolvedValue({total: 3, list: []});
    mockProviders.mockResolvedValue({total: 4, list: []});
    mockModels.mockResolvedValue({total: 5, list: []});
    mockRules.mockResolvedValue({total: 6, list: []});
    mockLogs.mockResolvedValue({list: logs(8)});
  });

  afterEach(() => {
    vi.resetAllMocks();
  });

  it("数据按区块落位：四张卡、模块概览、快捷入口、最近操作", async () => {
    const wrapper = await mountPage();

    const cards = wrapper.findAll(".metric-card");
    expect(cards).toHaveLength(4);
    // 顺序恒定：应用 / 供应商账户 / 统一模型 / 路由规则
    expect(cards.map((card) => card.find(".metric-card__value").text())).toEqual(["3", "4", "5", "6"]);
    // 徽标是「模块 N」，与模块概览的条目数不是一个概念
    expect(wrapper.find(".panel-badge").text()).toBe("模块 1");
    expect(wrapper.findAll(".module-overview__item")).toHaveLength(1);
    // 能力 4 项 → 预览 3 个 + 剩余 1
    expect(wrapper.find(".module-overview__more").text()).toBe("+1");
    expect(wrapper.findAll(".quick-links__item")).toHaveLength(1);
    // 接口给 8 条，页面只渲染 6 条
    expect(wrapper.findAll(".activity-list__item")).toHaveLength(6);
    expect(wrapper.find(".el-alert").exists()).toBe(false);
  });

  it("单区块失败：页头提示出现，该卡按 0 展示，其余区块照常", async () => {
    mockApps.mockRejectedValue(new Error("500"));
    const warn = vi.spyOn(console, "warn").mockImplementation(() => undefined);

    const wrapper = await mountPage();

    const alert = wrapper.find(".el-alert");
    expect(alert.exists()).toBe(true);
    expect(alert.text()).toContain("部分数据加载失败");
    expect(alert.text()).toContain("应用总数");
    // 失败区块落 0，其它三张卡不受影响
    const cards = wrapper.findAll(".metric-card");
    expect(cards[0].find(".metric-card__value").text()).toBe("0");
    expect(cards[1].find(".metric-card__value").text()).toBe("4");
    // 最近操作照常
    expect(wrapper.findAll(".activity-list__item")).toHaveLength(6);

    warn.mockRestore();
  });

  it("布局骨架未变：main 下是「概览面板 + 右侧三块」", async () => {
    // 拆分最容易伤到的不是数据而是嵌套：多包一层 div，grid 就退化成上下排列。
    // 这条不追样式（CSS 在浏览器里才说得清），只钉「谁是谁的直接子节点」。
    const wrapper = await mountPage();

    // 用「路径选择器」而不是数 children：v-loading 会额外插一层 transition + mask，
    // 数个数就把指令的实现细节写进断言了。
    expect(wrapper.find(".dashboard-page > .dashboard-hero").exists()).toBe(true);
    expect(wrapper.find(".dashboard-page > .metric-grid").exists()).toBe(true);
    expect(wrapper.find(".dashboard-page > .dashboard-main").exists()).toBe(true);
    const main = wrapper.find(".dashboard-main");
    expect(main.element.children).toHaveLength(2);
    const overview = main.element.children[0];
    expect(overview.classList.contains("dashboard-panel")).toBe(true);
    expect(overview.classList.contains("dashboard-panel--overview")).toBe(true);

    const side = wrapper.find(".dashboard-side");
    expect(side.element.children).toHaveLength(3);
    for (const panel of Array.from(side.element.children)) {
      expect(panel.classList.contains("dashboard-panel")).toBe(true);
    }
  });

  it("点指标卡与快捷入口都会跳转（路由由宿主统一处理）", async () => {
    const wrapper = await mountPage();

    await wrapper.findAll(".metric-card")[0].trigger("click");
    expect(hoisted.push).toHaveBeenCalledWith("/apps");

    hoisted.push.mockReset();
    await wrapper.find(".quick-links__item").trigger("click");
    expect(hoisted.push).toHaveBeenCalledWith("/apps");
  });

  it("先加载后渲染：首轮完成前数据区不挂载，只有动态蒙版", async () => {
    type AppsResult = Awaited<ReturnType<typeof listApps>>;
    let releaseApps: (value: AppsResult) => void = () => undefined;
    mockApps.mockReturnValue(
      new Promise<AppsResult>((resolve) => {
        releaseApps = resolve;
      })
    );

    const wrapper = mount(DashboardView);
    await flushPromises();

    // ⭐ 数据区整体不存在 → 蒙版下面不会露出 0 / 空列表这一屏「半成品」
    expect(wrapper.find(".metric-grid").exists()).toBe(false);
    expect(wrapper.find(".dashboard-main").exists()).toBe(false);
    // 首轮态额外撑高，否则窄屏下页面高度只有 Hero，蒙版只剩一条
    expect(wrapper.find(".dashboard-page").classes()).toContain("dashboard-page--booting");
    const mask = wrapper.find(".el-loading-mask");
    expect(mask.exists()).toBe(true);
    expect(mask.text()).toContain("正在加载看板数据");

    releaseApps({total: 3, list: []});
    await flushPromises();

    expect(wrapper.find(".metric-grid").exists()).toBe(true);
    expect(wrapper.find(".dashboard-page").classes()).not.toContain("dashboard-page--booting");

    // 蒙版是 300ms 淡出后才移除节点，等它走完再断言「不挡了」
    await new Promise((resolve) => setTimeout(resolve, 400));
    expect(wrapper.find(".el-loading-mask").exists()).toBe(false);
  });
});

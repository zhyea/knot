import {describe, it, expect, vi, beforeEach, afterEach} from "vitest";
import {listApps} from "@/api/apps";
import {getHealth} from "@/api/health";
import {listLogicalModels} from "@/api/logicalModels";
import {listModuleCatalog} from "@/api/modules";
import {listOperationLogs} from "@/api/operationLogs";
import {listProviders} from "@/api/providers";
import {listRoutingRules} from "@/api/routing";
import {useDashboard} from "@/composables/useDashboard";

/**
 * 总览页的取数编排：**8 个来源各自降级**（7 张数据 + 授权菜单）。
 *
 * <p>这组用例钉的是降级语义，而不是「数据取回来了」——因为降级一旦写错，
 * 表现是「这页永远显示 0」而没有任何报错，比崩溃更难发现：
 * ① 单区块失败只登记自己，别的区块照常出数；
 * ② 失败区块的数值落 0，但 `lastUpdatedAt` 仍要写（否则「更新时间」停住，运维会以为是页面卡了）；
 * ③ 每次 load 都重置失败名单，否则修好之后提示还在；
 * ④ 首轮加载前不得放行数据区（`initialized`），并发加载只认最后一次。</p>
 */
const hoisted = vi.hoisted(() => ({loadAuthorizations: vi.fn()}));

vi.mock("@/composables/useAuth", () => ({
  useAuth: () => ({loadAuthorizations: hoisted.loadAuthorizations})
}));
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
  return Array.from({length: count}, (_, i) => ({id: i + 1, module: "app", operation: `op-${i}`}));
}

describe("useDashboard 降级编排", () => {
  let warn: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    warn = vi.spyOn(console, "warn").mockImplementation(() => undefined);
    hoisted.loadAuthorizations.mockResolvedValue({permissions: [], modules: []});
    mockHealth.mockResolvedValue({status: "UP", serverTime: "2026-10-31T12:00:00Z", dbTime: "2026-10-31T12:00:01Z"});
    mockModules.mockResolvedValue([{code: "app", name: "应用管理", capabilities: ["read", "write"]}]);
    mockApps.mockResolvedValue({total: 3, list: []});
    mockProviders.mockResolvedValue({list: [{}, {}]});
    mockModels.mockResolvedValue({total: 5, list: []});
    mockRules.mockResolvedValue({total: 7, list: []});
    // 接口给 8 条，页面只留 6 条
    mockLogs.mockResolvedValue({list: logs(8)});
  });

  afterEach(() => {
    warn.mockRestore();
    vi.resetAllMocks();
  });

  it("全成功：三种返回形态各自归一，最近操作截到 6 条", async () => {
    const dashboard = useDashboard();
    await dashboard.loadDashboard();

    expect(dashboard.statTotals).toEqual({apps: 3, providers: 2, logicalModels: 5, routingRules: 7});
    expect(dashboard.moduleCatalog.value).toHaveLength(1);
    expect(dashboard.recentLogs.value).toHaveLength(6);
    expect(dashboard.failedSections.value).toEqual([]);
    expect(dashboard.loading.value).toBe(false);
    expect(dashboard.lastUpdatedAt.value).not.toBe("");
    expect(dashboard.health.status).toBe("UP");
  });

  it("单区块失败：只登记自己，其余区块照常出数", async () => {
    mockApps.mockRejectedValue(new Error("500"));

    const dashboard = useDashboard();
    await dashboard.loadDashboard();

    expect(dashboard.failedSections.value).toEqual(["应用总数"]);
    expect(dashboard.statTotals.apps).toBe(0);
    // 别的区块不受影响
    expect(dashboard.statTotals.providers).toBe(2);
    expect(dashboard.statTotals.logicalModels).toBe(5);
    expect(dashboard.recentLogs.value).toHaveLength(6);
    // ⭐ 刷新时间仍要写：否则界面看起来像「刷新没生效」
    expect(dashboard.lastUpdatedAt.value).not.toBe("");
    expect(dashboard.loading.value).toBe(false);
  });

  it("多区块失败：登记顺序按请求声明顺序，便于页头提示稳定", async () => {
    mockHealth.mockRejectedValue(new Error("health down"));
    mockRules.mockRejectedValue(new Error("rules down"));

    const dashboard = useDashboard();
    await dashboard.loadDashboard();

    expect(dashboard.failedSections.value).toEqual(["健康状态", "路由规则总数"]);
    // 健康状态取不到 → 三个字段都是空串，展示层兜成占位符
    expect(dashboard.health.status).toBe("");
    expect(dashboard.health.serverTime).toBe("—");
    expect(dashboard.statTotals.routingRules).toBe(0);
  });

  it("二次加载重置失败名单：修好之后提示必须消失", async () => {
    mockApps.mockRejectedValueOnce(new Error("500"));

    const dashboard = useDashboard();
    await dashboard.loadDashboard();
    expect(dashboard.failedSections.value).toEqual(["应用总数"]);

    mockApps.mockResolvedValue({total: 9, list: []});
    await dashboard.loadDashboard();
    expect(dashboard.failedSections.value).toEqual([]);
    expect(dashboard.statTotals.apps).toBe(9);
  });

  it("首轮之前就是加载态：initialized 为 false，加载结束才放行数据区", async () => {
    const dashboard = useDashboard();
    // ⭐ 挂载那一刻就得有蒙版，否则首帧铺出来的是 0 / 空列表
    expect(dashboard.loading.value).toBe(true);
    expect(dashboard.initialized.value).toBe(false);

    await dashboard.loadDashboard();

    expect(dashboard.initialized.value).toBe(true);
    expect(dashboard.loading.value).toBe(false);
  });

  it("授权菜单与看板同批等待：登录后首次进首页不缺快捷入口", async () => {
    let releaseAuth: (value: unknown) => void = () => undefined;
    hoisted.loadAuthorizations.mockReturnValue(
      new Promise<unknown>((resolve) => {
        releaseAuth = resolve;
      })
    );

    const dashboard = useDashboard();
    const loading = dashboard.loadDashboard();
    // 授权还没回来 → 整页仍算「没加载完」，数据区不渲染
    expect(dashboard.initialized.value).toBe(false);

    releaseAuth({permissions: [], modules: []});
    await loading;
    expect(dashboard.initialized.value).toBe(true);
    expect(dashboard.failedSections.value).toEqual([]);
  });

  it("并发加载只认最后一次：慢请求后到不得覆盖新数据，也不得重新拉起蒙版", async () => {
    type AppsResult = Awaited<ReturnType<typeof listApps>>;
    let releaseFirst: (value: AppsResult) => void = () => undefined;
    mockApps.mockReturnValueOnce(
      new Promise<AppsResult>((resolve) => {
        releaseFirst = resolve;
      })
    );

    const dashboard = useDashboard();
    const first = dashboard.loadDashboard();
    const second = dashboard.loadDashboard();
    await second;

    // 第二次（最新一轮）先回来：数据与蒙版都以它为准
    expect(dashboard.loading.value).toBe(false);
    expect(dashboard.statTotals.apps).toBe(3);

    // 第一次此刻才回来（total 99）→ 必须整轮丢弃
    releaseFirst({total: 99, list: []});
    await first;

    expect(dashboard.statTotals.apps).toBe(3);
    expect(dashboard.loading.value).toBe(false);
    expect(dashboard.initialized.value).toBe(true);
  });
});

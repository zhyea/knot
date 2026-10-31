import {describe, it, expect, vi, beforeEach, afterEach} from "vitest";
import {effectScope, type EffectScope} from "vue";
import type {Row} from "@/types";

// P1-6 的回归锁：`changeResourceStatus` 曾用字符串 "ENABLED"/"DISABLED" 与后端数字
// status（1/0）比较，导致两个后果——① 视图 `=== EnabledStatus.ENABLED` 永不相等，
// 开关乐观更新后视觉回弹；② 幂等短路成了死代码，重复切换会重复发请求。
// 这里只测这条判定链，不测 UI（视图侧由组件测试负责）。
vi.mock("element-plus", () => ({
  ElMessage: {success: vi.fn(), error: vi.fn(), warning: vi.fn(), info: vi.fn()}
}));

vi.mock("@/api/authorizations", () => ({
  createAuthorizationApiBinding: vi.fn(),
  createAuthorizationMenu: vi.fn(),
  createAuthorizationModule: vi.fn(),
  createAuthorizationPermission: vi.fn(),
  deleteAuthorizationApiBinding: vi.fn(),
  deleteAuthorizationMenu: vi.fn(),
  deleteAuthorizationModule: vi.fn(),
  deleteAuthorizationPermission: vi.fn(),
  deleteAuthorizationRole: vi.fn(),
  getRoleAuthorizationSnapshot: vi.fn(),
  listAuthorizationApiBindings: vi.fn(),
  listAuthorizationMenus: vi.fn(),
  listAuthorizationModules: vi.fn(),
  listAuthorizationPermissions: vi.fn(),
  listAuthorizationRoles: vi.fn(),
  saveRolePermissions: vi.fn(),
  updateAuthorizationApiBinding: vi.fn(),
  updateAuthorizationApiBindingStatus: vi.fn(),
  updateAuthorizationMenu: vi.fn(),
  updateAuthorizationMenuStatus: vi.fn(),
  updateAuthorizationModule: vi.fn(),
  updateAuthorizationModuleStatus: vi.fn(),
  updateAuthorizationPermission: vi.fn(),
  updateAuthorizationPermissionStatus: vi.fn()
}));

vi.mock("@/api/operationLogs", () => ({
  listOperationLogsByEntity: vi.fn()
}));

import {
  listAuthorizationModules,
  updateAuthorizationModuleStatus
} from "@/api/authorizations";
import {useAuthorizationManagement} from "@/composables/useAuthorizationManagement";

function makeModuleRow(status: unknown): Row {
  return {id: 7, moduleCode: "system", moduleName: "系统管理", status} as unknown as Row;
}

describe("useAuthorizationManagement 资源状态开关", () => {
  // 组合式函数内部有 onScopeDispose（useAutoQuery 的防抖清理），脱离 effectScope 直接调用
  // 会刷一片 "no active effect scope" 警告——包一层既消噪音，也在 afterEach 里把 watcher 真的停掉。
  let scope: EffectScope;
  let api: ReturnType<typeof useAuthorizationManagement>;

  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(listAuthorizationModules).mockResolvedValue({list: [], total: 0});
    vi.mocked(updateAuthorizationModuleStatus).mockResolvedValue(undefined);
    scope = effectScope();
    api = scope.run(() => useAuthorizationManagement())!;
  });

  afterEach(() => {
    scope.stop();
  });

  it("status=1 关掉：调禁用接口，行状态落为 0", async () => {
    const row = makeModuleRow(1);
    await api.handleModuleStatusChange(row, false);
    expect(updateAuthorizationModuleStatus).toHaveBeenCalledWith(7, false);
    expect(row.status).toBe(0);
  });

  it("status=0 打开：调启用接口，行状态落为 1（0 是合法值，不能被当成未填写）", async () => {
    const row = makeModuleRow(0);
    await api.handleModuleStatusChange(row, true);
    expect(updateAuthorizationModuleStatus).toHaveBeenCalledWith(7, true);
    expect(row.status).toBe(1);
  });

  it("状态未变时短路，不发请求（旧实现字符串比较导致短路失效）", async () => {
    const row = makeModuleRow(1);
    await api.handleModuleStatusChange(row, true);
    expect(updateAuthorizationModuleStatus).not.toHaveBeenCalled();
    expect(row.status).toBe(1);
  });

  it("status 缺失按停用处理：再关一次也不发请求", async () => {
    const row = makeModuleRow(undefined);
    await api.handleModuleStatusChange(row, false);
    expect(updateAuthorizationModuleStatus).not.toHaveBeenCalled();
  });

  it("接口失败回滚到原状态，且不残留 loading", async () => {
    vi.mocked(updateAuthorizationModuleStatus).mockRejectedValue(new Error("网络错误"));
    const row = makeModuleRow(1);
    await api.handleModuleStatusChange(row, false);
    expect(row.status).toBe(1);
    expect(api.moduleStatusUpdatingId.value).toBeNull();
  });

  it("没有 id 直接返回，不发请求", async () => {
    const row = {status: 1} as unknown as Row;
    await api.handleModuleStatusChange(row, false);
    expect(updateAuthorizationModuleStatus).not.toHaveBeenCalled();
  });
});

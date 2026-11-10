import { describe, it, expect, vi } from "vitest";
import type { Row } from "@/types";

// p1-2 点名的高危交互之一：权限态 / 状态判断。列表启用开关的 flag 归一逻辑必须正确处理
// 「0 / false / "false" 都表示禁用」，否则会把合法值 0 误判为启用（前端禁用 truthy 是头号风险）。
// element-plus 的 ElMessage 在测试环境无意义，隔离掉避免触碰 DOM。
vi.mock("element-plus", () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn(), info: vi.fn() }
}));

import { useEnabledToggle } from "@/composables/useEnabledToggle";

function makeRow(enabled: unknown): Row {
  return { id: 1, enabled } as unknown as Row;
}

describe("useEnabledToggle.onEnabledChange 状态归一", () => {
  it("没有 id 直接返回，不调用更新接口，不动行", async () => {
    const updateApi = vi.fn().mockResolvedValue(undefined);
    const { onEnabledChange } = useEnabledToggle({ updateApi });
    const row = { enabled: true } as unknown as Row; // 无 id
    await onEnabledChange(row, false);
    expect(updateApi).not.toHaveBeenCalled();
    expect((row as { enabled: boolean }).enabled).toBe(true);
  });

  it("true -> false：调用禁用接口", async () => {
    const updateApi = vi.fn().mockResolvedValue(undefined);
    const { onEnabledChange } = useEnabledToggle({ updateApi });
    const row = makeRow(true);
    await onEnabledChange(row, false);
    expect(updateApi).toHaveBeenCalledWith(1, false);
    expect((row as { enabled: boolean }).enabled).toBe(false);
  });

  it("更新失败回滚到之前状态", async () => {
    const updateApi = vi.fn().mockRejectedValue(new Error("网络错误"));
    const { onEnabledChange } = useEnabledToggle({ updateApi });
    const row = makeRow(true);
    await onEnabledChange(row, false);
    expect((row as { enabled: boolean }).enabled).toBe(true); // 还原
  });

  it("数字 0 归一为禁用（不被 truthy 误判为启用）", async () => {
    const updateApi = vi.fn().mockResolvedValue(undefined);
    const { onEnabledChange } = useEnabledToggle({ updateApi });
    const row = makeRow(true);
    await onEnabledChange(row, 0);
    expect(updateApi).toHaveBeenCalledWith(1, false);
    expect((row as { enabled: boolean }).enabled).toBe(false);
  });

  it("数字 1 归一为启用", async () => {
    const updateApi = vi.fn().mockResolvedValue(undefined);
    const { onEnabledChange } = useEnabledToggle({ updateApi });
    const row = makeRow(false);
    await onEnabledChange(row, 1);
    expect(updateApi).toHaveBeenCalledWith(1, true);
    expect((row as { enabled: boolean }).enabled).toBe(true);
  });

  it("字符串 'false' 归一为禁用", async () => {
    const updateApi = vi.fn().mockResolvedValue(undefined);
    const { onEnabledChange } = useEnabledToggle({ updateApi });
    const row = makeRow(true);
    await onEnabledChange(row, "false");
    expect(updateApi).toHaveBeenCalledWith(1, false);
  });

  it("enable 值等于当前状态时不重复调用接口", async () => {
    const updateApi = vi.fn().mockResolvedValue(undefined);
    const { onEnabledChange } = useEnabledToggle({ updateApi });
    const row = makeRow(true);
    await onEnabledChange(row, true); // 已是启用
    expect(updateApi).not.toHaveBeenCalled();
  });
});

describe("useEnabledToggle.handleEnabledChange 成功回调", () => {
  it("包装层在开关完成后触发一次 onChanged（替代各列表的 emit('changed')）", async () => {
    const updateApi = vi.fn().mockResolvedValue(undefined);
    const onChanged = vi.fn();
    const { handleEnabledChange } = useEnabledToggle({ updateApi, onChanged });
    await handleEnabledChange(makeRow(true), false);
    expect(updateApi).toHaveBeenCalledWith(1, false);
    expect(onChanged).toHaveBeenCalledTimes(1);
  });

  it("未配置 onChanged 时包装层不抛错", async () => {
    const updateApi = vi.fn().mockResolvedValue(undefined);
    const { handleEnabledChange } = useEnabledToggle({ updateApi });
    await expect(handleEnabledChange(makeRow(true), false)).resolves.toBeUndefined();
  });
});

describe("useEnabledToggle.handleEnabledChange 异步刷新", () => {
  it("onChanged 返回 Promise 时包装层等它完成才 resolve（页面刷新用 await load()）", async () => {
    let resolveChanged!: () => void;
    const changed = new Promise<void>((resolve) => { resolveChanged = resolve; });
    let refreshed = false;
    const updateApi = vi.fn().mockResolvedValue(undefined);
    const onChanged = vi.fn(() => changed.then(() => { refreshed = true; }));
    const { handleEnabledChange } = useEnabledToggle({ updateApi, onChanged });

    let wrapperSettled = false;
    const pending = handleEnabledChange(makeRow(true), false).then(() => { wrapperSettled = true; });
    await vi.waitFor(() => expect(onChanged).toHaveBeenCalledTimes(1));
    await new Promise((resolve) => setTimeout(resolve, 0));
    // 刷新还没结束，包装层不许先返回；一旦去掉 await 这里立刻转红
    expect(wrapperSettled).toBe(false);
    expect(refreshed).toBe(false);

    resolveChanged();
    await pending;
    expect(wrapperSettled).toBe(true);
    expect(refreshed).toBe(true);
  });

  it("更新失败回滚后仍按原列表语义通知一次刷新", async () => {
    const updateApi = vi.fn().mockRejectedValue(new Error("网络错误"));
    const onChanged = vi.fn();
    const { handleEnabledChange } = useEnabledToggle({ updateApi, onChanged });
    await handleEnabledChange(makeRow(true), false);
    expect(onChanged).toHaveBeenCalledTimes(1);
  });
});

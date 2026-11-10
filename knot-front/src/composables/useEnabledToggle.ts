import {isRowEnabled, normalizeEnabledFlag} from "@/utils/enabledFlag";
import {ref} from "vue";
import type {Row} from "@/types";
import {ElMessage} from "element-plus";

export interface EnabledToggleOptions {
  updateApi: (id: number | string, enabled: boolean) => Promise<unknown>;
  /**
   * 开关成功后触发一次刷新。列表组件此前各自包一层
   * `await onEnabledChange(...); emit("changed");`，语义完全相同，收口到此处。
   * 允许返回 Promise：部分页面刷新用 `await load()`，包装层会等它完成。
   */
  onChanged?: () => void | Promise<unknown>;
}

/**
 * 列表行启用开关：调用独立状态接口并支持失败回滚。
 */
export function useEnabledToggle({updateApi, onChanged}: EnabledToggleOptions) {
  const togglingId = ref<number | string | null>(null);

  async function onEnabledChange(row: Row, enabled: boolean | string | number) {
    if (!row?.id) return;
    const flag = normalizeEnabledFlag(enabled);
    const prev = isRowEnabled(row);
    if (flag === prev) return;

    togglingId.value = row.id;
    row.enabled = flag;
    try {
      await updateApi(row.id, flag);
      ElMessage.success(flag ? "已启用" : "已禁用");
    } catch {
      row.enabled = prev;
    } finally {
      togglingId.value = null;
    }
  }

  /** 供 el-table / RowActions 直接绑定的包装：成功后通知使用方刷新 */
  async function handleEnabledChange(row: Row, enabled: boolean | string | number): Promise<void> {
    await onEnabledChange(row, enabled);
    await Promise.resolve(onChanged?.());
  }

  return {togglingId, onEnabledChange, handleEnabledChange};
}

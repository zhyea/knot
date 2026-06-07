import { ref } from "vue";
import { ElMessage } from "element-plus";

/**
 * 列表行启用开关：调用独立状态接口并支持失败回滚。
 * @param {object} options
 * @param {(id: number, enabled: boolean) => Promise<unknown>} options.updateApi
 */
export function useEnabledToggle({ updateApi }) {
  const togglingId = ref(null);

  async function onEnabledChange(row, enabled) {
    if (!row?.id) return;
    const prev = row.enabled !== false;
    if (enabled === prev) return;

    togglingId.value = row.id;
    row.enabled = enabled;
    try {
      await updateApi(row.id, enabled);
      ElMessage.success(enabled ? "已启用" : "已禁用");
    } catch {
      row.enabled = prev;
    } finally {
      togglingId.value = null;
    }
  }

  return { togglingId, onEnabledChange };
}

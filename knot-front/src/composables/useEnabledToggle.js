import { ref } from "vue";
import { ElMessage } from "element-plus";

/**
 * 列表行启用开关：调用更新接口并支持失败回滚。
 * @param {object} options
 * @param {(id: number, payload: object) => Promise<unknown>} options.updateApi
 * @param {(row: object, enabled: boolean) => object} options.buildPayload
 */
export function useEnabledToggle({ updateApi, buildPayload }) {
  const togglingId = ref(null);

  async function onEnabledChange(row, enabled) {
    if (!row?.id) return;
    const prev = row.enabled !== false;
    if (enabled === prev) return;

    togglingId.value = row.id;
    row.enabled = enabled;
    try {
      await updateApi(row.id, buildPayload(row, enabled));
      ElMessage.success(enabled ? "已启用" : "已禁用");
    } catch {
      row.enabled = prev;
    } finally {
      togglingId.value = null;
    }
  }

  return { togglingId, onEnabledChange };
}

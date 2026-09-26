import { ref } from "vue";
import type { Row } from "../types";
import { ElMessage } from "element-plus";

/**
 * 列表行启用开关：调用独立状态接口并支持失败回滚。
 * @param {object} options
 * @param {(id: number, enabled: boolean) => Promise<unknown>} options.updateApi
 */
export function useEnabledToggle({ updateApi }: { updateApi: (id: number | string, enabled: boolean) => Promise<unknown> }) {
  const togglingId = ref(null);

  async function onEnabledChange(row: Row, enabled: boolean | string | number) {
    if (!row?.id) return;
    const flag = enabled !== false && enabled !== 0 && enabled !== "false";
    const prev = row.enabled !== false;
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

  return { togglingId, onEnabledChange };
}

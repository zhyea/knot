import {ref} from "vue";
import type {Row} from "@/types";

export interface LogDrawerOptions {
  /** 依次尝试的显示名字段，都取不到时回退 `#${id}` */
  labelFields: readonly string[];
}

/**
 * 「列表 → 变更日志抽屉」的开合状态机。
 *
 * <p>各管理页此前都写 `logXxxId.value = row.id; logXxxName.value = row.a || row.b || '#id';
 * logDrawer.value = true;`。这里只收口状态，`:load-logs` 对应的 API 各页不同，
 * 仍留在页面（编排不下沉）。</p>
 */
export function useLogDrawer(options: LogDrawerOptions) {
  const id = ref<number | string | null>(null);
  const name = ref("");
  const visible = ref(false);

  function open(row: Row): void {
    id.value = row.id;
    // 与原实现一致：显示名采用 || 语义（空串 / null 视为未填），回退到 #id
    const label = options.labelFields.map((field) => row[field]).find((value) => Boolean(value));
    name.value = label == null ? `#${row.id}` : String(label);
    visible.value = true;
  }

  return {id, name, visible, open};
}

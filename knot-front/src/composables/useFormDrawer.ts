import {ref, type Ref} from "vue";
import type {Row} from "@/types";

export interface FormDrawerOptions<T> {
  /** 新建态的编辑值。必填：让调用方显式声明类型，避免 `ref(null)` 退化成 `Ref<null>` */
  emptyValue: T;
  /** 编辑态把行转成编辑值（默认原样）。Provider 只存 id、部门要补 children 时用它 */
  toEditing?: (row: Row) => T;
  /** 打开前后的额外复位（copyFromId / viewMode / defaultParentId 等） */
  onOpen?: (row: Row | null) => void;
}

/**
 * 「列表 → 表单抽屉」的开合状态机。
 *
 * <p>各管理页此前都写 `editingX.value = null; formVisible.value = true;` 与
 * `editingX.value = row; formVisible.value = true;` 两份同构函数。这里只收口**状态**，
 * 提交、保存后 reload、关闭时复位仍归各页面/抽屉自己（编排不下沉）。</p>
 */
export function useFormDrawer<T>(options: FormDrawerOptions<T>) {
  const visible = ref(false);
  const editing = ref(options.emptyValue) as Ref<T>;

  function openCreate(): void {
    editing.value = options.emptyValue;
    options.onOpen?.(null);
    visible.value = true;
  }

  function openEdit(row: Row): void {
    editing.value = options.toEditing ? options.toEditing(row) : (row as unknown as T);
    options.onOpen?.(row);
    visible.value = true;
  }

  function close(): void {
    visible.value = false;
  }

  return {visible, editing, openCreate, openEdit, close};
}

/** 常规「null = 新建 / Row = 编辑」抽屉：省掉每个页面重复写泛型与空值 */
export function useRowFormDrawer() {
  return useFormDrawer<Row | null>({emptyValue: null});
}

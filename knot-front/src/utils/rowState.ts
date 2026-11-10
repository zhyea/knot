import type {Row} from "@/types";

/**
 * 「逻辑删除态」的唯一判据。
 *
 * <p>后端 DTO 的 deleted 是 primitive boolean；**只有严格 true 才算已删除**。
 * 历史上散落在三个列表组件、卡片视图与行操作里的 `row.deleted === true` 是同一业务事实的
 * 多个定义点，收口到此处避免某处被改成 truthy 后把 1 / "true" 之类的脏值误判成已删除
 * （`logicalModelRowActions.test.ts` 已钉死这条语义）。</p>
 *
 * <p>只做判定，不碰展示样式；样式由 {@link deletedRowClassName} 给出。</p>
 */
export function isDeletedRow(row: Row): boolean {
  return row.deleted === true;
}

/** el-table 的 row-class-name 回调：已删除行统一浅红底，三个模型列表共用 */
export function deletedRowClassName({row}: {row: Row}): string {
  return isDeletedRow(row) ? "row-deleted" : "";
}

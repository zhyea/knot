import {Delete, Document, Edit, RefreshLeft} from "@element-plus/icons-vue";
import type {Row, RowAction} from "@/types";
import {isDeletedRow} from "@/utils/rowState";

/**
 * 统一模型的表格视图与卡片视图共用同一套行操作。
 *
 * <p>两处此前逐字重复，任一侧增删按钮都会让两种视图行为不一致。
 * 供应商模型 / 模型池 / 敏感词的动作集不同，各自保留，不塞进本函数参数。</p>
 */
export function logicalModelRowActions(row: Row): RowAction[] {
  if (isDeletedRow(row)) {
    return [
      {key: "log", label: "日志", icon: Document},
      {key: "restore", label: "恢复", icon: RefreshLeft, type: "success", confirm: "确认恢复该统一模型？"}
    ];
  }
  return [
    {key: "edit", label: "编辑", icon: Edit},
    {key: "log", label: "日志", icon: Document},
    {key: "delete", label: "删除", icon: Delete, type: "danger", confirm: "确认删除该统一模型？"}
  ];
}

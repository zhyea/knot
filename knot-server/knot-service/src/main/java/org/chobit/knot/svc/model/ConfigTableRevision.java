package org.chobit.knot.svc.model;

/**
 * 单张配置表的变更指纹。
 *
 * @param tableName 配置表名
 * @param rowCount  当前行数（捕获物理删除与新增）
 * @param revision  内容指纹（有 {@code updated_at} 取最大时间，无该列取全列 CRC 聚合）
 */
public record ConfigTableRevision(String tableName, long rowCount, String revision) {
}

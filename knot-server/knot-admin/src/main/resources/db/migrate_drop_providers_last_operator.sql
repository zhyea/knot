-- 回退：移除 providers 表最后操作人字段（变更改由 operation_logs 记录）
-- 若列不存在可忽略对应语句报错
ALTER TABLE providers DROP COLUMN last_operator_name;
ALTER TABLE providers DROP COLUMN last_operator_id;

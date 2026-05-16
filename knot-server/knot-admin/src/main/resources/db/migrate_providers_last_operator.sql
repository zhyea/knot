-- 已有库：为 providers 增加最后操作人字段（最后修改时间沿用 updated_at）
ALTER TABLE providers
    ADD COLUMN last_operator_id BIGINT DEFAULT NULL COMMENT '最后操作人用户ID' AFTER quota_json,
    ADD COLUMN last_operator_name VARCHAR(64) DEFAULT NULL COMMENT '最后操作人用户名' AFTER last_operator_id;

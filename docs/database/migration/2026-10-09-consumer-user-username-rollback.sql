-- 回滚：kb_routing_consumers 归属用户恢复为绑定 user.id（2026-10-09-consumer-user-username 的反向操作）
--
-- 仅在需要回退「消费者归属用户由 user_id 改为 user_username」时使用。
-- ⚠ 数据不可恢复：user_username 存的是登录名（如 zhangsan），无法反推原 user_id 数值。
--   本脚本 DROP 旧列 + ADD 新列，**不做类型转换**——若用 CHANGE COLUMN 把 VARCHAR 直接转成 BIGINT，
--   库里已有的 'zhangsan' 这类值在严格模式下会让 ALTER 直接失败：
--     Data truncation: Truncated incorrect INTEGER value: 'zhangsan'
--   采用 DROP+ADD 规避该转换风险，代价是列值清空（回滚后需人工回填 user_id，无数据可参照）。
--
-- 列位置：kb_routing_consumers 中该列原位于 name 之后（与 schema.sql 一致）。

SET @old = (
    SELECT COUNT(1)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'kb_routing_consumers'
      AND COLUMN_NAME = 'user_id'
);
SET @new = (
    SELECT COUNT(1)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'kb_routing_consumers'
      AND COLUMN_NAME = 'user_username'
);
SELECT @old AS user_id_exists, @new AS user_username_exists;

SET @sql = IF(
    @old = 0 AND @new > 0,
    CONCAT('ALTER TABLE kb_routing_consumers DROP COLUMN user_username, ',
           ' ADD COLUMN user_id BIGINT DEFAULT NULL COMMENT ''归属用户 id'' AFTER name'),
    'SELECT 1' -- 已是旧结构或全新库，跳过
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---- 执行后核对（用 .workbuddy/audit 下的 JDBC 探针跑）----
-- 预期返回 1（仅剩 user_id）：
-- SELECT COLUMN_NAME FROM information_schema.COLUMNS
--  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME='kb_routing_consumers'
--    AND COLUMN_NAME IN ('user_id','user_username');

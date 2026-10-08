-- 回滚：恢复 kb_routing_rules.user_id 列（2026-10-09）
--
-- 仅在需要回退「路由规则不再记录 userId」时使用。
-- ⚠ 列可空且无历史回填值：回滚只恢复列结构，原 user_id 数据不可恢复。

SET @exists = (
    SELECT COUNT(1)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'kb_routing_rules'
      AND COLUMN_NAME = 'user_id'
);
SELECT @exists AS user_id_exists;

SET @sql = IF(
    @exists = 0,
    'ALTER TABLE kb_routing_rules ADD COLUMN user_id BIGINT DEFAULT NULL AFTER app_id',
    'SELECT 1' -- 列已存在，跳过
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

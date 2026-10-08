-- 回滚：路由规则编码长度收回至 32（2026-10-08）
--
-- 仅当 kb_routing_rules.rule_code 中不存在长度 > 32 的记录时方可安全执行，
-- 否则会触发「Data too long」错误。先核对再执行。
--
-- SELECT COUNT(*) FROM kb_routing_rules WHERE CHAR_LENGTH(rule_code) > 32;
-- 若返回 0，则可安全回滚：

SET @col_type = (
    SELECT COLUMN_TYPE
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'kb_routing_rules'
      AND COLUMN_NAME = 'rule_code'
);
SET @sql = IF(
    @col_type = 'varchar(64)',
    'ALTER TABLE kb_routing_rules MODIFY rule_code VARCHAR(32) NOT NULL',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

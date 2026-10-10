-- 路由规则/计费版本绑定改业务码（2026-10-10）
--
-- 背景（按「跨模块绑定存 code 不存 id」口径收口，robin 拍板）：
--   1) kb_routing_rule_consumers.rule_id(BIGINT) + consumer_id(BIGINT)
--      → rule_code(VARCHAR(64), kb_routing_rules.rule_code)
--        + consumer_code(VARCHAR(32), kb_routing_consumers.consumer_code)
--   2) kb_routing_rule_targets.rule_id(BIGINT)
--      → rule_code(VARCHAR(64), kb_routing_rules.rule_code)
--   3) kb_billing_rule_versions.rule_id(BIGINT)
--      → rule_code(VARCHAR(64), kb_billing_rules.code)
--
-- schema.sql 已把新列纳入 CREATE TABLE 作为权威定义；data.sql 种子值已改为业务码。
-- 本迁移仅用于让「已存在、非由 schema.sql 重建」的库补齐/改列；全新库由 schema.sql 直接建出。
--
-- 幂等：每步先查 information_schema，旧列存在且新列不存在才改；已改过或全新库均跳过。
-- 索引策略（遵循 MariaDB 约定）：DROP COLUMN 前先显式 DROP 依赖旧列的索引，再加新索引。

-- ========== 1. kb_routing_rule_consumers.rule_id/consumer_id -> rule_code/consumer_code ==========
SET @old_exists = (
    SELECT COUNT(1) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_routing_rule_consumers'
      AND COLUMN_NAME IN ('rule_id', 'consumer_id')
);
SET @new_exists = (
    SELECT COUNT(1) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_routing_rule_consumers'
      AND COLUMN_NAME IN ('rule_code', 'consumer_code')
);

SET @sql = IF(@old_exists > 0 AND @new_exists = 0,
    'ALTER TABLE kb_routing_rule_consumers ADD COLUMN rule_code VARCHAR(64) DEFAULT NULL COMMENT ''路由规则业务码（kb_routing_rules.rule_code），非主键 id'' AFTER id, ADD COLUMN consumer_code VARCHAR(32) DEFAULT NULL COMMENT ''消费者业务码（kb_routing_consumers.consumer_code），非主键 id'' AFTER rule_code',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 回填：按旧 id 关联换出业务码
UPDATE kb_routing_rule_consumers rc
    JOIN kb_routing_rules r ON r.id = rc.rule_id
    SET rc.rule_code = r.rule_code
    WHERE rc.rule_code IS NULL AND rc.rule_id IS NOT NULL;

UPDATE kb_routing_rule_consumers rc
    JOIN kb_routing_consumers c ON c.id = rc.consumer_id
    SET rc.consumer_code = c.consumer_code
    WHERE rc.consumer_code IS NULL AND rc.consumer_id IS NOT NULL;

-- 孤儿核对：rule_id/consumer_id 指向不存在的行会导致业务码为空（预期 0）
SELECT COUNT(*) AS orphan_routing_rule_consumers
    FROM kb_routing_rule_consumers
    WHERE rule_code IS NULL OR consumer_code IS NULL;

-- 删旧列前先删依赖旧列的索引（MariaDB 约定：先 DROP INDEX 再 DROP COLUMN）
SET @idx1 = (SELECT COUNT(1) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='kb_routing_rule_consumers' AND INDEX_NAME='uk_routing_rule_consumer');
SET @sql = IF(@idx1 > 0, 'ALTER TABLE kb_routing_rule_consumers DROP INDEX uk_routing_rule_consumer', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx2 = (SELECT COUNT(1) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='kb_routing_rule_consumers' AND INDEX_NAME='idx_rrc_consumer');
SET @sql = IF(@idx2 > 0, 'ALTER TABLE kb_routing_rule_consumers DROP INDEX idx_rrc_consumer', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx3 = (SELECT COUNT(1) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='kb_routing_rule_consumers' AND INDEX_NAME='idx_rrc_rule');
SET @sql = IF(@idx3 > 0, 'ALTER TABLE kb_routing_rule_consumers DROP INDEX idx_rrc_rule', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(@old_exists > 0 AND @new_exists = 0,
    'ALTER TABLE kb_routing_rule_consumers DROP COLUMN rule_id, DROP COLUMN consumer_id',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(@old_exists > 0 AND @new_exists = 0,
    'ALTER TABLE kb_routing_rule_consumers MODIFY COLUMN rule_code VARCHAR(64) NOT NULL COMMENT ''路由规则业务码（kb_routing_rules.rule_code），非主键 id'', MODIFY COLUMN consumer_code VARCHAR(32) NOT NULL COMMENT ''消费者业务码（kb_routing_consumers.consumer_code），非主键 id''',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 加新索引（独立存在性守卫，幂等）
SET @new_idx1 = (SELECT COUNT(1) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='kb_routing_rule_consumers' AND INDEX_NAME='uk_routing_rule_consumer');
SET @sql = IF(@new_idx1 = 0, 'ALTER TABLE kb_routing_rule_consumers ADD UNIQUE KEY uk_routing_rule_consumer (rule_code, consumer_code)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @new_idx2 = (SELECT COUNT(1) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='kb_routing_rule_consumers' AND INDEX_NAME='idx_rrc_consumer');
SET @sql = IF(@new_idx2 = 0, 'ALTER TABLE kb_routing_rule_consumers ADD KEY idx_rrc_consumer (consumer_code, status)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @new_idx3 = (SELECT COUNT(1) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='kb_routing_rule_consumers' AND INDEX_NAME='idx_rrc_rule');
SET @sql = IF(@new_idx3 = 0, 'ALTER TABLE kb_routing_rule_consumers ADD KEY idx_rrc_rule (rule_code, status)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ========== 2. kb_routing_rule_targets.rule_id -> rule_code ==========
SET @old_exists = (
    SELECT COUNT(1) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_routing_rule_targets' AND COLUMN_NAME = 'rule_id'
);
SET @new_exists = (
    SELECT COUNT(1) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_routing_rule_targets' AND COLUMN_NAME = 'rule_code'
);

SET @sql = IF(@old_exists > 0 AND @new_exists = 0,
    'ALTER TABLE kb_routing_rule_targets ADD COLUMN rule_code VARCHAR(64) DEFAULT NULL COMMENT ''路由规则业务码（kb_routing_rules.rule_code），非主键 id'' AFTER id',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE kb_routing_rule_targets t
    JOIN kb_routing_rules r ON r.id = t.rule_id
    SET t.rule_code = r.rule_code
    WHERE t.rule_code IS NULL AND t.rule_id IS NOT NULL;

SELECT COUNT(*) AS orphan_routing_rule_targets
    FROM kb_routing_rule_targets WHERE rule_code IS NULL;

SET @idx1 = (SELECT COUNT(1) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='kb_routing_rule_targets' AND INDEX_NAME='uk_routing_rule_target');
SET @sql = IF(@idx1 > 0, 'ALTER TABLE kb_routing_rule_targets DROP INDEX uk_routing_rule_target', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx2 = (SELECT COUNT(1) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='kb_routing_rule_targets' AND INDEX_NAME='idx_routing_rule_targets_rule');
SET @sql = IF(@idx2 > 0, 'ALTER TABLE kb_routing_rule_targets DROP INDEX idx_routing_rule_targets_rule', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(@old_exists > 0 AND @new_exists = 0,
    'ALTER TABLE kb_routing_rule_targets DROP COLUMN rule_id',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(@old_exists > 0 AND @new_exists = 0,
    'ALTER TABLE kb_routing_rule_targets MODIFY COLUMN rule_code VARCHAR(64) NOT NULL COMMENT ''路由规则业务码（kb_routing_rules.rule_code），非主键 id''',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @new_idx1 = (SELECT COUNT(1) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='kb_routing_rule_targets' AND INDEX_NAME='uk_routing_rule_target');
SET @sql = IF(@new_idx1 = 0, 'ALTER TABLE kb_routing_rule_targets ADD UNIQUE KEY uk_routing_rule_target (rule_code, target_type, target_code)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @new_idx2 = (SELECT COUNT(1) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='kb_routing_rule_targets' AND INDEX_NAME='idx_routing_rule_targets_rule');
SET @sql = IF(@new_idx2 = 0, 'ALTER TABLE kb_routing_rule_targets ADD KEY idx_routing_rule_targets_rule (rule_code, priority)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ========== 3. kb_billing_rule_versions.rule_id -> rule_code ==========
SET @old_exists = (
    SELECT COUNT(1) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_billing_rule_versions' AND COLUMN_NAME = 'rule_id'
);
SET @new_exists = (
    SELECT COUNT(1) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_billing_rule_versions' AND COLUMN_NAME = 'rule_code'
);

SET @sql = IF(@old_exists > 0 AND @new_exists = 0,
    'ALTER TABLE kb_billing_rule_versions ADD COLUMN rule_code VARCHAR(64) DEFAULT NULL COMMENT ''计费规则业务码（kb_billing_rules.code），非主键 id'' AFTER id',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE kb_billing_rule_versions bv
    JOIN kb_billing_rules b ON b.id = bv.rule_id
    SET bv.rule_code = b.code
    WHERE bv.rule_code IS NULL AND bv.rule_id IS NOT NULL;

SELECT COUNT(*) AS orphan_billing_rule_versions
    FROM kb_billing_rule_versions WHERE rule_code IS NULL;

SET @idx1 = (SELECT COUNT(1) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='kb_billing_rule_versions' AND INDEX_NAME='uk_billing_rule_version_code');
SET @sql = IF(@idx1 > 0, 'ALTER TABLE kb_billing_rule_versions DROP INDEX uk_billing_rule_version_code', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx2 = (SELECT COUNT(1) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='kb_billing_rule_versions' AND INDEX_NAME='uk_billing_rule_version_hash');
SET @sql = IF(@idx2 > 0, 'ALTER TABLE kb_billing_rule_versions DROP INDEX uk_billing_rule_version_hash', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx3 = (SELECT COUNT(1) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='kb_billing_rule_versions' AND INDEX_NAME='idx_billing_rule_versions_active');
SET @sql = IF(@idx3 > 0, 'ALTER TABLE kb_billing_rule_versions DROP INDEX idx_billing_rule_versions_active', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(@old_exists > 0 AND @new_exists = 0,
    'ALTER TABLE kb_billing_rule_versions DROP COLUMN rule_id',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(@old_exists > 0 AND @new_exists = 0,
    'ALTER TABLE kb_billing_rule_versions MODIFY COLUMN rule_code VARCHAR(64) NOT NULL COMMENT ''计费规则业务码（kb_billing_rules.code），非主键 id''',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @new_idx1 = (SELECT COUNT(1) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='kb_billing_rule_versions' AND INDEX_NAME='uk_billing_rule_version_code');
SET @sql = IF(@new_idx1 = 0, 'ALTER TABLE kb_billing_rule_versions ADD UNIQUE KEY uk_billing_rule_version_code (rule_code, version_code)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @new_idx2 = (SELECT COUNT(1) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='kb_billing_rule_versions' AND INDEX_NAME='uk_billing_rule_version_hash');
SET @sql = IF(@new_idx2 = 0, 'ALTER TABLE kb_billing_rule_versions ADD UNIQUE KEY uk_billing_rule_version_hash (rule_code, uniq_hash)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @new_idx3 = (SELECT COUNT(1) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='kb_billing_rule_versions' AND INDEX_NAME='idx_billing_rule_versions_active');
SET @sql = IF(@new_idx3 = 0, 'ALTER TABLE kb_billing_rule_versions ADD KEY idx_billing_rule_versions_active (rule_code, status, effective_from, effective_to)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---- 执行后核对 ----
-- 1) 旧列应全部消失，仅剩新列（预期各 2 行 / 1 行结果）：
--    SELECT TABLE_NAME, COLUMN_NAME FROM information_schema.COLUMNS
--     WHERE TABLE_SCHEMA = DATABASE()
--       AND ((TABLE_NAME='kb_routing_rule_consumers' AND COLUMN_NAME IN ('rule_id','consumer_id','rule_code','consumer_code'))
--         OR (TABLE_NAME='kb_routing_rule_targets'   AND COLUMN_NAME IN ('rule_id','rule_code'))
--         OR (TABLE_NAME='kb_billing_rule_versions'  AND COLUMN_NAME IN ('rule_id','rule_code')));
-- 2) 回填结果（业务码不应为空）：
--    SELECT rule_code, consumer_code FROM kb_routing_rule_consumers;
--    SELECT rule_code, target_type, target_code FROM kb_routing_rule_targets;
--    SELECT rule_code, version_code FROM kb_billing_rule_versions;

-- 应用/模型绑定改业务码（2026-10-10，批次 3 & 4）
--
-- 背景（按「跨模块绑定存 code 不存 id」口径收口，robin 拍板）：
--   3) kb_app_model_permissions.app_id(BIGINT) + model_id(BIGINT)
--      → app_code(VARCHAR(64), kb_apps.app_code)
--        + model_code(VARCHAR(128), kb_models.model_code)
--   4) kb_routing_rules.app_id(BIGINT, 可空)
--      → app_code(VARCHAR(64), kb_apps.app_code)
--
-- schema.sql 已把新列纳入 CREATE TABLE 作为权威定义；data.sql 种子值已改为业务码。
-- 本迁移仅用于让「已存在、非由 schema.sql 重建」的库补齐/改列；全新库由 schema.sql 直接建出。
--
-- 幂等：每步先查 information_schema，旧列存在且新列不存在才改；已改过或全新库均跳过。
-- 索引策略（遵循 MariaDB 约定）：DROP COLUMN 前先显式 DROP 依赖旧列的索引，再加新索引。
-- 回填语句同样按旧列存在性守卫，避免对全新库（无旧列）执行时报 Unknown column。

-- ========== 3. kb_app_model_permissions.app_id/model_id -> app_code/model_code ==========
SET @old_exists = (
    SELECT COUNT(1) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_app_model_permissions'
      AND COLUMN_NAME IN ('app_id', 'model_id')
);
SET @new_exists = (
    SELECT COUNT(1) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_app_model_permissions'
      AND COLUMN_NAME IN ('app_code', 'model_code')
);

SET @sql = IF(@old_exists > 0 AND @new_exists = 0,
    'ALTER TABLE kb_app_model_permissions ADD COLUMN app_code VARCHAR(64) DEFAULT NULL COMMENT ''应用业务码（kb_apps.app_code），非主键 id'', ADD COLUMN model_code VARCHAR(128) DEFAULT NULL COMMENT ''供应商模型业务码（kb_models.model_code），非主键 id''',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 回填：按旧 id 关联换出业务码（仅当旧列存在）
SET @sql = IF(@old_exists > 0,
    'UPDATE kb_app_model_permissions p JOIN kb_apps a ON a.id = p.app_id SET p.app_code = a.app_code WHERE p.app_code IS NULL AND p.app_id IS NOT NULL',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(@old_exists > 0,
    'UPDATE kb_app_model_permissions p JOIN kb_models m ON m.id = p.model_id SET p.model_code = m.model_code WHERE p.model_code IS NULL AND p.model_id IS NOT NULL',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 孤儿核对：app_id/model_id 指向不存在的行会导致业务码为空（预期 0）
SELECT COUNT(*) AS orphan_app_model_permissions
    FROM kb_app_model_permissions WHERE app_code IS NULL OR model_code IS NULL;

-- 删旧列前先删复合主键（依赖 app_id/model_id）
SET @sql = IF(@old_exists > 0 AND @new_exists = 0,
    'ALTER TABLE kb_app_model_permissions DROP PRIMARY KEY',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(@old_exists > 0 AND @new_exists = 0,
    'ALTER TABLE kb_app_model_permissions DROP COLUMN app_id, DROP COLUMN model_id',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(@old_exists > 0 AND @new_exists = 0,
    'ALTER TABLE kb_app_model_permissions MODIFY COLUMN app_code VARCHAR(64) NOT NULL COMMENT ''应用业务码（kb_apps.app_code），非主键 id'', MODIFY COLUMN model_code VARCHAR(128) NOT NULL COMMENT ''供应商模型业务码（kb_models.model_code），非主键 id''',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 加新主键（独立存在性守卫，幂等）
SET @new_pk = (SELECT COUNT(1) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='kb_app_model_permissions' AND INDEX_NAME='PRIMARY');
SET @sql = IF(@new_pk = 0, 'ALTER TABLE kb_app_model_permissions ADD PRIMARY KEY (app_code, model_code)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ========== 4. kb_routing_rules.app_id -> app_code ==========
SET @old_exists = (
    SELECT COUNT(1) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_routing_rules' AND COLUMN_NAME = 'app_id'
);
SET @new_exists = (
    SELECT COUNT(1) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_routing_rules' AND COLUMN_NAME = 'app_code'
);

SET @sql = IF(@old_exists > 0 AND @new_exists = 0,
    'ALTER TABLE kb_routing_rules ADD COLUMN app_code VARCHAR(64) DEFAULT NULL COMMENT ''绑定应用业务码（kb_apps.app_code），非主键 id'' AFTER app_scenario',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 回填：按旧 app_id 关联换出 app_code（仅当旧列存在；app_id 可空，只回填非空的）
SET @sql = IF(@old_exists > 0,
    'UPDATE kb_routing_rules r JOIN kb_apps a ON a.id = r.app_id SET r.app_code = a.app_code WHERE r.app_code IS NULL AND r.app_id IS NOT NULL',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 孤儿核对：app_id 非空却换不出 app_code（预期 0）
SELECT COUNT(*) AS orphan_routing_rules_app
    FROM kb_routing_rules WHERE app_id IS NOT NULL AND app_code IS NULL;

-- 删旧列前先删依赖旧列的索引（MariaDB 约定：先 DROP INDEX 再 DROP COLUMN）
SET @idx1 = (SELECT COUNT(1) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='kb_routing_rules' AND INDEX_NAME='idx_routing_rules_app');
SET @sql = IF(@idx1 > 0, 'ALTER TABLE kb_routing_rules DROP INDEX idx_routing_rules_app', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(@old_exists > 0 AND @new_exists = 0,
    'ALTER TABLE kb_routing_rules DROP COLUMN app_id',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 加新索引（独立存在性守卫，幂等）
SET @new_idx1 = (SELECT COUNT(1) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='kb_routing_rules' AND INDEX_NAME='idx_routing_rules_app');
SET @sql = IF(@new_idx1 = 0, 'ALTER TABLE kb_routing_rules ADD KEY idx_routing_rules_app (app_code, status)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---- 执行后核对 ----
-- 1) 旧列应全部消失，仅剩新列：
--    SELECT TABLE_NAME, COLUMN_NAME FROM information_schema.COLUMNS
--     WHERE TABLE_SCHEMA = DATABASE()
--       AND ((TABLE_NAME='kb_app_model_permissions' AND COLUMN_NAME IN ('app_id','model_id','app_code','model_code'))
--         OR (TABLE_NAME='kb_routing_rules'        AND COLUMN_NAME IN ('app_id','app_code')));
-- 2) 回填结果（业务码不应为空）：
--    SELECT app_code, model_code FROM kb_app_model_permissions;
--    SELECT id, rule_code, app_code FROM kb_routing_rules;

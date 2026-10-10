-- 绑定列改业务码（2026-10-10，批次 9）
--
-- 背景（按「跨模块绑定存 code 不存 id」口径收口，robin 拍板）：
--   9) kb_provider_discount_policies.scope_ref_id(BIGINT) -> scope_ref_code(VARCHAR(128))
--        GLOBAL 为空；MODEL -> kb_models.model_code；APP -> kb_apps.app_code
--      kb_plugin_bindings.scope_ref_id(BIGINT)            -> scope_ref_code(VARCHAR(128))
--        GLOBAL 为空；APP -> kb_apps.app_code；RULE -> kb_routing_rules.rule_code；
--        PROVIDER -> kb_provider_accounts.code；MODEL -> kb_models.model_code；POOL -> kb_model_pools.pool_code
--
-- schema.sql 已把新列纳入 CREATE TABLE 作为权威定义；data.sql 种子列名已改。
-- 本迁移仅用于让「已存在、非由 schema.sql 重建」的库补齐/改列；全新库由 schema.sql 直接建出。
--
-- 幂等：每步先查 information_schema，旧列存在且新列不存在才改；已改过或全新库均跳过。
-- 索引策略（MariaDB）：DROP COLUMN 前先显式 DROP 依赖旧列的索引，再加新索引。
-- 回填语句按旧列存在性守卫，避免对全新库（无旧列）执行时报 Unknown column。
-- 孤儿核对：旧 id 指向不存在的行会导致业务码为空，脚本里以 SELECT 报出（预期 0）。

-- ==========================================================
-- 9.1 kb_provider_discount_policies.scope_ref_id -> scope_ref_code
-- ==========================================================
SET @old_exists = (
    SELECT COUNT(1) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_provider_discount_policies' AND COLUMN_NAME = 'scope_ref_id'
);
SET @new_exists = (
    SELECT COUNT(1) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_provider_discount_policies' AND COLUMN_NAME = 'scope_ref_code'
);

SET @sql = IF(@old_exists > 0 AND @new_exists = 0,
    'ALTER TABLE kb_provider_discount_policies ADD COLUMN scope_ref_code VARCHAR(128) DEFAULT NULL COMMENT ''折扣范围业务码（GLOBAL 为空；MODEL→model_code，APP→app_code），非主键 id''',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 回填：MODEL 范围按模型主键换业务码
SET @sql = IF(@old_exists > 0,
    'UPDATE kb_provider_discount_policies p JOIN kb_models m ON p.scope_ref_id = m.id SET p.scope_ref_code = m.model_code WHERE p.scope_type = ''MODEL'' AND p.scope_ref_code IS NULL AND p.scope_ref_id IS NOT NULL',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 回填：APP 范围按应用主键换业务码
SET @sql = IF(@old_exists > 0,
    'UPDATE kb_provider_discount_policies p JOIN kb_apps a ON p.scope_ref_id = a.id SET p.scope_ref_code = a.app_code WHERE p.scope_type = ''APP'' AND p.scope_ref_code IS NULL AND p.scope_ref_id IS NOT NULL',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 孤儿核对（预期 0：非 GLOBAL 却换不出业务码的行）
SET @sql = IF(@old_exists > 0,
    'SELECT COUNT(*) AS orphan_discount_scope FROM kb_provider_discount_policies WHERE scope_type <> ''GLOBAL'' AND scope_ref_id IS NOT NULL AND scope_ref_code IS NULL',
    'SELECT 0 AS orphan_discount_scope');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(@old_exists > 0 AND @new_exists = 0,
    'ALTER TABLE kb_provider_discount_policies DROP INDEX idx_provider_discount_scope', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(@old_exists > 0 AND @new_exists = 0,
    'ALTER TABLE kb_provider_discount_policies DROP COLUMN scope_ref_id', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_idx = (SELECT COUNT(1) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='kb_provider_discount_policies' AND INDEX_NAME='idx_provider_discount_scope');
SET @sql = IF(@has_idx = 0,
    'ALTER TABLE kb_provider_discount_policies ADD KEY idx_provider_discount_scope (scope_type, scope_ref_code, status)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ==========================================================
-- 9.2 kb_plugin_bindings.scope_ref_id -> scope_ref_code
-- ==========================================================
SET @old_exists = (
    SELECT COUNT(1) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_plugin_bindings' AND COLUMN_NAME = 'scope_ref_id'
);
SET @new_exists = (
    SELECT COUNT(1) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_plugin_bindings' AND COLUMN_NAME = 'scope_ref_code'
);

SET @sql = IF(@old_exists > 0 AND @new_exists = 0,
    'ALTER TABLE kb_plugin_bindings ADD COLUMN scope_ref_code VARCHAR(128) DEFAULT NULL COMMENT ''作用范围对象业务码（GLOBAL 为空；APP→app_code，RULE→rule_code，PROVIDER→账户 code，MODEL→model_code，POOL→pool_code），非主键 id''',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 回填：六种作用范围各自按主键换业务码
SET @sql = IF(@old_exists > 0,
    'UPDATE kb_plugin_bindings b JOIN kb_apps a ON b.scope_ref_id = a.id SET b.scope_ref_code = a.app_code WHERE b.scope_type = ''APP'' AND b.scope_ref_code IS NULL AND b.scope_ref_id IS NOT NULL',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(@old_exists > 0,
    'UPDATE kb_plugin_bindings b JOIN kb_routing_rules r ON b.scope_ref_id = r.id SET b.scope_ref_code = r.rule_code WHERE b.scope_type = ''RULE'' AND b.scope_ref_code IS NULL AND b.scope_ref_id IS NOT NULL',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(@old_exists > 0,
    'UPDATE kb_plugin_bindings b JOIN kb_provider_accounts pa ON b.scope_ref_id = pa.id SET b.scope_ref_code = pa.code WHERE b.scope_type = ''PROVIDER'' AND b.scope_ref_code IS NULL AND b.scope_ref_id IS NOT NULL',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(@old_exists > 0,
    'UPDATE kb_plugin_bindings b JOIN kb_models m ON b.scope_ref_id = m.id SET b.scope_ref_code = m.model_code WHERE b.scope_type = ''MODEL'' AND b.scope_ref_code IS NULL AND b.scope_ref_id IS NOT NULL',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(@old_exists > 0,
    'UPDATE kb_plugin_bindings b JOIN kb_model_pools mp ON b.scope_ref_id = mp.id SET b.scope_ref_code = mp.pool_code WHERE b.scope_type = ''POOL'' AND b.scope_ref_code IS NULL AND b.scope_ref_id IS NOT NULL',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 孤儿核对（预期 0：非 GLOBAL 却换不出业务码的行）
SET @sql = IF(@old_exists > 0,
    'SELECT COUNT(*) AS orphan_plugin_binding_scope FROM kb_plugin_bindings WHERE scope_type <> ''GLOBAL'' AND scope_ref_id IS NOT NULL AND scope_ref_code IS NULL',
    'SELECT 0 AS orphan_plugin_binding_scope');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 唯一键与两个二级索引都依赖旧列，先全部 DROP 再 DROP COLUMN
SET @sql = IF(@old_exists > 0 AND @new_exists = 0,
    'ALTER TABLE kb_plugin_bindings DROP INDEX uk_plugin_bindings_scope', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(@old_exists > 0 AND @new_exists = 0,
    'ALTER TABLE kb_plugin_bindings DROP INDEX idx_plugin_bindings_scope_status', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(@old_exists > 0 AND @new_exists = 0,
    'ALTER TABLE kb_plugin_bindings DROP COLUMN scope_ref_id', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_uk = (SELECT COUNT(1) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='kb_plugin_bindings' AND INDEX_NAME='uk_plugin_bindings_scope');
SET @sql = IF(@has_uk = 0,
    'ALTER TABLE kb_plugin_bindings ADD UNIQUE KEY uk_plugin_bindings_scope (instance_code, scope_type, scope_ref_code, stage_code)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_idx = (SELECT COUNT(1) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='kb_plugin_bindings' AND INDEX_NAME='idx_plugin_bindings_scope_status');
SET @sql = IF(@has_idx = 0,
    'ALTER TABLE kb_plugin_bindings ADD KEY idx_plugin_bindings_scope_status (scope_type, scope_ref_code, status)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

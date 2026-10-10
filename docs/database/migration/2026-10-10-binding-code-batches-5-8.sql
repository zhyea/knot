-- 绑定列改业务码（2026-10-10，批次 5 / 6 / 7 / 8）
--
-- 背景（按「跨模块绑定存 code 不存 id」口径收口，robin 拍板）：
--   5) kb_model_api_bindings.model_id(BIGINT)          -> model_code(VARCHAR(128), kb_models.model_code)
--   6) kb_provider_model_mappings.model_id(BIGINT)     -> model_code(VARCHAR(128), kb_models.model_code)
--   7) kb_provider_credentials.provider_account_id(BIGINT)
--                                                      -> provider_account_code(VARCHAR(32), kb_provider_accounts.code)
--   8) kb_resource_traffic_policies:
--        resource_id(BIGINT)          -> resource_code(VARCHAR(128)，按 resource_type 指向对应表 code)
--        rate_limit_policy_id(BIGINT) -> rate_limit_policy_code(VARCHAR(64), kb_rate_limit_policies.policy_code)
--        quota_policy_id(BIGINT)      -> quota_policy_code(VARCHAR(64), kb_quota_policies.policy_code)
--
-- schema.sql 已把新列纳入 CREATE TABLE 作为权威定义；data.sql 种子值已改为业务码。
-- 本迁移仅用于让「已存在、非由 schema.sql 重建」的库补齐/改列；全新库由 schema.sql 直接建出。
--
-- 幂等：每步先查 information_schema，旧列存在且新列不存在才改；已改过或全新库均跳过。
-- 索引策略（遵循 MariaDB 约定）：DROP COLUMN 前先显式 DROP 依赖旧列的索引，再加新索引。
-- 回填语句同样按旧列存在性守卫，避免对全新库（无旧列）执行时报 Unknown column。
-- 孤儿核对：旧 id 指向不存在的行会导致业务码为空，脚本里以 SELECT 报出（预期 0）。

-- ==========================================================
-- 5. kb_model_api_bindings.model_id -> model_code
-- ==========================================================
SET @old_exists = (
    SELECT COUNT(1) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_model_api_bindings' AND COLUMN_NAME = 'model_id'
);
SET @new_exists = (
    SELECT COUNT(1) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_model_api_bindings' AND COLUMN_NAME = 'model_code'
);

SET @sql = IF(@old_exists > 0 AND @new_exists = 0,
    'ALTER TABLE kb_model_api_bindings ADD COLUMN model_code VARCHAR(128) DEFAULT NULL COMMENT ''模型业务码（kb_models.model_code），非主键 id''',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(@old_exists > 0,
    'UPDATE kb_model_api_bindings b JOIN kb_models m ON b.model_id = m.id SET b.model_code = m.model_code WHERE b.model_code IS NULL AND b.model_id IS NOT NULL',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 孤儿核对（预期 0）
SET @sql = IF(@old_exists > 0,
    'SELECT COUNT(*) AS orphan_model_api_bindings FROM kb_model_api_bindings WHERE model_code IS NULL',
    'SELECT 0 AS orphan_model_api_bindings');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(@old_exists > 0 AND @new_exists = 0,
    'ALTER TABLE kb_model_api_bindings DROP INDEX uk_model_api_protocol', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(@old_exists > 0 AND @new_exists = 0,
    'ALTER TABLE kb_model_api_bindings DROP INDEX idx_model_api_bindings_model', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(@old_exists > 0 AND @new_exists = 0,
    'ALTER TABLE kb_model_api_bindings DROP COLUMN model_id', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(@old_exists > 0 AND @new_exists = 0,
    'ALTER TABLE kb_model_api_bindings MODIFY COLUMN model_code VARCHAR(128) NOT NULL COMMENT ''模型业务码（kb_models.model_code），非主键 id''',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_uk = (SELECT COUNT(1) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='kb_model_api_bindings' AND INDEX_NAME='uk_model_api_protocol');
SET @sql = IF(@has_uk = 0,
    'ALTER TABLE kb_model_api_bindings ADD UNIQUE KEY uk_model_api_protocol (model_code, protocol)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_idx = (SELECT COUNT(1) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='kb_model_api_bindings' AND INDEX_NAME='idx_model_api_bindings_model');
SET @sql = IF(@has_idx = 0,
    'ALTER TABLE kb_model_api_bindings ADD KEY idx_model_api_bindings_model (model_code)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ==========================================================
-- 6. kb_provider_model_mappings.model_id -> model_code
-- ==========================================================
SET @old_exists = (
    SELECT COUNT(1) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_provider_model_mappings' AND COLUMN_NAME = 'model_id'
);
SET @new_exists = (
    SELECT COUNT(1) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_provider_model_mappings' AND COLUMN_NAME = 'model_code'
);

SET @sql = IF(@old_exists > 0 AND @new_exists = 0,
    'ALTER TABLE kb_provider_model_mappings ADD COLUMN model_code VARCHAR(128) DEFAULT NULL COMMENT ''供应商模型业务码（kb_models.model_code），非主键 id''',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(@old_exists > 0,
    'UPDATE kb_provider_model_mappings pm JOIN kb_models m ON pm.model_id = m.id SET pm.model_code = m.model_code WHERE pm.model_code IS NULL AND pm.model_id IS NOT NULL',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 孤儿核对（预期 0）
SET @sql = IF(@old_exists > 0,
    'SELECT COUNT(*) AS orphan_provider_model_mappings FROM kb_provider_model_mappings WHERE model_code IS NULL',
    'SELECT 0 AS orphan_provider_model_mappings');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 三个索引都依赖 model_id，DROP COLUMN 前必须先删
SET @sql = IF(@old_exists > 0 AND @new_exists = 0,
    'ALTER TABLE kb_provider_model_mappings DROP INDEX uk_provider_model_mapping_model', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(@old_exists > 0 AND @new_exists = 0,
    'ALTER TABLE kb_provider_model_mappings DROP INDEX idx_logical_provider_model', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(@old_exists > 0 AND @new_exists = 0,
    'ALTER TABLE kb_provider_model_mappings DROP INDEX idx_provider_account_model', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(@old_exists > 0 AND @new_exists = 0,
    'ALTER TABLE kb_provider_model_mappings DROP COLUMN model_id', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(@old_exists > 0 AND @new_exists = 0,
    'ALTER TABLE kb_provider_model_mappings MODIFY COLUMN model_code VARCHAR(128) NOT NULL COMMENT ''供应商模型业务码（kb_models.model_code），非主键 id''',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_uk = (SELECT COUNT(1) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='kb_provider_model_mappings' AND INDEX_NAME='uk_provider_model_mapping_model');
SET @sql = IF(@has_uk = 0,
    'ALTER TABLE kb_provider_model_mappings ADD UNIQUE KEY uk_provider_model_mapping_model (model_code)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_idx = (SELECT COUNT(1) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='kb_provider_model_mappings' AND INDEX_NAME='idx_logical_provider_model');
SET @sql = IF(@has_idx = 0,
    'ALTER TABLE kb_provider_model_mappings ADD KEY idx_logical_provider_model (logical_model_code, provider_account_code, model_code)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_idx = (SELECT COUNT(1) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='kb_provider_model_mappings' AND INDEX_NAME='idx_provider_account_model');
SET @sql = IF(@has_idx = 0,
    'ALTER TABLE kb_provider_model_mappings ADD KEY idx_provider_account_model (provider_account_code, model_code, status)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ==========================================================
-- 7. kb_provider_credentials.provider_account_id -> provider_account_code
-- ==========================================================
SET @old_exists = (
    SELECT COUNT(1) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_provider_credentials' AND COLUMN_NAME = 'provider_account_id'
);
SET @new_exists = (
    SELECT COUNT(1) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_provider_credentials' AND COLUMN_NAME = 'provider_account_code'
);

SET @sql = IF(@old_exists > 0 AND @new_exists = 0,
    'ALTER TABLE kb_provider_credentials ADD COLUMN provider_account_code VARCHAR(32) DEFAULT NULL COMMENT ''供应商账户业务码（kb_provider_accounts.code），非主键 id''',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(@old_exists > 0,
    'UPDATE kb_provider_credentials c JOIN kb_provider_accounts pa ON c.provider_account_id = pa.id SET c.provider_account_code = pa.code WHERE c.provider_account_code IS NULL AND c.provider_account_id IS NOT NULL',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 孤儿核对（预期 0）
SET @sql = IF(@old_exists > 0,
    'SELECT COUNT(*) AS orphan_provider_credentials FROM kb_provider_credentials WHERE provider_account_code IS NULL',
    'SELECT 0 AS orphan_provider_credentials');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(@old_exists > 0 AND @new_exists = 0,
    'ALTER TABLE kb_provider_credentials DROP INDEX idx_provider_credentials_account', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(@old_exists > 0 AND @new_exists = 0,
    'ALTER TABLE kb_provider_credentials DROP COLUMN provider_account_id', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(@old_exists > 0 AND @new_exists = 0,
    'ALTER TABLE kb_provider_credentials MODIFY COLUMN provider_account_code VARCHAR(32) NOT NULL COMMENT ''供应商账户业务码（kb_provider_accounts.code），非主键 id''',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_idx = (SELECT COUNT(1) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='kb_provider_credentials' AND INDEX_NAME='idx_provider_credentials_account');
SET @sql = IF(@has_idx = 0,
    'ALTER TABLE kb_provider_credentials ADD KEY idx_provider_credentials_account (provider_account_code)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ==========================================================
-- 8. kb_resource_traffic_policies
--    resource_id -> resource_code（多态，按 resource_type 回填）
--    rate_limit_policy_id -> rate_limit_policy_code
--    quota_policy_id -> quota_policy_code
-- ==========================================================
SET @old_exists = (
    SELECT COUNT(1) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_resource_traffic_policies' AND COLUMN_NAME = 'resource_id'
);
SET @new_exists = (
    SELECT COUNT(1) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_resource_traffic_policies' AND COLUMN_NAME = 'resource_code'
);

SET @sql = IF(@old_exists > 0 AND @new_exists = 0,
    'ALTER TABLE kb_resource_traffic_policies '
    'ADD COLUMN resource_code VARCHAR(128) DEFAULT NULL COMMENT ''资源业务码（按 resource_type 指向对应表的 code），非主键 id'', '
    'ADD COLUMN rate_limit_policy_code VARCHAR(64) DEFAULT NULL COMMENT ''频控策略业务码（kb_rate_limit_policies.policy_code），非主键 id'', '
    'ADD COLUMN quota_policy_code VARCHAR(64) DEFAULT NULL COMMENT ''额度策略业务码（kb_quota_policies.policy_code），非主键 id''',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 资源业务码回填：resource_type 决定资源表（APP/MODEL/PROVIDER/ROUTING_RULE/ROUTING_CONSUMER）
SET @sql = IF(@old_exists > 0,
    'UPDATE kb_resource_traffic_policies p JOIN kb_apps r ON p.resource_id = r.id SET p.resource_code = r.app_code '
    'WHERE p.resource_code IS NULL AND p.resource_type = ''APP''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(@old_exists > 0,
    'UPDATE kb_resource_traffic_policies p JOIN kb_models r ON p.resource_id = r.id SET p.resource_code = r.model_code '
    'WHERE p.resource_code IS NULL AND p.resource_type = ''MODEL''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(@old_exists > 0,
    'UPDATE kb_resource_traffic_policies p JOIN kb_provider_accounts r ON p.resource_id = r.id SET p.resource_code = r.code '
    'WHERE p.resource_code IS NULL AND p.resource_type = ''PROVIDER''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(@old_exists > 0,
    'UPDATE kb_resource_traffic_policies p JOIN kb_routing_rules r ON p.resource_id = r.id SET p.resource_code = r.rule_code '
    'WHERE p.resource_code IS NULL AND p.resource_type = ''ROUTING_RULE''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(@old_exists > 0,
    'UPDATE kb_resource_traffic_policies p JOIN kb_routing_consumers r ON p.resource_id = r.id SET p.resource_code = r.consumer_code '
    'WHERE p.resource_code IS NULL AND p.resource_type = ''ROUTING_CONSUMER''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 策略业务码回填
SET @sql = IF(@old_exists > 0,
    'UPDATE kb_resource_traffic_policies p JOIN kb_rate_limit_policies rlp ON p.rate_limit_policy_id = rlp.id '
    'SET p.rate_limit_policy_code = rlp.policy_code WHERE p.rate_limit_policy_code IS NULL AND p.rate_limit_policy_id IS NOT NULL',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(@old_exists > 0,
    'UPDATE kb_resource_traffic_policies p JOIN kb_quota_policies qp ON p.quota_policy_id = qp.id '
    'SET p.quota_policy_code = qp.policy_code WHERE p.quota_policy_code IS NULL AND p.quota_policy_id IS NOT NULL',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 孤儿核对（预期 0：resource_code 为空说明 resource_id 指向了不存在的资源）
SET @sql = IF(@old_exists > 0,
    'SELECT COUNT(*) AS orphan_resource_traffic FROM kb_resource_traffic_policies WHERE resource_code IS NULL',
    'SELECT 0 AS orphan_resource_traffic');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 三个索引都依赖旧列，DROP COLUMN 前必须先删
SET @sql = IF(@old_exists > 0 AND @new_exists = 0,
    'ALTER TABLE kb_resource_traffic_policies DROP INDEX uk_resource_traffic', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(@old_exists > 0 AND @new_exists = 0,
    'ALTER TABLE kb_resource_traffic_policies DROP INDEX idx_resource_traffic_rate', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(@old_exists > 0 AND @new_exists = 0,
    'ALTER TABLE kb_resource_traffic_policies DROP INDEX idx_resource_traffic_quota', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(@old_exists > 0 AND @new_exists = 0,
    'ALTER TABLE kb_resource_traffic_policies DROP COLUMN resource_id, '
    'DROP COLUMN rate_limit_policy_id, DROP COLUMN quota_policy_id', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(@old_exists > 0 AND @new_exists = 0,
    'ALTER TABLE kb_resource_traffic_policies MODIFY COLUMN resource_code VARCHAR(128) NOT NULL COMMENT ''资源业务码（按 resource_type 指向对应表的 code），非主键 id''',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_uk = (SELECT COUNT(1) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='kb_resource_traffic_policies' AND INDEX_NAME='uk_resource_traffic');
SET @sql = IF(@has_uk = 0,
    'ALTER TABLE kb_resource_traffic_policies ADD UNIQUE KEY uk_resource_traffic (resource_type, resource_code)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_idx = (SELECT COUNT(1) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='kb_resource_traffic_policies' AND INDEX_NAME='idx_resource_traffic_rate');
SET @sql = IF(@has_idx = 0,
    'ALTER TABLE kb_resource_traffic_policies ADD KEY idx_resource_traffic_rate (rate_limit_policy_code)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_idx = (SELECT COUNT(1) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='kb_resource_traffic_policies' AND INDEX_NAME='idx_resource_traffic_quota');
SET @sql = IF(@has_idx = 0,
    'ALTER TABLE kb_resource_traffic_policies ADD KEY idx_resource_traffic_quota (quota_policy_code)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

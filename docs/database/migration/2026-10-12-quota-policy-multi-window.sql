-- 额度改为子表（一个资源可挂多套不同统计窗口的额度策略，2026-10-12，B1 子表版）
--
-- 背景（robin 拍板：每个资源支持一组不同统计窗口的限额配置）：
--   原 kb_resource_traffic_policies 用单列 quota_policy_code 只能挂「一个窗口」的额度策略；
--   现改为 kb_resource_quota_bindings 子表，一个资源可挂多套（每窗口一份 token + 成本上限）额度策略。
--
-- schema.sql 已把新表纳入 CREATE TABLE 作为权威定义，并删除了 kb_resource_traffic_policies 的
-- quota_policy_code 列与 idx_resource_traffic_quota 索引；data.sql 种子已改为写子表。
-- 本迁移仅用于让「已存在、非由 schema.sql 重建」的库完成列删除 + 子表回填；全新库由 schema.sql 直接建出。
--
-- 幂等：先查 information_schema，旧列 / 旧索引存在才删；子表用 CREATE TABLE IF NOT EXISTS。
-- 索引策略（遵循 MariaDB 约定）：DROP COLUMN 前先显式 DROP 依赖旧列的索引。
-- 回填语句同样按旧列存在性守卫，避免对全新库（无旧列）执行时报 Unknown column。
-- 孤儿核对：旧 quota_policy_code 指向不存在的策略会导致业务码为空，脚本里以 SELECT 报出（预期 0）。

-- ==========================================================
-- 1. 建子表（全新库由 schema.sql 已建；既有库补齐）
-- ==========================================================
CREATE TABLE IF NOT EXISTS kb_resource_quota_bindings (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  resource_type VARCHAR(32) NOT NULL COMMENT 'APP / PROVIDER / ROUTING_CONSUMER（仅这三层配额度）',
  resource_code VARCHAR(128) NOT NULL COMMENT '资源业务码（按 resource_type 指向对应表的 code），非主键 id',
  quota_policy_code VARCHAR(64) NOT NULL COMMENT '额度策略业务码（kb_quota_policies.policy_code），非主键 id',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_rqb_resource_policy (resource_type, resource_code, quota_policy_code),
  KEY idx_rqb_policy (quota_policy_code)
);

-- ==========================================================
-- 2. 旧列守卫：kb_resource_traffic_policies.quota_policy_code 是否还在
-- ==========================================================
SET @has_quota_col = (
    SELECT COUNT(1) FROM information_schema.COLumns
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_resource_traffic_policies' AND COLUMN_NAME = 'quota_policy_code'
);

-- ==========================================================
-- 3. 回填：把旧单列额度绑定搬进子表（旧列存在才执行）
-- ==========================================================
SET @sql = IF(@has_quota_col > 0,
    'INSERT IGNORE INTO kb_resource_quota_bindings (resource_type, resource_code, quota_policy_code) '
    'SELECT resource_type, resource_code, quota_policy_code FROM kb_resource_traffic_policies '
    'WHERE quota_policy_code IS NOT NULL AND resource_code IS NOT NULL',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 孤儿核对（预期 0：迁移后旧列里不应再残留有效绑定）
SET @sql = IF(@has_quota_col > 0,
    'SELECT COUNT(*) AS pending_quota_column_rows FROM kb_resource_traffic_policies WHERE quota_policy_code IS NOT NULL',
    'SELECT 0 AS pending_quota_column_rows');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ==========================================================
-- 4. 删依赖旧列的索引，再删旧列
-- ==========================================================
SET @has_idx = (SELECT COUNT(1) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_resource_traffic_policies' AND INDEX_NAME = 'idx_resource_traffic_quota');
SET @sql = IF(@has_idx > 0,
    'ALTER TABLE kb_resource_traffic_policies DROP INDEX idx_resource_traffic_quota', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(@has_quota_col > 0,
    'ALTER TABLE kb_resource_traffic_policies DROP COLUMN quota_policy_code', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

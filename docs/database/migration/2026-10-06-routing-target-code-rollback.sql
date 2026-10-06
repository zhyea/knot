-- 回滚：路由目标存储迁移（2026-10-06）
-- 对应正向脚本：2026-10-06-routing-target-code.sql
--
-- 定位：本项目无历史包袱（新库新代码，不兼容历史数据），回滚只做 DDL 逆向 +
--       按 target_code 反填 target_id，不做数据兜底。
--
-- ⚠ 回滚 ④ 会删除 target_code 列；若已存在 target_code 为空的目标（5.3 非 0），
--   其 target_id 将反填为 0（无效）。执行前先核对 5.3 为 0。
-- ⚠ 回滚后应用侧仍按「存储 id」运行，需同步回滚代码与 schema.sql / data.sql 才能一致。

-- ---- 0. 执行前核查（预期 0）----
-- SELECT COUNT(*) FROM kb_routing_rule_targets WHERE target_code = '' OR target_code IS NULL;

-- ---- 1. 恢复 target_id 列（不存在才加），默认 0 便于反填判定 ----
SET @ddl := IF(
    EXISTS (SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_routing_rule_targets'
              AND COLUMN_NAME = 'target_id'),
    'DO 0',
    'ALTER TABLE kb_routing_rule_targets ADD COLUMN target_id BIGINT NOT NULL DEFAULT 0 AFTER target_type');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---- 2. 按 target_code 反填 target_id ----
UPDATE kb_routing_rule_targets rt
  LEFT JOIN kb_models m ON rt.target_type = 'MODEL' AND rt.target_code = m.model_code
  LEFT JOIN kb_model_pools mp ON rt.target_type = 'MODEL_POOL' AND rt.target_code = mp.pool_code AND mp.is_deleted = 0
  SET rt.target_id = COALESCE(m.id, mp.id)
  WHERE rt.target_id = 0 OR rt.target_id IS NULL;

-- ---- 3. 重建旧唯一键（先删新，再建旧；均条件式）----
SET @ddl := IF(
    EXISTS (SELECT 1 FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_routing_rule_targets'
              AND INDEX_NAME = 'uk_routing_rule_target'),
    'ALTER TABLE kb_routing_rule_targets DROP INDEX uk_routing_rule_target',
    'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := IF(
    EXISTS (SELECT 1 FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_routing_rule_targets'
              AND INDEX_NAME = 'uk_routing_rule_target'),
    'DO 0',
    'ALTER TABLE kb_routing_rule_targets ADD UNIQUE KEY uk_routing_rule_target (rule_id, target_type, target_id)');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---- 4. 删除 target_code 列（存在才删）----
SET @ddl := IF(
    EXISTS (SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_routing_rule_targets'
              AND COLUMN_NAME = 'target_code'),
    'ALTER TABLE kb_routing_rule_targets DROP COLUMN target_code',
    'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---- 5. 执行后核对 ----
-- 5.1 target_id 已恢复（预期 1）
-- SELECT COUNT(*) FROM information_schema.COLUMNS
--  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_routing_rule_targets' AND COLUMN_NAME = 'target_id';
-- 5.2 target_code 已不存在（预期 0）
-- SELECT COUNT(*) FROM information_schema.COLUMNS
--  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_routing_rule_targets' AND COLUMN_NAME = 'target_code';
-- 5.3 反填覆盖率（预期 0 行 target_id 为 0）
-- SELECT COUNT(*) FROM kb_routing_rule_targets WHERE target_id = 0;

-- 路由规则移除「规则级 fallback」字段 fallback_rule_id（2026-10-06）
-- 迁移版本：2026-10-06-drop-routing-rule-fallback
--
-- 【背景】
-- fallback_rule_id 原拟作为三层降级（retry → failover → 规则 fallback）的第三层指向，
-- 但运行层从未接线（死字段）。路由规则已是对外暴露的最小交互单位，不再需要规则级 fallback，
-- 故清理该列。
--
-- 【不影响】retry_policy / 跨候选 failover 不受影响。
--
-- 幂等性：DDL 用 information_schema 判定 + PREPARE 条件式执行，可重复跑。
--         全新实例以更新后的 schema.sql 建库，该列已不存在，无需执行本脚本。
--         既有库必须先跑本脚本再启应用（schema.sql 已无该列，须与库结构对齐）。
--
-- ⚠ 用 ProbeMigrate.java 执行时注意：脚本按分号切分，
--   PREPARE / EXECUTE / DEALLOCATE PREPARE 必须各占一条语句。

-- ---- 1. 删除列（不存在则跳过）----
SET @ddl := IF(
    EXISTS (SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_routing_rules'
              AND COLUMN_NAME = 'fallback_rule_id'),
    'ALTER TABLE kb_routing_rules DROP COLUMN fallback_rule_id',
    'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

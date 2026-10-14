-- 路由规则增加「异常摘除策略」（2026-10-15）
-- 迁移版本：2026-10-15-routing-rule-ejection-policy
--
-- 【语义】
-- kb_routing_rules 新增 ejection_policy 列（TEXT，可空），承载规则级异常摘除配置（JSON）。
-- 语义详见 docs/design/routing-ejection-design.md：窗口内反复异常（上游故障类失败 / 首 token 过慢）
-- 的供应商模型，在后续请求里被临时移出候选链；冷却结束后由单个探活请求半开恢复。
-- 默认关闭（EjectionPolicy.DEFAULT.enabled = false），列为空 = 走内置默认策略。
--
-- 【不影响】retry_policy / selection_strategy 不动；摘除状态本身是运行时数据，不落库。
--
-- 幂等性：DDL 用 information_schema 判定 + PREPARE 条件式执行，可重复跑。
--         全新实例以更新后的 schema.sql 建库，本列已就位，无需执行本脚本。
--         既有库必须先跑本脚本再启应用（否则路由规则读写会因列缺失失败）。
--
-- ⚠ 用 ProbeMigrate.java 执行时注意：脚本按分号切分，
--   PREPARE / EXECUTE / DEALLOCATE PREPARE 必须各占一条语句。

-- ---- 0. 执行前快照（非执行语句）----
-- SELECT id, rule_code, ejection_policy FROM kb_routing_rules ORDER BY id;

-- ---- 1. 新增列（存在则跳过）----
SET @ddl := IF(
    EXISTS (SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_routing_rules'
              AND COLUMN_NAME = 'ejection_policy'),
    'DO 0',
    'ALTER TABLE kb_routing_rules ADD COLUMN ejection_policy TEXT NULL COMMENT ''异常摘除策略（JSON）：窗口内反复异常（上游故障/首token过慢）的供应商模型临时移出候选链；空表示走内置默认策略（默认关闭）'' AFTER retry_policy');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---- 2. 执行后核对 ----
-- 2.1 列就位（预期 1 行，COLUMN_TYPE = text）
-- SELECT COLUMN_NAME, IS_NULLABLE, COLUMN_TYPE FROM information_schema.COLUMNS
--  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_routing_rules'
--    AND COLUMN_NAME = 'ejection_policy';
-- 2.2 存量行均为空（预期全部 NULL，运行时按默认策略＝默认关闭，无需回填）
-- SELECT id, rule_code, ejection_policy FROM kb_routing_rules ORDER BY id;

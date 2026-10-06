-- 路由规则增加「失败重试策略」（2026-10-05）
-- 迁移版本：2026-10-05-routing-rule-retry-policy
--
-- 【语义】
-- kb_routing_rules 新增 retry_policy 列（TEXT，可空），承载规则级失败重试配置（JSON）。
-- 语义详见 docs/design/routing-retry-design.md：重试位于「跨候选 failover」之前，
-- 对同一路由目标（同一 upstream_model）原地重投；默认开启（总尝试 3 次、指数退避 + jitter、
-- 重试 5xx/429 与网络层异常）。
-- 列为空 = 未显式配置，运行时走内置默认策略（RetryPolicy.DEFAULT）。
--
-- 【不影响】频控/额度仍走 ResourceTrafficPolicySupport 独立表，本次不动。
--
-- 幂等性：DDL 用 information_schema 判定 + PREPARE 条件式执行，可重复跑。
--         全新实例以更新后的 schema.sql 建库，本列已就位，无需执行本脚本。
--         既有库必须先跑本脚本再启应用（否则 contextLoads / *ControllerTest 会因列缺失失败）。
--
-- ⚠ 用 ProbeMigrate.java 执行时注意：脚本按分号切分，
--   PREPARE / EXECUTE / DEALLOCATE PREPARE 必须各占一条语句。

-- ---- 0. 执行前快照（非执行语句）----
-- SELECT id, rule_code, retry_policy FROM kb_routing_rules ORDER BY id;

-- ---- 1. 新增列（存在则跳过）----
SET @ddl := IF(
    EXISTS (SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_routing_rules'
              AND COLUMN_NAME = 'retry_policy'),
    'DO 0',
    'ALTER TABLE kb_routing_rules ADD COLUMN retry_policy TEXT NULL COMMENT ''失败重试策略（JSON）；空表示走内置默认策略'' AFTER user_id');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---- 2. 执行后核对 ----
-- 2.1 列就位（预期 1 行，COLUMN_TYPE = text）
-- SELECT COLUMN_NAME, IS_NULLABLE, COLUMN_TYPE FROM information_schema.COLUMNS
--  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_routing_rules'
--    AND COLUMN_NAME = 'retry_policy';
-- 2.2 存量行均为空（预期全部 NULL，运行时按默认策略处理，无需回填）
-- SELECT id, rule_code, retry_policy FROM kb_routing_rules ORDER BY id;

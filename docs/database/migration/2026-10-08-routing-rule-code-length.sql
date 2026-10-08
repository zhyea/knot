-- 路由规则编码长度放宽至 64（2026-10-08）
--
-- 背景：前端路由规则编辑抽屉的 ruleCode 输入限制由 32 放宽到 64，
--   后端 RULE_CODE_MAX_LEN 与 schema.sql 中 kb_routing_rules.rule_code 同步调整。
--   既有库需通过本迁移扩列，否则已存在的 VARCHAR(32) 会截断/拒绝更长的编码。
--
-- 幂等：先用 SELECT 确认列宽，避免重复 ALTER（MySQL 对无变化的 MODIFY 仍会重建表）。
-- 注意：`rule_code` 上有 UNIQUE KEY uk_routing_rules_code，扩列不影响唯一约束。

SET @col_type = (
    SELECT COLUMN_TYPE
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'kb_routing_rules'
      AND COLUMN_NAME = 'rule_code'
);
SELECT @col_type AS current_rule_code_type;

SET @sql = IF(
    @col_type = 'varchar(32)',
    'ALTER TABLE kb_routing_rules MODIFY rule_code VARCHAR(64) NOT NULL',
    'SELECT 1' -- 已是目标宽度，跳过
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---- 执行后核对（非执行语句，迁移后用 JDBC 探针核对）----
-- 预期 column_type = varchar(64)：
-- SELECT COLUMN_TYPE FROM information_schema.COLUMNS
--  WHERE TABLE_NAME='kb_routing_rules' AND COLUMN_NAME='rule_code';

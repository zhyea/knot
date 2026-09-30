-- 2026-10-01 计费规则去掉 provider_code；供应商模型改用 code 绑定计费规则
-- 适用库：knot（MariaDB 10.6 / MySQL 8）
-- 说明：规则作用域只保留统一模型；kb_models 关联列由主键 id 换为业务码 code。
--       既有绑定先按 id 回填 code 再删旧列（一次性迁移，代码侧不留兼容分支）。

ALTER TABLE kb_billing_rules
    DROP INDEX idx_billing_rules_match,
    DROP COLUMN provider_code,
    ADD KEY idx_billing_rules_match (logical_model_code, status);

ALTER TABLE kb_models
    ADD COLUMN billing_rule_code VARCHAR(64) DEFAULT NULL COMMENT '绑定计费规则 code（kb_billing_rules.code），非主键 id' AFTER remark,
    ADD KEY idx_models_billing_rule (billing_rule_code);

UPDATE kb_models m
    JOIN kb_billing_rules br ON br.id = m.billing_rule_id
SET m.billing_rule_code = br.code
WHERE m.billing_rule_id IS NOT NULL;

ALTER TABLE kb_models DROP COLUMN billing_rule_id;

-- 2026-09-23 收敛统一模型字段 - 回滚脚本
-- 回滚只恢复字段和枚举结构，不恢复已删除的历史字段值。

ALTER TABLE kb_logical_models
    ADD COLUMN version VARCHAR(64) DEFAULT NULL AFTER model_family,
    ADD COLUMN quality_level VARCHAR(32) DEFAULT NULL AFTER featured,
    ADD COLUMN latency_level VARCHAR(32) DEFAULT NULL AFTER quality_level,
    ADD COLUMN cost_level VARCHAR(32) DEFAULT NULL AFTER latency_level,
    ADD COLUMN pricing_summary VARCHAR(255) DEFAULT NULL AFTER cost_level;

INSERT IGNORE INTO ks_enum_categories (id, category, category_name, is_system, is_enabled) VALUES
(18, 'logical_model_quality_level', '统一模型质量等级', 0, 1),
(19, 'logical_model_latency_level', '统一模型延迟等级', 0, 1),
(20, 'logical_model_cost_level', '统一模型成本等级', 0, 1);

INSERT IGNORE INTO ks_enum_configs (category_id, item_code, item_label, sort_order, is_enabled) VALUES
(18, 'HIGH', '高', 1, 1),
(18, 'MEDIUM', '中', 2, 1),
(18, 'LOW', '低', 3, 1),
(19, 'LOW', '低', 1, 1),
(19, 'MEDIUM', '中', 2, 1),
(19, 'HIGH', '高', 3, 1),
(20, 'LOW', '低', 1, 1),
(20, 'MEDIUM', '中', 2, 1),
(20, 'HIGH', '高', 3, 1);

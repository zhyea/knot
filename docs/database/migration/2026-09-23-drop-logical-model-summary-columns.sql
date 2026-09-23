-- 2026-09-23 收敛统一模型字段
-- 移除统一模型版本、质量/延迟/成本等级和价格摘要。

ALTER TABLE kb_logical_models
    DROP COLUMN version,
    DROP COLUMN quality_level,
    DROP COLUMN latency_level,
    DROP COLUMN cost_level,
    DROP COLUMN pricing_summary;

DELETE ec
FROM ks_enum_configs ec
INNER JOIN ks_enum_categories c ON c.id = ec.category_id
WHERE c.category IN (
    'logical_model_quality_level',
    'logical_model_latency_level',
    'logical_model_cost_level'
);

DELETE FROM ks_enum_categories
WHERE category IN (
    'logical_model_quality_level',
    'logical_model_latency_level',
    'logical_model_cost_level'
);

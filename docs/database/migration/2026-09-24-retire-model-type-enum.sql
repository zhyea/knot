-- 2026-09-24 模型类型改由 ModelTypeEnum 维护（含 displayName 与允许协议规则）

DELETE ec
FROM ks_enum_configs ec
INNER JOIN ks_enum_categories c ON c.id = ec.category_id
WHERE c.category = 'model_type';

DELETE FROM ks_enum_categories
WHERE category = 'model_type';

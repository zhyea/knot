-- 2026-09-23 接口协议改由 ModelApiProtocolEnum 维护

DELETE ec
FROM ks_enum_configs ec
INNER JOIN ks_enum_categories c ON c.id = ec.category_id
WHERE c.category = 'model_api_protocol';

DELETE FROM ks_enum_categories
WHERE category = 'model_api_protocol';

-- 回滚：恢复 DB 枚举分类 credential_type（2026-09-26）
--
-- 仅在需要回退「认证类型以代码枚举为准」时使用。恢复后前端仍走
-- GET /api/provider-accounts/credential-types，回滚脚本不会改变代码枚举的权威地位。

INSERT IGNORE INTO ks_enum_categories (id, category, category_name, is_system, is_enabled) VALUES
(4, 'credential_type', '凭证类型', 0, 1);

INSERT IGNORE INTO ks_enum_configs (category_id, item_code, item_label, sort_order, is_enabled) VALUES
(4, 'API_KEY',      'API Key',    1, 1),
(4, 'OAUTH_TOKEN',  'OAuth Token', 2, 1),
(4, 'BEARER_TOKEN', 'Bearer Token',3, 1);

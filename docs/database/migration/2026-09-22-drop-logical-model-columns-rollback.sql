-- 2026-09-22 kb_logical_models 冗余字段清理 - 回滚脚本
-- 注意：被删列在迁移前全部为 NULL（库内仅 2 行种子数据），回滚只恢复列结构、不恢复数据。

ALTER TABLE kb_logical_models
    ADD COLUMN external_source_code VARCHAR(64) DEFAULT NULL AFTER version,
    ADD COLUMN external_model_id VARCHAR(160) DEFAULT NULL AFTER external_source_code,
    ADD COLUMN canonical_slug VARCHAR(256) DEFAULT NULL AFTER external_model_id,
    ADD COLUMN provider_code VARCHAR(128) DEFAULT NULL AFTER canonical_slug,
    ADD COLUMN provider_name VARCHAR(128) DEFAULT NULL AFTER provider_code,
    ADD COLUMN logo_url VARCHAR(512) DEFAULT NULL AFTER description,
    ADD COLUMN cover_url VARCHAR(512) DEFAULT NULL AFTER logo_url,
    ADD COLUMN capabilities_json JSON DEFAULT NULL AFTER use_cases_json,
    ADD COLUMN default_params_json JSON DEFAULT NULL AFTER languages_json,
    ADD COLUMN param_schema_json JSON DEFAULT NULL AFTER default_params_json,
    ADD COLUMN safety_policy_json JSON DEFAULT NULL AFTER param_schema_json,
    ADD COLUMN pricing_json JSON DEFAULT NULL AFTER safety_policy_json,
    ADD COLUMN supported_parameters_json JSON DEFAULT NULL AFTER pricing_json,
    ADD COLUMN owner_user_id BIGINT DEFAULT NULL AFTER featured,
    ADD COLUMN owner_team VARCHAR(128) DEFAULT NULL AFTER owner_user_id,
    ADD INDEX idx_logical_models_external (external_source_code, external_model_id);

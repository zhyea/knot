-- 2026-09-22 kb_logical_models 冗余字段清理
-- 背景：43 列中 15 列为「只写不读」的影子列（外部模型识别、供应商归属、未落地的 JSON 扩展位），
--       全项目无任何读取方（仅 LogicalModelConverter 机械搬运），库内 2 行数据全部为 NULL。
-- 说明：schema.sql 用 CREATE TABLE IF NOT EXISTS，对已存在的表不会改结构 -> 必须显式 DROP COLUMN。
--       执行顺序：先跑本脚本（迁移），再部署新包（改完 db/*.sql 需重新 mvn package 才生效）。
-- 回滚：见同目录 2026-09-22-drop-logical-model-columns-rollback.sql

ALTER TABLE kb_logical_models
    DROP INDEX idx_logical_models_external,
    DROP COLUMN external_source_code,
    DROP COLUMN external_model_id,
    DROP COLUMN canonical_slug,
    DROP COLUMN provider_code,
    DROP COLUMN provider_name,
    DROP COLUMN logo_url,
    DROP COLUMN cover_url,
    DROP COLUMN capabilities_json,
    DROP COLUMN default_params_json,
    DROP COLUMN param_schema_json,
    DROP COLUMN safety_policy_json,
    DROP COLUMN pricing_json,
    DROP COLUMN supported_parameters_json,
    DROP COLUMN owner_user_id,
    DROP COLUMN owner_team;

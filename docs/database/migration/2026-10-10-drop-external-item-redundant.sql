-- kx_model_items 数据收敛（2026-10-10）
-- 删除 17 个「只写不读」或内容重复的冗余列，40 列 -> 23 列：
--   hugging_face_id, provider_code, details_path, created_timestamp, modality,
--   tokenizer, instruct_type, pricing_json, top_provider_json,
--   supported_parameters_json, default_parameters_json, supported_voices_json,
--   knowledge_cutoff, expiration_date, model_family, last_seen_at, capabilities_json
-- 被删内容均可由 raw_json（上游原始报文）兜底找回，不影响列表/详情/建统一模型三条链路。
--
-- 幂等：列或索引不存在时跳过，可安全重复执行。
-- ⚠ provider_code 上的单列索引必须先删：MariaDB 的 DROP COLUMN 不会自动清理该索引。

-- 1) 先删 provider_code 索引（若仍存在）
SET @stmt := IF(
    EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'kx_model_items'
          AND index_name = 'idx_external_model_provider'
    ),
    'ALTER TABLE kx_model_items DROP INDEX idx_external_model_provider',
    'DO 0'
);
PREPARE s1 FROM @stmt; EXECUTE s1; DEALLOCATE PREPARE s1;

-- 2) 逐列删除（仅当列仍存在）

SET @stmt := IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'kx_model_items' AND column_name = 'hugging_face_id'), 'ALTER TABLE kx_model_items DROP COLUMN hugging_face_id', 'DO 0');
PREPARE s1 FROM @stmt; EXECUTE s1; DEALLOCATE PREPARE s1;

SET @stmt := IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'kx_model_items' AND column_name = 'details_path'), 'ALTER TABLE kx_model_items DROP COLUMN details_path', 'DO 0');
PREPARE s1 FROM @stmt; EXECUTE s1; DEALLOCATE PREPARE s1;

SET @stmt := IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'kx_model_items' AND column_name = 'created_timestamp'), 'ALTER TABLE kx_model_items DROP COLUMN created_timestamp', 'DO 0');
PREPARE s1 FROM @stmt; EXECUTE s1; DEALLOCATE PREPARE s1;

SET @stmt := IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'kx_model_items' AND column_name = 'modality'), 'ALTER TABLE kx_model_items DROP COLUMN modality', 'DO 0');
PREPARE s1 FROM @stmt; EXECUTE s1; DEALLOCATE PREPARE s1;

SET @stmt := IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'kx_model_items' AND column_name = 'tokenizer'), 'ALTER TABLE kx_model_items DROP COLUMN tokenizer', 'DO 0');
PREPARE s1 FROM @stmt; EXECUTE s1; DEALLOCATE PREPARE s1;

SET @stmt := IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'kx_model_items' AND column_name = 'instruct_type'), 'ALTER TABLE kx_model_items DROP COLUMN instruct_type', 'DO 0');
PREPARE s1 FROM @stmt; EXECUTE s1; DEALLOCATE PREPARE s1;

SET @stmt := IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'kx_model_items' AND column_name = 'pricing_json'), 'ALTER TABLE kx_model_items DROP COLUMN pricing_json', 'DO 0');
PREPARE s1 FROM @stmt; EXECUTE s1; DEALLOCATE PREPARE s1;

SET @stmt := IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'kx_model_items' AND column_name = 'top_provider_json'), 'ALTER TABLE kx_model_items DROP COLUMN top_provider_json', 'DO 0');
PREPARE s1 FROM @stmt; EXECUTE s1; DEALLOCATE PREPARE s1;

SET @stmt := IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'kx_model_items' AND column_name = 'supported_parameters_json'), 'ALTER TABLE kx_model_items DROP COLUMN supported_parameters_json', 'DO 0');
PREPARE s1 FROM @stmt; EXECUTE s1; DEALLOCATE PREPARE s1;

SET @stmt := IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'kx_model_items' AND column_name = 'default_parameters_json'), 'ALTER TABLE kx_model_items DROP COLUMN default_parameters_json', 'DO 0');
PREPARE s1 FROM @stmt; EXECUTE s1; DEALLOCATE PREPARE s1;

SET @stmt := IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'kx_model_items' AND column_name = 'supported_voices_json'), 'ALTER TABLE kx_model_items DROP COLUMN supported_voices_json', 'DO 0');
PREPARE s1 FROM @stmt; EXECUTE s1; DEALLOCATE PREPARE s1;

SET @stmt := IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'kx_model_items' AND column_name = 'knowledge_cutoff'), 'ALTER TABLE kx_model_items DROP COLUMN knowledge_cutoff', 'DO 0');
PREPARE s1 FROM @stmt; EXECUTE s1; DEALLOCATE PREPARE s1;

SET @stmt := IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'kx_model_items' AND column_name = 'expiration_date'), 'ALTER TABLE kx_model_items DROP COLUMN expiration_date', 'DO 0');
PREPARE s1 FROM @stmt; EXECUTE s1; DEALLOCATE PREPARE s1;

SET @stmt := IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'kx_model_items' AND column_name = 'model_family'), 'ALTER TABLE kx_model_items DROP COLUMN model_family', 'DO 0');
PREPARE s1 FROM @stmt; EXECUTE s1; DEALLOCATE PREPARE s1;

SET @stmt := IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'kx_model_items' AND column_name = 'last_seen_at'), 'ALTER TABLE kx_model_items DROP COLUMN last_seen_at', 'DO 0');
PREPARE s1 FROM @stmt; EXECUTE s1; DEALLOCATE PREPARE s1;

SET @stmt := IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'kx_model_items' AND column_name = 'capabilities_json'), 'ALTER TABLE kx_model_items DROP COLUMN capabilities_json', 'DO 0');
PREPARE s1 FROM @stmt; EXECUTE s1; DEALLOCATE PREPARE s1;

-- provider_code 放在最后：其索引已在第 1 步删除，避免 MariaDB 残留索引
SET @stmt := IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'kx_model_items' AND column_name = 'provider_code'), 'ALTER TABLE kx_model_items DROP COLUMN provider_code', 'DO 0');
PREPARE s1 FROM @stmt; EXECUTE s1; DEALLOCATE PREPARE s1;

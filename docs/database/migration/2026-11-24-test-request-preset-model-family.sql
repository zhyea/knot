-- ============================================================================
-- 2026-11-24 · 路由调试预设请求：归类维度由「统一模型」改为「模型族」
--
-- 变更：
--   新增  model_family  VARCHAR(64) NULL   模型族 code（ks_enum_configs.category=model_family 的 item_code）
--   新增  KEY idx_test_request_presets_family (model_family)
--   删除  KEY idx_test_request_presets_logical
--   删除  logical_model_code VARCHAR(128) NULL
--
-- 语义：model_family 为空 = 通用用例，任何模型族的调试目标都可选用；非空则只供同族目标选用。
--   分类口径与计费规则一致（`br.model_family = ? or br.model_family is null`），不引入第二套判据。
--
-- 存量：logical_model_code 只作归类展示，无业务后果，不迁移、不回填（铁律 11：本表无外部消费方）。
--   原统一模型 → 模型族的多对一映射也无法无损还原，硬迁移只会造出错误归类。
--
-- 幂等：列与索引都先查 information_schema 再决定是否 ALTER / DROP，可重复执行。
--   ⚠ 只覆盖 schema.sql 的 CREATE TABLE IF NOT EXISTS 补不上的存量库场景。
--
-- 上线顺序：先跑本脚本再发版。旧代码 SELECT/INSERT 显式引用 logical_model_code，
--   列被删后会直接报 Unknown column；反过来先发版则新代码找不到 model_family。
--   两者必须同批，脚本先行。
--
-- 回滚：不单独出回滚脚本。需要回到旧形态时手工加回 logical_model_code + idx_test_request_presets_logical，
--   已丢弃的归类值不可恢复（本就不影响业务）。
-- ============================================================================

SET @db := DATABASE();

-- model_family
SET @ddl := (
    SELECT IF(
        (SELECT COUNT(*) FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA = @db
            AND TABLE_NAME = 'kb_test_request_presets'
            AND COLUMN_NAME = 'model_family') = 0,
        'ALTER TABLE kb_test_request_presets ADD COLUMN model_family VARCHAR(64) DEFAULT NULL COMMENT ''模型族 code（ks_enum_configs.category=model_family 的 item_code），可空：空表示通用用例，任何模型族都可选用'' AFTER protocol_code',
        'DO 0')
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- idx_test_request_presets_family
SET @ddl := (
    SELECT IF(
        (SELECT COUNT(*) FROM information_schema.STATISTICS
          WHERE TABLE_SCHEMA = @db
            AND TABLE_NAME = 'kb_test_request_presets'
            AND INDEX_NAME = 'idx_test_request_presets_family') = 0,
        'ALTER TABLE kb_test_request_presets ADD KEY idx_test_request_presets_family (model_family)',
        'DO 0')
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- drop old index idx_test_request_presets_logical
SET @ddl := (
    SELECT IF(
        (SELECT COUNT(*) FROM information_schema.STATISTICS
          WHERE TABLE_SCHEMA = @db
            AND TABLE_NAME = 'kb_test_request_presets'
            AND INDEX_NAME = 'idx_test_request_presets_logical') > 0,
        'ALTER TABLE kb_test_request_presets DROP KEY idx_test_request_presets_logical',
        'DO 0')
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- drop old column logical_model_code
SET @ddl := (
    SELECT IF(
        (SELECT COUNT(*) FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA = @db
            AND TABLE_NAME = 'kb_test_request_presets'
            AND COLUMN_NAME = 'logical_model_code') > 0,
        'ALTER TABLE kb_test_request_presets DROP COLUMN logical_model_code',
        'DO 0')
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

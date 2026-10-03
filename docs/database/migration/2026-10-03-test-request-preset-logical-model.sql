-- 预设请求用例增加统一模型维度（2026-10-03）
-- 迁移版本：2026-10-03-test-request-preset-logical-model
--
-- 【语义】
-- kb_test_request_presets 增加 logical_model_code 列（按业务码绑定，非主键 id），
-- 用于把一条预设请求用例归类到某个统一模型（kb_logical_models.model_code）。
-- 该维度**可空**：留空表示通用用例，任何协议/模型都能用；填了则仅作归类，
-- 不参与调试面板「预设请求」下拉的过滤，也不覆盖请求体的 model 字段
--   （model 仍由调试面板按当前调试目标覆盖，见 RoutingRuleTestDrawer.syncModelInto）。
--   应用侧同步变更：Entity/Dto/VO 增加 logicalModelCode（+ 派生 logicalModelName）；
--   TestRequestPresetFormDrawer 增加可清空的「统一模型」下拉。
--
-- 幂等性：全部 DDL 用 information_schema 判定 + PREPARE 条件式执行，可重复跑。
--         全新实例以更新后的 schema.sql 建库，本列已就位，无需执行本脚本。
--         既有库必须先跑本脚本再启应用（否则 dataSourceScriptDatabaseInitializer
--         与 contextLoads 会因列缺失/实体不匹配失败）。

-- ---- 0. 执行前快照（非执行语句）----
-- SELECT id, code, protocol_code FROM kb_test_request_presets ORDER BY id;

-- ---- 1. kb_test_request_presets 增加 logical_model_code（存在则跳过）----
SET @ddl := IF(
    EXISTS (SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_test_request_presets'
              AND COLUMN_NAME = 'logical_model_code'),
    'DO 0',
    'ALTER TABLE kb_test_request_presets ADD COLUMN logical_model_code VARCHAR(128) NULL COMMENT ''统一模型 code（kb_logical_models.model_code），可空，仅作归类'' AFTER protocol_code');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---- 2. 重建索引 idx_test_request_presets_logical（存在则跳过）----
SET @ddl := IF(
    EXISTS (SELECT 1 FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_test_request_presets'
              AND INDEX_NAME = 'idx_test_request_presets_logical'),
    'DO 0',
    'ALTER TABLE kb_test_request_presets ADD KEY idx_test_request_presets_logical (logical_model_code)');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---- 3. 执行后核对 ----
-- 3.1 列就位且可空
-- SELECT COLUMN_NAME, IS_NULLABLE, COLUMN_TYPE FROM information_schema.COLUMNS
--  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_test_request_presets'
--    AND COLUMN_NAME = 'logical_model_code';
-- 3.2 索引就位（预期 1 行）
-- SELECT INDEX_NAME, seq_in_index, column_name FROM information_schema.STATISTICS
--  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_test_request_presets'
--    AND INDEX_NAME = 'idx_test_request_presets_logical';
-- 3.3 既有行该列均为 NULL（通用用例，预期全为 NULL，应用侧按需手工归类）
-- SELECT id, code, logical_model_code FROM kb_test_request_presets ORDER BY id;

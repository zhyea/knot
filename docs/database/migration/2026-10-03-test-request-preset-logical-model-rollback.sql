-- 回滚：预设请求用例统一模型维度（2026-10-03）
-- 对应正向脚本：2026-10-03-test-request-preset-logical-model.sql
--
-- 定位：本项目**无历史包袱**（新库新代码，不兼容历史数据），故回滚只做 DDL 逆向，
--       不做数据回填/默认值兜底。
--
-- ⚠ 回滚会**丢失全部预设与统一模型的归类关系**。
-- ⚠ 回滚后应用侧仍按新逻辑运行（Entity/Dto/VO 持 logicalModelCode），需同步回滚
--   代码与 schema.sql 才能一致。

-- ---- 0. 执行前核查（非执行语句）----
-- SELECT id, code, logical_model_code FROM kb_test_request_presets ORDER BY id;

-- ---- 1. 删除 kb_test_request_presets.logical_model_code（连带删除同名索引）----
SET @ddl := IF(
    EXISTS (SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_test_request_presets'
              AND COLUMN_NAME = 'logical_model_code'),
    'ALTER TABLE kb_test_request_presets DROP COLUMN logical_model_code',
    'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---- 2. 执行后核对 ----
-- 2.1 logical_model_code 已不存在（预期 0 行）
-- SELECT COLUMN_NAME FROM information_schema.COLUMNS
--  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_test_request_presets'
--    AND COLUMN_NAME = 'logical_model_code';
-- 2.2 索引已不存在（预期 0 行）
-- SELECT INDEX_NAME FROM information_schema.STATISTICS
--  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_test_request_presets'
--    AND INDEX_NAME = 'idx_test_request_presets_logical';

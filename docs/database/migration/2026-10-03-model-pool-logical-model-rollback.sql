-- 回滚：模型池绑定统一模型（2026-10-03）
-- 对应正向脚本：2026-10-03-model-pool-logical-model.sql
--
-- 定位：本项目**无历史包袱**（新库新代码，不兼容历史数据），故回滚只做 DDL 逆向，
--       不做数据回填/默认值兜底。恢复 model_type 列时全部取 NOT NULL DEFAULT 'CHAT'。
--
-- ⚠ 回滚 ①（删 logical_model_code）会**丢失池与统一模型的绑定关系**。
-- ⚠ 回滚 ②（恢复 model_type）会**丢失原有类型值** —— 删列时原值已丢弃。
-- ⚠ 回滚后应用侧仍按新逻辑运行（模型池按统一模型绑定），需同步回滚代码与
--   schema.sql / data.sql 才能一致。

-- ---- 0. 执行前核查（非执行语句）----
-- SELECT id, pool_code, logical_model_code FROM kb_model_pools ORDER BY id;

-- ---- 1. 删除 kb_model_pools.logical_model_code（连带删除 idx_model_pools_logical_status）----
SET @ddl := IF(
    EXISTS (SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_model_pools'
              AND COLUMN_NAME = 'logical_model_code'),
    'ALTER TABLE kb_model_pools DROP COLUMN logical_model_code',
    'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---- 2. 恢复 kb_model_pools.model_type 列（不存在才加），全部取默认值 ----
SET @ddl := IF(
    EXISTS (SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_model_pools'
              AND COLUMN_NAME = 'model_type'),
    'DO 0',
    "ALTER TABLE kb_model_pools ADD COLUMN model_type VARCHAR(64) NOT NULL DEFAULT 'CHAT' AFTER name");
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---- 3. 恢复索引 idx_model_pools_type_status（存在则跳过）----
SET @ddl := IF(
    EXISTS (SELECT 1 FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_model_pools'
              AND INDEX_NAME = 'idx_model_pools_type_status'),
    'DO 0',
    'ALTER TABLE kb_model_pools ADD KEY idx_model_pools_type_status (model_type, status)');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---- 4. 执行后核对 ----
-- 4.1 logical_model_code 已不存在，model_type 已恢复（值全为默认 CHAT）
-- SELECT COLUMN_NAME, IS_NULLABLE FROM information_schema.COLUMNS
--  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_model_pools'
--    AND COLUMN_NAME IN ('logical_model_code', 'model_type');
-- 4.2 两个索引各自就位（预期各 1）
-- SELECT INDEX_NAME FROM information_schema.STATISTICS
--  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_model_pools'
--    AND INDEX_NAME IN ('idx_model_pools_type_status', 'idx_model_pools_logical_status');

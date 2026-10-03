-- 回滚：路由规则去模型类型 + 统一模型/模型池逻辑删除（2026-10-03）
-- 对应正向脚本：2026-10-03-routing-drop-model-types.sql
--
-- 定位：本项目**无历史包袱**（新库新代码，不兼容历史数据），故回滚只做 DDL 逆向，
--       不做任何数据回填/默认值兜底。恢复 model_types 列时全部取 NOT NULL DEFAULT 'CHAT'。
--
-- ⚠ 回滚 ①（恢复 model_types）会**丢失原有数据** —— 删列时原值已丢弃。
-- ⚠ 回滚 ②③（删 is_deleted）会**使已逻辑删除的记录重新可见**，
--   执行前先确认没有行处于 is_deleted = 1，否则先物理清理这些行。
--   核查：SELECT COUNT(*) FROM kb_logical_models WHERE is_deleted = 1;
--         SELECT COUNT(*) FROM kb_model_pools     WHERE is_deleted = 1;
--
-- ⚠ 回滚后应用侧仍按新逻辑运行（不再读 model_types、统一模型/模型池改物理删除），
--   需同步回滚代码与 schema.sql / data.sql 才能一致。

-- ---- 0. 执行前核查（两条都应为 0，否则先处理再回滚）----
-- SELECT COUNT(*) FROM kb_logical_models WHERE is_deleted = 1;
-- SELECT COUNT(*) FROM kb_model_pools     WHERE is_deleted = 1;

-- ---- 1. 删除 kb_logical_models.is_deleted（存在才删）----
SET @ddl := IF(
    EXISTS (SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_logical_models'
              AND COLUMN_NAME = 'is_deleted'),
    'ALTER TABLE kb_logical_models DROP COLUMN is_deleted',
    'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---- 2. 删除 kb_model_pools.is_deleted（存在才删）----
SET @ddl := IF(
    EXISTS (SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_model_pools'
              AND COLUMN_NAME = 'is_deleted'),
    'ALTER TABLE kb_model_pools DROP COLUMN is_deleted',
    'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---- 3. 恢复 kb_routing_rules.model_types 列（不存在才加），全部取默认值 ----
SET @ddl := IF(
    EXISTS (SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_routing_rules'
              AND COLUMN_NAME = 'model_types'),
    'DO 0',
    "ALTER TABLE kb_routing_rules ADD COLUMN model_types VARCHAR(255) NOT NULL DEFAULT 'CHAT' AFTER app_scenario");
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---- 4. 执行后核对 ----
-- 4.1 model_types 已恢复（值全为默认 CHAT，未回填历史值）
-- SELECT rule_code, model_types FROM kb_routing_rules ORDER BY id;
-- 4.2 两张表 is_deleted 已不存在（预期 0）
-- SELECT COUNT(*) FROM information_schema.COLUMNS
--  WHERE TABLE_SCHEMA = DATABASE() AND COLUMN_NAME = 'is_deleted'
--    AND TABLE_NAME IN ('kb_logical_models','kb_model_pools');

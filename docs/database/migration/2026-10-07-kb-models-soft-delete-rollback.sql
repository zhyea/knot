-- 回滚：kb_models 逻辑删除列（2026-10-07）
-- 迁移版本：2026-10-07-kb-models-soft-delete-rollback
--
-- ⚠ 回滚前必须先处理已逻辑删除的模型：is_deleted = 1 的行在列被 DROP 后会重新出现在
-- 所有列表与网关路由里（因为无法再区分）。若存在这样的行，必须先物理删除或改回 0：
--   DELETE FROM kb_models WHERE is_deleted = 1;   -- 或
--   UPDATE kb_models SET is_deleted = 0 WHERE is_deleted = 1;
-- 核对：SELECT COUNT(*) FROM kb_models WHERE is_deleted = 1;  -- 期望 0 才可执行回滚

-- ---- 1. 删除 is_deleted 列（存在才删）----
SET @ddl := IF(
    EXISTS (SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_models'
              AND COLUMN_NAME = 'is_deleted'),
    'ALTER TABLE kb_models DROP COLUMN is_deleted',
    'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
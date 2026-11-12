-- ============================================================================
-- 2026-11-12 · 回滚「异步任务档案新增 usage_path」
--
-- ⚠⚠ 本脚本会**丢掉数据**：usage_path 一列一旦 DROP，已配好的用量表达式不可恢复。
--      只有在「这一列从没填过值」（即本次上线后没人配过异步档案的用量路径）时才直接回滚；
--      否则先把值导出来：
--
--        SELECT profile_code, usage_path
--        FROM kb_async_task_profiles
--        WHERE usage_path IS NOT NULL;
--
-- ⚠ 代码侧必须同时回滚：新版 AsyncTaskSpec / 档案 DTO / Entity / Mapper / AsyncTaskCompletion
--    都在读这一列。只回滚 DB 会让档案保存直接报「字段不存在」，管理面整页不可用。
--    回滚顺序：先停网关流量 → 回滚代码 → 跑本脚本。
--
-- 幂等性：先查 information_schema，列不存在时跳过。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1) 丢列（⚠ 数据丢失，见顶部警告）
-- ---------------------------------------------------------------------------
SET @has_usage_path = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME   = 'kb_async_task_profiles'
    AND COLUMN_NAME  = 'usage_path'
);

SET @ddl = IF(@has_usage_path > 0,
  'ALTER TABLE kb_async_task_profiles DROP COLUMN usage_path',
  'SELECT ''usage_path 不存在，跳过'' AS skipped');
PREPARE stmt_usage_path FROM @ddl;
EXECUTE stmt_usage_path;
DEALLOCATE PREPARE stmt_usage_path;

-- ---------------------------------------------------------------------------
-- 2) 回滚后自检（应为 0）
-- ---------------------------------------------------------------------------
SELECT 'CHECK usage_path 已移除（应为 0）' AS check_item, COUNT(*) AS columns_cnt
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME   = 'kb_async_task_profiles'
  AND COLUMN_NAME  = 'usage_path';

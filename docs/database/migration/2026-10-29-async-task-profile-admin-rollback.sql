-- ============================================================================
-- 2026-10-29 · 回滚「异步任务档案管理面」
--
-- ⚠⚠ 本脚本会**丢掉数据**：error_message_path / result_url_field 两列一旦 DROP，
--      已配好的失败原因表达式与产物字段名不可恢复。只有在「这两列从没填过值」
--      （即本次上线后没人配过视频档案）时才直接回滚；否则先把值导出来：
--
--        SELECT profile_code, result_path, result_url_field, error_message_path
--        FROM kb_async_task_profiles
--        WHERE result_url_field IS NOT NULL OR error_message_path IS NOT NULL;
--
-- ⚠ 代码侧必须同时回滚：新版 AsyncTaskSpec / 档案 DTO / 前端抽屉都在读这两列。
--   只回滚 DB 会让档案保存直接报「字段不存在」，管理面整页不可用。
--   回滚顺序：先停网关流量 → 回滚代码 → 跑本脚本。
--
-- ⚠ 菜单/权限/绑定可以安全删除：它们只是授权行，删掉后接口回到「默认拒绝」，
--   但代码若还是新版，访问档案接口会 403 —— 这本身是可接受的中间态（比放行更安全）。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1) 撤销授权（顺序：绑定 → 角色授权 → 权限 → 菜单）
-- ---------------------------------------------------------------------------
DELETE FROM ks_api_permission_bindings WHERE controller_class = 'AsyncTaskProfileController';

DELETE rp FROM ks_role_permissions rp
  JOIN ks_permissions p ON p.id = rp.permission_id
WHERE p.permission_code LIKE 'routing:async-task-profile:%';

DELETE FROM ks_permissions WHERE permission_code LIKE 'routing:async-task-profile:%';

DELETE FROM ks_menus WHERE menu_code = 'routing.async-task-profiles';

-- ---------------------------------------------------------------------------
-- 2) 丢列（⚠ 数据丢失，见顶部警告）
-- ---------------------------------------------------------------------------
SET @has_error_message_path = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME   = 'kb_async_task_profiles'
    AND COLUMN_NAME  = 'error_message_path'
);
SET @ddl = IF(@has_error_message_path > 0,
  'ALTER TABLE kb_async_task_profiles DROP COLUMN error_message_path',
  'SELECT ''error_message_path 不存在，跳过'' AS skipped);
PREPARE stmt_error_message_path FROM @ddl;
EXECUTE stmt_error_message_path;
DEALLOCATE PREPARE stmt_error_message_path;

SET @has_result_url_field = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME   = 'kb_async_task_profiles'
    AND COLUMN_NAME  = 'result_url_field'
);
SET @ddl = IF(@has_result_url_field > 0,
  'ALTER TABLE kb_async_task_profiles DROP COLUMN result_url_field',
  'SELECT ''result_url_field 不存在，跳过'' AS skipped);
PREPARE stmt_result_url_field FROM @ddl;
EXECUTE stmt_result_url_field;
DEALLOCATE PREPARE stmt_result_url_field;

-- ---------------------------------------------------------------------------
-- 3) 回滚后自检（三项都应为 0）
-- ---------------------------------------------------------------------------
SELECT 'CHECK-1 两列已移除（应为 0）' AS check_item, COUNT(*) AS columns_cnt
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME   = 'kb_async_task_profiles'
  AND COLUMN_NAME IN ('error_message_path', 'result_url_field');

SELECT 'CHECK-2 权限已移除（应为 0）' AS check_item, COUNT(*) AS permissions_cnt
FROM ks_permissions
WHERE permission_code LIKE 'routing:async-task-profile:%';

SELECT 'CHECK-3 绑定已移除（应为 0）' AS check_item, COUNT(*) AS bindings_cnt
FROM ks_api_permission_bindings
WHERE controller_class = 'AsyncTaskProfileController';

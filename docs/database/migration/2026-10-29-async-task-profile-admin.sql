-- ============================================================================
-- 2026-10-29 · 异步任务档案管理面（对接视频协议用）
--
-- 两件事：
--   1) kb_async_task_profiles 补两列 —— 对接可灵 / 方舟这类视频上游时立刻会撞上的两个缺口：
--      · error_message_path：失败原因不总在顶层 message。可灵在 data.task_status_msg，
--        方舟在 error.message。没有这一列，终端失败的任务永远只有一句
--        「上游返回失败」，排障时得自己去翻上游控制台。
--      · result_url_field：可灵一次生成 n 个视频（data.task_result.videos[].url）。
--        原来的 result_path 只能吐一个标量地址，n>1 时要么丢产物、要么让人把数组
--        拼成字符串（MVEL 的 `videos.![url]` 在 2.5.2 上直接抛 PropertyAccessException，
--        详见 docs/异步任务档案对接指南-2026-10-29.md）。改成「result_path 指向数组 +
--        result_url_field 指定取对象里的哪个字段」，两列各管一件事。
--   2) 菜单 / 权限 / 接口绑定：菜单挂在「路由规则」模块下（module 3, sort 18），
--      紧贴「异步任务」实例页 —— 排查顺序是「任务 → 当时用的档案 → 口径写错没」。
--
-- 幂等性：
--   - 加列先查 information_schema，重复执行跳过（不靠 ADD COLUMN IF NOT EXISTS，
--     MySQL 5.7 不支持）；
--   - 种子全部 INSERT IGNORE + 显式 id（与 db/data.sql 完全一致的 id）：
--     重启时 data.sql 也插同一批 id，先到者生效，不会产生第二份；
--   - 角色授权按 permission_code 关联，不依赖硬编码权限 id。
--
-- 存量语义：两列都可空。老档案的 error_message_path 为空 → 沿用上游顶层 message（原行为）；
-- result_url_field 为空 → 元素必须是标量（原行为）。不写数据迁移，不动在途任务。
--
-- 回滚：见同目录 2026-10-29-async-task-profile-admin-rollback.sql
--   ⚠ 回滚会丢掉已配置的失败原因表达式与产物字段名，只在「本次上线完全没填过这两列」时安全。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1) kb_async_task_profiles.error_message_path
-- ---------------------------------------------------------------------------
SET @has_error_message_path = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME   = 'kb_async_task_profiles'
    AND COLUMN_NAME  = 'error_message_path'
);

SET @ddl = IF(@has_error_message_path = 0,
  'ALTER TABLE kb_async_task_profiles ADD COLUMN error_message_path VARCHAR(255) DEFAULT NULL COMMENT ''失败原因的 MVEL 表达式（如 data.task_status_msg）；空表示沿用上游顶层 message'' AFTER result_path',
  'SELECT ''error_message_path 已存在，跳过'' AS skipped);
PREPARE stmt_error_message_path FROM @ddl;
EXECUTE stmt_error_message_path;
DEALLOCATE PREPARE stmt_error_message_path;

-- ---------------------------------------------------------------------------
-- 2) kb_async_task_profiles.result_url_field
-- ---------------------------------------------------------------------------
SET @has_result_url_field = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME   = 'kb_async_task_profiles'
    AND COLUMN_NAME  = 'result_url_field'
);

SET @ddl = IF(@has_result_url_field = 0,
  'ALTER TABLE kb_async_task_profiles ADD COLUMN result_url_field VARCHAR(64) DEFAULT NULL COMMENT ''result_path 返回对象数组时，取对象的哪个字段作为产物地址（如 url）；空表示元素必须是标量'' AFTER result_path',
  'SELECT ''result_url_field 已存在，跳过'' AS skipped);
PREPARE stmt_result_url_field FROM @ddl;
EXECUTE stmt_result_url_field;
DEALLOCATE PREPARE stmt_result_url_field;

-- ---------------------------------------------------------------------------
-- 3) 菜单：异步任务（sort 15）之后
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO ks_menus (id, module_id, parent_id, menu_code, menu_name, route_path, component_key, icon, sort_order, status) VALUES
(26, 3, NULL, 'routing.async-task-profiles', '异步任务档案', '/routing/async-task-profiles', 'routing/AsyncTaskProfileView', 'Setting', 18, 1);

-- ---------------------------------------------------------------------------
-- 4) 权限：只读与写动作分开登记
--    （改一条档案会让所有引用它的模型换口径，风险等级不同于「看任务列表」）
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO ks_permissions (id, permission_code, permission_name, permission_type, module_id, menu_id, status, built_in, remark) VALUES
(159, 'routing:async-task-profile:page', '异步任务档案页面访问', 'PAGE', 3, 26, 1, 1, NULL),
(160, 'routing:async-task-profile:view', '异步任务档案查看', 'API', 3, 26, 1, 1, NULL),
(161, 'routing:async-task-profile:save', '异步任务档案保存', 'API', 3, 26, 1, 1, NULL),
(162, 'routing:async-task-profile:delete', '异步任务档案删除', 'API', 3, 26, 1, 1, NULL);

-- ---------------------------------------------------------------------------
-- 5) 接口绑定（默认拒绝：漏一条就是运行期 403）
--    ⚠ /options 走 view：模型表单里的「任务协议」下拉要读它；
--      试跑只做表达式求值、不落库，风险等级与查看相同。
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO ks_api_permission_bindings (id, permission_id, http_method, path_pattern, controller_class, status) VALUES
(222, 160, 'POST', '/api/system/async-task-profiles/list', 'AsyncTaskProfileController', 1),
(223, 160, 'POST', '/api/system/async-task-profiles/options', 'AsyncTaskProfileController', 1),
(224, 160, 'GET', '/api/system/async-task-profiles/{profileCode}', 'AsyncTaskProfileController', 1),
(225, 160, 'POST', '/api/system/async-task-profiles/dry-run', 'AsyncTaskProfileController', 1),
(226, 161, 'POST', '/api/system/async-task-profiles/save', 'AsyncTaskProfileController', 1),
(227, 162, 'POST', '/api/system/async-task-profiles/delete/{profileCode}', 'AsyncTaskProfileController', 1);

-- ---------------------------------------------------------------------------
-- 6) 角色授权：OPERATOR 全量，DEVELOPER 只读，ADMIN 全量（按编码关联）
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO ks_role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM ks_roles r
  CROSS JOIN ks_permissions p
WHERE r.code IN ('OPERATOR', 'DEVELOPER')
  AND p.permission_code LIKE 'routing:async-task-profile:%'
  AND (r.code = 'OPERATOR'
       OR p.permission_code IN ('routing:async-task-profile:page', 'routing:async-task-profile:view'));

INSERT IGNORE INTO ks_role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM ks_roles r
  CROSS JOIN ks_permissions p
WHERE r.code = 'ADMIN';

-- ---------------------------------------------------------------------------
-- 7) 迁移后自检
-- ---------------------------------------------------------------------------
SELECT 'CHECK-1 新增两列（应为 2）' AS check_item, COUNT(*) AS columns_cnt
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME   = 'kb_async_task_profiles'
  AND COLUMN_NAME IN ('error_message_path', 'result_url_field');

SELECT 'CHECK-2 权限就位（应为 4）' AS check_item, COUNT(*) AS permissions_cnt
FROM ks_permissions
WHERE permission_code LIKE 'routing:async-task-profile:%';

SELECT 'CHECK-3 绑定就位（应为 6）' AS check_item, COUNT(*) AS bindings_cnt
FROM ks_api_permission_bindings
WHERE controller_class = 'AsyncTaskProfileController';

SELECT 'CHECK-4 菜单就位（应为 1）' AS check_item, COUNT(*) AS menus_cnt
FROM ks_menus
WHERE menu_code = 'routing.async-task-profiles';

SELECT 'CHECK-5 DEVELOPER 只读（应为 2）' AS check_item, COUNT(*) AS developer_cnt
FROM ks_role_permissions rp
  JOIN ks_roles r ON r.id = rp.role_id
  JOIN ks_permissions p ON p.id = rp.permission_id
WHERE r.code = 'DEVELOPER'
  AND p.permission_code LIKE 'routing:async-task-profile:%';

SELECT 'CHECK-6 档案列结构' AS check_item, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME   = 'kb_async_task_profiles'
  AND COLUMN_NAME IN ('result_path', 'result_url_field', 'error_message_path')
ORDER BY ORDINAL_POSITION;

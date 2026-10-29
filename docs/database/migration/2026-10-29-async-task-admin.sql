-- ============================================================================
-- 2026-10-29 · 异步任务管理面（knot-admin 查看 + 终止 / 重试 / 清理）
--
-- 两件事：
--   1) kb_async_tasks 新增 retry_round —— 管理端「重试」会产生第二轮轮询，
--      attempts 归零后 task_query_seq 从 1 重新计数，与上一轮在事件流里完全同键
--      （task_code + task_phase + task_query_seq）。没有轮次字段，数仓按该键去重时
--      会把两次真实上游调用压成一条 —— 方向是「少记」，事后无从还原。
--   2) 菜单 / 权限 / 接口绑定：菜单挂在「路由规则」下面（module 3, sort 15），
--      因为一条异步任务就是某条规则下的一次调用，排查路径是「规则 → 任务」。
--
-- 幂等性：
--   - 加列先查 information_schema，重复执行跳过；
--   - 种子全部 INSERT IGNORE + 显式 id（与 db/data.sql 完全一致的 id）：
--     重启时 data.sql 也插同一批 id，先到者生效，不会产生第二份；
--   - 角色授权按 permission_code 关联，不依赖硬编码权限 id。
--
-- 存量语义：新列默认 0（从未重试），历史任务行为不变；不停机、可带流量执行。
-- ============================================================================

-- ============================================================================
-- ⚠⚠ 交叉影响 · 与「回调交付」的冲突（详见 2026-10-29-async-task-callback.sql 顶部交接标记）
--
-- 本脚本只加 retry_round + 菜单/权限种子，两份脚本的 id 不冲突（回调那份是纯 DDL）。
-- 但回调能力上线后，本管理面的两个写操作在 AsyncTaskMapper.xml 里会出错：
--   1) deleteCompletedBefore 不看交付维度 → 删掉「已终态、回调未送达」的行，客户端静默丢结果；
--   2) reopen 不复位交付六字段 → 同一任务被回调两次，其中一条发的是已清空的 result_json。
-- 两处都是改同一个 AsyncTaskMapper.xml，必须等回调侧收口后一次性改，改法见上述交接标记。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1) kb_async_tasks.retry_round
-- ---------------------------------------------------------------------------
SET @has_retry_round = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME   = 'kb_async_tasks'
    AND COLUMN_NAME  = 'retry_round'
);

SET @ddl = IF(@has_retry_round = 0,
  'ALTER TABLE kb_async_tasks ADD COLUMN retry_round INT NOT NULL DEFAULT 0 COMMENT ''管理端重试轮次，0=从未重试；重试后事件流靠它区分轮次'' AFTER attempts',
  'SELECT ''retry_round 已存在，跳过'' AS skipped');
PREPARE stmt_retry_round FROM @ddl;
EXECUTE stmt_retry_round;
DEALLOCATE PREPARE stmt_retry_round;

-- 顺带把 attempts 的注释对齐 data.sql（口径变了：attempts 现在按「本轮」计，重试时归零）
ALTER TABLE kb_async_tasks
  MODIFY COLUMN attempts INT NOT NULL DEFAULT 0
    COMMENT '已轮询次数（按「本轮」计，管理端重试时归零）';

-- ---------------------------------------------------------------------------
-- 2) 菜单：路由规则（sort 10）之后
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO ks_menus (id, module_id, parent_id, menu_code, menu_name, route_path, component_key, icon, sort_order, status) VALUES
(25, 3, NULL, 'routing.async-tasks', '异步任务', '/routing/async-tasks', 'routing/AsyncTaskView', 'Timer', 15, 1);

-- ---------------------------------------------------------------------------
-- 3) 权限：查看与三个写动作分开登记（「能看」≠「能终止别人的在途任务」）
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO ks_permissions (id, permission_code, permission_name, permission_type, module_id, menu_id, status, built_in, remark) VALUES
(154, 'routing:async-task:page', '异步任务页面访问', 'PAGE', 3, 25, 1, 1, NULL),
(155, 'routing:async-task:view', '异步任务查看', 'API', 3, 25, 1, 1, NULL),
(156, 'routing:async-task:cancel', '异步任务终止', 'API', 3, 25, 1, 1, NULL),
(157, 'routing:async-task:retry', '异步任务重试', 'API', 3, 25, 1, 1, NULL),
(158, 'routing:async-task:cleanup', '异步任务清理', 'API', 3, 25, 1, 1, NULL);

-- ---------------------------------------------------------------------------
-- 4) 接口绑定（默认拒绝：漏一条就是运行期 403）
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO ks_api_permission_bindings (id, permission_id, http_method, path_pattern, controller_class, status) VALUES
(217, 155, 'POST', '/api/routing/async-tasks/list', 'AsyncTaskController', 1),
(218, 155, 'GET', '/api/routing/async-tasks/{taskCode}', 'AsyncTaskController', 1),
(219, 156, 'POST', '/api/routing/async-tasks/{taskCode}/cancel', 'AsyncTaskController', 1),
(220, 157, 'POST', '/api/routing/async-tasks/{taskCode}/retry', 'AsyncTaskController', 1),
(221, 158, 'POST', '/api/routing/async-tasks/cleanup', 'AsyncTaskController', 1);

-- ---------------------------------------------------------------------------
-- 5) 角色授权：OPERATOR 全量，DEVELOPER 只读，ADMIN 全量（按编码关联）
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO ks_role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM ks_roles r
  CROSS JOIN ks_permissions p
WHERE r.code IN ('OPERATOR', 'DEVELOPER')
  AND p.permission_code LIKE 'routing:async-task:%'
  AND (r.code = 'OPERATOR' OR p.permission_code IN ('routing:async-task:page', 'routing:async-task:view'));

INSERT IGNORE INTO ks_role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM ks_roles r
  CROSS JOIN ks_permissions p
WHERE r.code = 'ADMIN';

-- ---------------------------------------------------------------------------
-- 6) 迁移后自检
-- ---------------------------------------------------------------------------
SELECT 'CHECK-1 retry_round 列' AS check_item, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME   = 'kb_async_tasks'
  AND COLUMN_NAME  = 'retry_round';

SELECT 'CHECK-2 权限就位（应为 5）' AS check_item, COUNT(*) AS permissions_cnt
FROM ks_permissions
WHERE permission_code LIKE 'routing:async-task:%';

SELECT 'CHECK-3 绑定就位（应为 5）' AS check_item, COUNT(*) AS bindings_cnt
FROM ks_api_permission_bindings
WHERE controller_class = 'AsyncTaskController';

SELECT 'CHECK-4 菜单就位（应为 1）' AS check_item, COUNT(*) AS menus_cnt
FROM ks_menus
WHERE menu_code = 'routing.async-tasks';

SELECT 'CHECK-5 DEVELOPER 只读（应为 2）' AS check_item, COUNT(*) AS developer_cnt
FROM ks_role_permissions rp
  JOIN ks_roles r ON r.id = rp.role_id
  JOIN ks_permissions p ON p.id = rp.permission_id
WHERE r.code = 'DEVELOPER'
  AND p.permission_code LIKE 'routing:async-task:%';

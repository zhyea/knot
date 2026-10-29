-- ============================================================================
-- 2026-10-30 · 回滚：异步任务档案菜单 / 权限搬回「路由管理」
--
-- 对应正向脚本：2026-10-30-async-task-profile-menu-to-model.sql
-- ⚠ 只回滚「归属」（模块、路由、编码前缀），不删任何数据、不动角色授权与接口绑定
--   （它们按 permission_id 关联，编码改回去之后照旧生效）。
-- 回滚后前端必须同步回到 /routing/async-task-profiles 的路由，否则菜单点开 404。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1) 菜单：回到路由模块
-- ---------------------------------------------------------------------------
UPDATE ks_menus
SET module_id     = 3,
    menu_code     = 'routing.async-task-profiles',
    route_path    = '/routing/async-task-profiles',
    component_key = 'routing/AsyncTaskProfileView',
    sort_order    = 18
WHERE id = 26;

-- ---------------------------------------------------------------------------
-- 2) 权限：编码前缀与模块回到 routing
-- ---------------------------------------------------------------------------
UPDATE ks_permissions
SET permission_code = CASE id
      WHEN 159 THEN 'routing:async-task-profile:page'
      WHEN 160 THEN 'routing:async-task-profile:view'
      WHEN 161 THEN 'routing:async-task-profile:save'
      WHEN 162 THEN 'routing:async-task-profile:delete'
    END,
    module_id = 3
WHERE id IN (159, 160, 161, 162);

-- ---------------------------------------------------------------------------
-- 3) 回滚后自检
-- ---------------------------------------------------------------------------
SELECT 'CHECK-1 菜单回到路由模块（应为 1）' AS check_item, COUNT(*) AS menus_cnt
FROM ks_menus
WHERE id = 26
  AND module_id = 3
  AND route_path = '/routing/async-task-profiles';

SELECT 'CHECK-2 旧编码恢复（应为 4）' AS check_item, COUNT(*) AS permissions_cnt
FROM ks_permissions
WHERE permission_code LIKE 'routing:async-task-profile:%'
  AND module_id = 3;

SELECT 'CHECK-3 新编码残留（应为 0）' AS check_item, COUNT(*) AS leftover_cnt
FROM ks_permissions
WHERE permission_code LIKE 'model:async-task-profile:%';

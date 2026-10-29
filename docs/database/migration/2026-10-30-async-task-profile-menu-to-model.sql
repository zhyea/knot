-- ============================================================================
-- 2026-10-30 · 异步任务档案：菜单与权限从「路由管理」搬到「模型管理」
--
-- 为什么搬：
--   档案（kb_async_task_profiles 一行 = 一套「怎么查上游」的口径）唯一的消费方是
--   **模型绑定**——模型表单里的「任务协议」下拉选的就是它，配一条档案等价于给某个
--   模型定口径。放在路由管理下时，配模型的人要在两个模块之间来回跳。
--   异步任务**实例**页（/routing/async-tasks）不动：那是运行期的运维视图，排查路径
--   是「路由规则 → 任务 → 当时用的档案」，仍属路由域。
--
-- 改什么：
--   1) ks_menus id=26：module_id 3→2，menu_code routing.async-task-profiles
--      → model.async-task-profiles，route_path → /model-management/async-task-profiles，
--      component_key → AsyncTaskProfileView（去掉 routing/ 前缀，与模型模块其它菜单同形），
--      sort_order 18→80（模型模块末尾）。
--   2) ks_permissions id 159~162：permission_code 前缀 routing: → model:，module_id 3→2。
--      ⚠ 只改编码不改 id —— ks_api_permission_bindings / ks_role_permissions 都按
--      permission_id 关联，id 一动就是全站 403。
--
-- ⚠ 为什么必须写这份脚本（不能只改 data.sql）：
--   data.sql 全是 INSERT IGNORE + 显式 id，存量库里 id=26 / 159~162 已存在 → 启动时会
--   被静默跳过，改 data.sql 只在全新库生效。存量库只能靠这份 UPDATE。
--   幂等：UPDATE 按 id 定位，重复执行结果一致；全新库上跑是 no-op（data.sql 已插新值）。
--
-- 不改什么：
--   · 后端接口路径 /api/system/async-task-profiles 不变 —— 档案仍是系统级配置
--     （改错会把所有引用它的模型带偏），接口前缀与菜单归属是两件事；
--   · 6 条 ks_api_permission_bindings（按 permission_id，无需动）；
--   · ks_role_permissions（按 permission_id，无需动）。
--
-- 回滚：见同目录 2026-10-30-async-task-profile-menu-to-model-rollback.sql
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1) 菜单：挪到模型模块
-- ---------------------------------------------------------------------------
UPDATE ks_menus
SET module_id     = 2,
    menu_code     = 'model.async-task-profiles',
    route_path    = '/model-management/async-task-profiles',
    component_key = 'AsyncTaskProfileView',
    sort_order    = 80
WHERE id = 26;

-- ---------------------------------------------------------------------------
-- 2) 权限：编码前缀 + 模块归属（id 保持不变）
-- ---------------------------------------------------------------------------
UPDATE ks_permissions
SET permission_code = CASE id
      WHEN 159 THEN 'model:async-task-profile:page'
      WHEN 160 THEN 'model:async-task-profile:view'
      WHEN 161 THEN 'model:async-task-profile:save'
      WHEN 162 THEN 'model:async-task-profile:delete'
    END,
    module_id = 2
WHERE id IN (159, 160, 161, 162);

-- ---------------------------------------------------------------------------
-- 3) 迁移后自检
-- ---------------------------------------------------------------------------
SELECT 'CHECK-1 菜单归属模型模块（应为 1）' AS check_item, COUNT(*) AS menus_cnt
FROM ks_menus
WHERE id = 26
  AND module_id = 2
  AND menu_code = 'model.async-task-profiles'
  AND route_path = '/model-management/async-task-profiles';

SELECT 'CHECK-2 权限编码前缀（应为 4）' AS check_item, COUNT(*) AS permissions_cnt
FROM ks_permissions
WHERE permission_code LIKE 'model:async-task-profile:%'
  AND module_id = 2;

SELECT 'CHECK-3 旧编码残留（应为 0）' AS check_item, COUNT(*) AS legacy_cnt
FROM ks_permissions
WHERE permission_code LIKE 'routing:async-task-profile:%';

SELECT 'CHECK-4 接口绑定未丢（应为 6）' AS check_item, COUNT(*) AS bindings_cnt
FROM ks_api_permission_bindings
WHERE controller_class = 'AsyncTaskProfileController';

SELECT 'CHECK-5 角色授权未丢（OPERATOR 4 / DEVELOPER 2）' AS check_item,
       r.code AS role_code,
       COUNT(*) AS granted_cnt
FROM ks_role_permissions rp
  JOIN ks_roles r ON r.id = rp.role_id
  JOIN ks_permissions p ON p.id = rp.permission_id
WHERE p.permission_code LIKE 'model:async-task-profile:%'
GROUP BY r.code
ORDER BY r.code;

SELECT 'CHECK-6 模型模块菜单顺序' AS check_item, m.id, m.menu_code, m.sort_order, m.route_path
FROM ks_menus m
WHERE m.module_id = 2
ORDER BY m.sort_order, m.id;

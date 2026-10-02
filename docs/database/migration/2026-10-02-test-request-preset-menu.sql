-- 2026-10-02 补齐「预设请求」页面菜单项（data.sql 漏加）
-- 背景：TestRequestPresetView（/routing/presets）上线时只加了 API 权限（128-132，
--       且误挂在 menu_id=11 路由规则名下），未加 ks_menus 行与 PAGE 权限，
--       导致侧边栏（listMenusByUserId 按 PAGE 权限联查 ks_menus）不显示该页面。
-- 说明：新增行 data.sql 启动重放（INSERT IGNORE）即可自动补齐；本脚本额外处理
--       存量权限的 menu_id 归位（INSERT IGNORE 不会更新已存在行）。

-- 1) 菜单：路由管理 → 预设请求
INSERT IGNORE INTO ks_menus (id, module_id, parent_id, menu_code, menu_name, route_path, component_key, icon, sort_order, status) VALUES
(22, 3, NULL, 'routing.presets', '预设请求', '/routing/presets', 'routing/TestRequestPresetView', 'Share', 30, 'ENABLED');

-- 2) PAGE 权限（菜单可见性依赖 PAGE 权限 → menu_id 联查）
INSERT IGNORE INTO ks_permissions (id, permission_code, permission_name, permission_type, module_id, menu_id, status, built_in, remark) VALUES
(133, 'routing:preset:page', '预设请求页面访问', 'PAGE', 3, 22, 'ENABLED', 1, NULL);

-- 3) 存量 API 权限归位到新菜单（幂等，可重复执行）
UPDATE ks_permissions SET menu_id = 22 WHERE id BETWEEN 128 AND 132;

-- 4) 角色授权：OPERATOR 全量 / DEVELOPER 只读（ADMIN 由 data.sql 末尾权威块全量授予）
INSERT IGNORE INTO ks_role_permissions (role_id, permission_id) VALUES
(2, 133),
(3, 133);

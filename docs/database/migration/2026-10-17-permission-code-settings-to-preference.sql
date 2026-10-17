-- ============================================================
-- 2026-10-17 · 权限 code 关键字分工：用户设置 = preference，系统设置 = settings
--
-- ⚠ 前置依赖：必须先跑 `2026-10-17-system-settings.sql`（建 ks_system_settings 表）。
--   否则重启时 data.sql 会在 ks_system_settings 的种子 INSERT 处失败，
--   其后的「权威授权块」不会执行 → 新权限虽存在却没授给 ADMIN，管理端照样 403。
--   （实测：mysql 批处理遇首错即中断，后续语句全部不执行，且容易被打包进 2>/dev/null 里看不见。）
--
-- 背景：robin 2026-10-17 拍板「settings」关键字专归「系统设置」，
--       「用户设置」改用 preference。原实现（未提交的中间态）把系统设置临时
--       命名成 system:platform-settings:* 以避开撞名，本脚本把它归位。
--
-- 为什么必须跑这个脚本（不能只重跑 data.sql）：
--       ks_permissions.permission_code 有唯一键 uk_sys_permissions_code，
--       而 data.sql 用 INSERT IGNORE。若「用户设置」三行仍占着
--       system:settings:{page,update,view}，重跑 data.sql 时新插入的
--       system:settings:{view,update} 会被唯一键拦掉并被 IGNORE 静默跳过，
--       于是 ks_api_permission_bindings 的 212/213 指向不存在的权限 id
--       （该列无外键）→ 接口永久 403 且无任何报错。
--       **必须先改名腾出关键字，再让 data.sql 补新行。**
--       实测对照（不跑本脚本直接上新版 data.sql）：
--         perm_count 151（少了 154/155）、codes_16_17_18 仍为 system:settings:*、
--         orphan_bindings = 2（212/213 指向不存在的 154/155）→ 正是上述 403 场景。
--
-- 幂等性：全部 UPDATE 按「旧 code → 新 code」定向改写，重复执行为 0 行影响；
--         INSERT IGNORE 靠主键 + 唯一键兜底。三种历史状态（从未落过
--         platform-settings / 已落过 / 已跑过本脚本）都收敛到同一终态。
--
-- 影响面：只改 ks_permissions 的 permission_code 文本列。ks_role_permissions
--         按 permission_id 关联，**授权关系不受任何影响**；
--         前端无任何硬编码权限 code（只在授权管理页通用展示），无需改前端。
-- ============================================================

-- ① 用户设置：settings -> preference（id 16/17/18，腾出 settings 关键字）
UPDATE ks_permissions SET permission_code = 'system:preference:page'
WHERE permission_code = 'system:settings:page';
UPDATE ks_permissions SET permission_code = 'system:preference:update'
WHERE permission_code = 'system:settings:update';
UPDATE ks_permissions SET permission_code = 'system:preference:view'
WHERE permission_code = 'system:settings:view';

-- ② 系统设置（计费校验裁判配置）归位：platform-settings -> settings（id 154/155）
--    若中间态未落库则下面两步各影响 0 行，改由第 ③ 步补插
UPDATE ks_permissions SET permission_code = 'system:settings:view'
WHERE permission_code = 'system:platform-settings:view';
UPDATE ks_permissions SET permission_code = 'system:settings:update'
WHERE permission_code = 'system:platform-settings:update';

-- ③ 系统设置权限补插（id 154/155；已存在则被主键 IGNORE）
INSERT IGNORE INTO ks_permissions
    (id, permission_code, permission_name, permission_type, module_id, menu_id, status, built_in, remark) VALUES
(154, 'system:settings:view', '查看系统设置', 'API', 1, NULL, 1, 1, NULL),
(155, 'system:settings:update', '更新系统设置', 'API', 1, NULL, 1, 1, NULL);

-- ④ 路由规则计费校验权限补插（id 156；模块 3 / 菜单 11，与 routing:rule:* 同组）
INSERT IGNORE INTO ks_permissions
    (id, permission_code, permission_name, permission_type, module_id, menu_id, status, built_in, remark) VALUES
(156, 'routing:rule:billing-verify', '路由规则计费校验', 'API', 3, 11, 1, 1, NULL);

-- ⑤ 三个新端点的 API 权限绑定补插（默认拒绝：未绑定一律 403）
--    注意：必须在第 ③④ 步之后执行，否则绑定的 permission_id 可能指向不存在的权限
INSERT IGNORE INTO ks_api_permission_bindings
    (id, permission_id, http_method, path_pattern, controller_class, status) VALUES
(212, 154, 'GET', '/api/system/settings', 'PlatformSettingController', 1),
(213, 155, 'PUT', '/api/system/settings', 'PlatformSettingController', 1),
(214, 156, 'POST', '/api/routing-rules/{id}/test/billing-verify', 'RoutingRuleController', 1);

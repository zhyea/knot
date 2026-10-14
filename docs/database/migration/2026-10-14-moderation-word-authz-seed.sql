-- =====================================================================
-- 敏感词管理授权种子 + 插件删除绑定补缺（2026-10-14）
-- ---------------------------------------------------------------------
-- 背景：P0-A 词库管理落地，新增「内容安全」模块与敏感词管理页；管理端接口
--       /api/moderation-words 全部走接口级授权（默认拒绝），必须补权限与绑定，
--       否则既有库（KNOT_SQL_INIT_MODE=NEVER）启动后整页 403。
--       同批修复既有缺陷：PluginController DELETE /api/plugins/{id} 从未绑定，
--       前端 del(`/api/plugins/${id}`) 恒 403（插件管理页删除按钮不可用）。
-- 幂等：全部 INSERT IGNORE（按 id / permission_code 唯一键冲突即跳过），可重复执行。
-- 口径：与 data.sql 完全一致——ks_modules(5) / ks_menus(24) / ks_permissions(144-151)
--       / ks_api_permission_bindings(196-206)。id 已对齐 data.sql，重复执行不会产生第二套。
-- 注意：status 列是 TINYINT，必须写数字 1；写字符串 'ENABLED' 在严格模式下会直接报
--       Data truncation（旧迁移 2026-10-02/03/07 有此问题，本脚本不沿用）。
-- 执行顺序：先跑本迁移，再启动应用。
-- =====================================================================

-- 段一：模块 + 菜单（模块 5 内容安全，菜单 24 敏感词管理）
INSERT IGNORE INTO ks_modules (id, module_code, module_name, icon, sort_order, status) VALUES
(5, 'moderation', '内容安全', 'Warning', 50, 1);

INSERT IGNORE INTO ks_menus (id, module_id, parent_id, menu_code, menu_name, route_path, component_key, icon, sort_order, status) VALUES
(24, 5, NULL, 'moderation.words', '敏感词管理', '/moderation/words', 'moderation/ModerationWordManageView', 'Warning', 10, 1);

-- 段二：权限（PAGE 144 手写；API 145-150 由 gen_api_bindings.py 生成；151 补插件删除）
INSERT IGNORE INTO ks_permissions (id, permission_code, permission_name, permission_type, module_id, menu_id, status, built_in, remark) VALUES
(144, 'moderation:word:page', '敏感词管理页面访问', 'PAGE', 5, 24, 1, 1, NULL),
(145, 'moderation:word:view', '敏感词查看', 'API', 5, 24, 1, 1, NULL),
(146, 'moderation:word:create', '敏感词创建', 'API', 5, 24, 1, 1, NULL),
(147, 'moderation:word:update', '敏感词更新', 'API', 5, 24, 1, 1, NULL),
(148, 'moderation:word:enable', '敏感词更新状态', 'API', 5, 24, 1, 1, NULL),
(149, 'moderation:word:delete', '敏感词删除', 'API', 5, 24, 1, 1, NULL),
(150, 'moderation:word:import', '敏感词导入', 'API', 5, 24, 1, 1, NULL),
(151, 'system:plugin:delete', '插件删除', 'API', 1, 16, 1, 1, NULL);

-- 段三：API 绑定（默认拒绝：未绑定接口一律 403）
INSERT IGNORE INTO ks_api_permission_bindings (id, permission_id, http_method, path_pattern, controller_class, status) VALUES
(196, 145, 'GET', '/api/moderation-words/check-code', 'ModerationWordController', 1),
(197, 145, 'GET', '/api/moderation-words/{id}', 'ModerationWordController', 1),
(198, 145, 'POST', '/api/moderation-words/list', 'ModerationWordController', 1),
(199, 146, 'POST', '/api/moderation-words', 'ModerationWordController', 1),
(200, 147, 'PUT', '/api/moderation-words/{id}', 'ModerationWordController', 1),
(201, 148, 'PUT', '/api/moderation-words/{id}/status', 'ModerationWordController', 1),
(202, 149, 'DELETE', '/api/moderation-words/{id}', 'ModerationWordController', 1),
(203, 147, 'PUT', '/api/moderation-words/{id}/restore', 'ModerationWordController', 1),
(204, 150, 'POST', '/api/moderation-words/import', 'ModerationWordController', 1),
(205, 145, 'GET', '/api/moderation-words/export', 'ModerationWordController', 1),
(206, 151, 'DELETE', '/api/plugins/{id}', 'PluginController', 1);

-- 段四：内置角色授权（按 permission_code 关联，避免依赖硬编码 id）
--   OPERATOR(2)  全量（与既有 27-55 全量口径一致）
--   DEVELOPER(3) 仅只读 moderation:word:view
INSERT IGNORE INTO ks_role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM ks_roles r
         CROSS JOIN ks_permissions p
WHERE r.code IN ('OPERATOR', 'DEVELOPER')
  AND p.permission_code LIKE 'moderation:word:%'
  AND (r.code = 'OPERATOR' OR p.permission_code = 'moderation:word:view');

-- 段五：ADMIN 全量（与 data.sql 末尾权威块同口径；按 code 关联，兼容 id 漂移）
INSERT IGNORE INTO ks_role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM ks_roles r
         CROSS JOIN ks_permissions p
WHERE r.code = 'ADMIN'
  AND (p.permission_code LIKE 'moderation:word:%' OR p.permission_code = 'system:plugin:delete');

-- 段六：插件删除补给 OPERATOR（补 403 既有缺陷；DEVELOPER 为只读角色不授予写权限）
INSERT IGNORE INTO ks_role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM ks_roles r
         CROSS JOIN ks_permissions p
WHERE r.code = 'OPERATOR'
  AND p.permission_code = 'system:plugin:delete';

-- =====================================================================
-- 复核（执行后应全部符合预期）
-- =====================================================================
-- select id, module_code from ks_modules where module_code = 'moderation';              -- 1 行 id=5
-- select id, menu_code, route_path from ks_menus where menu_code = 'moderation.words';  -- 1 行 id=24
-- select id, permission_code from ks_permissions where id between 144 and 151 order by id; -- 8 行
-- select id, permission_id, http_method, path_pattern from ks_api_permission_bindings
--   where id between 196 and 206 order by id;                                          -- 11 行
-- select count(*) from ks_role_permissions rp join ks_permissions p on p.id = rp.permission_id
--   where p.permission_code like 'moderation:word:%';                                   -- 2 角色 × 7 + 1(ADMIN view) = 15
-- select count(*) from ks_role_permissions rp join ks_permissions p on p.id = rp.permission_id
--   where p.permission_code = 'system:plugin:delete';                                   -- ADMIN + OPERATOR = 2

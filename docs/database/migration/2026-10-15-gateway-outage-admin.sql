-- =====================================================================
-- 网关摘除管理面（B4）接口授权绑定与角色授权（2026-10-15）
-- ---------------------------------------------------------------------
-- 背景：新增 admin 侧 GatewayOutageController 三个端点，网关侧 OutageAdminController
--       提供 /_admin/outages* 管理面（令牌/loopback 准入）。admin 端走接口级授权
--       （默认拒绝），若不对既有库（KNOT_SQL_INIT_MODE=NEVER）补绑定与角色授权，
--       启动后这些端点恒 403「接口未配置访问权限」。
-- 权限设计（路由模块 3 / 菜单 11，与路由规则组同组）：
--   routing:target:outage:view   (id=152) 路由目标摘除查看
--   routing:target:outage:manage (id=153) 路由目标摘除管理（恢复 + 手动摘除）
-- 角色授权：
--   OPERATOR(2)  全量（查看 + 管理）
--   DEVELOPER(3) 仅查看
--   ADMIN(1)     全量（data.sql 末尾权威块在 ALWAYS 模式下已覆盖；此处对 NEVER 模式
--                 既有库显式补齐，保证 ADMIN 也能操作摘除管理面）
-- 幂等：所有写入均 INSERT IGNORE（permission_code / (method,path) / (role,permission)
--       均有唯一键，冲突即跳过），可重复执行；id 与 data.sql 对齐。
-- 执行顺序：先跑本迁移，再启动应用（或随 schema 一同执行）。
-- =====================================================================

-- 段一：权限定义
INSERT IGNORE INTO ks_permissions
  (id, permission_code, permission_name, permission_type, module_id, menu_id, status, built_in, remark)
VALUES
  (152, 'routing:target:outage:view',   '路由目标摘除查看', 'API', 3, 11, 1, 1, NULL),
  (153, 'routing:target:outage:manage', '路由目标摘除管理', 'API', 3, 11, 1, 1, NULL);

-- 段二：API 绑定（默认拒绝：未绑定接口一律 403）
INSERT IGNORE INTO ks_api_permission_bindings
  (id, permission_id, http_method, path_pattern, controller_class, status)
VALUES
  (209, 152, 'GET',  '/api/gateway/outages',            'GatewayOutageController', 1),
  (210, 153, 'POST', '/api/gateway/outages/release',    'GatewayOutageController', 1),
  (211, 153, 'POST', '/api/gateway/outages/eject',      'GatewayOutageController', 1);

-- 段三：角色授权（按角色编码关联，避免硬编码 id）
INSERT IGNORE INTO ks_role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM ks_roles r
  CROSS JOIN ks_permissions p
WHERE r.code IN ('OPERATOR', 'DEVELOPER', 'ADMIN')
  AND p.permission_code IN ('routing:target:outage:view', 'routing:target:outage:manage')
  AND (r.code <> 'DEVELOPER' OR p.permission_code = 'routing:target:outage:view');

-- =====================================================================
-- 复核（执行后应全部符合预期）
-- =====================================================================
-- select id, permission_code from ks_permissions
--   where permission_code like 'routing:target:outage:%';          -- 2 行（152/153）
-- select id, permission_id, http_method, path_pattern
--   from ks_api_permission_bindings where id between 209 and 211;   -- 3 行
-- select r.code, p.permission_code
--   from ks_role_permissions rp join ks_roles r on r.id = rp.role_id
--   join ks_permissions p on p.id = rp.permission_id
--   where p.permission_code like 'routing:target:outage:%'
--   order by r.code, p.permission_code;
--   -- ADMIN: view+manage / OPERATOR: view+manage / DEVELOPER: view

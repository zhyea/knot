-- =====================================================================
-- 路由调试协议下发接口授权绑定（2026-10-15）
-- ---------------------------------------------------------------------
-- 背景：路由规则测试抽屉的「协议下拉」原先由前端自行解析（按模型 id / 模型池
--       条目 modelCode 取 apiBindings 后取并集），与后端调试准入校验
--       RoutingRuleService#supportedProtocolsForTarget 各算一遍，逻辑漂移。
--       现新增 GET /api/routing-rules/{id}/debug-protocols 由后端权威下发，
--       前端删除自实现。本接口走接口级授权（默认拒绝），若不补绑定，既有库
--       （KNOT_SQL_INIT_MODE=NEVER）启动后该端点恒 403「接口未配置访问权限」，
--       抽屉协议下拉为空且无协议可选。
-- 权限复用：permission_id=112（routing:rule:view，路由规则查看），与既有
--       GET /api/routing-rules/debug-capabilities 同源。
-- 角色授权已齐备（无需在本迁移补齐）：
--   OPERATOR(2)  已持有 (2,112)        —— data.sql L457
--   DEVELOPER(3) 已持有 (3,112)        —— data.sql L466
--   ADMIN(1)     由 data.sql 末尾权威块全量授予
--   实测：select count(*) ... permission_code='routing:rule:view' → 3
-- 幂等：INSERT IGNORE（按 id 唯一键冲突即跳过），可重复执行；id=208 与
--       data.sql 对齐，重复执行不会产生第二套。
-- 执行顺序：先跑本迁移，再启动应用。
-- =====================================================================

-- 段一：API 绑定（默认拒绝：未绑定接口一律 403）
INSERT IGNORE INTO ks_api_permission_bindings (id, permission_id, http_method, path_pattern, controller_class, status) VALUES
(208, 112, 'GET', '/api/routing-rules/{id}/debug-protocols', 'RoutingRuleController', 1);

-- =====================================================================
-- 复核（执行后应全部符合预期）
-- =====================================================================
-- select id, permission_id, http_method, path_pattern
--   from ks_api_permission_bindings where id = 208;               -- 1 行，path=/api/routing-rules/{id}/debug-protocols
-- select count(*) from ks_role_permissions rp join ks_permissions p on p.id = rp.permission_id
--   where p.permission_code = 'routing:rule:view';                -- ADMIN + OPERATOR + DEVELOPER = 3

-- =====================================================================
-- 供应商模型「按 code 取详情」接口授权绑定（2026-10-14）
-- ---------------------------------------------------------------------
-- 背景：RoutingRuleTestDrawer 协议下拉需按模型 code 取真实 apiBindings
--       （池项 ModelPoolItem 仅有 modelCode 无 modelId），后端新增端点
--       GET /api/models/code/{code}（ModelController）。本接口走接口级授权
--       （默认拒绝），若不补绑定，既有库（KNOT_SQL_INIT_MODE=NEVER）启动后
--       该端点恒 403「接口未配置访问权限」，前端抽屉协议解析失败。
-- 权限复用：permission_id=84（model:model:view，供应商模型查看），与既有
--       GET /api/models/{id} 同源。
-- 角色授权已齐备（无需在本迁移补齐）：
--   OPERATOR(2)  已持有 (2,84)        —— data.sql L454
--   DEVELOPER(3) 已持有 (3,84)        —— data.sql L463
--   ADMIN        由 data.sql 末尾权威块全量授予
-- 幂等：INSERT IGNORE（按 id 唯一键冲突即跳过），可重复执行；id=207 与
--       data.sql 对齐，重复执行不会产生第二套。
-- 执行顺序：先跑本迁移，再启动应用。
-- =====================================================================

-- 段一：API 绑定（默认拒绝：未绑定接口一律 403）
INSERT IGNORE INTO ks_api_permission_bindings (id, permission_id, http_method, path_pattern, controller_class, status) VALUES
(207, 84, 'GET', '/api/models/code/{code}', 'ModelController', 1);

-- =====================================================================
-- 复核（执行后应全部符合预期）
-- =====================================================================
-- select id, permission_id, http_method, path_pattern
--   from ks_api_permission_bindings where id = 207;               -- 1 行，path=/api/models/code/{code}
-- select count(*) from ks_role_permissions rp join ks_permissions p on p.id = rp.permission_id
--   where p.permission_code = 'model:model:view';                 -- OPERATOR + DEVELOPER + ADMIN ≥ 3

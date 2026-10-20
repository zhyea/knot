-- ============================================================
-- 2026-10-20 · 补齐「按业务码查消费者详情」的接口权限绑定
--
-- 背景：
--   路由规则↔消费者收敛为 1:1（见 2026-10-20-routing-rule-consumer-1to1.sql）后，
--   测试抽屉改按规则上的 consumerCode 取密钥，新增管理端端点
--     GET /api/routing-consumers/code/{code}   （RoutingConsumerController#getByCode）
--   该端点未登记 ks_api_permission_bindings。管理端拦截器是「默认拒绝」
--   （AuthorizationAccessService#validateApiAccess：无绑定抛 ForbiddenException
--   「接口未配置访问权限」），因此该接口对所有角色一律 403 —— 包括 ADMIN
--   （ADMIN 的全量授权来自 ks_role_permissions，绕不开「接口必须有绑定」这一层）。
--   实测影响：路由规则测试抽屉取不到消费者密钥，测试主流程前的鉴权装配失败。
--
-- 权限粒度：沿用 routing:consumer:view（id=107），与既有
--   GET /api/routing-consumers/{id}（同为返回 secretKey 的详情接口）保持一致；
--   OPERATOR / DEVELOPER 均已持有 107，无需改 ks_role_permissions。
--
-- 既有库处理范围：
--   * 只 INSERT 一条 ks_api_permission_bindings；不动 ks_permissions、ks_role_permissions；
--   * 新库由 data.sql（id=216 那一行）生效，本脚本对新库是幂等空操作。
--
-- 幂等性：依赖唯一键 uk_sys_api_permission_binding (http_method, path_pattern) + INSERT IGNORE。
--   ⚠ 故意不写死 id —— 既有库若经管理端页面新增过绑定，自增 id 可能已占用 216，
--     写死会因主键冲突被 INSERT IGNORE 静默丢弃；id 交自增可规避该陷阱。
--   permission_id 按 permission_code 关联（不硬编码 107），权限 id 变化时仍正确。
--
-- MariaDB：单行 INSERT ... SELECT，无 DDL、无临时表、无存储过程。
-- 权限需求：SELECT + INSERT。
-- ============================================================

INSERT IGNORE INTO ks_api_permission_bindings
    (permission_id, http_method, path_pattern, controller_class, status)
SELECT p.id, 'GET', '/api/routing-consumers/code/{code}', 'RoutingConsumerController', 1
FROM ks_permissions p
WHERE p.permission_code = 'routing:consumer:view';

-- ---------------------------------------------------------------------------
-- 校验（人工 should be 1 行；permissions 缺失时权限本体缺失，需先跑 data.sql 或补权限）
-- ---------------------------------------------------------------------------
SELECT b.id, b.permission_id, p.permission_code, b.http_method, b.path_pattern, b.controller_class, b.status
FROM ks_api_permission_bindings b
         JOIN ks_permissions p ON p.id = b.permission_id
WHERE b.http_method = 'GET'
  AND b.path_pattern = '/api/routing-consumers/code/{code}';

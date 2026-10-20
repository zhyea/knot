-- ============================================================
-- 2026-10-21 · 接口权限绑定清理（既有库）
--
-- 配套 data.sql 变更：
--   1) 流式测试端点权限纠偏：POST /api/routing-rules/{id}/test/stream
--      原绑 routing:rule:create(113)，改为与阻塞测试 / 计费裁判一致的
--      routing:rule:test(116)。否则 DEVELOPER（持 116 不持 113）只能阻塞测试、
--      流式测试被拒，语义不一致。
--   2) 删除 6 条孤儿绑定：端点已不存在或路径已变，与 data.sql 同步清理。
--      * POST /api/notifications/send        —— 通知发送端点已下线（仅留 /templates）
--      * POST /api/plugins                  —— 插件经文件 reconcile 安装，无创建接口
--      * POST /api/provider-accounts/{id}/discount-policies/list
--      * POST /api/provider-accounts/{id}/discount-policies
--      * PUT  /api/provider-accounts/{id}/discount-policies/{policyId}
--        —— 折扣策略端点已迁到 ModelController（/{modelCode}/discount-policies...）
--      * POST /api/system/operation-logs/{id}  —— 实际端点为 /api/system/operation-logs（无 /{id}）
--
-- 权限本体保留（不删 ks_permissions）：删除绑定后 system:notification:send(96)、
-- system:plugin:create(97) 不再被任何绑定引用，但按「授权粒度为权限、非绑定」的既有约定
-- （见 2026-10-19-remove-notification-policy-binding.sql）保留权限本身，避免误伤角色授权。
--
-- 影响面核实：被删绑定的 permission_id（96/97/99/100/101/15）仍被其它有效绑定占用
--   （99/100/101 用于 provider-accounts 主端点，15 用于 /api/system/logs 等），
--   无需改 ks_role_permissions。
--
-- 幂等：UPDATE 带当前值条件；DELETE 按唯一键 (http_method, path_pattern) 整组删除，
--   行不存在时影响 0 行，可重复执行。
-- MariaDB：仅 UPDATE / DELETE，无 DDL、无临时表、无存储过程。
-- 权限需求：UPDATE + DELETE。
-- ============================================================

-- ---------------------------------------------------------------------------
-- 1) 流式测试权限：routing:rule:create(113) → routing:rule:test(116)
-- ---------------------------------------------------------------------------
UPDATE ks_api_permission_bindings
SET permission_id = 116
WHERE http_method = 'POST'
  AND path_pattern = '/api/routing-rules/{id}/test/stream'
  AND permission_id = 113;

-- ---------------------------------------------------------------------------
-- 2) 删除孤儿绑定（一次性清理，已无对应端点）
-- ---------------------------------------------------------------------------
DELETE FROM ks_api_permission_bindings
WHERE (http_method, path_pattern) IN (
    ('POST', '/api/notifications/send'),
    ('POST', '/api/plugins'),
    ('POST', '/api/provider-accounts/{id}/discount-policies/list'),
    ('POST', '/api/provider-accounts/{id}/discount-policies'),
    ('PUT',  '/api/provider-accounts/{id}/discount-policies/{policyId}'),
    ('POST', '/api/system/operation-logs/{id}')
);

-- ---------------------------------------------------------------------------
-- 校验：期望 0 行（这些路径不应再有任何绑定）
-- ---------------------------------------------------------------------------
SELECT b.id, b.permission_id, b.http_method, b.path_pattern
FROM ks_api_permission_bindings b
WHERE (b.http_method, b.path_pattern) IN (
    ('POST', '/api/notifications/send'),
    ('POST', '/api/plugins'),
    ('POST', '/api/provider-accounts/{id}/discount-policies/list'),
    ('POST', '/api/provider-accounts/{id}/discount-policies'),
    ('PUT',  '/api/provider-accounts/{id}/discount-policies/{policyId}'),
    ('POST', '/api/system/operation-logs/{id}')
)
   OR (b.http_method = 'POST'
       AND b.path_pattern = '/api/routing-rules/{id}/test/stream'
       AND b.permission_id <> 116);

-- ============================================================================
-- 2026-11-17 · 回滚：撤销 GET /api/docs/openapi.json 的权限绑定
--
-- 幂等：不存在时删除 0 行。只删绑定，不动 ks_permissions（system:doc:view 仍在权限树里）。
-- 回滚后该端点重新变成 403（默认拒绝），文档若要对外可用必须同时撤销本次回滚。
-- ============================================================================

DELETE FROM ks_api_permission_bindings
WHERE http_method = 'GET' AND path_pattern = '/api/docs/openapi.json';

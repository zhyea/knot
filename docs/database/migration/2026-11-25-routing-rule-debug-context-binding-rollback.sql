-- ============================================================================
-- 2026-11-25 · 回滚：路由规则详情 / 调试上下文绑定
--
-- 回到旧形态：删掉 detail 与 debug-context 两条绑定，恢复 /api/routing-rules/{id}/debug-protocols。
-- ⚠ 只有同时回滚代码时才有意义：新代码没有 /debug-protocols 端点，
--   单独跑本脚本会让调试面板 403；旧代码没有 detail 端点，留着两条绑定只是无主记录。
--
-- 不恢复 /api/test-request-presets/options：该端点的代码已随本批删除
--   （预设下拉改由 debug-context 下发），恢复绑定只会留下一条指向不存在端点的记录。
-- ============================================================================

DELETE FROM ks_api_permission_bindings
WHERE http_method = 'GET'
  AND path_pattern IN ('/api/routing-rules/{id}', '/api/routing-rules/{id}/debug-context');

INSERT INTO ks_api_permission_bindings (permission_id, http_method, path_pattern, controller_class, status)
SELECT p.id, 'GET', '/api/routing-rules/{id}/debug-protocols', 'RoutingRuleController', 1
FROM ks_permissions p
WHERE p.permission_code = 'routing:rule:view'
  AND NOT EXISTS (
      SELECT 1 FROM ks_api_permission_bindings b
      WHERE b.http_method = 'GET' AND b.path_pattern = '/api/routing-rules/{id}/debug-protocols'
  );

-- ============================================================================
-- 2026-11-25 · 路由规则：列表瘦身 + 调试上下文端点补权限绑定
--
-- 变更（两条绑定，权限都取 routing:rule:view = id 112）：
--   新增  GET /api/routing-rules/{id}                 规则详情（编辑抽屉按需取限流/额度/重试/摘除/选择策略）
--   新增  GET /api/routing-rules/{id}/debug-context    调试上下文（逐目标协议 + 模型族 + 已筛预设）
--   删除  GET /api/routing-rules/{id}/debug-protocols  已被 debug-context 取代，路径不再存在
--   删除  GET /api/test-request-presets/options        预设下拉已由 debug-context 下发，该端点随之下线
--
-- 为什么需要：admin 采用「默认拒绝」——`/api/**` 在 ks_api_permission_bindings 里没有绑定就 403。
--   列表接口不再下发这些配置，前端全靠这两个端点取数；漏登记的表现是「编辑/测试抽屉一律 403」。
--
-- 幂等：按唯一键 (http_method, path_pattern) 判重，不写死自增 id
--   （既有库的 AUTO_INCREMENT 可能已越过 223，写死 id 反而撞主键）。
--   权限行不存在时 SELECT 无结果 → 插入 0 行，不会造出悬挂绑定。
--
-- 发布顺序：先跑本脚本再发版（旧版前端还在打 /debug-protocols，删掉这条绑定会让旧版调试面板 403）。
--   本地 application-local.yml 的 SQL_INIT_MODE=ALWAYS 会由 data.sql 自动补上同样两条。
--
-- 回滚：见同目录 2026-11-25-routing-rule-debug-context-binding-rollback.sql
--   （恢复旧 debug-protocols 绑定，删掉这两条）。
-- ============================================================================

DELETE FROM ks_api_permission_bindings
WHERE http_method = 'GET'
  AND path_pattern IN ('/api/routing-rules/{id}/debug-protocols',
                       '/api/test-request-presets/options');

INSERT INTO ks_api_permission_bindings (permission_id, http_method, path_pattern, controller_class, status)
SELECT p.id, 'GET', '/api/routing-rules/{id}', 'RoutingRuleController', 1
FROM ks_permissions p
WHERE p.permission_code = 'routing:rule:view'
  AND NOT EXISTS (
      SELECT 1 FROM ks_api_permission_bindings b
      WHERE b.http_method = 'GET' AND b.path_pattern = '/api/routing-rules/{id}'
  );

INSERT INTO ks_api_permission_bindings (permission_id, http_method, path_pattern, controller_class, status)
SELECT p.id, 'GET', '/api/routing-rules/{id}/debug-context', 'RoutingRuleController', 1
FROM ks_permissions p
WHERE p.permission_code = 'routing:rule:view'
  AND NOT EXISTS (
      SELECT 1 FROM ks_api_permission_bindings b
      WHERE b.http_method = 'GET' AND b.path_pattern = '/api/routing-rules/{id}/debug-context'
  );

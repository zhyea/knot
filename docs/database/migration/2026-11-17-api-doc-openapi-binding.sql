-- ============================================================================
-- 2026-11-17 · 运行时接口文档端点补权限绑定（GET /api/docs/openapi.json）
--
-- 只做一件事：给 springdoc 的 GET 文档端点补一条接口级授权绑定，权限取
--   ks_permissions id 58 / code system:doc:view（ADMIN 由 data.sql 末尾权威块全量授予）。
--
-- 为什么需要：admin 的 adminAuthorizationInterceptor 采用「默认拒绝」——`/api/**` 的
--   handler 在 ks_api_permission_bindings 里没有绑定就 403。springdoc 的
--   OpenApiWebMvcResource 是第三方 @RestController，不在 controller 包扫描范围内，
--   守门测试结构上抓不到；此前只有已删除的 POST 占位端点绑定，GET 从未登记。
--   结果：带合法 JWT 访问 /api/docs/openapi.json 也是 403，文档「已上线」不成立。
--
-- 幂等：按唯一键 (http_method, path_pattern) 判重，不写死自增 id
--   （既有库的 AUTO_INCREMENT 可能已越过 228，写死 id 反而会撞主键）。
--   权限行不存在时 SELECT 无结果 → 插入 0 行，不会造出悬挂绑定。
--
-- 发布顺序：本脚本与代码无耦合，先跑 DB 或先发版都可以；本地
--   application-local.yml 的 SQL_INIT_MODE=ALWAYS 会由 data.sql 自动补上同一条。
--
-- 回滚：见同目录 2026-11-17-api-doc-openapi-binding-rollback.sql
--   （仅删这一条绑定；system:doc:view 权限保留，它还是权限树上的一个可见项）。
-- ============================================================================

INSERT INTO ks_api_permission_bindings (permission_id, http_method, path_pattern, controller_class, status)
SELECT p.id, 'GET', '/api/docs/openapi.json', 'OpenApiWebMvcResource', 1
FROM ks_permissions p
WHERE p.permission_code = 'system:doc:view'
  AND NOT EXISTS (
      SELECT 1 FROM ks_api_permission_bindings b
      WHERE b.http_method = 'GET' AND b.path_pattern = '/api/docs/openapi.json'
  );

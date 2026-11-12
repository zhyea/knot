-- =============================================================================
-- 移除 /api/docs 占位端点的权限绑定（2026-11-12）
--
-- 背景：ApiDocController 是脚手架占位，两个端点都不随代码演进更新——
--       POST /api/docs/openapi.json 返回硬编码骨架（paths 里只有 /api/health），
--       POST /api/docs/changelog 返回两条手写版本。
--       真实文档由 springdoc 以 GET /api/docs/openapi.json 提供，与
--       docs/prd|tech/modules/10-API文档管理*.md 写的一致；占位端点反而是 POST，
--       文档与实现对不上。仓库内零引用（前端 src、后端除本类外均无命中），
--       2026-11-12 robin 拍板删除，同时删除 data.sql 中的绑定行（id 54 / 55）。
--
-- 口径：权限 system:doc:view（id 58，built_in=1）**保留**——它是内置权限，
--       可能已被角色授权（ks_role_permissions），删掉会动授权记录与权限列表；
--       本次只解除「路径 → 权限」映射。端点已不存在，映射留着才是脏数据。
--
-- 幂等性：DELETE 以 http_method + path_pattern 精确匹配，重复执行无副作用。
--         可在启新 jar 前后任意时刻执行（与代码不互相依赖）。
-- =============================================================================

DELETE FROM ks_api_permission_bindings
WHERE http_method = 'POST'
  AND path_pattern IN ('/api/docs/openapi.json', '/api/docs/changelog');

-- 核对：应为 0 行
SELECT COUNT(*) AS remaining_placeholder_bindings
FROM ks_api_permission_bindings
WHERE path_pattern IN ('/api/docs/openapi.json', '/api/docs/changelog');

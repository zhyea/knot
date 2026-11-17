-- ============================================================================
-- 2026-11-17 · 下线 4 个零消费的操作日志查询端点，同步清掉它们的接口绑定
--
-- 删掉的端点（代码侧已移除）：
--   GET /api/operation-logs/module/{module}
--   GET /api/operation-logs/operator/{operatorId}
--   GET /api/operation-logs/entity/{entityType}/{entityId}
--   GET /api/system/enums/operation-logs/{category}
--
-- 为什么删：前三个返回 List、无分页无 limit，ks_operation_logs 是持续增长表，
--   且前端全仓 grep 零调用点，只剩权限树上的一个可见项。枚举日志改走
--   POST /api/operation-logs/list（module=enum + entity_name 前缀），分页与上限都在服务端。
--
-- 只删绑定，不删权限：这四条绑定的 permission_id 是 15（system:log:view）与 30（system:enum:view），
--   两者还覆盖 /api/operation-logs/list、/{id}、/api/system/enums/* 等在用端点。
--   ks_role_permissions 绑的是 permission 而不是 binding，同样保持不动——删权限会让整个
--   日志页与枚举页 403。
--
-- 幂等：按 (http_method, path_pattern) 精确匹配删除，重复执行影响 0 行。
-- 执行顺序：先发版（端点已不存在）再跑本脚本，或反过来都可以——绑定指向的端点消失后，
--   请求只会 404，不会 403。
--
-- 回滚：无。端点已从代码移除，恢复绑定只会造出一条指向空端点的孤儿记录；
--   真要回滚，回滚代码后由 data.sql / 本脚本的反向 INSERT 重新登记。
-- ============================================================================

DELETE FROM ks_api_permission_bindings
WHERE (http_method, path_pattern) IN (
    ('GET', '/api/operation-logs/module/{module}'),
    ('GET', '/api/operation-logs/operator/{operatorId}'),
    ('GET', '/api/operation-logs/entity/{entityType}/{entityId}'),
    ('GET', '/api/system/enums/operation-logs/{category}')
);

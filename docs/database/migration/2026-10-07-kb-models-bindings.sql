--补齐 kb_models 逻辑删除（2026-10-07）新增端点的权限绑定
--
-- 背景：2026-10-07「kb_models 逻辑删除」改造在 data.sql 中新增了两条绑定
--   (189,142,'DELETE','/api/models/{id}')与 (190,86,'PUT','/api/models/{id}/restore')
-- 但配套迁移脚本 2026-10-07-kb-models-soft-delete.sql 只含 DDL（加 is_deleted 列），
-- **遗漏了权限绑定 INSERT**，导致既有库跑完迁移后：
--   DELETE  /api/models/{id}          -> 403
--   PUT     /api/models/{id}/restore  -> 403
-- 本仓默认拒绝（ks_api_permission_bindings 无绑定即403），故必须补齐。
--
-- 权限映射（与 data.sql 一致）：
--   142 = model:model:delete  -> DELETE /api/models/{id}
--    86 = model:model:update  -> PUT    /api/models/{id}/restore（恢复复用update）
--
-- 幂等：INSERT IGNORE + 固定主键id，重复执行无副作用；
--另有 uk_sys_api_permission_binding(http_method,path_pattern) 唯一键兜底。

INSERT IGNORE INTO ks_api_permission_bindings
    (id, permission_id, http_method, path_pattern, controller_class, status)
VALUES
    (189, 142, 'DELETE', '/api/models/{id}',        'ModelController', 'ENABLED'),
    (190,  86, 'PUT',    '/api/models/{id}/restore','ModelController', 'ENABLED');

-- ---- 执行后核对（非执行语句，迁移后用 JDBC 探针核对）----
-- 预期返回 2：
-- SELECT COUNT(*) FROM ks_api_permission_bindings
--  WHERE (http_method, path_pattern) IN
--        (('DELETE','/api/models/{id}'),
--         ('PUT','/api/models/{id}/restore'));
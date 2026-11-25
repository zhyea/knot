-- ============================================================================
-- 2026-11-25 · 铁律 12：编辑 / 功能页改按 id 取详情，补 11 条 GET 权限绑定
--
-- 背景：列表接口只保留列表所需字段；编辑抽屉 / 弹窗打开时按 id 重新取详情。
-- admin 默认拒绝，未登记的 GET 会 403，所以必须与新端点同批发布。
--
-- 幂等：按 (http_method, path_pattern) 判重，不写死自增 id；权限行不存在时
--   JOIN 无结果 → 插入 0 行，不会造出悬挂绑定。
-- 本地 application-local.yml 的 SQL_INIT_MODE=ALWAYS 会由 data.sql 补同样 11 条。
-- 回滚：docs/database/migration/2026-11-25-detail-endpoints-for-edit-forms-rollback.sql
-- ============================================================================

INSERT INTO ks_api_permission_bindings (permission_id, http_method, path_pattern, controller_class, status)
SELECT p.id, 'GET', x.path_pattern, x.controller_class, 1
FROM (
    SELECT 'system:app:view' AS permission_code, '/api/apps/{id}' AS path_pattern, 'AppController' AS controller_class
    UNION ALL SELECT 'system:user:view', '/api/users/{id}', 'UserController'
    UNION ALL SELECT 'system:department:view', '/api/system/departments/{id}', 'DepartmentController'
    UNION ALL SELECT 'system:scheduled-task:view', '/api/system/scheduled-tasks/{id}', 'ScheduledTaskController'
    UNION ALL SELECT 'system:enum:view', '/api/system/enums/items/detail/{itemId}', 'EnumConfigController'
    UNION ALL SELECT 'system:role:view', '/api/system/authorizations/roles/{id}', 'AuthorizationRoleController'
    UNION ALL SELECT 'system:authz:module:view', '/api/system/authorizations/modules/{id}', 'AuthorizationModuleController'
    UNION ALL SELECT 'system:authz:menu:view', '/api/system/authorizations/menus/{id}', 'AuthorizationMenuController'
    UNION ALL SELECT 'system:authz:permission:view', '/api/system/authorizations/permissions/{id}', 'AuthorizationPermissionController'
    UNION ALL SELECT 'system:authz:api-binding:view', '/api/system/authorizations/api-bindings/{id}', 'AuthorizationApiBindingController'
    UNION ALL SELECT 'model:model:view', '/api/models/{modelCode}/discount-policies/{policyId}', 'ModelController'
) x
JOIN ks_permissions p ON p.permission_code = x.permission_code
WHERE NOT EXISTS (
    SELECT 1 FROM ks_api_permission_bindings b
    WHERE b.http_method = 'GET' AND b.path_pattern = x.path_pattern
);

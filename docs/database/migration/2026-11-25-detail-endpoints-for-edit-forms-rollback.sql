-- 回滚 2026-11-25-detail-endpoints-for-edit-forms.sql：删掉 11 条详情端点权限绑定。
-- 端点本身仍存在；回滚后 admin 会对这些 GET 返回 403。

DELETE FROM ks_api_permission_bindings
WHERE http_method = 'GET'
  AND path_pattern IN (
      '/api/apps/{id}',
      '/api/users/{id}',
      '/api/system/departments/{id}',
      '/api/system/scheduled-tasks/{id}',
      '/api/system/enums/items/detail/{itemId}',
      '/api/system/authorizations/roles/{id}',
      '/api/system/authorizations/modules/{id}',
      '/api/system/authorizations/menus/{id}',
      '/api/system/authorizations/permissions/{id}',
      '/api/system/authorizations/api-bindings/{id}',
      '/api/models/{modelCode}/discount-policies/{policyId}'
  );

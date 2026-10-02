-- 2026-10-02 计费规则详情接口绑定
-- 背景：编辑抽屉改为打开时按 id 从后端取全量记录（GET /api/billing/rules/{id}），
--       列表走轻量 VO（不再下发 configJson 等）。新增端点须补权限绑定（默认拒绝）。
-- 权限：挂到既有 billing:rule:view（id=67），无需新权限。

INSERT IGNORE INTO ks_api_permission_bindings (id, permission_id, http_method, path_pattern, controller_class, status) VALUES
(164, 67, 'GET', '/api/billing/rules/{id}', 'BillingController', 'ENABLED');

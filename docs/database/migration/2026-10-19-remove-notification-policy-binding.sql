-- ============================================================
-- 2026-10-19  清理通知策略端点的孤儿 API 权限绑定
--
-- 背景：系统能力评估 P0-2 收口 —— NotificationController.createPolicy
-- 为未落库的桩实现（return request 直接回显），本次已删除该端点
-- （POST /api/notifications/policies）并下线前端入口。
-- 该端点对应的 ks_api_permission_bindings 绑定随之成为孤儿，
-- 与 2026-10-18 评估报告 P2 #11 列出的孤儿绑定清理一并进行。
--
-- 影响面（已核实）：
--   * ks_api_permission_bindings 仅删 POST /api/notifications/policies 一条；
--   * 权限 system:notification:create（id=95）仍被
--     POST /api/notifications/templates 绑定占用，权限本体保留；
--   * ks_permissions / ks_role_permissions 不动（授权粒度为权限，非绑定）。
--
-- 幂等：按唯一键 (http_method, path_pattern) 删除，可重复执行。
-- MariaDB：单行 DELETE，无 DDL，无索引变更。
-- ============================================================

DELETE FROM ks_api_permission_bindings
WHERE http_method = 'POST'
  AND path_pattern = '/api/notifications/policies';

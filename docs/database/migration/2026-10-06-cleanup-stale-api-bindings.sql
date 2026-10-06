-- 清理僵尸接口权限绑定（对应端点/Controller 已删除，绑定残留会导致清单失真）
-- 背景：2026-10-06 options 改造二次审计，用 controller 端点清单与 ks_api_permission_bindings 求差集发现。
--
-- 1) GET /api/provider-accounts/options/{id}
--    端点在「前端下拉独立 options 接口改造」阶段三删除（由 POST /api/provider-accounts/options
--    的 values 回显替代），绑定未清理。
-- 2) GET /api/enums/summaries、GET /api/enums/{category}/items
--    EnumController 已删除（枚举统一由 /api/common/enums 与 EnumConfigController 提供），绑定残留。
--
-- 幂等：按 (http_method, path_pattern) 精确删，重复执行无副作用。

DELETE FROM ks_api_permission_bindings
WHERE (http_method, path_pattern) IN (
    ('GET', '/api/provider-accounts/options/{id}'),
    ('GET', '/api/enums/summaries'),
    ('GET', '/api/enums/{category}/items')
);

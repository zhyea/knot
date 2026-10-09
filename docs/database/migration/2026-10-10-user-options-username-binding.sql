-- 用户下拉端点改为 username 型：补新绑定 + 删旧死绑定（2026-10-10）
--
-- 背景：用户绑定统一按 username（路由规则 / 应用 / 消费者三处），id 型端点
--   `POST /api/users/options` 已删除（UserController.listOptions / OptionsService.listUserOptions /
--   OptionsMapper.listUserOptions* / OptionConverter.toUserItem* 一并清理），
--   用户下拉唯一入口改为 `POST /api/users/options-by-username`（value=username）。
--
-- ⚠ 默认拒绝策略（AdminAuthorizationInterceptor 按 ks_api_permission_bindings 判定）：
--   新端点无绑定 → 一律 403；旧端点绑定留着 → 死绑定。故两端都要同步：
--   ① 补 /api/users/options-by-username 绑定（permission_id=2 = system:user:view）
--   ② 删 /api/users/options 的死绑定
--
-- 幂等：① INSERT IGNORE（主键 id 冲突即跳过）；② DELETE 无条件依赖，重复执行无副作用。
-- 全新库由 schema.sql + data.sql 直接建出（data.sql 绑定段 id=192 即为此条），本迁移为存量库准备。

-- ① 补新端点绑定（id 与 db/data.sql 保持一致：192）
INSERT IGNORE INTO ks_api_permission_bindings
    (id, permission_id, http_method, path_pattern, controller_class, status)
VALUES
    (192, 2, 'POST', '/api/users/options-by-username', 'UserController', 1);

-- ② 删旧端点死绑定
DELETE FROM ks_api_permission_bindings
WHERE http_method = 'POST'
  AND path_pattern = '/api/users/options'
  AND controller_class = 'UserController';

-- ---- 执行后核对（用 .workbuddy/audit 下的 JDBC 探针跑）----
-- 预期：① 返回 1  ② 返回 0
-- SELECT COUNT(1) FROM ks_api_permission_bindings
--  WHERE http_method='POST' AND path_pattern='/api/users/options-by-username';
-- SELECT COUNT(1) FROM ks_api_permission_bindings
--  WHERE http_method='POST' AND path_pattern='/api/users/options';

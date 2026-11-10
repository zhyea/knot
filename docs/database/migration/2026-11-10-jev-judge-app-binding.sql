-- =============================================================================
-- 修复「启用但无应用绑定」的路由规则（2026-11-10）
--
-- 背景：启用态路由规则必须绑定「存在且启用」的应用，唯一权威校验在
--       RoutingRuleService#validateForSave；网关 RoutingResolver#resolveAccess
--       也强制要求 rule.appId() 非空且应用已启用。
--       data.sql 的 JEV 计费裁判规则曾以 app_code = NULL + status = 1 直写，
--       绕过全部 service 校验：JevBillingJudge#judge 带 secretKey + Rule 头
--       请求网关 DECISIONS 端点时必然 403，裁判链路 available=false。
--
-- 口径：JEV 裁判规则绑定 app_003（代码审查工具，种子中 status=1、
--       未被其它路由规则占用），见 data.sql 同批修改。
--
-- 幂等性：UPDATE 以 app_code IS NULL / '' 为条件，重复执行无副作用；
--         仅当目标应用存在且启用时才回填，避免把脏数据指向一个不可用的应用。
--         执行顺序：可在启新 jar 前后任意时刻执行（与代码校验互不依赖）。
-- =============================================================================

-- 1) 回填存量：只动「没绑应用」的裁判规则，已绑定的行一律不动
UPDATE kb_routing_rules
SET app_code = 'app_003'
WHERE rule_code = 'jev-billing-judge'
  AND (app_code IS NULL OR app_code = '')
  AND EXISTS (
      SELECT 1 FROM kb_apps a
      WHERE a.app_code = 'app_003' AND a.is_deleted = 0 AND a.status = 1
  );

-- 2) 核对：启用态规则中仍不满足约束的行（应为 0 行）
--    三种违规：未绑应用 / 应用已删 / 应用停用
SELECT r.id, r.rule_code, r.status, r.app_code,
       a.id AS app_id, a.status AS app_status, a.is_deleted
FROM kb_routing_rules r
LEFT JOIN kb_apps a ON a.app_code = r.app_code AND a.is_deleted = 0
WHERE r.status = 1
  AND (r.app_code IS NULL OR r.app_code = '' OR a.id IS NULL OR a.status <> 1)
ORDER BY r.id;

-- 3) 核对：裁判规则回填结果（应为 app_003）
SELECT id, rule_code, status, app_code, consumer_code
FROM kb_routing_rules
WHERE rule_code = 'jev-billing-judge';

-- 路由规则不再记录 userId（2026-10-09）
--
-- 背景：路由规则与「归属用户」解绑。`kb_routing_rules.user_id` 列及其派生的
--   userName 展示一并移除（用户维度归属改由消费者 / 应用承担），
--   `RoutingRuleEntity` / `RoutingRuleDto` / `RoutingRule` VO / `RoutingRuleMapper.xml`
--   已同步删除字段、4 处 `left join ks_users` 与 keyword 里的 u.* 条件。
--   ⚠ 仅删路由规则表；`kb_routing_consumers.user_id` 仍在使用，**不在本迁移范围**。
--
-- 幂等：先查 information_schema，列不存在则跳过（避免重复 ALTER 报错）。
-- 索引：kb_routing_rules 现有索引为 uk_routing_rules_code(rule_code)、
--   idx_routing_rules_app(app_id, status)，均不含 user_id，可直接 DROP COLUMN。

SET @exists = (
    SELECT COUNT(1)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'kb_routing_rules'
      AND COLUMN_NAME = 'user_id'
);
SELECT @exists AS user_id_exists;

SET @sql = IF(
    @exists > 0,
    'ALTER TABLE kb_routing_rules DROP COLUMN user_id',
    'SELECT 1' -- 列已不存在，跳过
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---- 执行后核对（用 .workbuddy/audit 下的 JDBC 探针跑）----
-- 预期返回 0：
-- SELECT COUNT(1) FROM information_schema.COLUMNS
--  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME='kb_routing_rules' AND COLUMN_NAME='user_id';

-- 路由规则增加 username 列（绑定归属用户登录名，而非主键 id）（2026-10-09）
--
-- 背景：路由规则重新绑定归属用户，但按「跨模块绑定存 code 不存 id」口径，存 username
--   （业务码、ks_users.uk_users_username 唯一）而非 user.id。
--   RoutingRuleEntity / RoutingRuleDto / RoutingRule VO / RoutingRuleMapper.xml 同步增加
--   username 字段与读写；schema.sql 已把该列纳入 CREATE TABLE 作为权威定义。
--   本迁移仅用于让「已存在、非由 schema.sql 重建」的库补齐列；全新库由 schema.sql 直接建出。
--
-- 幂等：先查 information_schema，列不存在才 ALTER；已存在则跳过。

SET @exists = (
    SELECT COUNT(1)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'kb_routing_rules'
      AND COLUMN_NAME = 'username'
);
SELECT @exists AS username_exists;

SET @sql = IF(
    @exists = 0,
    'ALTER TABLE kb_routing_rules ADD COLUMN username VARCHAR(64) DEFAULT NULL COMMENT ''归属用户登录名（绑定 username，非主键 id）'' AFTER app_id',
    'SELECT 1' -- 列已存在，跳过
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---- 执行后核对（用 .workbuddy/audit 下的 JDBC 探针跑）----
-- 预期返回 1：
-- SELECT COUNT(1) FROM information_schema.COLUMNS
--  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME='kb_routing_rules' AND COLUMN_NAME='username';

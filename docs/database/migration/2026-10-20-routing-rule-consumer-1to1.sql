-- ============================================================================
-- 2026-10-20 · 路由规则↔消费者 收敛为 1:1，去掉冗余关联表
--            kb_routing_rule_consumers → kb_routing_rules.consumer_code
--
-- 背景：
--   原 kb_routing_rule_consumers 结构上是 M:N（复合唯一键 + 双向索引），但实际
--   使用基数永远是「一个消费者可绑多条规则，一条规则只绑一个消费者」——即消费者
--   1 : 规则 N。关联表纯冗余。本次把绑定下沉为 kb_routing_rules 单列 consumer_code
--   （业务码，非主键 id），并删除关联表。绑定一律走 code，不引入 id 关联。
--
-- 代码侧同步改动（全部在本批内）：
--   schema.sql（kb_routing_rules 加列 + 删表 DDL）、data.sql（规则行直接带 consumer_code，
--   删关联表 INSERT）、RoutingRuleConsumerEntity/Mapper 删除、RoutingRuleEntity 加
--   consumerCode/consumerName、RoutingRuleMapper.xml/Java、RoutingRuleService、
--   RoutingRuleDto/RoutingRule VO、JevBillingJudge（改从规则反查消费者）、
--   OptionsMapper.xml（消费者 options 的 value 由 id 改 consumer_code）、前端三处绑定 UI。
--
-- 本脚本只处理「既有库」：schema.sql 用 IF NOT EXISTS，既有库不会自动加列/删表；
--   新库直接由 schema.sql 生效，本脚本对它们应是无操作。
--
-- 幂等性：全程用 INFORMATION_SCHEMA 判列/表是否存在，再决定是否执行 ALTER/UPDATE/DROP；
--   重复执行安全。无 Flyway/Liquibase，需手动在目标库执行（约定：先迁移后打包）。
--   权限：仅需本仓库应用账号的 SELECT + ALTER + UPDATE + DROP，不建临时表/存储过程。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1) kb_routing_rules 加 consumer_code（若尚未存在）
-- ---------------------------------------------------------------------------
SET @has_col = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME   = 'kb_routing_rules'
    AND COLUMN_NAME  = 'consumer_code'
);
SET @sql_add = IF(
  @has_col = 0,
  "ALTER TABLE kb_routing_rules ADD COLUMN consumer_code VARCHAR(32) DEFAULT NULL COMMENT '绑定消费者业务码（kb_routing_consumers.consumer_code），一对一，非主键 id；空表示未绑定'",
  'SELECT 1'
);
PREPARE stmt_add FROM @sql_add;
EXECUTE stmt_add;
DEALLOCATE PREPARE stmt_add;

-- ---------------------------------------------------------------------------
-- 2) 回填：把关联表的 (rule_code → consumer_code) 写回规则表（仅规则侧为空时）
--    关联表不存在（新库或已清理）时跳过 UPDATE，避免报「表不存在」。
-- ---------------------------------------------------------------------------
SET @has_rrc = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME   = 'kb_routing_rule_consumers'
);
SET @sql_backfill = IF(
  @has_rrc > 0,
  "UPDATE kb_routing_rules r JOIN kb_routing_rule_consumers rc ON rc.rule_code = r.rule_code "
  "SET r.consumer_code = rc.consumer_code "
  "WHERE r.consumer_code IS NULL OR r.consumer_code = ''",
  'SELECT 1'
);
PREPARE stmt_backfill FROM @sql_backfill;
EXECUTE stmt_backfill;
DEALLOCATE PREPARE stmt_backfill;

-- ---------------------------------------------------------------------------
-- 3) 删除冗余关联表（若仍存在）
-- ---------------------------------------------------------------------------
SET @has_rrc2 = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME   = 'kb_routing_rule_consumers'
);
SET @sql_drop = IF(
  @has_rrc2 > 0,
  'DROP TABLE kb_routing_rule_consumers',
  'SELECT 1'
);
PREPARE stmt_drop FROM @sql_drop;
EXECUTE stmt_drop;
DEALLOCATE PREPARE stmt_drop;

-- ---------------------------------------------------------------------------
-- 4) 审计：列出最终绑定结果（正常应为每条规则至多一个 consumer_code，且无空壳）
-- ---------------------------------------------------------------------------
SELECT rule_code, name, consumer_code, status
FROM kb_routing_rules
ORDER BY id;

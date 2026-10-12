-- 路由规则增加 selection_strategy 列（目标选择策略，默认 PRIORITY）（2026-10-12）
--
-- 背景：路由规则的目标顺序此前恒为「主目标置顶 + 其余按 priority 降序」的固定序，
--   不反映实时拥塞。新增 LEAST_LOAD 后，规则可改为「主目标置顶 + 其余按目标在途并发升序」，
--   使请求优先落到当前最空闲的模型/模型池（PRD 承诺的「负载均衡路由」）。
--   默认 PRIORITY，存量数据行为完全不变；未知/空值在运行时同样回落 PRIORITY。
--   在途数来自 ModelLoadStore（knot.load.store，caffeine 单节点 / redis 多节点），
--   模型池目标的负载 = 池内启用条目的在途之和。
--   RoutingRuleEntity / RoutingRuleDto / RoutingRule VO / RoutingRuleMapper.xml 同步增加
--   selection_strategy 读写；schema.sql 已把该列纳入 CREATE TABLE 作为权威定义。
--   本迁移仅用于让「已存在、非由 schema.sql 重建」的库补齐列；全新库由 schema.sql 直接建出。
--
-- 幂等：先查 information_schema，列不存在才 ALTER；已存在则跳过。

SET @exists = (
    SELECT COUNT(1)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'kb_routing_rules'
      AND COLUMN_NAME = 'selection_strategy'
);
SELECT @exists AS selection_strategy_exists;

SET @sql = IF(
    @exists = 0,
    'ALTER TABLE kb_routing_rules ADD COLUMN selection_strategy VARCHAR(32) NOT NULL DEFAULT ''PRIORITY'' COMMENT ''路由目标选择策略（ModelPoolSelectionStrategyEnum）：PRIORITY-按优先级/主目标固定序；LEAST_LOAD-按目标在途并发动态重排（最空闲优先，主目标仍钉首）'' AFTER retry_policy',
    'SELECT 1' -- 列已存在，跳过
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---- 执行后核对（用 .workbuddy/audit 下的 JDBC 探针跑）----
-- 预期返回 1：
-- SELECT COUNT(1) FROM information_schema.COLUMNS
--  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME='kb_routing_rules' AND COLUMN_NAME='selection_strategy';
-- 预期返回 0（存量默认 PRIORITY）：
-- SELECT COUNT(1) FROM kb_routing_rules WHERE selection_strategy IS NULL;

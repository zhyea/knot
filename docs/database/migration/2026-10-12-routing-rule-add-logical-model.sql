-- 路由规则增加 logical_model_code 列（绑定统一模型，非必填）（2026-10-12）
--
-- 背景：路由规则新增「统一模型」字段（非必填）。选中统一模型后，路由目标选择器
--   （供应商模型 / 模型池）把该统一模型对应的项优先置前，但不过滤——其他模型/模型池仍可出现。
--   按「跨模块绑定存 code 不存 id」口径，存 kb_logical_models.model_code。
--   排序实现走 options 接口新增的 preferredLogicalModelCode 参数（见 OptionsMapper.xml），
--   与既有 logicalModelCode（硬过滤，模型池编辑场景）语义分离，互不影响。
--   RoutingRuleEntity / RoutingRuleDto / RoutingRule VO / RoutingRuleMapper.xml 同步增加
--   logical_model_code 读写，并派生 logical_model_name（join kb_logical_models，不落库）。
--   schema.sql 已把该列纳入 CREATE TABLE 作为权威定义。
--   本迁移仅用于让「已存在、非由 schema.sql 重建」的库补齐列；全新库由 schema.sql 直接建出。
--
-- 幂等：先查 information_schema，列不存在才 ALTER；已存在则跳过。

SET @exists = (
    SELECT COUNT(1)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'kb_routing_rules'
      AND COLUMN_NAME = 'logical_model_code'
);
SELECT @exists AS logical_model_code_exists;

SET @sql = IF(
    @exists = 0,
    'ALTER TABLE kb_routing_rules ADD COLUMN logical_model_code VARCHAR(128) DEFAULT NULL COMMENT ''统一模型 code（kb_logical_models.model_code），非必填；选中后路由目标选择器将该统一模型对应的供应商模型/模型池优先置前（仅排序，不过滤）'' AFTER username',
    'SELECT 1' -- 列已存在，跳过
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---- 执行后核对（用 .workbuddy/audit 下的 JDBC 探针跑）----
-- 预期返回 1：
-- SELECT COUNT(1) FROM information_schema.COLUMNS
--  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME='kb_routing_rules' AND COLUMN_NAME='logical_model_code';

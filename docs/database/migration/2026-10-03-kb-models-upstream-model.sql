-- 供应商模型增加「上游模型」（2026-10-03）
-- 迁移版本：2026-10-03-kb-models-upstream-model
--
-- 【语义】
-- kb_models 增加 upstream_model 列（NOT NULL），语义是「向上游发起请求时写入请求体
-- model 参数的模型标识」；kb_models.model_code 退化为 knot 内部的供应商模型业务码
-- （模型池条目、路由规则目标、统一模型映射仍按 model_code 引用，不动）。
-- 网关热路径替换请求体 model 时改用本列（RoutingRuleTargetDto.upstreamModelCode），
-- Qwen / Zhipu 两个自建请求体的适配器同步改用上游模型。
-- 本列**必填**：ModelService.validateModelRequest 校验「请填写上游模型」，
-- 前端 ModelFormDrawer 在「基础信息」中与「统一模型」并列填写。
--
-- 【配套】客户端不再需要传 model：请求体 model 恒由网关按路由目标覆盖，
-- 管理端调试链路（RoutingTestRequest / RoutingRuleService.testInvoke）已移除 model 入参。
--
-- 幂等性：全部 DDL 用 information_schema 判定 + PREPARE 条件式执行，可重复跑。
--         全新实例以更新后的 schema.sql 建库，本列已就位，无需执行本脚本。
--         既有库必须先跑本脚本再启应用（否则 contextLoads / *ControllerTest 会因列缺失失败）。

-- ---- 0. 执行前快照（非执行语句）----
-- SELECT id, model_code FROM kb_models ORDER BY id;

-- ---- 1. 新增列（可空落地，存在则跳过）----
SET @ddl := IF(
    EXISTS (SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_models'
              AND COLUMN_NAME = 'upstream_model'),
    'DO 0',
    'ALTER TABLE kb_models ADD COLUMN upstream_model VARCHAR(128) NULL COMMENT ''上游模型标识：向上游发起请求时写入请求体 model 参数'' AFTER model_code');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---- 2. 回填：存量行以 model_code 兜底（不改已有非空值）----
UPDATE kb_models SET upstream_model = model_code WHERE upstream_model IS NULL OR upstream_model = '';

-- ---- 3. 收敛为 NOT NULL（已是 NOT NULL 则跳过）----
SET @ddl := IF(
    EXISTS (SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_models'
              AND COLUMN_NAME = 'upstream_model' AND IS_NULLABLE = 'NO'),
    'DO 0',
    'ALTER TABLE kb_models MODIFY COLUMN upstream_model VARCHAR(128) NOT NULL COMMENT ''上游模型标识：向上游发起请求时写入请求体 model 参数''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---- 4. 执行后核对 ----
-- 4.1 列就位且非空（预期：IS_NULLABLE = NO）
-- SELECT COLUMN_NAME, IS_NULLABLE, COLUMN_TYPE FROM information_schema.COLUMNS
--  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_models'
--    AND COLUMN_NAME = 'upstream_model';
-- 4.2 无空值残留（预期 0 行）
-- SELECT id, model_code, upstream_model FROM kb_models
--  WHERE upstream_model IS NULL OR upstream_model = '';

-- 路由规则去模型类型 + 统一模型/模型池逻辑删除（2026-10-03）
-- 迁移版本：2026-10-03-routing-drop-model-types
--
-- 本脚本含 3 项 DDL 变更：
--   ① 删除 kb_routing_rules.model_types 列（规则不再持有模型类型）
--   ② kb_logical_models 增加 is_deleted 列（统一模型改逻辑删除）
--   ③ kb_model_pools     增加 is_deleted 列（模型池改逻辑删除）
--
-- 【① 去模型类型的理由】
-- 模型类型信息只在「模型池」或「供应商模型」处维护，路由规则不再冗余持有。
-- 全工程核查：model_types 仅 knot-dal/RoutingRuleMapper.xml 读写，网关路由
-- mapper 与 GatewayDataService 均不读该列 → 删列不影响运行时行为。
-- 后端 DTO/VO/Entity/Service、前端抽屉/列表/筛选/载荷已同步移除该字段。
--
-- 【②③ 逻辑删除 + 引用拦截】
-- 统一模型：被 kb_provider_model_mappings.logical_model_code 或
--           kx_model_items.logical_model_id 引用时禁止删除，否则逻辑删除。
-- 模型池：  被 kb_routing_rule_targets(target_type='MODEL_POOL') 引用时禁止删除，
--           否则逻辑删除。
-- 引用拦截在应用层（LogicalModelService/ModelPoolService）实现，本脚本只做 DDL。
-- 外部模型（kx_model_items）保持物理删除，不在本脚本范围。
--
-- 幂等性：① 用 information_schema 判断列是否存在；②③ ADD COLUMN 若已存在会报错，
--         故用「列存在则跳过」的条件式 DDL（MariaDB 10.6 支持
--         PREPARE + information_schema 判定）。
-- 全新实例以更新后的 schema.sql 建库，三项均已就位，无需执行本脚本。
-- 既有库必须先跑本脚本再启应用。

-- ---- 0. 执行前快照（非执行语句，迁移前人工核对）----
-- SELECT 'kb_routing_rules' AS tbl, COUNT(*) AS cnt FROM kb_routing_rules;
-- SELECT rule_code, model_types FROM kb_routing_rules ORDER BY id;
-- SELECT 'kb_logical_models' AS tbl, COUNT(*) AS cnt FROM kb_logical_models;
-- SELECT 'kb_model_pools'     AS tbl, COUNT(*) AS cnt FROM kb_model_pools;

-- ---- 1. 删除 kb_routing_rules.model_types 列（存在才删）----
SET @ddl := IF(
    EXISTS (SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_routing_rules'
              AND COLUMN_NAME = 'model_types'),
    'ALTER TABLE kb_routing_rules DROP COLUMN model_types',
    'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---- 2. kb_logical_models 增加 is_deleted（存在则跳过）----
SET @ddl := IF(
    EXISTS (SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_logical_models'
              AND COLUMN_NAME = 'is_deleted'),
    'DO 0',
    'ALTER TABLE kb_logical_models ADD COLUMN is_deleted TINYINT(1) NOT NULL DEFAULT 0 AFTER status');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---- 3. kb_model_pools 增加 is_deleted（存在则跳过）----
SET @ddl := IF(
    EXISTS (SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_model_pools'
              AND COLUMN_NAME = 'is_deleted'),
    'DO 0',
    'ALTER TABLE kb_model_pools ADD COLUMN is_deleted TINYINT(1) NOT NULL DEFAULT 0 AFTER status');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---- 4. 执行后核对 ----
-- 4.1 model_types 已不存在（预期 0）
-- SELECT COUNT(*) FROM information_schema.COLUMNS
--  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_routing_rules' AND COLUMN_NAME = 'model_types';
-- 4.2 两张表 is_deleted 已就位（预期各 1）
-- SELECT TABLE_NAME, COLUMN_NAME FROM information_schema.COLUMNS
--  WHERE TABLE_SCHEMA = DATABASE() AND COLUMN_NAME = 'is_deleted'
--    AND TABLE_NAME IN ('kb_logical_models','kb_model_pools');
-- 4.3 新列默认 0，历史数据全部可见（预期 0）
-- SELECT COUNT(*) FROM kb_logical_models WHERE is_deleted <> 0;
-- SELECT COUNT(*) FROM kb_model_pools     WHERE is_deleted <> 0;

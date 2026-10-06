-- 路由目标存储迁移：target_id（模型/池主键 id）→ target_code（model_code/pool_code）（2026-10-06）
-- 迁移版本：2026-10-06-routing-target-code
--
-- 背景：路由目标的「主键 id」与「业务 code」解耦。决策 1（2026-10-06 拍板，见
-- .workbuddy/memory/options-refactor-constraints.md 第 0 节第 3 条）要求
-- kb_routing_rule_targets 不再持有模型/模型池的主键 id，改为直接存业务 code
-- （model_code / pool_code），以便后续切换 options loader（其值即 code）时提交 DTO 与存储对齐。
--
-- 本脚本做 4 件事（均幂等，MariaDB 10.6 条件式 DDL）：
--   ① ADD COLUMN target_code（NOT NULL DEFAULT ''，避免回填前 NOT NULL 违例）
--   ② 回填 target_code：按现有 target_id 反查 kb_models.model_code / kb_model_pools.pool_code
--   ③ 重建唯一键：DROP uk_routing_rule_target(rule_id,target_type,target_id)
--                 → ADD  uk_routing_rule_target(rule_id,target_type,target_code)
--   ④ DROP COLUMN target_id
--
-- 全新实例以更新后的 schema.sql 建库，target_code 已就位，本脚本幂等跳过。
-- 既有库必须先跑本脚本，再启应用（应用启动以 ALWAYS 模式重跑 data.sql，
-- 其中 kb_routing_rule_targets 的 INSERT 已改为经 JOIN 计算 target_code）。
--
-- 回滚脚本：2026-10-06-routing-target-code-rollback.sql

-- ---- 1. 增加 target_code 列（存在则跳过）----
SET @ddl := IF(
    EXISTS (SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_routing_rule_targets'
              AND COLUMN_NAME = 'target_code'),
    'DO 0',
    'ALTER TABLE kb_routing_rule_targets ADD COLUMN target_code VARCHAR(64) NOT NULL DEFAULT '''' AFTER target_type');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---- 2. 回填 target_code（按 target_id 反查业务 code）----
UPDATE kb_routing_rule_targets rt
  LEFT JOIN kb_models m ON rt.target_type = 'MODEL' AND rt.target_id = m.id
  LEFT JOIN kb_model_pools mp ON rt.target_type = 'MODEL_POOL' AND rt.target_id = mp.id AND mp.is_deleted = 0
  SET rt.target_code = COALESCE(m.model_code, mp.pool_code)
  WHERE rt.target_code = '' OR rt.target_code IS NULL;

-- ---- 3. 重建唯一键（先删旧，再建新；均条件式）----
SET @ddl := IF(
    EXISTS (SELECT 1 FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_routing_rule_targets'
              AND INDEX_NAME = 'uk_routing_rule_target'),
    'ALTER TABLE kb_routing_rule_targets DROP INDEX uk_routing_rule_target',
    'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := IF(
    EXISTS (SELECT 1 FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_routing_rule_targets'
              AND INDEX_NAME = 'uk_routing_rule_target'),
    'DO 0',
    'ALTER TABLE kb_routing_rule_targets ADD UNIQUE KEY uk_routing_rule_target (rule_id, target_type, target_code)');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---- 4. 删除 target_id 列（存在才删）----
SET @ddl := IF(
    EXISTS (SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_routing_rule_targets'
              AND COLUMN_NAME = 'target_id'),
    'ALTER TABLE kb_routing_rule_targets DROP COLUMN target_id',
    'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---- 5. 执行后核对（非执行语句，迁移后人工/探针核对）----
-- 5.1 target_code 已就位（预期 1）
-- SELECT COUNT(*) FROM information_schema.COLUMNS
--  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_routing_rule_targets' AND COLUMN_NAME = 'target_code';
-- 5.2 target_id 已不存在（预期 0）
-- SELECT COUNT(*) FROM information_schema.COLUMNS
--  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_routing_rule_targets' AND COLUMN_NAME = 'target_id';
-- 5.3 回填覆盖率（预期 0 行 target_code 为空，若有非 0 行说明存在孤立目标需人工处理）
-- SELECT COUNT(*) FROM kb_routing_rule_targets WHERE target_code = '' OR target_code IS NULL;
-- 5.4 唯一键已切换（预期 1）
-- SELECT COUNT(*) FROM information_schema.STATISTICS
--  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_routing_rule_targets' AND INDEX_NAME = 'uk_routing_rule_target';

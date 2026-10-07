-- 供应商模型逻辑删除：kb_models 增加 is_deleted 列（2026-10-07）
-- 迁移版本：2026-10-07-kb-models-soft-delete
--
-- 背景：kb_models 此前**没有任何删除入口**（ModelController 无 DELETE 端点、ModelMapper 无 delete 语句），
-- 供应商模型只能改 code 或停用。一旦 model_code 被改（ModelService.update 允许改 model_code），
-- kb_routing_rule_targets 里按 target_code 存的老 code 就再也 JOIN 不上 kb_models
-- → RoutingRuleTargetMapper 派生的 coalesce(m.id, mp.id) 为 null
-- → 曾导致 orderTargets 排序 NPE / sameTarget 无法判重（已于 2026-10-07 同期修复）。
--
-- 本脚本让「删除供应商模型」成为可恢复的安全操作：逻辑删除而非物理删除，
-- 使历史路由目标的 target_code 仍能解析出可读信息，且可随时恢复（uk_models_code 不区分
-- is_deleted，逻辑删除后同 model_code 无法新建，只能恢复 —— 与 kb_model_pools / kb_logical_models 一致）。
--
-- 本脚本做 2 件事（均幂等，MariaDB 10.6 条件式 DDL）：
--   ① ADD COLUMN is_deleted（NOT NULL DEFAULT 0，已有行自动为 0）
--   ② 归零 NULL（防御性；列定义 NOT NULL，正常无需执行）
--
-- 全新实例以更新后的 schema.sql 建库，is_deleted 已就位，本脚本幂等跳过。
-- 既有库必须先跑本脚本，再启应用（否则 MyBatis 读 m.is_deleted 会报 Unknown column）。
--
-- 回滚脚本：2026-10-07-kb-models-soft-delete-rollback.sql

-- ---- 1. 增加 is_deleted 列（存在则跳过）----
SET @ddl := IF(
    EXISTS (SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_models'
              AND COLUMN_NAME = 'is_deleted'),
    'DO 0',
    'ALTER TABLE kb_models ADD COLUMN is_deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''逻辑删除：0-否 1-是'' AFTER status');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---- 2. 归零 NULL（防御性）----
UPDATE kb_models SET is_deleted = 0 WHERE is_deleted IS NULL;

-- ---- 3. 执行后核对（非执行语句，迁移后用 JDBC 探针核对）----
-- 3.1 is_deleted 已就位（预期 1）
-- SELECT COUNT(*) FROM information_schema.COLUMNS
--  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_models' AND COLUMN_NAME = 'is_deleted';
-- 3.2 无 NULL（预期 0）
-- SELECT COUNT(*) FROM kb_models WHERE is_deleted IS NULL;
-- 3.3 现有数据未被误删（预期等于迁移前总行数；本库当前 8 行）
-- SELECT COUNT(*) FROM kb_models; SELECT COUNT(*) FROM kb_models WHERE is_deleted = 0;
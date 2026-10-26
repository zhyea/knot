-- ============================================================================
-- 2026-10-26 · 计费规则版本改按规则主键关联（rule_code → rule_id）
--
-- 背景（robin 2026-10-26 拍板）：
--   1) 版本表 kb_billing_rule_versions 原按 rule_code（业务码）关联主体
--      kb_billing_rules.code。每条规则恒有一条版本行，于是「改码守卫」
--      （BillingService.assertCodeChangeAllowed）把版本行也算成引用方，
--      导致计费规则编码**恒判已引用、实际永远改不了**。
--   2) 即使绕过守卫，改码后 syncVersion 会按新码查不到旧版本行 → 另起一条 v1，
--      旧版本行留成 rule_code=旧码 的孤儿（旧码被永久占用，且旧配置悬空）。
--   3) 版本是规则主体的从属明细，与主体同生命周期，用主键绑定即可；
--      编码是「可改的业务标识」，不该承载内部外键。改造后规则编码在无模型绑定时可自由修改。
--
-- 影响面：仅版本表内部关联列；对外接口、模型绑定（kb_models.billing_rule_code）、
--   计费入口（按 code 查主体 + 按 rule_id 关联版本）语义不变，故无规则迁移副作用。
--
-- 执行顺序约定：先跑本脚本，再 package / 启动应用（代码与迁移同版本上线，不留兼容分支）。
-- 幂等性：information_schema 判列/索引是否存在，重复执行安全。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 0) 迁移前预检：以下 SELECT 必须人工过目，有输出必须先处理再执行后续步骤
-- ---------------------------------------------------------------------------

-- 0.1 版本行指向的 rule_code 在主体表不存在（历史改码遗留的孤儿版本行）
--     → 回填 rule_id 会得到 NULL，后续 NOT NULL 必失败。须先确认归属（改回 rule_code 或删除该行）。
--     ⚠ 三条预检都引用 rule_code，故必须用动态 SQL 包起来：脚本已执行过（列已删）时跳过，
--       否则重复执行会在第 1 条就报 Unknown column，幂等性直接失效。
SET @has_rule_code_pre = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME   = 'kb_billing_rule_versions'
    AND COLUMN_NAME  = 'rule_code'
);

SET @sql_pc1 = IF(@has_rule_code_pre > 0,
  "SELECT 'PRECHECK-1 孤儿版本行（结果非空须人工处理）' AS check_item,
          v.id AS version_id, v.rule_code, v.version_code, v.status, v.effective_from
   FROM kb_billing_rule_versions v
   LEFT JOIN kb_billing_rules br ON br.code = v.rule_code
   WHERE br.id IS NULL",
  "SELECT 'PRECHECK-1 跳过：rule_code 列已删除（本库已迁移）' AS check_item");
PREPARE stmt_pc1 FROM @sql_pc1;
EXECUTE stmt_pc1;
DEALLOCATE PREPARE stmt_pc1;

-- 0.2 同一规则下存在重复 (version_code) 或重复 (uniq_hash)
--     旧唯一键已按 rule_code 约束，理论上不会出现；若非空说明存在脏数据，须先清理。
SET @sql_pc2 = IF(@has_rule_code_pre > 0,
  "SELECT 'PRECHECK-2 同规则重复版本号（结果非空须人工处理）' AS check_item,
          rule_code, version_code, COUNT(*) AS dup_version_code
   FROM kb_billing_rule_versions
   GROUP BY rule_code, version_code
   HAVING COUNT(*) > 1",
  "SELECT 'PRECHECK-2 跳过：rule_code 列已删除（本库已迁移）' AS check_item");
PREPARE stmt_pc2 FROM @sql_pc2;
EXECUTE stmt_pc2;
DEALLOCATE PREPARE stmt_pc2;

SET @sql_pc3 = IF(@has_rule_code_pre > 0,
  "SELECT 'PRECHECK-3 同规则重复指纹（结果非空须人工处理）' AS check_item,
          rule_code, uniq_hash, COUNT(*) AS dup_hash
   FROM kb_billing_rule_versions
   GROUP BY rule_code, uniq_hash
   HAVING COUNT(*) > 1",
  "SELECT 'PRECHECK-3 跳过：rule_code 列已删除（本库已迁移）' AS check_item");
PREPARE stmt_pc3 FROM @sql_pc3;
EXECUTE stmt_pc3;
DEALLOCATE PREPARE stmt_pc3;

-- ---------------------------------------------------------------------------
-- 1) 新增 rule_id（可空，先回填再加约束）
-- ---------------------------------------------------------------------------
SET @has_rule_id = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME   = 'kb_billing_rule_versions'
    AND COLUMN_NAME  = 'rule_id'
);
SET @sql_add_rule_id = IF(
  @has_rule_id = 0,
  "ALTER TABLE kb_billing_rule_versions ADD COLUMN rule_id BIGINT NULL COMMENT '计费规则主键（kb_billing_rules.id）：版本是规则主体的从属，按主键绑定，规则编码可自由修改' AFTER id",
  'SELECT 1'
);
PREPARE stmt_add_rule_id FROM @sql_add_rule_id;
EXECUTE stmt_add_rule_id;
DEALLOCATE PREPARE stmt_add_rule_id;

-- ---------------------------------------------------------------------------
-- 2) 回填 rule_id：按业务码映射到主体主键（幂等；已回填的行不重复写）
--    ⚠ 同样必须动态执行：rule_code 列已删时（重复执行）静态 UPDATE 会解析失败。
-- ---------------------------------------------------------------------------
SET @sql_backfill_rule_id = IF(@has_rule_code_pre > 0,
  "UPDATE kb_billing_rule_versions v
   JOIN kb_billing_rules br ON br.code = v.rule_code
   SET v.rule_id = br.id
   WHERE v.rule_id IS NULL",
  "SELECT '回填跳过：rule_code 列已删除（本库已迁移）' AS check_item");
PREPARE stmt_backfill_rule_id FROM @sql_backfill_rule_id;
EXECUTE stmt_backfill_rule_id;
DEALLOCATE PREPARE stmt_backfill_rule_id;

-- ---------------------------------------------------------------------------
-- 3) 回填结果核对：必须 0 行 NULL 才能继续（非 0 说明 PRECHECK-1 未处理）
-- ---------------------------------------------------------------------------
SELECT 'POSTCHECK-1 回填后仍无 rule_id 的版本行（必须为空）' AS check_item,
       COUNT(*) AS null_rule_id_rows
FROM kb_billing_rule_versions
WHERE rule_id IS NULL;

-- ---------------------------------------------------------------------------
-- 4) rule_id 收紧为 NOT NULL（有 NULL 行时此步会失败，属预期保护）
-- ---------------------------------------------------------------------------
SET @rule_id_nullable = (
  SELECT IS_NULLABLE FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME   = 'kb_billing_rule_versions'
    AND COLUMN_NAME  = 'rule_id'
);
SET @sql_modify_rule_id = IF(
  @rule_id_nullable = 'YES',
  "ALTER TABLE kb_billing_rule_versions MODIFY COLUMN rule_id BIGINT NOT NULL COMMENT '计费规则主键（kb_billing_rules.id）：版本是规则主体的从属，按主键绑定，规则编码可自由修改'",
  'SELECT 1'
);
PREPARE stmt_modify_rule_id FROM @sql_modify_rule_id;
EXECUTE stmt_modify_rule_id;
DEALLOCATE PREPARE stmt_modify_rule_id;

-- ---------------------------------------------------------------------------
-- 5) 删除以 rule_code 为前导的旧索引（⚠ MariaDB DROP COLUMN 不会自动删复合索引）
-- ---------------------------------------------------------------------------
SET @has_uk_version_code = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME   = 'kb_billing_rule_versions'
    AND INDEX_NAME   = 'uk_billing_rule_version_code'
);
SET @sql_drop_uk_version_code = IF(
  @has_uk_version_code > 0,
  'ALTER TABLE kb_billing_rule_versions DROP INDEX uk_billing_rule_version_code',
  'SELECT 1'
);
PREPARE stmt_drop_uk_version_code FROM @sql_drop_uk_version_code;
EXECUTE stmt_drop_uk_version_code;
DEALLOCATE PREPARE stmt_drop_uk_version_code;

SET @has_uk_hash = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME   = 'kb_billing_rule_versions'
    AND INDEX_NAME   = 'uk_billing_rule_version_hash'
);
SET @sql_drop_uk_hash = IF(
  @has_uk_hash > 0,
  'ALTER TABLE kb_billing_rule_versions DROP INDEX uk_billing_rule_version_hash',
  'SELECT 1'
);
PREPARE stmt_drop_uk_hash FROM @sql_drop_uk_hash;
EXECUTE stmt_drop_uk_hash;
DEALLOCATE PREPARE stmt_drop_uk_hash;

SET @has_idx_active = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME   = 'kb_billing_rule_versions'
    AND INDEX_NAME   = 'idx_billing_rule_versions_active'
);
SET @sql_drop_idx_active = IF(
  @has_idx_active > 0,
  'ALTER TABLE kb_billing_rule_versions DROP INDEX idx_billing_rule_versions_active',
  'SELECT 1'
);
PREPARE stmt_drop_idx_active FROM @sql_drop_idx_active;
EXECUTE stmt_drop_idx_active;
DEALLOCATE PREPARE stmt_drop_idx_active;

-- ---------------------------------------------------------------------------
-- 6) 删除 rule_code 列（无更多索引引用，可直接 DROP）
-- ---------------------------------------------------------------------------
SET @has_rule_code = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME   = 'kb_billing_rule_versions'
    AND COLUMN_NAME  = 'rule_code'
);
SET @sql_drop_rule_code = IF(
  @has_rule_code > 0,
  'ALTER TABLE kb_billing_rule_versions DROP COLUMN rule_code',
  'SELECT 1'
);
PREPARE stmt_drop_rule_code FROM @sql_drop_rule_code;
EXECUTE stmt_drop_rule_code;
DEALLOCATE PREPARE stmt_drop_rule_code;

-- ---------------------------------------------------------------------------
-- 7) 重建以 rule_id 为前导的唯一键与查询索引
-- ---------------------------------------------------------------------------
SET @has_new_uk_version_code = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME   = 'kb_billing_rule_versions'
    AND INDEX_NAME   = 'uk_billing_rule_version_code'
);
SET @sql_add_new_uk_version_code = IF(
  @has_new_uk_version_code = 0,
  'ALTER TABLE kb_billing_rule_versions ADD UNIQUE KEY uk_billing_rule_version_code (rule_id, version_code)',
  'SELECT 1'
);
PREPARE stmt_add_new_uk_version_code FROM @sql_add_new_uk_version_code;
EXECUTE stmt_add_new_uk_version_code;
DEALLOCATE PREPARE stmt_add_new_uk_version_code;

SET @has_new_uk_hash = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME   = 'kb_billing_rule_versions'
    AND INDEX_NAME   = 'uk_billing_rule_version_hash'
);
SET @sql_add_new_uk_hash = IF(
  @has_new_uk_hash = 0,
  'ALTER TABLE kb_billing_rule_versions ADD UNIQUE KEY uk_billing_rule_version_hash (rule_id, uniq_hash)',
  'SELECT 1'
);
PREPARE stmt_add_new_uk_hash FROM @sql_add_new_uk_hash;
EXECUTE stmt_add_new_uk_hash;
DEALLOCATE PREPARE stmt_add_new_uk_hash;

SET @has_new_idx_active = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME   = 'kb_billing_rule_versions'
    AND INDEX_NAME   = 'idx_billing_rule_versions_active'
);
SET @sql_add_new_idx_active = IF(
  @has_new_idx_active = 0,
  'ALTER TABLE kb_billing_rule_versions ADD KEY idx_billing_rule_versions_active (rule_id, status, effective_from, effective_to)',
  'SELECT 1'
);
PREPARE stmt_add_new_idx_active FROM @sql_add_new_idx_active;
EXECUTE stmt_add_new_idx_active;
DEALLOCATE PREPARE stmt_add_new_idx_active;

-- ---------------------------------------------------------------------------
-- 8) 迁移后审计：结构与数据一致性
-- ---------------------------------------------------------------------------

-- 8.1 最终结构核对（rule_code 应为 0 行，rule_id 应为 NOT NULL）
SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_KEY
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'kb_billing_rule_versions'
  AND COLUMN_NAME IN ('rule_code', 'rule_id')
ORDER BY COLUMN_NAME;

-- 8.2 每条规则的版本行数（供人工核对「改造前后条数一致」，预期每规则 1 行）
SELECT 'POSTCHECK-2 每规则版本行数' AS check_item,
       br.id AS rule_id,
       br.code,
       COUNT(v.id) AS version_rows
FROM kb_billing_rules br
LEFT JOIN kb_billing_rule_versions v ON v.rule_id = br.id
GROUP BY br.id, br.code
ORDER BY br.id;

-- 8.3 新关联下的计费可达性自检：启用规则 + 启用版本应能被计费路径 join 到
SELECT 'POSTCHECK-3 启用规则缺启用版本（结果非空须排查）' AS check_item,
       br.id AS rule_id, br.code
FROM kb_billing_rules br
LEFT JOIN kb_billing_rule_versions v
       ON v.rule_id = br.id
      AND v.status = 1
WHERE br.is_deleted = 0
  AND br.status = 1
  AND v.id IS NULL;

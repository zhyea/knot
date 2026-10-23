-- ============================================================================
-- 2026-10-23 · 回滚：模型级 base_path 还原为 base_url
--
-- 恢复能力说明（务必读完再执行）：
--   1) 正向迁移把「模型 host」提升到了账户级。回滚时用
--      「账户 base_url + 模型 base_path」重建模型 base_url，对
--      「同账户单 host」的场景可完整还原。
--   2) 【无法还原】同一账户下原本存在多个 host 的模型（正向迁移的 PRECHECK-1
--      输出清单）——这些模型抬到账户级后共用了一个 host，回滚只能得到被选中的
--      那一个。必须按迁移时的清单手工补回。
--   3) 【无法还原】模型级 base_url 的尾斜杠、大小写等原始书写形态。
--
-- 幂等性：information_schema 判列是否存在，重复执行安全。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1) 恢复 kb_models.base_url 列（可空，与迁移前一致）
-- ---------------------------------------------------------------------------
SET @has_legacy_base_url = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME   = 'kb_models'
    AND COLUMN_NAME  = 'base_url'
);
SET @sql_add_base_url = IF(
  @has_legacy_base_url = 0,
  'ALTER TABLE kb_models ADD COLUMN base_url VARCHAR(255) DEFAULT NULL',
  'SELECT 1'
);
PREPARE stmt_add_base_url FROM @sql_add_base_url;
EXECUTE stmt_add_base_url;
DEALLOCATE PREPARE stmt_add_base_url;

-- ---------------------------------------------------------------------------
-- 2) 用「账户 base_url + base_path」回填
--    账户为空（AWS 凭据派生）的模型保持 NULL，回到「运行时按凭据推导」的旧行为
-- ---------------------------------------------------------------------------
UPDATE kb_models m
JOIN kb_provider_accounts pa ON pa.code = m.provider_account_code
SET m.base_url = CASE
    WHEN pa.base_url IS NULL OR pa.base_url = '' THEN NULL
    WHEN m.base_path IS NULL OR m.base_path = '' THEN TRIM(TRAILING '/' FROM TRIM(pa.base_url))
    ELSE CONCAT(TRIM(TRAILING '/' FROM TRIM(pa.base_url)), '/', TRIM(LEADING '/' FROM m.base_path))
  END;

-- ---------------------------------------------------------------------------
-- 3) 删除 base_path
-- ---------------------------------------------------------------------------
SET @has_base_path = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME   = 'kb_models'
    AND COLUMN_NAME  = 'base_path'
);
SET @sql_drop_base_path = IF(
  @has_base_path > 0,
  'ALTER TABLE kb_models DROP COLUMN base_path',
  'SELECT 1'
);
PREPARE stmt_drop_base_path FROM @sql_drop_base_path;
EXECUTE stmt_drop_base_path;
DEALLOCATE PREPARE stmt_drop_base_path;

-- ---------------------------------------------------------------------------
-- 4) 人工补漏清单：迁移时 PRECHECK-1 报出的多 host 账户，按清单逐模型恢复 base_url
--    （本节只提示，不改数据）
-- ---------------------------------------------------------------------------
SELECT 'ROLLBACK-REMINDER 多 host 原账户需手工补 base_url' AS check_item,
       m.model_code, m.provider_account_code, m.base_url
FROM kb_models m
JOIN kb_provider_accounts pa ON pa.code = m.provider_account_code
WHERE m.is_deleted = 0
  AND (m.base_url IS NULL OR m.base_url = '')
  AND pa.base_url IS NOT NULL
  AND pa.base_url <> '';

-- ---------------------------------------------------------------------------
-- 5) 最终结构核对
-- ---------------------------------------------------------------------------
SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND ((TABLE_NAME = 'kb_models' AND COLUMN_NAME IN ('base_url', 'base_path'))
    OR (TABLE_NAME = 'kb_provider_accounts' AND COLUMN_NAME = 'base_url'))
ORDER BY TABLE_NAME, COLUMN_NAME;

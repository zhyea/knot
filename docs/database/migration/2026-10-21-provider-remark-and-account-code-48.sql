-- ============================================================================
-- 2026-10-21 · 供应商信息（kb_providers）增加 remark 备注列；
--            供应商账户编码（kb_provider_accounts.code）长度 32 → 48，
--            连带放宽所有引用账户 code 的外键列到 48。
--
-- 背景：
--   1) 供应商信息需要"备注"自由文本，编辑抽屉维护、列表展示。
--   2) 上游账户编码 32 位不够用（部分供应商账户标识更长），放宽到 48。
--
-- 代码侧同步改动（全部在本批内）：
--   schema.sql（kb_providers 加 remark；kb_provider_accounts.code 及
--   kb_provider_credentials / kb_models / kb_provider_model_mappings 的
--   provider_account_code 统一 VARCHAR(48)）、ProviderCodes.MAX_LENGTH 32→48、
--   ProviderProfileEntity/Item/Service/Mapper.xml 透传 remark、
--   前端 ProviderProfileFormDrawer（备注输入）/ ProviderProfileListPanel（备注列）/
--   ProviderAccountFormDrawer（编码 maxlength 48）。
--
-- 本脚本只处理「既有库」：schema.sql 用 IF NOT EXISTS，既有库不会自动加列/改宽度；
--   新库直接由 schema.sql 生效，本脚本对它们应是无操作。
--
-- 幂等性：information_schema 判列是否存在 / 列宽是否已达标，再决定是否 ALTER；
--   重复执行安全。无 Flyway/Liquibase，需手动在目标库执行（约定：先迁移后打包）。
--   权限：仅需本仓库应用账号的 SELECT + ALTER。
--   说明：无回填——remark 可空，存量行即"无备注"；账户编码只放宽不缩短，存量数据无损。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1) kb_providers 增加 remark（若尚未存在）
-- ---------------------------------------------------------------------------
SET @has_remark = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME   = 'kb_providers'
    AND COLUMN_NAME  = 'remark'
);
SET @sql_remark = IF(
  @has_remark = 0,
  "ALTER TABLE kb_providers ADD COLUMN remark VARCHAR(255) DEFAULT NULL COMMENT '备注'",
  'SELECT 1'
);
PREPARE stmt_remark FROM @sql_remark;
EXECUTE stmt_remark;
DEALLOCATE PREPARE stmt_remark;

-- ---------------------------------------------------------------------------
-- 2) kb_provider_accounts.code 放宽到 VARCHAR(48)（唯一键 uk_provider_accounts_code
--    在 utf8mb4 下 48*4=192 字节，远低于 3072 索引上限，无需重建索引）
-- ---------------------------------------------------------------------------
SET @has_acct_code = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME   = 'kb_provider_accounts'
    AND COLUMN_NAME  = 'code'
    AND COLUMN_TYPE  = 'varchar(32)'
);
SET @sql_acct_code = IF(
  @has_acct_code > 0,
  'ALTER TABLE kb_provider_accounts MODIFY COLUMN code VARCHAR(48) NOT NULL',
  'SELECT 1'
);
PREPARE stmt_acct_code FROM @sql_acct_code;
EXECUTE stmt_acct_code;
DEALLOCATE PREPARE stmt_acct_code;

-- ---------------------------------------------------------------------------
-- 3) 连带放宽引用账户 code 的外键列（跨表绑定存 code 不存 id，列宽必须跟上，
--    否则长编码写入即报 "Data too long"）：
--    kb_provider_credentials / kb_models / kb_provider_model_mappings 的
--    provider_account_code 32 → 48
-- ---------------------------------------------------------------------------
SET @has_cred_fk = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME   = 'kb_provider_credentials'
    AND COLUMN_NAME  = 'provider_account_code'
    AND COLUMN_TYPE  = 'varchar(32)'
);
SET @sql_cred_fk = IF(
  @has_cred_fk > 0,
  'ALTER TABLE kb_provider_credentials MODIFY COLUMN provider_account_code VARCHAR(48) NOT NULL',
  'SELECT 1'
);
PREPARE stmt_cred_fk FROM @sql_cred_fk;
EXECUTE stmt_cred_fk;
DEALLOCATE PREPARE stmt_cred_fk;

SET @has_model_fk = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME   = 'kb_models'
    AND COLUMN_NAME  = 'provider_account_code'
    AND COLUMN_TYPE  = 'varchar(32)'
);
SET @sql_model_fk = IF(
  @has_model_fk > 0,
  'ALTER TABLE kb_models MODIFY COLUMN provider_account_code VARCHAR(48) NOT NULL',
  'SELECT 1'
);
PREPARE stmt_model_fk FROM @sql_model_fk;
EXECUTE stmt_model_fk;
DEALLOCATE PREPARE stmt_model_fk;

SET @has_mapping_fk = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME   = 'kb_provider_model_mappings'
    AND COLUMN_NAME  = 'provider_account_code'
    AND COLUMN_TYPE  = 'varchar(32)'
);
SET @sql_mapping_fk = IF(
  @has_mapping_fk > 0,
  'ALTER TABLE kb_provider_model_mappings MODIFY COLUMN provider_account_code VARCHAR(48) NOT NULL',
  'SELECT 1'
);
PREPARE stmt_mapping_fk FROM @sql_mapping_fk;
EXECUTE stmt_mapping_fk;
DEALLOCATE PREPARE stmt_mapping_fk;

-- ---------------------------------------------------------------------------
-- 4) 审计：核对最终列宽（remark 应为 varchar(255)，账户编码相关列应为 varchar(48)）
-- ---------------------------------------------------------------------------
SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND ((TABLE_NAME = 'kb_providers' AND COLUMN_NAME = 'remark')
    OR (TABLE_NAME = 'kb_provider_accounts' AND COLUMN_NAME = 'code')
    OR (COLUMN_NAME = 'provider_account_code'))
ORDER BY TABLE_NAME, COLUMN_NAME;

-- ============================================================================
-- 2026-10-23 · 上游接入策略重构：模型级 base_url 拆分为「账户 base_url + 模型 base_path」
--
-- 背景（详见 docs/上游接入策略重构-2026-10-23.md）：
--   1) 模型级 base_url 同时承担了「上游 host 覆盖」与「供应商给模型分配的独立
--      endpoint 路径前缀」两种语义，导致 host 被重复保存到每个模型。
--   2) 上游 host 的唯一来源改为 kb_provider_accounts.base_url；模型级只保留
--      base_path（路径前缀，如 Cloudsway 的 endpoint_id）。
--   3) 最终 URL = joinUrl(账户 base_url, 模型 base_path + 协议路径)。
--
-- 语义确认（robin 2026-10-23 拍板）：
--   - 账户 base_url **允许带路径前缀**（如 https://openrouter.ai/api/v1）；
--     拼接时按「路径段级重叠吸收」消除重复段，规则见 UpstreamUrlSupport。
--   - 模型级 base_path 不允许包含 scheme / host / query / fragment。
--
-- 执行顺序约定：先跑本脚本，再 package / 启动应用（代码与迁移同版本上线，
--   不保留双写或兼容分支）。
--
-- 幂等性：information_schema 判列是否存在，重复执行安全。
--   权限：本仓库应用账号的 SELECT + ALTER + UPDATE。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 0) 迁移前预检：以下三条 SELECT 必须人工过目，有输出就必须先处理再执行后续步骤
-- ---------------------------------------------------------------------------

-- 0.1 同一账户下存在多个不同 host → 无法自动合并，必须人工拆分账户或确认哪个是路径前缀
SELECT 'PRECHECK-1 同账户多 host（结果非空须人工处理）' AS check_item,
       provider_account_code,
       COUNT(DISTINCT base_url) AS host_count,
       GROUP_CONCAT(DISTINCT base_url ORDER BY base_url SEPARATOR ' | ') AS base_urls
FROM kb_models
WHERE is_deleted = 0
  AND base_url IS NOT NULL
  AND base_url <> ''
GROUP BY provider_account_code
HAVING COUNT(DISTINCT base_url) > 1;

-- 0.2 账户已有 base_url，但账户下模型指向别处 → 模型级 host 覆盖会丢失，必须人工确认
SELECT 'PRECHECK-2 账户 base_url 与模型不一致（结果非空须人工处理）' AS check_item,
       m.model_code,
       m.provider_account_code,
       m.base_url AS model_base_url,
       pa.base_url AS account_base_url
FROM kb_models m
JOIN kb_provider_accounts pa ON pa.code = m.provider_account_code
WHERE m.is_deleted = 0
  AND m.base_url IS NOT NULL
  AND m.base_url <> ''
  AND pa.base_url IS NOT NULL
  AND pa.base_url <> ''
  AND m.base_url <> pa.base_url
  AND m.base_url NOT LIKE CONCAT(TRIM(TRAILING '/' FROM pa.base_url), '/%');

-- 0.3 启用中的模型，其账户 base_url 与模型 base_url 都为空 → 改造后必然 503，必须先补配置。
--     排除可由凭据派生端点的接入策略（AWS_SIGV4）：它们的 host 本来就不落在库里。
SELECT 'PRECHECK-3 启用模型缺上游地址（结果非空须人工处理；已排除 AWS 派生账户）' AS check_item,
       m.model_code,
       m.provider_account_code
FROM kb_models m
JOIN kb_provider_accounts pa ON pa.code = m.provider_account_code
LEFT JOIN kb_provider_credentials c
       ON c.provider_account_code = pa.code
      AND c.status = 1
WHERE m.is_deleted = 0
  AND m.status = 1
  AND (m.base_url IS NULL OR m.base_url = '')
  AND (pa.base_url IS NULL OR pa.base_url = '')
  AND (c.auth_applier IS NULL OR c.auth_applier <> 'AWS_SIGV4');

-- ---------------------------------------------------------------------------
-- 1) 账户 base_url 回填：账户为空时，取该账户下模型统一的 base_url
--    仅处理「同账户模型 base_url 唯一」的情形（PRECHECK-1 无输出才安全）
-- ---------------------------------------------------------------------------
UPDATE kb_provider_accounts pa
JOIN (
  SELECT provider_account_code, MIN(base_url) AS unified_base_url
  FROM kb_models
  WHERE is_deleted = 0
    AND base_url IS NOT NULL
    AND base_url <> ''
  GROUP BY provider_account_code
  HAVING COUNT(DISTINCT base_url) = 1
) src ON src.provider_account_code = pa.code
SET pa.base_url = TRIM(TRAILING '/' FROM TRIM(src.unified_base_url))
WHERE pa.base_url IS NULL OR pa.base_url = '';

-- 账户 base_url 统一去尾斜杠（幂等；避免与路径拼接出现双斜杠）
UPDATE kb_provider_accounts
SET base_url = TRIM(TRAILING '/' FROM TRIM(base_url))
WHERE base_url IS NOT NULL
  AND base_url <> ''
  AND base_url LIKE '%/';

-- ---------------------------------------------------------------------------
-- 2) kb_models 增加 base_path（若尚未存在）
--    NOT NULL DEFAULT ''：空串表示「无模型级前缀」，与代码层归一化口径一致
-- ---------------------------------------------------------------------------
SET @has_base_path = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME   = 'kb_models'
    AND COLUMN_NAME  = 'base_path'
);
SET @sql_base_path = IF(
  @has_base_path = 0,
  "ALTER TABLE kb_models ADD COLUMN base_path VARCHAR(255) NOT NULL DEFAULT '' COMMENT '模型级路径前缀（如供应商 endpoint_id），空表示无前缀；不含 scheme/host/query/fragment'",
  'SELECT 1'
);
PREPARE stmt_base_path FROM @sql_base_path;
EXECUTE stmt_base_path;
DEALLOCATE PREPARE stmt_base_path;

-- ---------------------------------------------------------------------------
-- 3) base_path 回填：模型 base_url 比账户 base_url 多出的路径部分
--    3.1 与账户完全相同 → ''（种子数据全部落这里）
--    3.2 以「账户 base_url + /」开头 → 剥掉账户前缀，剩余部分前面补 '/'
--    3.3 其余情形（真 host 差异）不回填，留给 PRECHECK-2 的人工清单
-- ---------------------------------------------------------------------------
UPDATE kb_models m
JOIN kb_provider_accounts pa ON pa.code = m.provider_account_code
SET m.base_path = CASE
    WHEN m.base_url IS NULL OR m.base_url = '' THEN ''
    WHEN pa.base_url IS NULL OR pa.base_url = '' THEN ''
    WHEN m.base_url = pa.base_url THEN ''
    WHEN m.base_url LIKE CONCAT(pa.base_url, '/%')
      THEN CONCAT('/', TRIM(TRAILING '/' FROM SUBSTRING(m.base_url, CHAR_LENGTH(pa.base_url) + 2)))
    ELSE m.base_path
  END
WHERE m.base_path = '';

-- ---------------------------------------------------------------------------
-- 4) 迁移后审计：仍有 base_url 与账户不一致的模型（应只剩 PRECHECK-2 已确认的行）
-- ---------------------------------------------------------------------------
SELECT 'POSTCHECK 未自动合并的模型（须人工确认 base_path）' AS check_item,
       m.model_code, m.provider_account_code, m.base_url, pa.base_url AS account_base_url
FROM kb_models m
JOIN kb_provider_accounts pa ON pa.code = m.provider_account_code
WHERE m.is_deleted = 0
  AND m.base_url IS NOT NULL
  AND m.base_url <> ''
  AND (pa.base_url IS NULL OR pa.base_url = '' OR m.base_url <> pa.base_url);

-- ---------------------------------------------------------------------------
-- 5) 删除 kb_models.base_url（无索引引用，MariaDB 可直接 DROP）
-- ---------------------------------------------------------------------------
SET @has_legacy_base_url = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME   = 'kb_models'
    AND COLUMN_NAME  = 'base_url'
);
SET @sql_drop_base_url = IF(
  @has_legacy_base_url > 0,
  'ALTER TABLE kb_models DROP COLUMN base_url',
  'SELECT 1'
);
PREPARE stmt_drop_base_url FROM @sql_drop_base_url;
EXECUTE stmt_drop_base_url;
DEALLOCATE PREPARE stmt_drop_base_url;

-- ---------------------------------------------------------------------------
-- 6) 最终结构核对
-- ---------------------------------------------------------------------------
SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND ((TABLE_NAME = 'kb_models' AND COLUMN_NAME IN ('base_url', 'base_path'))
    OR (TABLE_NAME = 'kb_provider_accounts' AND COLUMN_NAME = 'base_url'))
ORDER BY TABLE_NAME, COLUMN_NAME;

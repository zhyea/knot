-- 供应商凭据新增「鉴权策略」列（auth_applier）
-- 背景：鉴权从请求适配器剥离为可插拔策略族（UpstreamAuthApplier），策略 code 落到供应商凭据上，
--       协议固定头（如 anthropic-version）随策略收口，不再硬编码在适配器里。
-- 幂等：可重复执行。

-- 1) 新增列（NULL 表示未配置，运行时由 UpstreamAuthApplierCatalog 回退默认 Bearer）
ALTER TABLE kb_provider_credentials
  ADD COLUMN IF NOT EXISTS auth_applier VARCHAR(64) DEFAULT NULL
  COMMENT '鉴权策略编码（UpstreamAuthApplier code），空则回退默认 Bearer';

-- 2) 回填存量：anthropic 供应商账户用 ANTHROPIC_API_KEY（x-api-key + anthropic-version），其余用 BEARER
UPDATE kb_provider_credentials c
  JOIN kb_provider_accounts a ON a.id = c.provider_account_id
  SET c.auth_applier = CASE WHEN a.provider_code = 'anthropic' THEN 'ANTHROPIC_API_KEY' ELSE 'BEARER' END
  WHERE c.auth_applier IS NULL OR c.auth_applier = '';

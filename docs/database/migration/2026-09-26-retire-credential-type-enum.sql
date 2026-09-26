-- 退役 DB 枚举分类 credential_type（2026-09-26）
--
-- 背景：认证类型此前在两处各有一份，且已经分叉：
--   - ks_enum_categories.id=4 / ks_enum_configs.category_id=4：API_KEY / OAUTH_TOKEN / BEARER_TOKEN
--   - ProviderCredentialTypeEnum：api-key / aws-auth / gemini-auth / ak-sk / custom（含 requiredFields）
-- 决策：以代码枚举为准。认证类型带 requiredFields 行为元数据，DB 扁平枚举表达不了，
--       且后端 ProviderService.validateCredential 早已按代码枚举校验必填字段。
--
-- 配套：GET /api/provider-accounts/credential-types 下发 code/label/requiredFields；
--       前端 ProviderAccountFormDrawer 已改为消费该接口，不再读本分类。

DELETE FROM ks_enum_configs WHERE category_id = 4;
DELETE FROM ks_enum_categories WHERE id = 4 AND category = 'credential_type';

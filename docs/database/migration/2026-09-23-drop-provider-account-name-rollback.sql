-- 回滚：恢复 kb_provider_accounts.name（存量行以 code 回填，满足 NOT NULL）
ALTER TABLE kb_provider_accounts ADD COLUMN name VARCHAR(100) NOT NULL AFTER code;
UPDATE kb_provider_accounts SET name = code;

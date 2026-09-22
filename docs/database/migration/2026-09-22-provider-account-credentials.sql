-- 供应商账户与认证配置扩展
-- 执行前请确认当前数据库已完成 kb_ 表前缀迁移。

ALTER TABLE kb_provider_accounts
    ADD COLUMN base_url VARCHAR(255) DEFAULT NULL AFTER provider_id,
    DROP COLUMN contact_name,
    DROP COLUMN contact_phone,
    DROP COLUMN provider_type;

ALTER TABLE kb_provider_credentials
    ADD COLUMN encrypted_config TEXT DEFAULT NULL
        COMMENT '加密后的任意键值认证配置 JSON';

ALTER TABLE kb_provider_credentials
    DROP COLUMN encrypted_key,
    DROP COLUMN encrypted_secret,
    DROP COLUMN token_value,
    DROP COLUMN expire_at;

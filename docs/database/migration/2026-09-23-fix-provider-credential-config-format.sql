-- 修复 kb_provider_credentials.encrypted_config 种子密文的语义过期问题
--
-- 背景
--   2026-09-22 的 2026-09-22-provider-account-credentials.sql 只做了
--     ALTER TABLE kb_provider_credentials ADD COLUMN encrypted_config TEXT ...
--     ALTER TABLE kb_provider_credentials DROP COLUMN encrypted_key, encrypted_secret, token_value, expire_at
--   没有做数据形态转换。旧列的语义是「单个密钥」，于是 data.sql 里的三条种子密文
--   解密后是裸密钥字符串（sk-openai-demo-xxxxx / sk-ant-api03-demo-xxxxx / sk-deepseek-demo-xxxxx）；
--   而新代码契约（ProviderCredentialSupport.saveAuthConfig）恒定写入 JSON 对象 {"apiKey":"..."}。
--
-- 症状
--   ProviderCredentialSupport.toAuthConfig -> JsonKit.fromJson 抛
--   JsonParseException: Unrecognized token 'sk'，被 catch 后静默降级为 defaultAuthConfig() = {"apiKey":""}。
--   接口仍返回 200，但：
--     · 供应商账户编辑抽屉里 API Key 显示为空，保存被前端必填校验拦住；
--     · gateway UpstreamProxyClient 拿到空 apiKey -> adapter 跳过 Authorization 头 -> 上游 401。
--
-- 为什么必须单独执行本脚本
--   data.sql 用的是 INSERT IGNORE + 显式主键 id，重复执行不会覆盖已存在的行，
--   所以即使把新密文写进 data.sql，存量库（行已存在）也不会自愈。改 data.sql 只对新库生效。
--
-- 幂等性
--   每条 UPDATE 都带旧密文原值作为条件，重复执行匹配 0 行，不会覆盖用户在界面上保存过的真实 Key。
--   执行后用文件末尾的 SELECT 核对 id=1/2/3 的 encrypted_config 是否已变为新密文。
--
-- 更换 KNOT_CREDENTIAL_ENCRYPTION_KEY 后如何重新生成密文
--   本脚本里的密文由默认密钥 knot-dev-credential-encryption-key（SHA-256 派生 AES-256 密钥）加密，
--   换密钥后必须重新生成，否则解密会抛 IllegalStateException("credential decrypt failed") -> 接口 500。
--   生成方式：取 JSON 明文（如 {"apiKey":"sk-xxx"}），用 AES/GCM/NoPadding、12 字节随机 IV、
--   128 位 tag，输出 "ENC:" + Base64(IV + ciphertext + tag)。

UPDATE kb_provider_credentials
SET encrypted_config = 'ENC:Gxdt09+Mk+BnH3+meLh03rvVO/lr/5vjxcjK0RNW0PgB0X/vYX0QzaixD9xPh/gKVYTC/0JSNl4lU7zy+g=='
WHERE id = 1
  AND encrypted_config = 'ENC:I093hp4rWvI0txMTx5d+MwVbdLU8NQ/9X1J6r33Y3gJwGT2slww78nJbasZjfOfD';

UPDATE kb_provider_credentials
SET encrypted_config = 'ENC:v3v8kgxDLYoAKyNICNxGKU655xOH3Sd1k2qtSS+bgYcvtq/fXGyufZ1gA+Qr2LMBT1eRTJ6teFCPY5r/OvTjqw=='
WHERE id = 2
  AND encrypted_config = 'ENC:RnptV2Rw7zw4QCm4qHUba0qKaxKQfsv6l3aymW/XfF3j0Fs/k02352ew7lE/tcRyax5/';

UPDATE kb_provider_credentials
SET encrypted_config = 'ENC:pnMjBuMkVI19v432F8xRJ1U976XGPXwM19DakGTc8GoKMEvIy/6QcOMUbwwsRbXMJ4kqXsY+X+sKrnBuuijw'
WHERE id = 3
  AND encrypted_config = 'ENC:mFDjmYE5M54d8MWNXUcTQS9Ngk7+dv3YDuH6o0x8hv6rehtmM9I38RJUeGrpc0zF4Tw=';

-- 核对：期望 id=1/2/3 的 encrypted_config 均以 ENC:Gxdt0 / ENC:v3v8k / ENC:pnMjB 开头
SELECT id, provider_account_id, credential_type, LEFT(encrypted_config, 10) AS enc_head, status
FROM kb_provider_credentials
ORDER BY id;

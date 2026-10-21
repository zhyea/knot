-- AWS Bedrock（runtime + mantle）种子数据：纯数据，无 DDL，可重复执行（INSERT IGNORE）。
--
-- 背景：Bedrock 对接不新增任何端点或表，只靠扩展点族（凭据类型 / 鉴权策略 / 请求适配器 /
-- 用量提取器）落地，故既有库只需补齐这几行主数据即可用。
--
-- 关键口径（2026-10-21 按 AWS 官方文档核实）：
--   * bedrock-runtime  host = bedrock-runtime.<region>.amazonaws.com       SigV4 service = bedrock
--   * bedrock-mantle    host = bedrock-mantle.<region>.api.aws              SigV4 service = bedrock-mantle
--   两者的 host 形态与签名 service 名都不同，而 endpointType 挂在凭据上，故一个供应商账户
--   只能对应一种端点形态 —— 这里拆成 aws-bedrock-runtime / aws-bedrock-mantle 两个账户。
--
-- ⚠ 凭据是占位值：AK/SK 为假值，密文由开发默认密钥（knot-dev-credential-encryption-key）加密。
--   上线前必须在管理台把两条 aws-auth 凭据换成真实 AK/SK；
--   若生产环境已改 KNOT_CREDENTIAL_ENCRYPTION_KEY，这两行密文必须重新生成（旧密钥解不开）。

-- 供应商
INSERT IGNORE INTO kb_providers (id, code, name, tag) VALUES
(7, 'aws', 'AWS Bedrock', '云厂商');

-- 供应商账户（两类端点各一个）
INSERT IGNORE INTO kb_provider_accounts (id, provider_code, code, status) VALUES
(5, 'aws', 'aws-bedrock-runtime', 1),
(6, 'aws', 'aws-bedrock-mantle',  1);

-- 供应商凭据（占位 AK/SK，必须替换）
INSERT IGNORE INTO kb_provider_credentials (id, provider_account_code, credential_type, auth_applier, encrypted_config, status) VALUES
(4, 'aws-bedrock-runtime', 'aws-auth', 'AWS_SIGV4', 'ENC:v2:RBcQiZXA4xtu1JzdUeeUYK0N8McJfBysNTivpk84EbZtuX6w2y+og03WhpCBSMeOGRgSOQi3xrwLtD8QJvd8woJFeJU2Ywqk7VyCMzKb2DMTemI5TyHsr9sO3H3h23MwMBQpWnsc/ZTZ2cO5xyCcpZtpgQM+lENnniJKzWhJ8gFS1UayJqyFMpc15+dN+JpWJ59XhEE3FrM61/jLnDwwZYECvwTOI5A=', 1),
(5, 'aws-bedrock-mantle',  'aws-auth', 'AWS_SIGV4', 'ENC:v2:i3tPFtFrDBtlI7tOb528ptatgvdcq146hlSMHrFP2hvjRktj64+md+piG6HQxP2w6WMLshEjAZe2G0Ja+S30mSf9uD9b1/XBYBykWm1q+UxVS/aBZFoV9spWbcwrSiAtn1bw4AFPH7q1FJSXZU542CHyH2s8zDDZhWkymGQxntEU79egcNHkdopmfqopvnrs65bv38fjSChprOWE+/LvrPOSqxWoHw==', 1);

-- 模型（base_url 留 NULL：host 由凭据的 region + endpointType 推导，填了会盖掉端点推导）
INSERT IGNORE INTO kb_models (id, provider_account_code, model_code, upstream_model, version, base_url, status) VALUES
(11, 'aws-bedrock-runtime', 'bedrock-claude-sonnet-4', 'anthropic.claude-sonnet-4-20250514-v1:0', '2025-05-14', NULL, 1),
(12, 'aws-bedrock-mantle',  'bedrock-gpt56-luna',      'openai.gpt-5.6-luna',                     '2026-01-01', NULL, 1);

-- 模型 API 协议绑定
--   runtime：api_path 留 NULL，由 BEDROCK_CONVERSE 适配器按 /model/{模型ID}/converse 推导
--            （模型 ID 里的 ':' 会 RFC3986 编码成 %3A，签名覆盖的路径与实际发出必须同一个串）
--   mantle ：不支持 Converse，只暴露 OpenAI 兼容，直接复用既有 OPENAI_COMPATIBLE 适配器
INSERT IGNORE INTO kb_model_api_bindings (id, model_code, protocol, api_path, request_adapter, usage_extractor, status, remark) VALUES
(9,  'bedrock-claude-sonnet-4', 'CHAT_COMPLETIONS', NULL, 'BEDROCK_CONVERSE',  'BEDROCK_CONVERSE', 1, 'Bedrock Converse（runtime 端点）'),
(10, 'bedrock-gpt56-luna',      'CHAT_COMPLETIONS', NULL, 'OPENAI_COMPATIBLE', 'DEFAULT-TOKEN',    1, 'Bedrock mantle OpenAI 兼容端点');

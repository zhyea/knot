-- 上游适配器重命名：request_adapter 旧 code → 新 code
--
-- 背景（2026-10-25）：适配器体系重构——旧体系 4 个适配器（Passthrough / OpenAiCompatible /
-- Anthropic / BedrockConverse）中有三个是多协议大杂烩，且命名不带转换方向。新体系 9 个适配器：
--   * 命名规则 <供应商>+<原协议>+<新协议>+UpstreamAdapter（泛化转换免供应商段，透传为 PassthroughUpstreamAdapter）；
--   * 一适配器只服务一种协议（透传豁免）；
--   * 接口 UpstreamAdapter 双向职责：请求侧 resolvePath/buildRequestBody/resolveContentType，
--     响应侧 handleResponse（整包）+ handleStreamEvent（流式），同批新增。
--
-- 本脚本只改数据（kb_model_api_bindings.request_adapter 列值），列名不变（改列会牵动
-- schema + mapper + 前端，无收益）。新旧 code 映射与行为等价性：
--
-- | 旧 code              | 新 code                                | 行为等价依据 |
-- |----------------------|----------------------------------------|--------------|
-- | OPENAI_COMPATIBLE    | PASSTHROUGH                            | 旧实现仅在 MESSAGES 协议下做 messages→chat 转换与路径改写；存量 6 个绑定协议均非 MESSAGES 且 5 个带 api_path，请求/响应两侧皆与透传逐字节一致。该转换职责由新 MessagesToChatCompletionsUpstreamAdapter 承接（当前无绑定使用） |
-- | ANTHROPIC            | PASSTHROUGH                            | 旧实现在 MESSAGES 协议下 resolvePath 取 apiPath、buildRequestBody 原样返回、无响应覆写——即纯透传；唯一绑定（claude-sonnet-4，MESSAGES + /v1/messages）逐项一致 |
-- | BEDROCK_CONVERSE     | AWS_BEDROCK_CHAT_COMPLETIONS_TO_CONVERSE | 同名重构：唯一绑定（bedrock-claude-sonnet-4）协议为 CHAT_COMPLETIONS，对应拆分后的 chat→converse 适配器 |
--
-- 执行顺序：必须先跑本脚本，再跑 2026-10-25-bedrock-usage-extractor-default.sql
-- （后者的 WHERE 用的是重命名后的 AWS_BEDROCK_CHAT_COMPLETIONS_TO_CONVERSE）。
--
-- 零历史包袱：项目未上线，直接 UPDATE 修正，不写回退兼容。UPDATE 以旧值为 WHERE 条件，
-- 重复执行无副作用（幂等）。
--
-- 不在本脚本范围（存量问题，另开单修）：
--   * qwen-image / qwen-image-edit 两行绑定的 QWEN 适配器 code 从来没有任何实现类
--     （旧目录 4 个适配器亦无 QWEN），运行期恒经 UpstreamAdapterRegistry 回落透传；
--   * docs/database/migration/2026-10-22-aws-bedrock-seed.sql 为已执行的历史记录，不回改，
--     新库由 knot-admin/src/main/resources/db/data.sql 直接播种新值。

UPDATE kb_model_api_bindings
SET request_adapter = 'PASSTHROUGH'
WHERE request_adapter = 'OPENAI_COMPATIBLE';

UPDATE kb_model_api_bindings
SET request_adapter = 'PASSTHROUGH'
WHERE request_adapter = 'ANTHROPIC';

UPDATE kb_model_api_bindings
SET request_adapter = 'AWS_BEDROCK_CHAT_COMPLETIONS_TO_CONVERSE'
WHERE request_adapter = 'BEDROCK_CONVERSE';

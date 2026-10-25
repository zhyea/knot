-- Bedrock runtime 绑定修正：usage_extractor BEDROCK_CONVERSE → DEFAULT-TOKEN
--
-- 背景（2026-10-25）：BedrockConverseUsageSupport 与 BedrockConverseUsageExtractor 已删除。
-- 整包路径上用量提取的输入是 adapter.handleResponse 的产物
-- （AbstractUpstreamProtocolExecutor.executeBuffered：先 handleResponse 再 extract），
-- usage 已被 Bedrock 适配器族（经 AwsBedrockConverseSupport.handleConverseResponse 的
-- toOpenAiUsage）转成 OpenAI snake_case 形状，
-- 通用 DEFAULT-TOKEN 提取器（TokenBillingUsage.from 的别名表，含
-- cache_read_input_tokens / cache_write_input_tokens）即可读出全部字段。
-- 原专用提取器的 camelCase 解析分支在生产链路（含影子链）不可达。
--
-- 零历史包袱：未上线，直接 UPDATE 修正，不写回退兼容。
-- stream_usage_extractor 为空即复用 usage_extractor（schema 注释），无需单独处理。
--
-- ⚠ 与 2026-10-25-upstream-adapter-rename.sql 的执行顺序：本脚本WHERE用的已是重命名后的
--   request_adapter 新值 AWS_BEDROCK_CHAT_COMPLETIONS_TO_CONVERSE，故必须先跑重命名迁移再跑本脚本
--   （或本脚本内联等价的 UPDATE 后再改名，二选一，勿调换）。

UPDATE kb_model_api_bindings
SET usage_extractor = 'DEFAULT-TOKEN'
WHERE request_adapter = 'AWS_BEDROCK_CHAT_COMPLETIONS_TO_CONVERSE'
  AND usage_extractor = 'BEDROCK_CONVERSE';

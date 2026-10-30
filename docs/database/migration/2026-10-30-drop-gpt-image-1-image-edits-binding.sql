-- ============================================================
-- 2026-10-30  摘除 gpt-image-1 的图像编辑绑定
--
-- 背景：网关 POST /openai/v1/images/edits 改为「JSON 原样透传」——
-- 收客户端原始 JSON 直接转发，不再解析 multipart 表单，上行 contentType
-- 由 multipart/form-data 改为 application/json。
--
-- 影响面（已核实）：
--   * kb_model_api_bindings id=8（gpt-image-1 / IMAGE_EDITS /
--     api_path=/v1/images/edits / PASSTHROUGH）指向 OpenAI 原生 edits 端点，
--     该端点只收 multipart/form-data；网关改发 JSON 后会被上游拒绝
--     （415/400），故整条绑定摘除；
--   * id=6（qwen-image-edit / IMAGE_EDITS）保留：其 api_path 为 QWEN 的
--     /api/v1/services/aigc/multimodal-generation/generation，本就是 JSON
--     端点，JSON 透传下行为正确；
--   * id=7（gpt-image-1 / IMAGE_GENERATIONS）不受本次改动影响，保留；
--   * kb_model_api_bindings 之外的表不动；模型、路由规则、权限均不动。
--
-- 幂等：按 (model_code, protocol) 删除，可重复执行。
-- MariaDB：单行 DELETE，无 DDL，无索引变更。
-- ============================================================

DELETE FROM kb_model_api_bindings
WHERE model_code = 'gpt-image-1'
  AND protocol = 'IMAGE_EDITS';

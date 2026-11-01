-- =============================================================================
-- 2026-11-01 接入第二个外部模型源 XFusionAI（llm.azopenai.com）
--
-- 两行数据，同一批变更：
--   §1 kx_model_sources 加 XFUSIONAI 源
--   §2 ks_scheduled_tasks 加 XFUSIONAI_MODEL_SYNC 定时任务（默认停用）
--
-- ── 为什么必须有这个脚本（不是只改 data.sql 就够了）────────────────────────
-- data.sql 里的种子是 `INSERT IGNORE` + 显式 id，只在**空库**生效：
-- 存量库主键冲突会静默跳过，新源永远插不进去，而外部模型页的「来源」下拉是
-- 读这张表渲染的 —— 下拉里没有 XFUSIONAI，就没有任何入口能触发它的首次同步
-- （ensureSource() 只在同步时才补源，鸡生蛋）。所以存量库必须靠本脚本补。
--
-- 因此这里**不写死 id**：存量库的 AUTO_INCREMENT 落点不可预知，写死 id 一旦撞上
-- 已占用的主键，INSERT IGNORE 会静默跳过整行，现象是新源「明明没插进去却不报错」。
-- 唯一键分别是 uk_external_model_sources_code(source_code) 与
-- uk_scheduled_tasks_code(task_code)，靠它们做幂等。
--
-- ⚠ 本次只同步模型目录，不落定价：XFusionAI 的 model_ratio 等字段是 New-API 的相对
--    倍率，quota→金额换算基准不在响应里，全部留在 raw_json 中，等口径定了再做。
--
-- 幂等：两处都靠唯一键 + INSERT IGNORE，重复执行无副作用。
-- =============================================================================

-- ---------------------------------------------------------------------------
-- §1 外部模型源
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO kx_model_sources (
  source_code, source_name, source_url, api_url, source_type, status
) VALUES (
  'XFUSIONAI', 'XFusionAI Pricing', 'https://llm.azopenai.com/pricing',
  'https://llm.azopenai.com/api/pricing', 'MODEL_CATALOG', 1
);

-- ---------------------------------------------------------------------------
-- §2 定时同步任务
--
-- status=0（停用）：与 OpenRouter 那条一致，种子默认不开启，要每天跑由运营在
-- 「定时任务」界面自行启用。cron 4:30，与 OpenRouter 的 4:00 错开半小时。
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO ks_scheduled_tasks (
  task_code, task_name, handler_code, cron_expression, execution_mode, status, description
) VALUES (
  'xfusionai-model-sync', 'XFusionAI 模型同步', 'XFUSIONAI_MODEL_SYNC',
  '0 30 4 * * ?', 'SINGLE', 0, '同步 XFusionAI 模型到外部模型库'
);

-- ---------------------------------------------------------------------------
-- 自检
-- ---------------------------------------------------------------------------

-- 1) 源应返回 1 行，api_url 必须是 /api/pricing（不是 /pricing 页面）
SELECT source_code, source_name, api_url, source_type, status
FROM kx_model_sources
WHERE source_code = 'XFUSIONAI';

-- 2) 定时任务应返回 1 行，handler_code 必须与 XFusionAIModelSyncHandler.CODE 一致
SELECT task_code, task_name, handler_code, cron_expression, execution_mode, status
FROM ks_scheduled_tasks
WHERE task_code = 'xfusionai-model-sync';

-- 3) 两个外部源应同时在册（返回 2 行即为成功）
SELECT source_code, source_name, status
FROM kx_model_sources
ORDER BY id;

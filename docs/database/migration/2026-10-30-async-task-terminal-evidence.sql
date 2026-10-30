-- ============================================================================
-- 2026-10-30 · 异步任务终态证据（terminal_body / settlement_json）
--
-- 为什么加这两列：
--   1) settlement_json —— 网关对异步任务算出的账，原先只存在于 Kafka 流量事件里，
--      没有任何接口/表可查。于是「这次视频调用到底算了多少、算得对不对」在管理面不可回答，
--      计费校验（自检的 GATEWAY_RECOMPUTE_MATCH）只能恒 SKIP。
--   2) terminal_body  —— 上游终态查询响应原文。视频类用量提取器从**响应**取输出时长与
--      产物数（VideoUsageFacts.of 的 body 参数），请求体只提供分辨率/声音/参考图。
--      这份响应原先用完即弃，离线重算没有输入 → 重算对照同样无从谈起。
--
-- 写入方式：与终态 CAS（AsyncTaskMapper.complete）同一条 UPDATE。理由见 mapper 注释——
--   终态一旦落下，行就退出在途集合，后续没有任何 UPDATE 入口；拆两次写会留下
--   「任务已终态、账却永远没有」的行，而重试也不会补。
--
-- 幂等性：加列先查 information_schema，重复执行跳过（MySQL 5.7 不支持 ADD COLUMN IF NOT EXISTS）。
--
-- 存量语义：两列均可空。老任务在查询端点上不带 knot_extend / upstream_body，
--   前端按「本次响应未返回结算账目」降级。**不写回填**：历史任务的账已经不在任何地方，
--   编一个出来比空着更糟。
--
-- 回滚：见同目录 2026-10-30-async-task-terminal-evidence-rollback.sql
--   ⚠ 回滚会丢掉已结算任务的账与上游原文，只在「本次上线后尚无真实异步任务」时安全。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1) kb_async_tasks.terminal_body
-- ---------------------------------------------------------------------------
SET @has_terminal_body = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME   = 'kb_async_tasks'
    AND COLUMN_NAME  = 'terminal_body'
);

SET @ddl = IF(@has_terminal_body = 0,
  'ALTER TABLE kb_async_tasks ADD COLUMN terminal_body LONGTEXT DEFAULT NULL COMMENT ''上游终态查询响应原文（计费复现与排障依据；失败/超时没有响应时为空）'' AFTER result_json',
  'SELECT ''terminal_body 已存在，跳过'' AS skipped);
PREPARE stmt_terminal_body FROM @ddl;
EXECUTE stmt_terminal_body;
DEALLOCATE PREPARE stmt_terminal_body;

-- ---------------------------------------------------------------------------
-- 2) kb_async_tasks.settlement_json
-- ---------------------------------------------------------------------------
SET @has_settlement_json = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME   = 'kb_async_tasks'
    AND COLUMN_NAME  = 'settlement_json'
);

SET @ddl = IF(@has_settlement_json = 0,
  'ALTER TABLE kb_async_tasks ADD COLUMN settlement_json JSON DEFAULT NULL COMMENT ''终态结算快照（knot_extend 同形 usage+billing；失败/超时不计费故为空）'' AFTER terminal_body',
  'SELECT ''settlement_json 已存在，跳过'' AS skipped);
PREPARE stmt_settlement_json FROM @ddl;
EXECUTE stmt_settlement_json;
DEALLOCATE PREPARE stmt_settlement_json;

-- ---------------------------------------------------------------------------
-- 3) 迁移后自检（全部应返回预期值；不修改任何数据）
-- ---------------------------------------------------------------------------

-- CHECK 1：两列都已存在（期望 2）
SELECT COUNT(*) AS evidence_columns_present
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME   = 'kb_async_tasks'
  AND COLUMN_NAME IN ('terminal_body', 'settlement_json');

-- CHECK 2：存量终态行两列均为空（期望 0 → 没有意外的历史数据被写成非空）
SELECT COUNT(*) AS legacy_terminal_rows_with_evidence
FROM kb_async_tasks
WHERE status IN (2, 3, 4, 5)
  AND (terminal_body IS NOT NULL OR settlement_json IS NOT NULL);

-- CHECK 3：在途行不得有结算（期望 0；有值说明有代码绕过了终态 CAS 直接写列）
SELECT COUNT(*) AS in_flight_rows_with_settlement
FROM kb_async_tasks
WHERE status IN (0, 1)
  AND settlement_json IS NOT NULL;

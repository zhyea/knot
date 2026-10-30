-- ============================================================================
-- 2026-10-30 · 回滚：异步任务终态证据（terminal_body / settlement_json）
--
-- ⚠ 回滚会丢掉已结算任务的账目与上游终态原文，只在「本次上线后尚无真实异步任务」时安全。
--    一旦有真实任务跑过，这两列就是唯一的事后对账依据，删掉等于把那一批任务变成不可复算。
--
-- 幂等：先查 information_schema，列不存在则跳过。
-- ============================================================================

SET @has_settlement_json = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME   = 'kb_async_tasks'
    AND COLUMN_NAME  = 'settlement_json'
);

SET @ddl = IF(@has_settlement_json = 1,
  'ALTER TABLE kb_async_tasks DROP COLUMN settlement_json',
  'SELECT ''settlement_json 不存在，跳过'' AS skipped);
PREPARE stmt_drop_settlement_json FROM @ddl;
EXECUTE stmt_drop_settlement_json;
DEALLOCATE PREPARE stmt_drop_settlement_json;

SET @has_terminal_body = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME   = 'kb_async_tasks'
    AND COLUMN_NAME  = 'terminal_body'
);

SET @ddl = IF(@has_terminal_body = 1,
  'ALTER TABLE kb_async_tasks DROP COLUMN terminal_body',
  'SELECT ''terminal_body 不存在，跳过'' AS skipped);
PREPARE stmt_drop_terminal_body FROM @ddl;
EXECUTE stmt_drop_terminal_body;
DEALLOCATE PREPARE stmt_drop_terminal_body;

-- 回滚后自检：两列都不存在（期望 0）
SELECT COUNT(*) AS evidence_columns_remaining
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME   = 'kb_async_tasks'
  AND COLUMN_NAME IN ('terminal_body', 'settlement_json');

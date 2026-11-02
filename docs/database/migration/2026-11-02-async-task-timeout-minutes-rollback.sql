-- ============================================================================
-- 2026-11-02 · 回滚：异步任务档案超时改回「毫秒」
--
-- ⚠ 只在「新版本撤下、旧版本（读 timeout_ms）要重新跑」时用。
-- ⚠ 精度按分钟计：回滚写回的是 timeout_minutes × 60000。原本是整分的值原样复原；
--    若某个旧值不是整分（例如 90s），它在正向迁移时已被 CEIL 成 2 分钟，回滚后是 120000ms，
--    回不到 90000ms —— 这是分钟口径的固有代价，不是脚本缺陷。
--
-- 幂等：加列 / 删列都先查 information_schema，重复执行跳过。
-- 顺序：**先跑本脚本，再启旧版本**（旧代码读不到 timeout_ms 会直接报列不存在）。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1) 加回 timeout_ms
-- ---------------------------------------------------------------------------
SET @has_timeout_ms = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME   = 'kb_async_task_profiles'
    AND COLUMN_NAME  = 'timeout_ms'
);

SET @ddl = IF(@has_timeout_ms = 0,
  'ALTER TABLE kb_async_task_profiles ADD COLUMN timeout_ms BIGINT NOT NULL DEFAULT 1800000 COMMENT ''knot 侧任务超时（默认 30min），到期转 EXPIRED'' AFTER max_attempts',
  'SELECT ''timeout_ms 已存在，跳过'' AS skipped);
PREPARE stmt_timeout_ms FROM @ddl;
EXECUTE stmt_timeout_ms;
DEALLOCATE PREPARE stmt_timeout_ms;

-- ---------------------------------------------------------------------------
-- 2) 回填毫秒（仅当分钟列还在）
-- ---------------------------------------------------------------------------
SET @has_timeout_minutes = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME   = 'kb_async_task_profiles'
    AND COLUMN_NAME  = 'timeout_minutes'
);

SET @ddl = IF(@has_timeout_minutes = 1,
  'UPDATE kb_async_task_profiles SET timeout_ms = timeout_minutes * 60000',
  'SELECT ''timeout_minutes 不存在，跳过回填'' AS skipped);
PREPARE stmt_backfill FROM @ddl;
EXECUTE stmt_backfill;
DEALLOCATE PREPARE stmt_backfill;

-- ---------------------------------------------------------------------------
-- 3) 删掉分钟列
-- ---------------------------------------------------------------------------
SET @ddl = IF(@has_timeout_minutes = 1,
  'ALTER TABLE kb_async_task_profiles DROP COLUMN timeout_minutes',
  'SELECT ''timeout_minutes 不存在，跳过'' AS skipped);
PREPARE stmt_drop_timeout_minutes FROM @ddl;
EXECUTE stmt_drop_timeout_minutes;
DEALLOCATE PREPARE stmt_drop_timeout_minutes;

-- ---------------------------------------------------------------------------
-- 4) 在途任务快照反向改写：timeoutMinutes → timeoutMs
-- ---------------------------------------------------------------------------
UPDATE kb_async_tasks
SET poll_spec_json = JSON_REMOVE(
        JSON_SET(poll_spec_json,
                 '$.timeoutMs',
                 (JSON_EXTRACT(poll_spec_json, '$.timeoutMinutes') + 0) * 60000),
        '$.timeoutMinutes')
WHERE JSON_EXTRACT(poll_spec_json, '$.timeoutMinutes') IS NOT NULL;

-- ---------------------------------------------------------------------------
-- 5) 自检
-- ---------------------------------------------------------------------------
SELECT 'CHECK-1 旧列就位（应为 1）' AS check_item, COUNT(*) AS columns_cnt
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME   = 'kb_async_task_profiles'
  AND COLUMN_NAME  = 'timeout_ms';

SELECT 'CHECK-2 分钟列已删（应为 0）' AS check_item, COUNT(*) AS columns_cnt
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME   = 'kb_async_task_profiles'
  AND COLUMN_NAME  = 'timeout_minutes';

SELECT 'CHECK-3 快照仍带分钟键（应为 0）' AS check_item, COUNT(*) AS stale_spec_cnt
FROM kb_async_tasks
WHERE JSON_EXTRACT(poll_spec_json, '$.timeoutMinutes') IS NOT NULL;

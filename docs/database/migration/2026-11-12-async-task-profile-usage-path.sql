-- ============================================================================
-- 2026-11-12 · 异步任务档案新增 usage_path（查询响应取上游 usage 对象）
--
-- 只做一件事：kb_async_task_profiles 补一列 usage_path VARCHAR(255) DEFAULT NULL。
--   · 用途：异步上游常把 usage（含 cost）嵌在 data.*，顶层没有，结算取不到就会漏计费。
--     档案配了 MVEL 表达式（如 data.usage）后，终态结算按它从查询响应取对象补到顶层 usage。
--   · 空表示沿用顶层 usage，与同步链路同一口径（UsageRawReader.readUsage）。
--
-- ⚠ 存量语义：
--   - 新增的是**可空列**，老档案该列为 NULL → 沿用顶层 usage（原行为），不写数据迁移；
--   - **不动 poll_spec_json**：在途任务的口径快照是提交时落下的，改档案 / 改列都不回填，
--     保证「同一笔任务无论何时完成，算法可复现」。旧快照没有 usage_path 字段，
--     反序列化为 null，运行时按顶层 usage 兜底；
--   - **不动在途任务**：库里 kb_async_task_profiles 当前 0 行（档案尚未启用），
--     没有需要迁移的存量配置，也没有引用它的在途任务。
--
-- 幂等性：先查 information_schema，重复执行跳过（不靠 ADD COLUMN IF NOT EXISTS，
--   MySQL 5.7 不支持）。
--
-- 发布顺序：先跑本脚本加列 → 再发布新版代码（实体 / Mapper / DTO / Spec / Completion 都读这列）。
--   只发代码不加列会让档案查询直接报「字段不存在」，管理面整页不可用，故 DB 必须先于代码。
--
-- 回滚：见同目录 2026-11-12-async-task-profile-usage-path-rollback.sql（幂等 DROP COLUMN）。
--   ⚠ 回滚会丢掉已配置的 usage 表达式，只在「本次上线完全没填过这一列」时安全；
--   代码侧必须同时回滚，否则档案保存 / 查询会读不存在的列。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1) kb_async_task_profiles.usage_path（AFTER error_message_path）
-- ---------------------------------------------------------------------------
SET @has_usage_path = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME   = 'kb_async_task_profiles'
    AND COLUMN_NAME  = 'usage_path'
);

SET @ddl = IF(@has_usage_path = 0,
  'ALTER TABLE kb_async_task_profiles ADD COLUMN usage_path VARCHAR(255) DEFAULT NULL COMMENT ''从查询响应取上游 usage 对象的 MVEL 表达式；空表示沿用顶层 usage'' AFTER error_message_path',
  'SELECT ''usage_path 已存在，跳过'' AS skipped');
PREPARE stmt_usage_path FROM @ddl;
EXECUTE stmt_usage_path;
DEALLOCATE PREPARE stmt_usage_path;

-- ---------------------------------------------------------------------------
-- 2) 迁移后自检：列应存在（应为 1）
-- ---------------------------------------------------------------------------
SELECT 'CHECK usage_path 已就位（应为 1）' AS check_item, COUNT(*) AS columns_cnt
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME   = 'kb_async_task_profiles'
  AND COLUMN_NAME  = 'usage_path';

SELECT 'CHECK usage_path 列结构' AS check_item, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME   = 'kb_async_task_profiles'
  AND COLUMN_NAME  = 'usage_path';

-- ============================================================================
-- 2026-11-02 · 异步任务档案：任务超时改按「分钟」存
--
-- 为什么改：
--   界面从第一天起就在问「这个任务最多等多久」，人的答案是「30 分钟」，而库里存 1800000。
--   两边单位不同带来两处实际损耗：
--     1) 报错文案只能写毫秒（「必须在 1s ~ 24h 之间：86400000ms」），看到的人得自己除；
--     2) 界面把下限收到 1 分钟（分钟粒度），后端却仍允许 1s——两端口径不一致，
--        而 el-input-number 会把越界值回写到 model，加载旧行时有静默改值的风险。
--   这次一次做干净：列 / DTO / 快照 / 网关计算 / 界面，全部按分钟。
--
-- 三件事（顺序不可换）：
--   1) kb_async_task_profiles 加 timeout_minutes，由 timeout_ms 换算回填，再删旧列；
--   2) kb_async_tasks.poll_spec_json 里快照键 timeoutMs → timeoutMinutes 就地改写；
--   3) 自检。
--
-- ⚠⚠ 为什么必须改快照：poll_spec_json 是**提交时的口径快照**，在途任务按它重算超时窗口
--   （AsyncTaskAdminRunner 运维重试会 now.plusMinutes(spec.timeoutMinutes())）。代码改成新键之后，
--   老快照里只剩 timeoutMs → 反序列化得到 0 → Math.max(1, 0) = 1 分钟，一个跑了 10 分钟的任务
--   会在下一轮扫描被判 EXPIRED。所以这一步不能省，也不能靠代码兜底（旧键兼容读是被明令禁止的写法）。
--
-- 与 db/schema.sql 同步：新库启动时按 `timeout_minutes BIGINT NOT NULL DEFAULT 30` 建表
--   （`CREATE TABLE IF NOT EXISTS` 不会给既有表补列，所以老库必须跑本脚本）。
--
-- 幂等性：
--   - 加列 / 删列都先查 information_schema，重复执行跳过（不靠 ADD/DROP COLUMN IF EXISTS，
--     MySQL 5.7 不支持）；
--   - 回填只在旧列仍存在时执行；
--   - 快照改写按「$.timeoutMs 存在」判定，重复执行是空操作。
--
-- 换算口径：CEIL(ms / 60000)，且不小于 1。
--   向上取整的理由：30s 的旧配置在新口径下变成 1 分钟，任务寿命只会变长不会变短——
--   宁可多等，也不能让在途任务提前判死。反向（FLOOR）会把 59s 压成 0 分钟。
--
-- 发布顺序：**先跑本脚本，再启新版本**。反过来新代码会读不到 timeout_minutes；
--   而脚本跑完旧版本仍在跑的话，旧代码读 timeout_ms 会报「列不存在」，所以中间不要留旧实例。
--
-- 回滚：见同目录 2026-11-02-async-task-timeout-minutes-rollback.sql
--   ⚠ 回滚把分钟乘回 60000，精度按分钟计（原本就不是整分的存量已经被取整，回不去）。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1) 加列：timeout_minutes
-- ---------------------------------------------------------------------------
SET @has_timeout_minutes = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME   = 'kb_async_task_profiles'
    AND COLUMN_NAME  = 'timeout_minutes'
);

SET @ddl = IF(@has_timeout_minutes = 0,
  'ALTER TABLE kb_async_task_profiles ADD COLUMN timeout_minutes BIGINT NOT NULL DEFAULT 30 COMMENT ''knot 侧任务寿命（分钟，1~1440），到期转 EXPIRED；⚠ 与本表 poll_interval_ms 等「毫秒」列不同单位'' AFTER max_attempts',
  'SELECT ''timeout_minutes 已存在，跳过'' AS skipped);
PREPARE stmt_timeout_minutes FROM @ddl;
EXECUTE stmt_timeout_minutes;
DEALLOCATE PREPARE stmt_timeout_minutes;

-- ---------------------------------------------------------------------------
-- 2) 回填：仅当旧列还在（首次执行），CEIL 向上取整且不小于 1
-- ---------------------------------------------------------------------------
SET @has_timeout_ms = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME   = 'kb_async_task_profiles'
    AND COLUMN_NAME  = 'timeout_ms'
);

SET @ddl = IF(@has_timeout_ms = 1,
  'UPDATE kb_async_task_profiles SET timeout_minutes = GREATEST(1, CEIL(timeout_ms / 60000))',
  'SELECT ''timeout_ms 已删除，跳过回填'' AS skipped);
PREPARE stmt_backfill FROM @ddl;
EXECUTE stmt_backfill;
DEALLOCATE PREPARE stmt_backfill;

-- ---------------------------------------------------------------------------
-- 3) 删旧列（数据已经在新列里；DDL 没有默认值可回退，留两列必然漂移）
-- ---------------------------------------------------------------------------
SET @ddl = IF(@has_timeout_ms = 1,
  'ALTER TABLE kb_async_task_profiles DROP COLUMN timeout_ms',
  'SELECT ''timeout_ms 已删除，跳过'' AS skipped);
PREPARE stmt_drop_timeout_ms FROM @ddl;
EXECUTE stmt_drop_timeout_ms;
DEALLOCATE PREPARE stmt_drop_timeout_ms;

-- ---------------------------------------------------------------------------
-- 4) 在途任务快照：timeoutMs(毫秒) → timeoutMinutes(分钟)
--    JSON 里的数值要先 +0 变成数值型再参与算术，否则部分 MariaDB 版本按字符串比较。
-- ---------------------------------------------------------------------------
UPDATE kb_async_tasks
SET poll_spec_json = JSON_REMOVE(
        JSON_SET(poll_spec_json,
                 '$.timeoutMinutes',
                 GREATEST(1, CEIL((JSON_EXTRACT(poll_spec_json, '$.timeoutMs') + 0) / 60000))),
        '$.timeoutMs')
WHERE JSON_EXTRACT(poll_spec_json, '$.timeoutMs') IS NOT NULL;

-- ---------------------------------------------------------------------------
-- 5) 迁移后自检
-- ---------------------------------------------------------------------------
SELECT 'CHECK-1 新列就位（应为 1）' AS check_item, COUNT(*) AS columns_cnt
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME   = 'kb_async_task_profiles'
  AND COLUMN_NAME  = 'timeout_minutes';

SELECT 'CHECK-2 旧列已删（应为 0）' AS check_item, COUNT(*) AS columns_cnt
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME   = 'kb_async_task_profiles'
  AND COLUMN_NAME  = 'timeout_ms';

SELECT 'CHECK-3 快照仍带旧键（应为 0）' AS check_item, COUNT(*) AS stale_spec_cnt
FROM kb_async_tasks
WHERE JSON_EXTRACT(poll_spec_json, '$.timeoutMs') IS NOT NULL;

SELECT 'CHECK-4 快照缺新键（应为 0，缺了说明有行是手工写的 spec）' AS check_item, COUNT(*) AS missing_spec_cnt
FROM kb_async_tasks
WHERE JSON_EXTRACT(poll_spec_json, '$.timeoutMinutes') IS NULL;

SELECT 'CHECK-5 档案超时分布' AS check_item, timeout_minutes, COUNT(*) AS profile_cnt
FROM kb_async_task_profiles
GROUP BY timeout_minutes
ORDER BY timeout_minutes;

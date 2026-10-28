-- ============================================================================
-- 2026-10-28 · kb_async_tasks.status 由 VARCHAR(24) 改 TINYINT（robin 拍板）
--
-- 背景：
--   原设计（2026-10-27）刻意选了 VARCHAR 存 AsyncTaskStatusEnum.name()，理由是
--   「任务状态是运维高频 WHERE 条件，字符串自解释，省一层字典翻译」。
--   2026-10-28 翻案，两条更硬的理由：
--     1) 热点索引变窄：idx_async_tasks_due(status, next_poll_at) 是轮询扫描入口，
--        status 是首列。'PENDING' 连长度前缀 9 字节，TINYINT 1 字节 —— 同 buffer pool
--        下索引页能装近两倍条目。
--     2) 全库风格统一：本项目其它状态列（ks_* 的 status，EnabledStatusEnum）都是 TINYINT。
--   代价与对策见 AsyncTaskStatusEnum 类注释：整型真正的危险不是「不可读」，是「撞 code」，
--   对策是 code 显式声明、只增不改、不复用废号，并由 AsyncTaskStatusEnumTest 钉死数值。
--
-- 映射（AsyncTaskStatusEnum.code）：
--   0-PENDING 1-POLLING 2-SUCCEEDED 3-FAILED 4-EXPIRED 5-CANCELLED
--
-- ⚠⚠ 执行顺序不可颠倒：**先转数据，再改列类型**。
--   若直接 MODIFY 成 TINYINT，MySQL 会把非数字字符串按「前导数字」截断 →
--   'PENDING'/'POLLING'/'SUCCEEDED'... 全部变成 0。
--   后果不是报错，而是：**已成功（SUCCEEDED）的行变回 0=在途**，会被下一拍重新领取、
--   重新查上游、重新计费 —— 这是资损级别的静默错误。所以本脚本第 2 步必须先做。
--
-- 幂等性：
--   - 数据转换带判据（只处理「不是纯数字」的行），重复执行安全；
--   - MODIFY COLUMN 重复执行安全；
--   - CHECK 约束先查 information_schema 再建。
-- 在途任务：本变更不影响进行中的任务（status 语义一一对应），可以带流量上线；
--   但**升级前请先跑本脚本**，新版本代码写入的已经是整型码，与旧字符串列不兼容。
-- ============================================================================

-- ⚠ 强制严格模式：让「未知状态值」导致 ALTER 失败，而不是被静默截断成 0。
--   宁可让 DBA 卡在迁移上，也不要带着一批语义不明的行继续跑业务。
SET @knot_old_sql_mode = @@SESSION.sql_mode;
SET SESSION sql_mode = CONCAT_WS(',', @@SESSION.sql_mode, 'STRICT_ALL_TABLES');

-- ---------------------------------------------------------------------------
-- 1) 前置自检：先看清有没有枚举之外的取值（有就必须人工处理，不要硬转）
--    CHECK-1 必须返回 0 行，否则停下来问清楚这些值从哪来。
-- ---------------------------------------------------------------------------
SELECT 'CHECK-1 未知状态取值（必须为空）' AS check_item, status, COUNT(*) AS rows_cnt
FROM kb_async_tasks
WHERE status NOT IN ('PENDING', 'POLLING', 'SUCCEEDED', 'FAILED', 'EXPIRED', 'CANCELLED')
  AND status NOT REGEXP '^[0-9]+$'
GROUP BY status;

-- ---------------------------------------------------------------------------
-- 2) 数据转换：名字 → 数字字符串（此时列还是 VARCHAR，故先落字符串，下一步再改类型）
--    判据「不是纯数字」= 还没转过；重复执行不会把 '3' 再套一层 CASE。
-- ---------------------------------------------------------------------------
UPDATE kb_async_tasks
SET status = CASE status
                 WHEN 'PENDING'   THEN '0'
                 WHEN 'POLLING'   THEN '1'
                 WHEN 'SUCCEEDED' THEN '2'
                 WHEN 'FAILED'    THEN '3'
                 WHEN 'EXPIRED'   THEN '4'
                 WHEN 'CANCELLED' THEN '5'
    END
WHERE status NOT REGEXP '^[0-9]+$';

-- ---------------------------------------------------------------------------
-- 3) 列类型：VARCHAR(24) → TINYINT
--    TINYINT 的有符号范围 -128..127，够 6 个状态且留足扩展位；用无符号反而要额外解释。
--    重建索引 idx_async_tasks_due 由 MySQL 自动完成。
-- ---------------------------------------------------------------------------
ALTER TABLE kb_async_tasks
  MODIFY COLUMN status TINYINT NOT NULL
    COMMENT '任务状态（AsyncTaskStatusEnum.code）：0-待首次轮询 1-轮询中 2-成功 3-上游失败 4-超时 5-已取消';

-- ---------------------------------------------------------------------------
-- 4) 写入端兜底：CHECK 把「撞 code / 越界」挡在 DB 层
--    MySQL 8.0.16+ 真正生效；更早版本会解析但忽略（不报错），MariaDB 支持。
--    这不是可选的「洁癖」：整型最大的风险就是两个枚举常量给了同一个 code，
--    一旦写进去数据就不可区分、不可恢复。
-- ---------------------------------------------------------------------------
SET @has_status_ck = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME   = 'kb_async_tasks'
    AND CONSTRAINT_NAME = 'ck_async_tasks_status'
);

SET @sql_add_status_ck = IF(@has_status_ck = 0,
  'ALTER TABLE kb_async_tasks ADD CONSTRAINT ck_async_tasks_status CHECK (status BETWEEN 0 AND 5)',
  'SELECT ''ck_async_tasks_status 已存在，跳过'' AS skipped');
PREPARE stmt_add_status_ck FROM @sql_add_status_ck;
EXECUTE stmt_add_status_ck;
DEALLOCATE PREPARE stmt_add_status_ck;

-- ---------------------------------------------------------------------------
-- 5) 迁移后自检
-- ---------------------------------------------------------------------------

-- CHECK-2 列类型必须是 tinyint（data_type=tinyint）
SELECT 'CHECK-2 列类型' AS check_item, COLUMN_TYPE, IS_NULLABLE
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME   = 'kb_async_tasks'
  AND COLUMN_NAME  = 'status';

-- CHECK-3 状态分布（一眼看清：在途 0/1 应等于「还没结单的任务数」）
SELECT 'CHECK-3 状态分布' AS check_item, status, COUNT(*) AS rows_cnt
FROM kb_async_tasks
GROUP BY status
ORDER BY status;

-- CHECK-4 在途行数与「未落 completed_at」的行数应一致（不一致说明转换过程中有语义漂移）
SELECT 'CHECK-4 在途 vs 未 CompletedAt' AS check_item,
       SUM(CASE WHEN status IN (0, 1) THEN 1 ELSE 0 END) AS in_flight_rows,
       SUM(CASE WHEN completed_at IS NULL THEN 1 ELSE 0 END) AS no_completed_at_rows
FROM kb_async_tasks;

-- CHECK-5 CHECK 约束是否就位（1 = 已建）
SELECT 'CHECK-5 约束' AS check_item,
       (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME   = 'kb_async_tasks'
           AND CONSTRAINT_NAME = 'ck_async_tasks_status') AS status_check_exists;

SET SESSION sql_mode = @knot_old_sql_mode;

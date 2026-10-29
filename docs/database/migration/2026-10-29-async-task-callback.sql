-- ============================================================================
-- 2026-10-29 · 异步任务回调交付（callback_url 支持）
--
-- 设计要点（与 2026-10-29 状态机定稿一致）：
--   1) 交付是**独立维度**，不与 task_status 合并。task_status 答「任务成没成」，
--      delivery_status 答「结果有没有送到 callback_url」。两者正交：
--      生成成功 + 回调失败 = 合法状态。合并会出现「某终态代表回调失败、却
--      不代表任务失败」这种自相矛盾的取值（参考实现 13=最终失败 即此坑）。
--   2) 交付有自己的租约（delivery_claim_id），不复用轮询的 claim_id：
--      交付发生在终态之后，轮询租约此时已释放。
--   3) delivery_status 用**正向包含**扫描（IN (待回调)），不用「排除成功/失败」。
--   4) 终态落库时（mapper complete）原子武装交付：delivery_status→1、next_delivery_at→此刻。
--      放进同一条 UPDATE，避免「已终态却永不回调」且无报错的半截状态。
--   5) 可崩溃恢复：投递进度在库里（delivery_attempts / next_delivery_at），
--      节点宕机后由下一拍重新抢占续投。
--
-- 幂等性：加列 / 加索引前先查 information_schema，重复执行跳过。
-- 存量语义：历史行 callback_url 为 NULL → delivery_status=0（无需回调），行为不变。
-- 不停机、可带流量执行。
-- ============================================================================

-- ============================================================================
-- ⚠⚠ 交接标记 · 与「异步任务管理端」的交叉影响（2026-10-29 21:30 由管理端侧补记）
--
-- 同一天另一路改动在 knot-admin 新增了异步任务管理入口（迁移脚本
-- 2026-10-29-async-task-admin.sql），其中两个写操作落在**同一个** AsyncTaskMapper.xml 里，
-- 而它们都还不认识交付维度。只要交付能力上线，下面两条就是真缺陷，不是边角情况：
--
-- 【一】清理终态行会静默丢掉回调
--   deleteCompletedBefore 现为：
--       where status not in (InFlight) and completed_at < #{before} limit #{limit}
--   只判任务状态 → 会把「任务已终态、回调还没送达」（delivery_status = 1 待回调）的行直接删掉，
--   客户端永远收不到结果，且没有任何报错。而「终态落库即武装交付」意味着**每一行终态落库后
--   都有这个窗口**，清理任务一拍就能撞上。
--   修法：补交付守卫，只有「无需回调(0) / 回调成功(2) / 回调失败(3)」才可删 ——
--       and delivery_status not in <include refid="Deliverable"/>
--   ⚠ 用 not in (Deliverable) 而不是 delivery_status <> 1：后者在新增交付态时不会报错，只会静默漏判。
--
-- 【二】管理端重试会让同一任务回调两次
--   reopen 现为：status=#{nextStatus}, retry_round+1, attempts=0, next_poll_at, expires_at,
--                claim_id=null, claim_expires_at=null, last_polled_at=null,
--                completed_at=null, result_json=null, error_code=null, error_message=null
--   清了结果，没碰 delivery。若该行停在 delivery_status = 1（上一轮终态已武装、回调未送达），
--   重开后调度器会拿**已被清空的 result_json** 回调一次（给客户端一条空结果），
--   随后新终态再武装一次（第二条回调）。同一任务两条回调、其中一条是垃圾。
--   修法：reopen 一并复位交付六字段 ——
--       delivery_status = 0, delivery_attempts = 0, next_delivery_at = null,
--       delivered_at = null, delivery_error = null,
--       delivery_claim_id = null, delivery_claim_expires_at = null
--   ⚠ 复位成 NONE(0) 而不是 PENDING(1)：置 1 会让在途行立刻被交付节拍捞走。
--   ⚠ callback_url 保留不动 —— 它是任务固有属性，不是一次交付的进度。
--
-- 【三】轻：SummaryColumns 已投影交付列，但管理端详情抽屉还没展示「回调交付」一段。
--
-- 归属：修一、修二由管理端侧在回调侧收口后改 AsyncTaskMapper.xml（同一个文件，现在动会互相覆盖）；
--       若回调侧先动手，请按上面的写法改，并同步 AsyncTaskMapperSqlTest 的交付谓词断言。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1) kb_async_tasks.callback_url
-- ---------------------------------------------------------------------------
SET @has_callback_url = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_async_tasks' AND COLUMN_NAME = 'callback_url'
);
SET @ddl = IF(@has_callback_url = 0,
  'ALTER TABLE kb_async_tasks ADD COLUMN callback_url VARCHAR(1024) DEFAULT NULL COMMENT ''客户端回推地址（提交时从原始请求体提取并落库；不转发给上游）'' AFTER result_expires_at',
  'SELECT ''callback_url 已存在，跳过'' AS skipped');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------------
-- 2) kb_async_tasks.delivery_status
-- ---------------------------------------------------------------------------
SET @has_delivery_status = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_async_tasks' AND COLUMN_NAME = 'delivery_status'
);
SET @ddl = IF(@has_delivery_status = 0,
  'ALTER TABLE kb_async_tasks ADD COLUMN delivery_status TINYINT NOT NULL DEFAULT 0 COMMENT ''回调交付状态（AsyncTaskDeliveryStatusEnum.code）：0-无需回调 1-待回调 2-成功 3-失败（与 task status 正交）'' AFTER callback_url',
  'SELECT ''delivery_status 已存在，跳过'' AS skipped');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------------
-- 3) kb_async_tasks.delivery_attempts
-- ---------------------------------------------------------------------------
SET @has_delivery_attempts = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_async_tasks' AND COLUMN_NAME = 'delivery_attempts'
);
SET @ddl = IF(@has_delivery_attempts = 0,
  'ALTER TABLE kb_async_tasks ADD COLUMN delivery_attempts INT NOT NULL DEFAULT 0 COMMENT ''已尝试投递回调次数（含失败重试）'' AFTER delivery_status',
  'SELECT ''delivery_attempts 已存在，跳过'' AS skipped');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------------
-- 4) kb_async_tasks.next_delivery_at
-- ---------------------------------------------------------------------------
SET @has_next_delivery_at = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_async_tasks' AND COLUMN_NAME = 'next_delivery_at'
);
SET @ddl = IF(@has_next_delivery_at = 0,
  'ALTER TABLE kb_async_tasks ADD COLUMN next_delivery_at DATETIME(3) DEFAULT NULL COMMENT ''下次投递时刻；NULL 表示不在待投递集'' AFTER delivery_attempts',
  'SELECT ''next_delivery_at 已存在，跳过'' AS skipped');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------------
-- 5) kb_async_tasks.delivered_at
-- ---------------------------------------------------------------------------
SET @has_delivered_at = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_async_tasks' AND COLUMN_NAME = 'delivered_at'
);
SET @ddl = IF(@has_delivered_at = 0,
  'ALTER TABLE kb_async_tasks ADD COLUMN delivered_at DATETIME(3) DEFAULT NULL COMMENT ''回调成功送达时刻'' AFTER next_delivery_at',
  'SELECT ''delivered_at 已存在，跳过'' AS skipped');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------------
-- 6) kb_async_tasks.delivery_error
-- ---------------------------------------------------------------------------
SET @has_delivery_error = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_async_tasks' AND COLUMN_NAME = 'delivery_error'
);
SET @ddl = IF(@has_delivery_error = 0,
  'ALTER TABLE kb_async_tasks ADD COLUMN delivery_error VARCHAR(1000) DEFAULT NULL COMMENT ''最近一次投递失败原因'' AFTER delivered_at',
  'SELECT ''delivery_error 已存在，跳过'' AS skipped');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------------
-- 7) kb_async_tasks.delivery_claim_id
-- ---------------------------------------------------------------------------
SET @has_delivery_claim_id = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_async_tasks' AND COLUMN_NAME = 'delivery_claim_id'
);
SET @ddl = IF(@has_delivery_claim_id = 0,
  'ALTER TABLE kb_async_tasks ADD COLUMN delivery_claim_id VARCHAR(64) DEFAULT NULL COMMENT ''交付占位标识（与轮询 claim_id 分开的两条租约）'' AFTER delivery_error',
  'SELECT ''delivery_claim_id 已存在，跳过'' AS skipped');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------------
-- 8) kb_async_tasks.delivery_claim_expires_at
-- ---------------------------------------------------------------------------
SET @has_delivery_claim_exp = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_async_tasks' AND COLUMN_NAME = 'delivery_claim_expires_at'
);
SET @ddl = IF(@has_delivery_claim_exp = 0,
  'ALTER TABLE kb_async_tasks ADD COLUMN delivery_claim_expires_at DATETIME(3) DEFAULT NULL COMMENT ''交付占位过期时刻'' AFTER delivery_claim_id',
  'SELECT ''delivery_claim_expires_at 已存在，跳过'' AS skipped');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------------
-- 9) 交付扫描索引（delivery_status 首列 + next_delivery_at）
-- ---------------------------------------------------------------------------
SET @has_delivery_idx = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_async_tasks' AND INDEX_NAME = 'idx_async_tasks_delivery'
);
SET @ddl = IF(@has_delivery_idx = 0,
  'ALTER TABLE kb_async_tasks ADD KEY idx_async_tasks_delivery (delivery_status, next_delivery_at)',
  'SELECT ''idx_async_tasks_delivery 已存在，跳过'' AS skipped');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------------
-- 10) 迁移后自检
-- ---------------------------------------------------------------------------
SELECT 'CHECK-1 交付列就位（应为 8）' AS check_item, COUNT(*) AS delivery_columns
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'kb_async_tasks'
  AND COLUMN_NAME IN ('callback_url', 'delivery_status', 'delivery_attempts', 'next_delivery_at',
                      'delivered_at', 'delivery_error', 'delivery_claim_id', 'delivery_claim_expires_at');

-- ⚠ 必须 COUNT(DISTINCT INDEX_NAME)：INFORMATION_SCHEMA.STATISTICS 是「索引列」粒度，
--    一个两列索引在这里是 2 行。写成 COUNT(*) 会让人以为索引被建了两遍
--    （2026-10-29 实测就报出了 2）。
SELECT 'CHECK-2 交付索引就位（应为 1）' AS check_item, COUNT(DISTINCT INDEX_NAME) AS delivery_index
FROM INFORMATION_SCHEMA.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'kb_async_tasks'
  AND INDEX_NAME = 'idx_async_tasks_delivery';

SELECT 'CHECK-3 存量行 delivery_status（迁移刚执行时应为 0）' AS check_item, COUNT(*) AS non_none_rows
FROM kb_async_tasks
WHERE delivery_status <> 0;

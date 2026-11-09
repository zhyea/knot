-- ============================================================================
-- 2026-11-10 · 异步任务占位号列宽 64 → 128
--
-- 为什么改：
--   claimId = nodeId + "-" + UUID(36)（运维动作多一个 "-admin-"），nodeId 来自
--   knot.gateway.async-task.node-id 或主机名，长度不受控。VARCHAR(64) 只容得下
--   27 字符的 nodeId（运维动作 21），超了在严格模式下 UPDATE 直接报 Data too long，
--   整拍扫描失败。扩到 128 后 nodeId 可用到 91 字符（运维动作 85）。
--   生成侧刻意不加截断：nodeId 是排查线索，静默截断会抹掉节点身份；列宽才是该兜底的地方。
--
-- 两列一起改：
--   轮询 claim_id 与交付 delivery_claim_id 都由同一个 newClaimId() 生成，交付是独立节拍、
--   独立写入。只改一列等于把同一个故障挪到另一条链路上。
--
-- 与 db/schema.sql 同步：新库按 VARCHAR(128) 建表；CREATE TABLE IF NOT EXISTS 不会给
--   既有表改列，所以老库必须跑本脚本。
--
-- 幂等性：
--   按 information_schema.CHARACTER_MAXIMUM_LENGTH 判列宽，已 >= 128 就跳过。
--   刻意「只扩不缩」：万一有人手工扩到更大，缩回来可能截断存量值，那是一次独立决策。
--   ⚠ MODIFY COLUMN 必须带全 DEFAULT / COMMENT，不带会把列注释清空；不带 FIRST / AFTER
--     则保持原列序。
--
-- 索引：idx_async_tasks_claim (claim_id) 在 utf8mb4 下 128 字符 = 512 字节，
--   远低于 InnoDB 单列索引 3072 字节上限，无需重建索引（自检 CHECK-4 复核）。
--
-- 发布顺序：先跑本脚本，再启新版本。反向不会立刻报错，但长 nodeId 仍会撞旧列宽。
--
-- 回滚：不提供缩列脚本 —— 只扩不缩，旧版本写入的短值仍落在 128 内，天然兼容。
--   真要缩回 64，必须先 SELECT 确认无 CHAR_LENGTH(claim_id) > 64 的存量行，否则
--   MODIFY 会截断（非严格模式）或直接报错。
--
-- ⚠ 历史脚本 2026-10-27-async-task-tables.sql / 2026-10-29-async-task-callback.sql
--   中的 VARCHAR(64) 是当时的建表记录，按「历史记录不改」保留原样。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1) kb_async_tasks.claim_id（轮询租约）
-- ---------------------------------------------------------------------------
SET @claim_len = (
  SELECT CHARACTER_MAXIMUM_LENGTH
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'kb_async_tasks'
    AND COLUMN_NAME = 'claim_id'
);
SET @ddl = IF(IFNULL(@claim_len, 0) < 128,
  'ALTER TABLE kb_async_tasks MODIFY COLUMN claim_id VARCHAR(128) DEFAULT NULL COMMENT ''轮询占位标识（nodeId-uuid），防多节点重复处理''',
  'SELECT ''claim_id 列宽已 >= 128，跳过'' AS skipped');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------------
-- 2) kb_async_tasks.delivery_claim_id（交付租约）
-- ---------------------------------------------------------------------------
SET @delivery_claim_len = (
  SELECT CHARACTER_MAXIMUM_LENGTH
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'kb_async_tasks'
    AND COLUMN_NAME = 'delivery_claim_id'
);
SET @ddl = IF(IFNULL(@delivery_claim_len, 0) < 128,
  'ALTER TABLE kb_async_tasks MODIFY COLUMN delivery_claim_id VARCHAR(128) DEFAULT NULL COMMENT ''交付占位标识（与轮询 claim_id 分开的两条租约）''',
  'SELECT ''delivery_claim_id 列宽已 >= 128，跳过'' AS skipped');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------------
-- 3) 迁移后自检
-- ---------------------------------------------------------------------------
SELECT 'CHECK-1 两列列宽（应均为 128）' AS check_item, COLUMN_NAME, CHARACTER_MAXIMUM_LENGTH AS chars
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'kb_async_tasks'
  AND COLUMN_NAME IN ('claim_id', 'delivery_claim_id')
ORDER BY COLUMN_NAME;

SELECT 'CHECK-2 列数（应为 2）' AS check_item, COUNT(*) AS column_cnt
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'kb_async_tasks'
  AND COLUMN_NAME IN ('claim_id', 'delivery_claim_id');

SELECT 'CHECK-3 列注释未丢（应为 2）' AS check_item, COUNT(*) AS commented_cnt
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'kb_async_tasks'
  AND COLUMN_NAME IN ('claim_id', 'delivery_claim_id')
  AND COLUMN_COMMENT <> '';

SELECT 'CHECK-4 claim 索引仍在（应为 1）' AS check_item, COUNT(DISTINCT INDEX_NAME) AS index_cnt
FROM INFORMATION_SCHEMA.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'kb_async_tasks'
  AND INDEX_NAME = 'idx_async_tasks_claim';

-- COALESCE 是必要的：空表时 SUM 返回 NULL，会被误读成「没跑出结果」而不是「没有超长值」
SELECT 'CHECK-5 存量超长值（应均为 0）' AS check_item,
       COALESCE(SUM(CASE WHEN CHAR_LENGTH(claim_id) > 128 THEN 1 ELSE 0 END), 0) AS claim_over,
       COALESCE(SUM(CASE WHEN CHAR_LENGTH(delivery_claim_id) > 128 THEN 1 ELSE 0 END), 0) AS delivery_over
FROM kb_async_tasks;

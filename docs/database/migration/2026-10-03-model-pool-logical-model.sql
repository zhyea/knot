-- 模型池绑定统一模型（2026-10-03）
-- 迁移版本：2026-10-03-model-pool-logical-model
--
-- 本脚本含 3 项 DDL 变更：
--   ① kb_model_pools 增加 logical_model_code 列（按业务码绑定，非主键 id）
--   ② logical_model_code 回填 + 收紧为 NOT NULL
--   ③ 删除 kb_model_pools.model_type 列 + 重建索引
--
-- 【① 语义】
-- 模型池的职责是「同一个统一模型下的多个供应商模型做流量分配」，
-- 因此池内模型必须全部映射到同一个 kb_logical_models.model_code。
-- 原 model_type 只是池内模型类型的公共前缀，绑定统一模型后可由
-- logical_model_code → kb_logical_models.model_type 派生，属冗余列，故删除。
--   应用侧同步变更：ModelPoolEntity/Dto/VO 改持 logicalModelCode；
--   ModelPoolMapper.list 的 model_type 筛选改为 join kb_logical_models 过滤；
--   RoutingRuleTargetMapper 的 coalesce(..., mp.model_type) 兜底改为按
--   mp.logical_model_code 关联 kb_logical_models 取 model_type；
--   模型类型展示（ModelPoolListPanel / 路由测试）仍返回 model_type 字段，
--   但由派生列提供。
--
-- 【② 回填口径】
-- 既有池的 logical_model_code 取「池内条目 → kb_models → kb_provider_model_mappings
-- → logical_model_code」。若某池内条目跨多个统一模型（脏数据），无法自动定唯一值，
--   取条目中 id 最小那条映射的 logical_model_code 回填，并在下方核对 SQL 中列出，
--   由人工在应用侧重新选择池内模型后保存修正。
--   核对：SELECT pool_code, COUNT(DISTINCT pm.logical_model_code) ...
--
-- 幂等性：全部 DDL 用 information_schema 判定 + PREPARE 条件式执行，可重复跑。
--         全新实例以更新后的 schema.sql 建库，三项均已就位，无需执行本脚本。
--         既有库必须先跑本脚本再启应用。

-- ---- 0. 执行前快照（非执行语句，迁移前人工核对）----
-- SELECT pool_code, model_type, status FROM kb_model_pools ORDER BY id;
-- SELECT pool_code, COUNT(*) AS item_cnt FROM kb_model_pool_items GROUP BY pool_code;

-- ---- 1. kb_model_pools 增加 logical_model_code（存在则跳过，先建可空列以便回填）----
SET @ddl := IF(
    EXISTS (SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_model_pools'
              AND COLUMN_NAME = 'logical_model_code'),
    'DO 0',
    'ALTER TABLE kb_model_pools ADD COLUMN logical_model_code VARCHAR(128) NULL COMMENT ''统一模型 code（kb_logical_models.model_code）'' AFTER name');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---- 2. 回填 logical_model_code：取池内条目中映射 id 最小那条的 logical_model_code ----
UPDATE kb_model_pools mp
SET mp.logical_model_code = COALESCE((
        SELECT pm.logical_model_code
        FROM kb_model_pool_items mpi
        JOIN kb_models m ON m.model_code = mpi.model_code
        JOIN kb_provider_model_mappings pm ON pm.model_id = m.id
        WHERE mpi.pool_code = mp.pool_code
        ORDER BY pm.id ASC
        LIMIT 1
    ), 'knot-chat-premium')
WHERE mp.logical_model_code IS NULL;

-- ---- 3. 收紧为 NOT NULL ----
SET @ddl := 'ALTER TABLE kb_model_pools MODIFY COLUMN logical_model_code VARCHAR(128) NOT NULL COMMENT ''统一模型 code（kb_logical_models.model_code），池内模型必须全部归属该统一模型''';
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---- 4. 删除 kb_model_pools.model_type ----
-- 注意：MariaDB 的 DROP COLUMN 只把被删列从复合索引里摘掉，索引本身会残留为单列索引，
--       故必须先显式 DROP INDEX，再删列（MySQL 会自动删整条索引，不受此影响）。
SET @ddl := IF(
    EXISTS (SELECT 1 FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_model_pools'
              AND INDEX_NAME = 'idx_model_pools_type_status'),
    'ALTER TABLE kb_model_pools DROP INDEX idx_model_pools_type_status',
    'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := IF(
    EXISTS (SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_model_pools'
              AND COLUMN_NAME = 'model_type'),
    'ALTER TABLE kb_model_pools DROP COLUMN model_type',
    'DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---- 5. 重建索引（logical_model_code, status），存在则跳过 ----
SET @ddl := IF(
    EXISTS (SELECT 1 FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_model_pools'
              AND INDEX_NAME = 'idx_model_pools_logical_status'),
    'DO 0',
    'ALTER TABLE kb_model_pools ADD KEY idx_model_pools_logical_status (logical_model_code, status)');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---- 6. 执行后核对 ----
-- 6.1 列状态：logical_model_code 存在且 NOT NULL，model_type 已不存在
-- SELECT COLUMN_NAME, IS_NULLABLE FROM information_schema.COLUMNS
--  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_model_pools'
--    AND COLUMN_NAME IN ('logical_model_code', 'model_type');
-- 6.2 全量回填结果
-- SELECT id, pool_code, logical_model_code, status FROM kb_model_pools ORDER BY id;
-- 6.3 池内条目跨统一模型的脏数据（应全为 0；非 0 需在应用侧重新选择池内模型后保存）
-- SELECT mpi.pool_code, COUNT(DISTINCT pm.logical_model_code) AS logical_model_cnt
--   FROM kb_model_pool_items mpi
--   JOIN kb_models m ON m.model_code = mpi.model_code
--   JOIN kb_provider_model_mappings pm ON pm.model_id = m.id
--  GROUP BY mpi.pool_code
-- HAVING COUNT(DISTINCT pm.logical_model_code) > 1;
-- 6.4 logical_model_code 指向已删除统一模型的孤儿池（应全为 0）
-- SELECT mp.id, mp.pool_code, mp.logical_model_code
--   FROM kb_model_pools mp
--   LEFT JOIN kb_logical_models lm ON lm.model_code = mp.logical_model_code AND lm.is_deleted = 0
--  WHERE lm.id IS NULL;
-- 6.5 索引就位：只剩 idx_model_pools_logical_status，无 model_type 残留索引
-- SELECT INDEX_NAME, seq_in_index, column_name FROM information_schema.STATISTICS
--  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_model_pools'
--    AND INDEX_NAME IN ('idx_model_pools_logical_status', 'idx_model_pools_type_status');

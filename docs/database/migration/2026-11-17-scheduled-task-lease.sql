-- ============================================================================
-- 2026-11-17 · 定时任务执行互斥：会话级 GET_LOCK → ks_scheduled_tasks 行级租约
--
-- 加两列 + 一个索引：
--   lock_owner   VARCHAR(128)  持有租约的节点 id，NULL 表示空闲
--   lease_until  DATETIME(3)   租约到期时刻，已过期的可被抢占
--   KEY idx_scheduled_tasks_lease (lease_until)
--
-- 为什么要换：GET_LOCK / RELEASE_LOCK 是 MySQL 的会话级用户锁，必须同一条连接上加/解。
--   原先两句 SQL 能落到同一条连接，唯一原因是 ScheduledTaskExecutor.execute 外层那个
--   @Transactional（连事务带连接一起兜住了）。而那个外层事务同时把 handler 关在长事务里。
--   一旦把 handler 移出事务，加锁与释放就会落到两个池化连接上：RELEASE_LOCK 返回 0，
--   锁挂在原连接上直到连接关闭——Hikari 连接长期存活，等于该任务永久不再执行。
--   换成表上的条件 UPDATE 后，抢/放都是一条短语句，与连接、事务都解耦。
--
-- 抢：update ... set lock_owner=?, lease_until=date_add(now(3), interval ? minute)
--       where task_code=? and (lock_owner is null or lease_until is null or lease_until <= now(3))
--     影响行数 = 1 才算抢到。
-- 放：update ... set lock_owner=null, lease_until=null where task_code=? and lock_owner=?
--     只清自己的，不会误清已被别人抢走的租约。
--
-- 幂等：列与索引都先查 information_schema 再决定是否 ALTER，可重复执行。
--   ⚠ 只覆盖 schema.sql 的 CREATE TABLE IF NOT EXISTS 补不上的存量库场景。
--
-- 上线顺序：先跑本脚本再发版。旧代码只用 GET_LOCK，多出两列对它无影响（都是 NULL）；
--   反过来先发版会让新代码 UPDATE 一个不存在的列，所有定时任务直接失败。
--
-- 回滚：不单独出回滚脚本。旧代码不读这两列，回滚代码后它们保持 NULL 即可，留着无害；
--   确实要清掉时手工 drop index idx_scheduled_tasks_lease 再 drop 两列。
-- ============================================================================

SET @db := DATABASE();

-- lock_owner
SET @ddl := (
    SELECT IF(
        (SELECT COUNT(*) FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA = @db
            AND TABLE_NAME = 'ks_scheduled_tasks'
            AND COLUMN_NAME = 'lock_owner') = 0,
        'ALTER TABLE ks_scheduled_tasks ADD COLUMN lock_owner VARCHAR(128) DEFAULT NULL COMMENT ''持有执行租约的节点 id，NULL 表示空闲'' AFTER next_fire_at',
        'DO 0')
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- lease_until
SET @ddl := (
    SELECT IF(
        (SELECT COUNT(*) FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA = @db
            AND TABLE_NAME = 'ks_scheduled_tasks'
            AND COLUMN_NAME = 'lease_until') = 0,
        'ALTER TABLE ks_scheduled_tasks ADD COLUMN lease_until DATETIME(3) DEFAULT NULL COMMENT ''租约到期时刻，已过期的租约可被抢占'' AFTER lock_owner',
        'DO 0')
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- idx_scheduled_tasks_lease
SET @ddl := (
    SELECT IF(
        (SELECT COUNT(*) FROM information_schema.STATISTICS
          WHERE TABLE_SCHEMA = @db
            AND TABLE_NAME = 'ks_scheduled_tasks'
            AND INDEX_NAME = 'idx_scheduled_tasks_lease') = 0,
        'ALTER TABLE ks_scheduled_tasks ADD KEY idx_scheduled_tasks_lease (lease_until)',
        'DO 0')
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

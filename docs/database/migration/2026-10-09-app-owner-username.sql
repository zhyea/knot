-- 应用归属用户由绑定 user.id 改为绑定 username（2026-10-09）
--
-- 背景：按「跨模块绑定存 code 不存 id」口径，kb_apps 归属用户由 owner_user_id（BIGINT，主键 id）
--   改为 owner_username（VARCHAR，ks_users.username 业务码）。AppEntity / AppDto / AppItem /
--   AppMapper.xml / AppService / RoutingResolver 同步改为读写 ownerUsername，join 改为
--   a.owner_username = u.username 仅用于展示 real_name。
--   schema.sql 已把该列纳入 CREATE TABLE 作为权威定义；data.sql 种子值改为 admin/zhangsan/lisi。
--   本迁移仅用于让「已存在、非由 schema.sql 重建」的库补齐/改列；全新库由 schema.sql 直接建出。
--
-- 幂等：先查 information_schema，旧列 owner_user_id 存在且新列 owner_username 不存在才 CHANGE；
--   已改过（仅剩 owner_username）或全新库（仅 owner_username）均跳过。

SET @old = (
    SELECT COUNT(1)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'kb_apps'
      AND COLUMN_NAME = 'owner_user_id'
);
SET @new = (
    SELECT COUNT(1)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'kb_apps'
      AND COLUMN_NAME = 'owner_username'
);
SELECT @old AS owner_user_id_exists, @new AS owner_username_exists;

SET @sql = IF(
    @old > 0 AND @new = 0,
    'ALTER TABLE kb_apps CHANGE COLUMN owner_user_id owner_username VARCHAR(64) DEFAULT NULL COMMENT ''归属用户登录名（绑定 username，非主键 id）''',
    'SELECT 1' -- 已改过或全新库，跳过
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---- 执行后核对（用 .workbuddy/audit 下的 JDBC 探针跑）----
-- 预期返回 1（仅剩 owner_username）：
-- SELECT COLUMN_NAME FROM information_schema.COLUMNS
--  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME='kb_apps'
--    AND COLUMN_NAME IN ('owner_user_id','owner_username');

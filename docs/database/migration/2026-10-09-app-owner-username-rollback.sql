-- 回滚：kb_apps 归属用户恢复为绑定 user.id（2026-10-09-app-owner-username 的反向操作）
--
-- 仅在需要回退「应用归属用户由 owner_user_id 改为 owner_username」时使用。
-- ⚠ 数据不可恢复：owner_username 存的是登录名（如 admin），无法反推原 user_id 数值。
--   本脚本 DROP 旧列 + ADD 新列，**不做类型转换**——若用 CHANGE COLUMN 把 VARCHAR 直接转成 BIGINT，
--   库里已有的 'admin' 这类值在严格模式下会让 ALTER 直接失败：
--     Data truncation: Truncated incorrect INTEGER value: 'admin'
--   采用 DROP+ADD 规避该转换风险，代价是列值清空（回滚后需人工回填 user_id，无数据可参照）。
--
-- 列位置：kb_apps 中该列原位于 dept_id 之后（与 schema.sql 一致）。

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
    @old = 0 AND @new > 0,
    CONCAT('ALTER TABLE kb_apps DROP COLUMN owner_username, ',
           ' ADD COLUMN owner_user_id BIGINT DEFAULT NULL COMMENT ''归属用户 id'' AFTER dept_id'),
    'SELECT 1' -- 已是旧结构或全新库，跳过
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---- 执行后核对（用 .workbuddy/audit 下的 JDBC 探针跑）----
-- 预期返回 1（仅剩 owner_user_id）：
-- SELECT COLUMN_NAME FROM information_schema.COLUMNS
--  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME='kb_apps'
--    AND COLUMN_NAME IN ('owner_user_id','owner_username');

-- 2026-09-23 移除供应商账户 name 字段（新项目无历史包袱，彻底删除）
-- 适用库：knot（MariaDB 10.6 / MySQL 8）
ALTER TABLE kb_provider_accounts DROP COLUMN name;

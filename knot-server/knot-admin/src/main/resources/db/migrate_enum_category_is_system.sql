-- ============================================================
-- 迁移：将「是否系统内置」从 enum_configs 提升到 enum_categories
-- 在已有库上执行前请备份。若为新库，直接使用 schema.sql + data.sql 即可。
-- ============================================================

-- 1. 分类表增加 is_system（若已存在请跳过本句）
ALTER TABLE enum_categories
    ADD COLUMN is_system TINYINT NOT NULL DEFAULT 0 COMMENT '1=系统内置分类' AFTER description;

-- 2. 根据原 enum_configs.is_system 将所属分类标为系统内置
UPDATE enum_categories c
INNER JOIN (SELECT DISTINCT category_id FROM enum_configs WHERE is_system = 1) t ON c.id = t.category_id
SET c.is_system = 1;

-- 3. 枚举表去掉 is_system（若列已删除请跳过）
ALTER TABLE enum_configs DROP COLUMN is_system;

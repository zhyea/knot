-- 2026-10-14 内容安全导航归并到路由管理（彻底整合，不保留空模块、不做兼容兜底）
-- 背景：data.sql 已将 ks_modules 的 module 5（内容安全）整体删除；敏感词管理菜单（menu_id=24）
--       改挂 module 3（路由管理），route_path 由 /moderation/words 改为 /routing/moderation-words；
--       敏感词管理权限（144-150）module_id 由 5 改为 3。本脚本补齐存量库。
-- 说明：data.sql 以 INSERT IGNORE 重放，不会删除已存在的 module 5 行，故存量库需显式收敛。
--       无外键约束（ks_menus / ks_permissions.module_id 仅为逻辑引用），仍保持「先改子表引用、再删父表」
--       的顺序，避免 AdminAuthorizationService.buildModules 按 module_id 分组时取到缺失的 module 名称。
--       幂等，可重复执行（先跑迁移再 package）。

-- 1) 权限 module_id 5 → 3（敏感词管理 144-150）
UPDATE ks_permissions SET module_id = 3 WHERE module_id = 5;

-- 2) 菜单 menu 24 归到 module 3，route_path 同步
UPDATE ks_menus SET module_id = 3, route_path = '/routing/moderation-words' WHERE id = 24;

-- 3) 删除已无菜单的空模块（内容安全）
DELETE FROM ks_modules WHERE id = 5;

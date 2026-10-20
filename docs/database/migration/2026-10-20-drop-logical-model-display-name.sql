-- 2026-10-20 移除统一模型(kb_logical_models)的展示名称列 display_name
-- 背景：该列从未被业务逻辑使用，统一模型展示名统一退化为 model_name。
-- 影响面（已 grep 确认，全部在本批改动内）：
--   schema.sql、data.sql 种子、LogicalModelEntity/Dto/Item、LogicalModelConverter、
--   LogicalModelMapper.xml、OptionsMapper.xml（下拉 label 与关键字搜索）、RoutingRuleMapper.xml（路由规则派生名）。
-- 落地顺序：先跑本迁移，再 package（约定：先迁移后打包）。
-- 既有库执行即可；新库由 schema.sql 直接不建该列。
ALTER TABLE kb_logical_models DROP COLUMN display_name;

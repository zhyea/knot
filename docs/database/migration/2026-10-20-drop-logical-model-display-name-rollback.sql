-- 回滚：恢复展示名称列。
-- 注意：DROP 已丢失历史展示名称数据，本回滚仅恢复空列结构，不会复原原值。
ALTER TABLE kb_logical_models
  ADD COLUMN display_name VARCHAR(128) DEFAULT NULL
  COMMENT '统一模型展示名称（已弃用）' AFTER model_family;

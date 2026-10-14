-- =====================================================================
-- 敏感词库迁移（2026-10-13）
-- ---------------------------------------------------------------------
-- 背景：网关内置敏感词审核（设计文档 docs/design/sensitive-word-moderation-design.md）。
--       收敛后只新增一张表 kb_moderation_word_lists；分类走既有枚举机制
--       （ks_enum_categories + ks_enum_configs，category='moderation_category'），
--       零 schema 变更；severity 挂在词条上而非分类。
-- 幂等：CREATE TABLE IF NOT EXISTS + 按 category 判存在 + INSERT IGNORE，可重复执行。
-- 执行顺序：先跑本迁移，再启动应用（schema.sql/data.sql 已同步包含本段）。
-- =====================================================================

SET @db = DATABASE();

-- =====================================================================
-- 段一：敏感词条表
-- =====================================================================
CREATE TABLE IF NOT EXISTS kb_moderation_word_lists (
  id              BIGINT       PRIMARY KEY AUTO_INCREMENT,
  word            VARCHAR(255) NOT NULL COMMENT '原始词条（支持 emoji/全角）',
  normalized_word VARCHAR(255) NOT NULL COMMENT '归一化键（NFKC+小写+去零宽），判定走它、展示走 word',
  category_code   VARCHAR(64)  NOT NULL COMMENT '绑分类（ks_enum_configs.item_code，category=moderation_category），非主键 id',
  severity        TINYINT      NOT NULL DEFAULT 2 COMMENT '严重度：1-低 2-中 3-高；配合常量 SEVERITY_THRESHOLD 决定是否触发动作',
  lang            VARCHAR(8)   DEFAULT 'zh' COMMENT '语言/变体标识',
  status          TINYINT      NOT NULL DEFAULT 1 COMMENT '启用状态（EnabledStatusEnum）：1-启用 0-停用',
  is_deleted      TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-否 1-是',
  remark          VARCHAR(255) DEFAULT NULL,
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_word_category (word, category_code),
  KEY idx_norm (normalized_word, category_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='敏感词条（Aho-Corasick 自动机数据源）';

-- =====================================================================
-- 段二：敏感词分类枚举（既有表，零 schema 变更，仅补种子）
-- =====================================================================
SET @hasCat = (SELECT COUNT(*) FROM ks_enum_categories
               WHERE category = 'moderation_category' AND is_deleted = 0);
SET @sql = IF(@hasCat = 0,
    "INSERT INTO ks_enum_categories (id, category, category_name, is_system, is_enabled) VALUES (30, 'moderation_category', '敏感词分类', 1, 1)",
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @catId = (SELECT id FROM ks_enum_categories
              WHERE category = 'moderation_category' AND is_deleted = 0 LIMIT 1);

INSERT IGNORE INTO ks_enum_configs (category_id, item_code, item_label, sort_order, is_enabled) VALUES
(@catId, 'POLITICS', '涉政',   1, 1),
(@catId, 'VIOLENCE', '暴恐',   2, 1),
(@catId, 'PORN',     '色情',   3, 1),
(@catId, 'AD',       '广告推广', 4, 1),
(@catId, 'ABUSE',    '侮辱辱骂', 5, 1),
(@catId, 'CUSTOM',   '自定义',  6, 1);

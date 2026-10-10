-- 部门绑定改业务码 dept_code + 折扣策略改绑供应商模型（2026-10-10）
--
-- 背景（按「跨模块绑定存 code 不存 id」口径，两处收口）：
--   1) ks_users.dept_id（BIGINT，ks_departments.id）与 kb_apps.dept_id（BIGINT）统一改为
--      dept_code（VARCHAR(64)，ks_departments.dept_code）。DAL entity / Mapper join 改为按 code
--      join；OptionsMapper 部门下拉 value 由 id 改为 dept_code（应用表单、用户表单共用同一接口，
--      故两者必须同时改，否则一方会写入错误类型）；DepartmentService 删除校验改按 code 反查。
--   2) kb_provider_discount_policies.provider_account_id（BIGINT）改为 model_code（VARCHAR(128)，
--      kb_models.model_code）：折扣策略从「挂在供应商账户下」改为「挂在供应商模型下」。
--      索引 idx_provider_discount_account_time 同步更名。
--
-- schema.sql 已把新列纳入 CREATE TABLE 作为权威定义；data.sql 种子值已改为
-- dept_code='HQ'/'OPS'/'RND'、折扣 model_code='gpt-4o'/'claude-sonnet-4-20250514'/'deepseek-chat'。
-- 本迁移仅用于让「已存在、非由 schema.sql 重建」的库补齐/改列；全新库由 schema.sql 直接建出。
--
-- 幂等：每步先查 information_schema，旧列存在且新列不存在才改；已改过或全新库均跳过。

-- ========== 1. ks_users.dept_id -> dept_code ==========
-- 直接 CHANGE 会把 id 值当 code 用，故先加列、回填、再删旧列。
SET @old_exists = (
    SELECT COUNT(1) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ks_users' AND COLUMN_NAME = 'dept_id'
);
SET @new_exists = (
    SELECT COUNT(1) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ks_users' AND COLUMN_NAME = 'dept_code'
);

SET @sql = IF(@old_exists > 0 AND @new_exists = 0,
    'ALTER TABLE ks_users ADD COLUMN dept_code VARCHAR(64) DEFAULT NULL COMMENT ''归属部门业务码（绑定 ks_departments.dept_code，非主键 id）'' AFTER real_name',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 回填：按旧 id 关联部门表换出 dept_code（部门已逻辑删除的行 code 仍回填，保持归属可追溯）
UPDATE ks_users u
    JOIN ks_departments d ON d.id = u.dept_id
    SET u.dept_code = d.dept_code
    WHERE u.dept_code IS NULL AND u.dept_id IS NOT NULL;

SET @sql = IF(@old_exists > 0 AND @new_exists = 0,
    'ALTER TABLE ks_users DROP COLUMN dept_id',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ========== 2. kb_apps.dept_id -> dept_code ==========
SET @old_exists = (
    SELECT COUNT(1) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_apps' AND COLUMN_NAME = 'dept_id'
);
SET @new_exists = (
    SELECT COUNT(1) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_apps' AND COLUMN_NAME = 'dept_code'
);

SET @sql = IF(@old_exists > 0 AND @new_exists = 0,
    'ALTER TABLE kb_apps ADD COLUMN dept_code VARCHAR(64) DEFAULT NULL COMMENT ''归属部门业务码（绑定 ks_departments.dept_code，非主键 id）'' AFTER name',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE kb_apps a
    JOIN ks_departments d ON d.id = a.dept_id
    SET a.dept_code = d.dept_code
    WHERE a.dept_code IS NULL AND a.dept_id IS NOT NULL;

SET @sql = IF(@old_exists > 0 AND @new_exists = 0,
    'ALTER TABLE kb_apps DROP COLUMN dept_id',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ========== 3. kb_provider_discount_policies.provider_account_id -> model_code ==========
-- ⚠ 语义变化（robin 明确）：**同一供应商账户下的不同模型可以有不同折扣**，所以折扣本就
--   是模型级概念，不存在「账户级折扣」这种东西。旧结构把折扣挂在账户上属于建模错误，
--   一个账户对应多个模型时（如 openai-default 下有 gpt-4o / gpt-4o-mini /
--   text-embedding-3-large / gpt-image-1），旧折扣到底该落到哪个模型**没有正确答案**。
--
--   因此本迁移**不做任何自动回填**：任何「取第一个模型」「取 id 最小」之类的猜测都会
--   凭空造出一条错误折扣（实测会让 claude-haiku 莫名继承本属 claude-sonnet 的折扣，
--   而同账户其余模型无折扣），比丢弃更危险——错误数据会被真实当成折扣执行。
--
--   处理方式（robin 拍板）：旧折扣行**一律作废删除**，由人工在管理端按模型重新配置。
--   这样 model_code 可保持 NOT NULL，语义干净、不留「未绑定」这种冗余状态。
--   建NOT NULL 约束前必须先 DELETE，故顺序为：加可空列 → 删旧行 → 收紧为 NOT NULL。
SET @old_exists = (
    SELECT COUNT(1) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_provider_discount_policies'
      AND COLUMN_NAME = 'provider_account_id'
);
SET @new_exists = (
    SELECT COUNT(1) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_provider_discount_policies'
      AND COLUMN_NAME = 'model_code'
);

SET @sql = IF(@old_exists > 0 AND @new_exists = 0,
    'ALTER TABLE kb_provider_discount_policies ADD COLUMN model_code VARCHAR(128) DEFAULT NULL COMMENT ''绑定的供应商模型业务码（kb_models.model_code），非主键 id'' AFTER id',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 不做自动回填（见上）：旧账户级折扣无正确模型可绑，作废删除，由人工按模型重建。
-- 仅在「本次确实刚加列」时才删（@old_exists>0 AND @new_exists=0），
-- 否则库里已是改后状态，无条件 DELETE 会误删人工配好的折扣。
SELECT COUNT(1) AS discount_policies_to_be_dropped
    FROM kb_provider_discount_policies WHERE model_code IS NULL;

SET @sql = IF(@old_exists > 0 AND @new_exists = 0,
    'DELETE FROM kb_provider_discount_policies WHERE model_code IS NULL',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 旧行已清空，收紧为 NOT NULL 与 schema.sql 保持一致
SET @sql = IF(@old_exists > 0 AND @new_exists = 0,
    'ALTER TABLE kb_provider_discount_policies MODIFY COLUMN model_code VARCHAR(128) NOT NULL COMMENT ''绑定的供应商模型业务码（kb_models.model_code），非主键 id''',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(@old_exists > 0 AND @new_exists = 0,
    'ALTER TABLE kb_provider_discount_policies DROP COLUMN provider_account_id',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 旧索引随列删除会一并消失，需重建为按 model_code 的索引
SET @idx_exists = (
    SELECT COUNT(1) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_provider_discount_policies'
      AND INDEX_NAME = 'idx_provider_discount_account_time'
);
SET @sql = IF(@idx_exists > 0,
    'ALTER TABLE kb_provider_discount_policies DROP INDEX idx_provider_discount_account_time',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @new_idx_exists = (
    SELECT COUNT(1) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kb_provider_discount_policies'
      AND INDEX_NAME = 'idx_provider_discount_model_time'
);
SET @sql = IF(@new_idx_exists = 0,
    'ALTER TABLE kb_provider_discount_policies ADD KEY idx_provider_discount_model_time (model_code, effective_from, effective_to)',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---- 执行后核对 ----
-- 1) 旧列应全部消失，仅剩新列（预期 6 行结果）：
--    SELECT TABLE_NAME, COLUMN_NAME FROM information_schema.COLUMNS
--     WHERE TABLE_SCHEMA = DATABASE()
--       AND ((TABLE_NAME='ks_users' AND COLUMN_NAME IN ('dept_id','dept_code'))
--         OR (TABLE_NAME='kb_apps' AND COLUMN_NAME IN ('dept_id','dept_code'))
--         OR (TABLE_NAME='kb_provider_discount_policies' AND COLUMN_NAME IN ('provider_account_id','model_code')));
-- 2) 回填结果（dept_code 不应为空；折扣 model_code 为空的行需人工配置）：
--    SELECT COUNT(*) FROM ks_users WHERE dept_id IS NULL AND dept_code IS NULL;  -- 旧列已删，改查：
--    SELECT username, dept_code FROM ks_users;
--    SELECT id, policy_name, model_code FROM kb_provider_discount_policies;
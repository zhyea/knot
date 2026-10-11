-- 供应商模型折扣策略瘦身（2026-10-11）
-- 移除 4 个「纯 CRUD 展示、网关运行时零引用」的字段：
--   policy_name（策略名）、scope_type（范围类型）、scope_ref_code（范围对象码）、priority（优先级）
-- 折扣在网关运行时并未被应用（knot-gateway 模块零 discount 引用），这 4 列仅 admin 端存储/展示，
-- 删除不影响任何计费/路由链路。effective_from/effective_to/status/discount_type/discount_value 保留。
--
-- 幂等：索引或列不存在时跳过，可安全重复执行。
-- ⚠ idx_provider_discount_scope 建立在 (scope_type, scope_ref_code, status) 上，
--   MariaDB 的 DROP COLUMN 不会自动清理该复合索引，必须先删索引再删列。

-- 1) 先删复合索引（若仍存在）
SET @stmt := IF(
    EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'kb_provider_discount_policies'
          AND index_name = 'idx_provider_discount_scope'
    ),
    'ALTER TABLE kb_provider_discount_policies DROP INDEX idx_provider_discount_scope',
    'DO 0'
);
PREPARE s1 FROM @stmt; EXECUTE s1; DEALLOCATE PREPARE s1;

-- 2) 逐列删除（仅当列仍存在）

SET @stmt := IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'kb_provider_discount_policies' AND column_name = 'policy_name'), 'ALTER TABLE kb_provider_discount_policies DROP COLUMN policy_name', 'DO 0');
PREPARE s1 FROM @stmt; EXECUTE s1; DEALLOCATE PREPARE s1;

SET @stmt := IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'kb_provider_discount_policies' AND column_name = 'scope_type'), 'ALTER TABLE kb_provider_discount_policies DROP COLUMN scope_type', 'DO 0');
PREPARE s1 FROM @stmt; EXECUTE s1; DEALLOCATE PREPARE s1;

SET @stmt := IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'kb_provider_discount_policies' AND column_name = 'scope_ref_code'), 'ALTER TABLE kb_provider_discount_policies DROP COLUMN scope_ref_code', 'DO 0');
PREPARE s1 FROM @stmt; EXECUTE s1; DEALLOCATE PREPARE s1;

SET @stmt := IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'kb_provider_discount_policies' AND column_name = 'priority'), 'ALTER TABLE kb_provider_discount_policies DROP COLUMN priority', 'DO 0');
PREPARE s1 FROM @stmt; EXECUTE s1; DEALLOCATE PREPARE s1;

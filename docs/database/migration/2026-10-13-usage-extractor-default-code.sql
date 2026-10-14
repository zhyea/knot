-- 默认 Usage 提取器编码收口为 DEFAULT-TOKEN（2026-10-13）
--
-- 背景：提交 ff09840 把 DefaultTokenUsageExtractor.CODE 从 'DEFAULT' 改为 'DEFAULT-TOKEN'，
--   但只改了一半——schema.sql 列默认值、data.sql 种子行、ModelService 落库兜底、前端默认值
--   与「默认」标签仍写 'DEFAULT'。后果：网关按 'DEFAULT' 解析提取器落空，静默走
--   UpstreamRequestAdapter.extractUsage 兜底路径（当前与默认提取器同口径，故长期被掩蔽）；
--   UsageExtractorView 的「默认」标签永不显示、ModelFormDrawer 的抽取器置顶排序失效。
--
--   本次全链路收口：后端 ModelService 与测试改用 DefaultTokenUsageExtractor.CODE 常量；
--   前端新增单一常量 DEFAULT_USAGE_EXTRACTOR_CODE（src/api/models.ts），ModelFormDrawer 的
--   表单兜底值 / 提交兜底值 / 下拉置顶排序与 UsageExtractorView 的「默认」标签同引该常量；
--   schema.sql 列默认值与 data.sql 种子行同步为 'DEFAULT-TOKEN'。
--   本迁移仅用于让「已存在、非由 schema.sql 重建」的库补齐数据；全新库由 schema.sql/data.sql 直接建出。
--
-- 幂等：UPDATE 与 ALTER SET DEFAULT 均可重复执行；重跑不报错且结果不变。

SET @exists = (
    SELECT COUNT(1)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'kb_model_api_bindings'
      AND COLUMN_NAME = 'usage_extractor'
);
SELECT @exists AS usage_extractor_exists;

SET @sql = IF(
    @exists = 1,
    'UPDATE kb_model_api_bindings SET usage_extractor = ''DEFAULT-TOKEN'' WHERE usage_extractor = ''DEFAULT''',
    'SELECT 1' -- 列不存在（表未建），跳过
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql2 = IF(
    @exists = 1,
    'ALTER TABLE kb_model_api_bindings ALTER COLUMN usage_extractor SET DEFAULT ''DEFAULT-TOKEN''',
    'SELECT 1'
);
PREPARE stmt2 FROM @sql2;
EXECUTE stmt2;
DEALLOCATE PREPARE stmt2;

-- ---- 执行后核对（用 .workbuddy/audit 下的 JDBC 探针跑）----
-- 预期返回 0（无残留旧编码）：
-- SELECT COUNT(1) FROM kb_model_api_bindings WHERE usage_extractor = 'DEFAULT';
--
-- 预期返回 'DEFAULT-TOKEN'：
-- SELECT COLUMN_DEFAULT FROM information_schema.COLUMNS
--  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME='kb_model_api_bindings' AND COLUMN_NAME='usage_extractor';

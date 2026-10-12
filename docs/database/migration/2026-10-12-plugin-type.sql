-- 插件分类：kb_plugin_instances 增加 plugin_type（同步插件 SYNC / 异步插件 ASYNC）
-- 幂等：先判列是否存在，再加列；存量实例按默认 SYNC 回填（当前内置插件均为同步 Handler）。
-- 既有库跑完不会因列已存在而报错；本脚本与 schema.sql/data.sql 保持一致。

SET @exist_plugin_type = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'kb_plugin_instances'
      AND COLUMN_NAME = 'plugin_type'
);

SET @sql_plugin_type = IF(
    @exist_plugin_type = 0,
    'ALTER TABLE kb_plugin_instances
       ADD COLUMN plugin_type VARCHAR(32) NOT NULL DEFAULT ''SYNC''
       COMMENT ''插件执行类型（PluginTypeEnum）：SYNC-同步插件（嵌入请求链内联执行），ASYNC-异步插件（离链消费事件）'',
       ADD KEY idx_plugin_instances_type (plugin_type, status)',
    'SELECT 1'
);

PREPARE stmt_plugin_type FROM @sql_plugin_type;
EXECUTE stmt_plugin_type;
DEALLOCATE PREPARE stmt_plugin_type;

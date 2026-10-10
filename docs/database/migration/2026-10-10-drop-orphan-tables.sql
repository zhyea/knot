-- 2026-10-10 清理废弃/下线表
-- 删除以下 6 张表及其全部数据：
--   ks_operation_log_details      操作日志明细（父表 ks_operation_logs 保留，仅删子表）
--   kb_app_credentials            应用凭证（网关按 app_key 鉴权的缓存已确认无调用方，一并移除）
--   kb_app_model_permissions      应用-模型权限
--   kr_plugin_execution_logs      插件执行流水（纯孤儿表，无任何代码读写）
--   kb_plugin_config_versions     插件配置版本（纯孤儿表，无任何代码读写）
--   kb_notification_records       通知发送记录（发送功能已从后端/前端移除）
--
-- 适用场景：针对已部署库执行本脚本；新库以 schema.sql 为准（建表语句已移除）。
-- 上述表均未被其它表以外键引用（系统内为逻辑引用，无 FK 约束），可直接 DROP。

DROP TABLE IF EXISTS ks_operation_log_details;
DROP TABLE IF EXISTS kb_app_credentials;
DROP TABLE IF EXISTS kb_app_model_permissions;
DROP TABLE IF EXISTS kr_plugin_execution_logs;
DROP TABLE IF EXISTS kb_plugin_config_versions;
DROP TABLE IF EXISTS kb_notification_records;

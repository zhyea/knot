-- ============================================================
-- Knot AI Gateway - 表前缀重命名迁移
-- 生成依据: docs/database/migration/table-rename-mapping.csv（已按 2026-09-21 拍板定稿）
-- 权威 schema: knot-server/knot-admin/src/main/resources/db/schema.sql
-- ============================================================
-- 执行前提（务必按顺序）：
--   1) 已全量备份：mysqldump -uroot -p --single-transaction knot > knot_backup_YYYYMMDD.sql
--   2) 应用已停写（admin 停服；gateway 建议一并停，避免 kr_* 流水表写入）
--   3) 全库无外键 / 无视图 / 无触发器依赖这些表（已核实）
--   4) 必须在部署新代码之前或与新代码同一窗口执行；不可先部署后迁移
-- 执行：mysql -uroot -p knot < 2026-09-21-rename-prefix.sql
-- 回滚：mysql -uroot -p knot < 2026-09-21-rename-prefix-rollback.sql
-- ============================================================

-- ----------------------------
-- ks_ 域（18 张）
-- ----------------------------
RENAME TABLE `users` TO `ks_users`;
RENAME TABLE `departments` TO `ks_departments`;
RENAME TABLE `user_settings` TO `ks_user_settings`;
RENAME TABLE `roles` TO `ks_roles`;
RENAME TABLE `user_roles` TO `ks_user_roles`;
RENAME TABLE `sys_modules` TO `ks_modules`;
RENAME TABLE `sys_menus` TO `ks_menus`;
RENAME TABLE `sys_permissions` TO `ks_permissions`;
RENAME TABLE `sys_role_permissions` TO `ks_role_permissions`;
RENAME TABLE `sys_api_permission_bindings` TO `ks_api_permission_bindings`;
RENAME TABLE `operation_logs` TO `ks_operation_logs`;
RENAME TABLE `operation_log_details` TO `ks_operation_log_details`;
RENAME TABLE `scheduled_tasks` TO `ks_scheduled_tasks`;
RENAME TABLE `scheduled_task_runs` TO `ks_scheduled_task_runs`;
RENAME TABLE `enum_categories` TO `ks_enum_categories`;
RENAME TABLE `enum_configs` TO `ks_enum_configs`;
RENAME TABLE `alerts` TO `ks_alerts`;
RENAME TABLE `cache_records` TO `ks_cache_records`;

-- ----------------------------
-- kb_ 域（31 张）
-- ----------------------------
RENAME TABLE `providers` TO `kb_providers`;
RENAME TABLE `provider_accounts` TO `kb_provider_accounts`;
RENAME TABLE `provider_credentials` TO `kb_provider_credentials`;
RENAME TABLE `provider_discount_policies` TO `kb_provider_discount_policies`;
RENAME TABLE `models` TO `kb_models`;
RENAME TABLE `model_pools` TO `kb_model_pools`;
RENAME TABLE `model_pool_items` TO `kb_model_pool_items`;
RENAME TABLE `logical_models` TO `kb_logical_models`;
RENAME TABLE `provider_model_mappings` TO `kb_provider_model_mappings`;
RENAME TABLE `model_api_bindings` TO `kb_model_api_bindings`;
RENAME TABLE `apps` TO `kb_apps`;
RENAME TABLE `app_credentials` TO `kb_app_credentials`;
RENAME TABLE `app_model_permissions` TO `kb_app_model_permissions`;
RENAME TABLE `routing_consumers` TO `kb_routing_consumers`;
RENAME TABLE `routing_rules` TO `kb_routing_rules`;
RENAME TABLE `routing_rule_consumers` TO `kb_routing_rule_consumers`;
RENAME TABLE `routing_rule_targets` TO `kb_routing_rule_targets`;
RENAME TABLE `rate_limit_policies` TO `kb_rate_limit_policies`;
RENAME TABLE `quota_policies` TO `kb_quota_policies`;
RENAME TABLE `resource_traffic_policies` TO `kb_resource_traffic_policies`;
RENAME TABLE `billing_rules` TO `kb_billing_rules`;
RENAME TABLE `billing_rule_versions` TO `kb_billing_rule_versions`;
RENAME TABLE `billing_rule_version_items` TO `kb_billing_rule_version_items`;
RENAME TABLE `security_policies` TO `kb_security_policies`;
RENAME TABLE `plugin_packages` TO `kb_plugin_packages`;
RENAME TABLE `plugin_capabilities` TO `kb_plugin_capabilities`;
RENAME TABLE `plugin_instances` TO `kb_plugin_instances`;
RENAME TABLE `plugin_bindings` TO `kb_plugin_bindings`;
RENAME TABLE `plugin_config_versions` TO `kb_plugin_config_versions`;
RENAME TABLE `notification_templates` TO `kb_notification_templates`;
RENAME TABLE `notification_records` TO `kb_notification_records`;

-- ----------------------------
-- kr_ 域（1 张）
-- ----------------------------
RENAME TABLE `plugin_execution_logs` TO `kr_plugin_execution_logs`;

-- ----------------------------
-- kx_ 域（2 张）
-- ----------------------------
RENAME TABLE `external_model_sources` TO `kx_model_sources`;
RENAME TABLE `external_model_items` TO `kx_model_items`;

-- =========================
-- 执行后校验（期望值：ks=18 / kb=31 / kr=1 / kx=2，总计 52）
-- =========================
SELECT 'ks_' AS prefix, COUNT(*) AS cnt FROM information_schema.tables
  WHERE table_schema = DATABASE() AND table_name LIKE 'ks\_%'
UNION ALL SELECT 'kb_', COUNT(*) FROM information_schema.tables
  WHERE table_schema = DATABASE() AND table_name LIKE 'kb\_%'
UNION ALL SELECT 'kr_', COUNT(*) FROM information_schema.tables
  WHERE table_schema = DATABASE() AND table_name LIKE 'kr\_%'
UNION ALL SELECT 'kx_', COUNT(*) FROM information_schema.tables
  WHERE table_schema = DATABASE() AND table_name LIKE 'kx\_%';

-- 残留旧表（期望 0）
SELECT table_name FROM information_schema.tables
  WHERE table_schema = DATABASE() AND table_name IN (
    'users','departments','user_settings','roles','user_roles','sys_modules','sys_menus',
    'sys_permissions','sys_role_permissions','sys_api_permission_bindings','operation_logs',
    'operation_log_details','scheduled_tasks','scheduled_task_runs','enum_categories','enum_configs',
    'providers','provider_accounts','provider_credentials','provider_discount_policies','models',
    'model_pools','model_pool_items','logical_models','provider_model_mappings','model_api_bindings',
    'apps','app_credentials','app_model_permissions','routing_consumers','routing_rules',
    'routing_rule_consumers','routing_rule_targets','rate_limit_policies','quota_policies',
    'resource_traffic_policies','billing_rules','billing_rule_versions','billing_rule_version_items',
    'security_policies','plugin_packages','plugin_capabilities','plugin_instances','plugin_bindings',
    'plugin_config_versions','notification_templates','notification_records','alerts','cache_records',
    'plugin_execution_logs','external_model_sources','external_model_items'
  );

-- 核心表行数抽样（与迁移前快照对比，必须一致）
-- SELECT (SELECT COUNT(*) FROM ks_users) AS ks_users,
--        (SELECT COUNT(*) FROM kb_provider_accounts) AS kb_provider_accounts,
--        (SELECT COUNT(*) FROM kb_models) AS kb_models,
--        (SELECT COUNT(*) FROM kb_providers) AS kb_providers;

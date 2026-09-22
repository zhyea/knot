-- ============================================================
-- 回滚脚本：新表名 -> 旧表名（与正向脚本严格互逆）
-- 仅在迁移后验证失败时使用；执行前同样需停写 + 备份
-- ============================================================

RENAME TABLE `ks_users` TO `users`;
RENAME TABLE `ks_departments` TO `departments`;
RENAME TABLE `ks_user_settings` TO `user_settings`;
RENAME TABLE `ks_roles` TO `roles`;
RENAME TABLE `ks_user_roles` TO `user_roles`;
RENAME TABLE `ks_modules` TO `sys_modules`;
RENAME TABLE `ks_menus` TO `sys_menus`;
RENAME TABLE `ks_permissions` TO `sys_permissions`;
RENAME TABLE `ks_role_permissions` TO `sys_role_permissions`;
RENAME TABLE `ks_api_permission_bindings` TO `sys_api_permission_bindings`;
RENAME TABLE `ks_operation_logs` TO `operation_logs`;
RENAME TABLE `ks_operation_log_details` TO `operation_log_details`;
RENAME TABLE `ks_scheduled_tasks` TO `scheduled_tasks`;
RENAME TABLE `ks_scheduled_task_runs` TO `scheduled_task_runs`;
RENAME TABLE `ks_enum_categories` TO `enum_categories`;
RENAME TABLE `ks_enum_configs` TO `enum_configs`;
RENAME TABLE `kb_providers` TO `providers`;
RENAME TABLE `kb_provider_accounts` TO `provider_accounts`;
RENAME TABLE `kb_provider_credentials` TO `provider_credentials`;
RENAME TABLE `kb_provider_discount_policies` TO `provider_discount_policies`;
RENAME TABLE `kb_models` TO `models`;
RENAME TABLE `kb_model_pools` TO `model_pools`;
RENAME TABLE `kb_model_pool_items` TO `model_pool_items`;
RENAME TABLE `kb_logical_models` TO `logical_models`;
RENAME TABLE `kb_provider_model_mappings` TO `provider_model_mappings`;
RENAME TABLE `kb_model_api_bindings` TO `model_api_bindings`;
RENAME TABLE `kb_apps` TO `apps`;
RENAME TABLE `kb_app_credentials` TO `app_credentials`;
RENAME TABLE `kb_app_model_permissions` TO `app_model_permissions`;
RENAME TABLE `kb_routing_consumers` TO `routing_consumers`;
RENAME TABLE `kb_routing_rules` TO `routing_rules`;
RENAME TABLE `kb_routing_rule_consumers` TO `routing_rule_consumers`;
RENAME TABLE `kb_routing_rule_targets` TO `routing_rule_targets`;
RENAME TABLE `kb_rate_limit_policies` TO `rate_limit_policies`;
RENAME TABLE `kb_quota_policies` TO `quota_policies`;
RENAME TABLE `kb_resource_traffic_policies` TO `resource_traffic_policies`;
RENAME TABLE `kb_billing_rules` TO `billing_rules`;
RENAME TABLE `kb_billing_rule_versions` TO `billing_rule_versions`;
RENAME TABLE `kb_billing_rule_version_items` TO `billing_rule_version_items`;
RENAME TABLE `kb_security_policies` TO `security_policies`;
RENAME TABLE `kb_plugin_packages` TO `plugin_packages`;
RENAME TABLE `kb_plugin_capabilities` TO `plugin_capabilities`;
RENAME TABLE `kb_plugin_instances` TO `plugin_instances`;
RENAME TABLE `kb_plugin_bindings` TO `plugin_bindings`;
RENAME TABLE `kb_plugin_config_versions` TO `plugin_config_versions`;
RENAME TABLE `kb_notification_templates` TO `notification_templates`;
RENAME TABLE `kb_notification_records` TO `notification_records`;
RENAME TABLE `ks_alerts` TO `alerts`;
RENAME TABLE `ks_cache_records` TO `cache_records`;
RENAME TABLE `kr_plugin_execution_logs` TO `plugin_execution_logs`;
RENAME TABLE `kx_model_sources` TO `external_model_sources`;
RENAME TABLE `kx_model_items` TO `external_model_items`;

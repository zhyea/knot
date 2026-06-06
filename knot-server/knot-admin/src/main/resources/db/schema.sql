-- ============================================================
-- Knot AI Gateway - Schema Definition
-- Compatible with Spring Boot spring.sql.init
-- ============================================================

-- =========================
-- 系统管理
-- =========================
CREATE TABLE IF NOT EXISTS users (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(64) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  real_name VARCHAR(100) DEFAULT NULL,
  dept_id BIGINT DEFAULT NULL,
  phone VARCHAR(32) DEFAULT NULL,
  email VARCHAR(128) DEFAULT NULL,
  status INT NOT NULL DEFAULT 1,
  last_login_time DATETIME DEFAULT NULL,
  is_deleted TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_users_username (username)
);

CREATE TABLE IF NOT EXISTS departments (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  dept_code VARCHAR(64) NOT NULL,
  dept_name VARCHAR(100) NOT NULL,
  parent_id BIGINT DEFAULT NULL,
  status INT NOT NULL DEFAULT 1,
  sort_order INT NOT NULL DEFAULT 0,
  remark VARCHAR(255) DEFAULT NULL,
  is_deleted TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_departments_code (dept_code)
);

CREATE TABLE IF NOT EXISTS user_settings (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  setting_key VARCHAR(64) NOT NULL,
  setting_value TEXT DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_settings_user_key (user_id, setting_key),
  KEY idx_user_settings_user (user_id)
);

CREATE TABLE IF NOT EXISTS roles (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  code VARCHAR(64) NOT NULL,
  name VARCHAR(100) NOT NULL,
  description VARCHAR(255) DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_roles_code (code)
);

CREATE TABLE IF NOT EXISTS user_roles (
  user_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  PRIMARY KEY (user_id, role_id)
);

CREATE TABLE IF NOT EXISTS operation_logs (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  module VARCHAR(64) NOT NULL COMMENT '模块名称，如 user、enum、provider、model等',
  operation VARCHAR(32) NOT NULL COMMENT '操作类型：CREATE/UPDATE/DELETE/LOGIN/LOGOUT等',
  entity_type VARCHAR(64) DEFAULT NULL COMMENT '实体类型，如 User、EnumConfig等',
  entity_id BIGINT DEFAULT NULL COMMENT '实体ID',
  entity_name VARCHAR(255) DEFAULT NULL COMMENT '实体名称或标识',
  description VARCHAR(500) DEFAULT NULL COMMENT '操作描述',
  old_value JSON DEFAULT NULL COMMENT '操作前的值',
  new_value JSON DEFAULT NULL COMMENT '操作后的值',
  operator_id BIGINT DEFAULT NULL COMMENT '操作人ID',
  operator_name VARCHAR(64) DEFAULT NULL COMMENT '操作人姓名',
  ip_address VARCHAR(64) DEFAULT NULL COMMENT 'IP地址',
  user_agent VARCHAR(500) DEFAULT NULL COMMENT '浏览器UA',
  status VARCHAR(32) NOT NULL DEFAULT 'SUCCESS' COMMENT '操作状态：SUCCESS/FAILURE',
  error_msg VARCHAR(1000) DEFAULT NULL COMMENT '错误信息',
  execution_time BIGINT DEFAULT NULL COMMENT '耗时(ms)',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_module (module),
  KEY idx_operator (operator_id),
  KEY idx_entity (entity_type, entity_id),
  KEY idx_created_at (created_at)
);

CREATE TABLE IF NOT EXISTS operation_log_details (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  log_id BIGINT NOT NULL,
  request_json JSON DEFAULT NULL,
  response_json JSON DEFAULT NULL,
  before_json JSON DEFAULT NULL,
  after_json JSON DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_operation_log_details_log (log_id)
);

CREATE TABLE IF NOT EXISTS scheduled_tasks (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_code VARCHAR(64) NOT NULL,
  task_name VARCHAR(128) NOT NULL,
  handler_code VARCHAR(64) NOT NULL,
  cron_expression VARCHAR(64) NOT NULL,
  execution_mode VARCHAR(32) NOT NULL DEFAULT 'SINGLE',
  status VARCHAR(32) NOT NULL DEFAULT 'ENABLED',
  description VARCHAR(500) DEFAULT NULL,
  last_fire_at DATETIME DEFAULT NULL,
  next_fire_at DATETIME DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_scheduled_tasks_code (task_code),
  KEY idx_scheduled_tasks_status (status),
  KEY idx_scheduled_tasks_handler (handler_code)
);

CREATE TABLE IF NOT EXISTS scheduled_task_runs (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_id BIGINT NOT NULL,
  task_code VARCHAR(64) NOT NULL,
  task_name VARCHAR(128) NOT NULL,
  execution_mode VARCHAR(32) NOT NULL,
  node_id VARCHAR(128) NOT NULL,
  trigger_type VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL,
  start_time DATETIME NOT NULL,
  end_time DATETIME DEFAULT NULL,
  duration_ms BIGINT DEFAULT NULL,
  affected_rows INT NOT NULL DEFAULT 0,
  error_msg VARCHAR(1000) DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_task_runs_task_time (task_code, start_time),
  KEY idx_task_runs_status_time (status, start_time),
  KEY idx_task_runs_created_at (created_at)
);

-- =========================
-- 频控与额度（独立策略 + 资源绑定）
-- =========================
CREATE TABLE IF NOT EXISTS rate_limit_policies (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  policy_code VARCHAR(64) NOT NULL,
  policy_name VARCHAR(100) NOT NULL,
  per_second INT NOT NULL DEFAULT 0,
  per_minute INT NOT NULL DEFAULT 0,
  time_window VARCHAR(32) NOT NULL DEFAULT 'MINUTE',
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  remark VARCHAR(255) DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_rate_limit_policies_code (policy_code)
);

CREATE TABLE IF NOT EXISTS quota_policies (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  policy_code VARCHAR(64) NOT NULL,
  policy_name VARCHAR(100) NOT NULL,
  daily_limit BIGINT NOT NULL DEFAULT 0,
  monthly_limit BIGINT NOT NULL DEFAULT 0,
  token_limit BIGINT NOT NULL DEFAULT 0,
  alert_enabled TINYINT NOT NULL DEFAULT 0,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  remark VARCHAR(255) DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_quota_policies_code (policy_code)
);

CREATE TABLE IF NOT EXISTS resource_traffic_policies (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  resource_type VARCHAR(32) NOT NULL COMMENT 'APP / MODEL / PROVIDER',
  resource_id BIGINT NOT NULL,
  rate_limit_policy_id BIGINT DEFAULT NULL,
  quota_policy_id BIGINT DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_resource_traffic (resource_type, resource_id),
  KEY idx_resource_traffic_rate (rate_limit_policy_id),
  KEY idx_resource_traffic_quota (quota_policy_id)
);

-- =========================
-- 供应商与模型
-- =========================
CREATE TABLE IF NOT EXISTS providers (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  code VARCHAR(32) NOT NULL,
  name VARCHAR(100) NOT NULL,
  provider_type VARCHAR(64) NOT NULL,
  base_url VARCHAR(255) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ENABLED',
  contact_name VARCHAR(100) DEFAULT NULL,
  contact_phone VARCHAR(32) DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_providers_code (code)
);

CREATE TABLE IF NOT EXISTS provider_credentials (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  provider_id BIGINT NOT NULL,
  credential_type VARCHAR(32) NOT NULL,
  encrypted_key TEXT DEFAULT NULL,
  encrypted_secret TEXT DEFAULT NULL,
  token_value TEXT DEFAULT NULL,
  expire_at DATETIME DEFAULT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_provider_credentials_provider (provider_id)
);

CREATE TABLE IF NOT EXISTS provider_discount_policies (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  provider_id BIGINT NOT NULL,
  policy_name VARCHAR(100) NOT NULL,
  scope_type VARCHAR(32) NOT NULL,
  scope_ref_id BIGINT DEFAULT NULL,
  discount_type VARCHAR(32) NOT NULL,
  discount_value DECIMAL(10,4) NOT NULL,
  priority INT NOT NULL DEFAULT 100,
  effective_from DATETIME NOT NULL,
  effective_to DATETIME DEFAULT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  remark VARCHAR(255) DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_provider_discount_provider_time (provider_id, effective_from, effective_to),
  KEY idx_provider_discount_scope (scope_type, scope_ref_id, status)
);

CREATE TABLE IF NOT EXISTS models (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  provider_id BIGINT NOT NULL,
  model_code VARCHAR(128) NOT NULL,
  name VARCHAR(100) NOT NULL,
  model_type VARCHAR(64) NOT NULL,
  version VARCHAR(64) NOT NULL,
  billing_rule_id BIGINT DEFAULT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'DISABLED',
  tags_json JSON DEFAULT NULL,
  params_json JSON DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_models_code (model_code),
  KEY idx_models_provider (provider_id)
);

CREATE TABLE IF NOT EXISTS model_pools (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  pool_code VARCHAR(64) NOT NULL,
  name VARCHAR(100) NOT NULL,
  model_type VARCHAR(64) NOT NULL,
  selection_strategy VARCHAR(32) NOT NULL DEFAULT 'WEIGHTED',
  status VARCHAR(32) NOT NULL DEFAULT 'DISABLED',
  remark VARCHAR(255) DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_model_pools_code (pool_code),
  KEY idx_model_pools_type_status (model_type, status)
);

CREATE TABLE IF NOT EXISTS model_pool_items (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  pool_id BIGINT NOT NULL,
  model_id BIGINT NOT NULL,
  weight INT NOT NULL DEFAULT 100,
  priority INT NOT NULL DEFAULT 100,
  status VARCHAR(32) NOT NULL DEFAULT 'ENABLED',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_model_pool_item (pool_id, model_id),
  KEY idx_model_pool_items_pool (pool_id, status, priority),
  KEY idx_model_pool_items_model (model_id)
);

CREATE TABLE IF NOT EXISTS logical_models (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  model_code VARCHAR(128) NOT NULL,
  model_name VARCHAR(128) NOT NULL,
  model_type VARCHAR(64) NOT NULL,
  model_family VARCHAR(64) DEFAULT NULL,
  version VARCHAR(64) DEFAULT NULL,
  external_source_code VARCHAR(64) DEFAULT NULL,
  external_model_id VARCHAR(160) DEFAULT NULL,
  canonical_slug VARCHAR(256) DEFAULT NULL,
  provider_code VARCHAR(128) DEFAULT NULL,
  provider_name VARCHAR(128) DEFAULT NULL,
  display_name VARCHAR(128) DEFAULT NULL,
  tagline VARCHAR(255) DEFAULT NULL,
  description TEXT DEFAULT NULL,
  logo_url VARCHAR(512) DEFAULT NULL,
  cover_url VARCHAR(512) DEFAULT NULL,
  tags_json JSON DEFAULT NULL,
  use_cases_json JSON DEFAULT NULL,
  capabilities_json JSON DEFAULT NULL,
  context_window INT DEFAULT NULL,
  max_output_tokens INT DEFAULT NULL,
  input_modalities_json JSON DEFAULT NULL,
  output_modalities_json JSON DEFAULT NULL,
  languages_json JSON DEFAULT NULL,
  default_params_json JSON DEFAULT NULL,
  param_schema_json JSON DEFAULT NULL,
  safety_policy_json JSON DEFAULT NULL,
  pricing_json JSON DEFAULT NULL,
  supported_parameters_json JSON DEFAULT NULL,
  visibility VARCHAR(32) NOT NULL DEFAULT 'PUBLIC',
  publish_status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
  status VARCHAR(32) NOT NULL DEFAULT 'ENABLED',
  sort_order INT NOT NULL DEFAULT 0,
  featured TINYINT NOT NULL DEFAULT 0,
  owner_user_id BIGINT DEFAULT NULL,
  owner_team VARCHAR(128) DEFAULT NULL,
  quality_level VARCHAR(32) DEFAULT NULL,
  latency_level VARCHAR(32) DEFAULT NULL,
  cost_level VARCHAR(32) DEFAULT NULL,
  pricing_summary VARCHAR(255) DEFAULT NULL,
  remark VARCHAR(255) DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_logical_models_code (model_code),
  KEY idx_logical_models_external (external_source_code, external_model_id),
  KEY idx_logical_models_type_status (model_type, publish_status, status),
  KEY idx_logical_models_sort (featured, sort_order)
);

CREATE TABLE IF NOT EXISTS provider_model_mappings (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  logical_model_id BIGINT NOT NULL,
  provider_id BIGINT NOT NULL,
  model_id BIGINT NOT NULL,
  provider_model_name VARCHAR(128) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ENABLED',
  priority INT NOT NULL DEFAULT 100,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_provider_model_mapping_model (model_id),
  KEY idx_logical_provider_model (logical_model_id, provider_id, model_id),
  KEY idx_logical_model (logical_model_id, status),
  KEY idx_provider_model (provider_id, model_id, status)
);

CREATE TABLE IF NOT EXISTS external_model_sources (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  source_code VARCHAR(64) NOT NULL,
  source_name VARCHAR(128) NOT NULL,
  source_url VARCHAR(512) NOT NULL,
  api_url VARCHAR(512) NOT NULL,
  source_type VARCHAR(32) NOT NULL DEFAULT 'COMMERCIAL_LLM',
  status VARCHAR(32) NOT NULL DEFAULT 'ENABLED',
  last_sync_at DATETIME DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_external_model_sources_code (source_code)
);

CREATE TABLE IF NOT EXISTS external_model_items (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  source_code VARCHAR(64) NOT NULL,
  model_id VARCHAR(160) NOT NULL,
  canonical_slug VARCHAR(256) DEFAULT NULL,
  hugging_face_id VARCHAR(256) DEFAULT NULL,
  model_name VARCHAR(256) NOT NULL,
  provider_code VARCHAR(128) DEFAULT NULL,
  provider_name VARCHAR(128) DEFAULT NULL,
  model_url VARCHAR(512) DEFAULT NULL,
  details_path VARCHAR(512) DEFAULT NULL,
  created_timestamp BIGINT DEFAULT NULL,
  model_created_at DATETIME DEFAULT NULL,
  description TEXT DEFAULT NULL,
  context_length INT DEFAULT NULL,
  modality VARCHAR(128) DEFAULT NULL,
  input_modalities_json JSON DEFAULT NULL,
  output_modalities_json JSON DEFAULT NULL,
  tokenizer VARCHAR(128) DEFAULT NULL,
  instruct_type VARCHAR(128) DEFAULT NULL,
  pricing_json JSON DEFAULT NULL,
  top_provider_json JSON DEFAULT NULL,
  supported_parameters_json JSON DEFAULT NULL,
  default_parameters_json JSON DEFAULT NULL,
  supported_voices_json JSON DEFAULT NULL,
  knowledge_cutoff VARCHAR(64) DEFAULT NULL,
  expiration_date VARCHAR(64) DEFAULT NULL,
  raw_json JSON DEFAULT NULL,
  normalized_name VARCHAR(256) DEFAULT NULL,
  model_family VARCHAR(128) DEFAULT NULL,
  model_type VARCHAR(64) DEFAULT NULL,
  tags_json JSON DEFAULT NULL,
  capabilities_json JSON DEFAULT NULL,
  max_completion_tokens INT DEFAULT NULL,
  logical_model_id BIGINT DEFAULT NULL,
  sync_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  sync_hash VARCHAR(64) DEFAULT NULL,
  last_seen_at DATETIME DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_external_model_item (source_code, model_id),
  KEY idx_external_model_logical_model (logical_model_id),
  KEY idx_external_model_provider (provider_code),
  KEY idx_external_model_created_time (model_created_at),
  KEY idx_external_model_sync_status (sync_status)
);

-- 供应商模型与 API 协议绑定（协议 + 消耗取值逻辑）
CREATE TABLE IF NOT EXISTS model_api_bindings (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  model_id BIGINT NOT NULL COMMENT '模型 ID，关联 models.id',
  protocol VARCHAR(64) NOT NULL COMMENT 'API 协议：OPENAI_CHAT_COMPLETIONS / OPENAI_COMPLETIONS / OPENAI_RESPONSES / ANTHROPIC_MESSAGES / OTHER',
  api_path VARCHAR(255) DEFAULT NULL COMMENT '上游 API 路径，为空时使用协议默认路径',
  usage_extractor VARCHAR(255) NOT NULL DEFAULT 'DEFAULT' COMMENT '非流式响应 Usage 解析器编码或类名',
  stream_usage_extractor VARCHAR(255) DEFAULT NULL COMMENT '流式响应 Usage 解析器编码或类名，为空时复用 usage_extractor',
  status VARCHAR(32) NOT NULL DEFAULT 'ENABLED',
  remark VARCHAR(255) DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_model_api_protocol (model_id, protocol),
  KEY idx_model_api_bindings_model (model_id),
  KEY idx_model_api_bindings_protocol (protocol)
);

-- =========================
-- 应用管理
-- =========================
CREATE TABLE IF NOT EXISTS apps (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  app_id VARCHAR(64) NOT NULL,
  name VARCHAR(100) NOT NULL,
  dept_id BIGINT DEFAULT NULL,
  owner_user_id BIGINT DEFAULT NULL,
  remark VARCHAR(255) DEFAULT NULL COMMENT '备注',
  is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-否 1-是',
  app_type VARCHAR(32) DEFAULT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ENABLED',
  ip_whitelist_json JSON DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_apps_app_id (app_id)
);

CREATE TABLE IF NOT EXISTS app_credentials (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  app_id BIGINT NOT NULL,
  app_key VARCHAR(128) NOT NULL,
  app_secret_hash VARCHAR(255) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_app_credentials_key (app_key),
  KEY idx_app_credentials_app (app_id)
);

CREATE TABLE IF NOT EXISTS app_model_permissions (
  app_id BIGINT NOT NULL,
  model_id BIGINT NOT NULL,
  PRIMARY KEY (app_id, model_id)
);

-- =========================
-- 路由规则
-- =========================
CREATE TABLE IF NOT EXISTS routing_consumers (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  consumer_code VARCHAR(32) NOT NULL,
  name VARCHAR(100) NOT NULL,
  user_id BIGINT DEFAULT NULL,
  secret_key VARCHAR(64) NOT NULL,
  return_usage_detail TINYINT(1) NOT NULL DEFAULT 0,
  status VARCHAR(32) NOT NULL DEFAULT 'ENABLED',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_routing_consumers_code (consumer_code),
  UNIQUE KEY uk_routing_consumers_secret (secret_key),
  KEY idx_routing_consumers_status (status)
);

CREATE TABLE IF NOT EXISTS routing_rules (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  rule_code VARCHAR(32) NOT NULL,
  name VARCHAR(100) NOT NULL,
  app_scenario VARCHAR(128) DEFAULT NULL,
  model_types VARCHAR(255) NOT NULL DEFAULT 'CHAT',
  app_id BIGINT DEFAULT NULL,
  user_id BIGINT DEFAULT NULL,
  fallback_rule_id BIGINT DEFAULT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ENABLED',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_routing_rules_code (rule_code),
  KEY idx_routing_rules_app (app_id, status)
);

CREATE TABLE IF NOT EXISTS routing_rule_consumers (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  rule_id BIGINT NOT NULL,
  consumer_id BIGINT NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ENABLED',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_routing_rule_consumer (rule_id, consumer_id),
  KEY idx_rrc_consumer (consumer_id, status),
  KEY idx_rrc_rule (rule_id, status)
);

CREATE TABLE IF NOT EXISTS routing_rule_targets (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  rule_id BIGINT NOT NULL,
  target_type VARCHAR(32) NOT NULL,
  target_id BIGINT NOT NULL,
  priority INT NOT NULL DEFAULT 100,
  is_primary TINYINT(1) NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_routing_rule_target (rule_id, target_type, target_id),
  KEY idx_routing_rule_targets_rule (rule_id, priority)
);

-- =========================
-- 计费与调用明细
-- =========================
CREATE TABLE IF NOT EXISTS billing_rules (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  code VARCHAR(64) NOT NULL,
  name VARCHAR(100) NOT NULL,
  provider_id BIGINT DEFAULT NULL,
  logical_model_id BIGINT DEFAULT NULL,
  current_version_id BIGINT DEFAULT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  is_deleted TINYINT NOT NULL DEFAULT 0,
  remark VARCHAR(255) DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_billing_rules_code (code),
  KEY idx_billing_rules_match (provider_id, logical_model_id, status, is_deleted)
);

CREATE TABLE IF NOT EXISTS billing_rule_versions (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  rule_id BIGINT NOT NULL,
  version_no INT NOT NULL,
  version_code VARCHAR(32) DEFAULT NULL,
  billing_mode VARCHAR(32) NOT NULL,
  currency VARCHAR(16) NOT NULL DEFAULT 'USD',
  config_json JSON DEFAULT NULL,
  ladder_json JSON DEFAULT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  effective_from DATETIME NOT NULL,
  effective_to DATETIME DEFAULT NULL,
  change_reason VARCHAR(255) DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_billing_rule_version (rule_id, version_no),
  KEY idx_billing_rule_versions_active (rule_id, status, effective_from, effective_to)
);


CREATE TABLE IF NOT EXISTS billing_rule_version_items (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  version_id BIGINT NOT NULL,
  item_type VARCHAR(64) NOT NULL,
  unit VARCHAR(32) NOT NULL,
  unit_size INT NOT NULL DEFAULT 1000,
  unit_price DECIMAL(18,6) NOT NULL,
  metadata_json JSON DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_billing_rule_version_items_version (version_id)
);

-- =========================
-- 安全与监控
-- =========================
CREATE TABLE IF NOT EXISTS security_policies (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  policy_type VARCHAR(32) NOT NULL,
  policy_code VARCHAR(64) NOT NULL,
  config_json JSON NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ENABLED',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_security_policies_code (policy_code)
);

CREATE TABLE IF NOT EXISTS alerts (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  alert_type VARCHAR(32) NOT NULL,
  level VARCHAR(16) NOT NULL,
  biz_type VARCHAR(32) DEFAULT NULL,
  biz_id BIGINT DEFAULT NULL,
  title VARCHAR(200) NOT NULL,
  content TEXT DEFAULT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  resolved_at DATETIME DEFAULT NULL,
  KEY idx_alerts_level_status_time (level, status, created_at)
);

CREATE TABLE IF NOT EXISTS cache_records (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  cache_key VARCHAR(255) NOT NULL,
  cache_type VARCHAR(64) NOT NULL,
  expire_at DATETIME DEFAULT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_cache_records_key (cache_key)
);

-- =========================
-- 可选扩展
-- =========================
CREATE TABLE IF NOT EXISTS plugins (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  code VARCHAR(64) NOT NULL,
  name VARCHAR(100) NOT NULL,
  plugin_type VARCHAR(32) NOT NULL,
  version VARCHAR(32) NOT NULL,
  config_json JSON DEFAULT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'DISABLED',
  UNIQUE KEY uk_plugins_code (code)
);

CREATE TABLE IF NOT EXISTS notification_templates (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  code VARCHAR(64) NOT NULL,
  name VARCHAR(100) NOT NULL,
  channel VARCHAR(32) NOT NULL,
  title_tpl VARCHAR(255) NOT NULL,
  content_tpl TEXT NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  UNIQUE KEY uk_notification_templates_code (code)
);

CREATE TABLE IF NOT EXISTS notification_records (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  template_id BIGINT NOT NULL,
  receiver VARCHAR(255) NOT NULL,
  channel VARCHAR(32) NOT NULL,
  send_status VARCHAR(32) NOT NULL,
  error_msg VARCHAR(255) DEFAULT NULL,
  sent_at DATETIME DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_notification_records_template (template_id)
);


-- =========================
-- 枚举配置
-- =========================
-- 枚举分类表
CREATE TABLE IF NOT EXISTS enum_categories (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    category    VARCHAR(64)  NOT NULL COMMENT '分类编码，如 provider_type、model_type',
    category_name VARCHAR(128) NOT NULL COMMENT '分类名称，如 供应商类型、模型类型',
    description VARCHAR(255) DEFAULT NULL COMMENT '分类描述',
    is_system   TINYINT      NOT NULL DEFAULT 0 COMMENT '1=系统内置分类，其下枚举项不可删改',
    is_enabled  TINYINT      NOT NULL DEFAULT 1 COMMENT '1=启用 0=禁用',
    is_deleted  TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=否 1=是',
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_enum_category (category)
);

-- 枚举配置表（移除category字段，改为category_id）
CREATE TABLE IF NOT EXISTS enum_configs (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    category_id BIGINT       NOT NULL COMMENT '分类ID',
    item_code   VARCHAR(64)  NOT NULL COMMENT '枚举编码，如 OPENAI、CHAT',
    item_label  VARCHAR(128) NOT NULL COMMENT '显示名称，如 OpenAI、对话',
    sort_order  INT          NOT NULL DEFAULT 0 COMMENT '排序',
    is_enabled  TINYINT      NOT NULL DEFAULT 1 COMMENT '1=启用 0=禁用',
    is_deleted  TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=否 1=是',
    remark      VARCHAR(255) DEFAULT NULL COMMENT '备注',
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_enum_category_code (category_id, item_code)
);

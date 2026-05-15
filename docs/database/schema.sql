-- AI 网关数据库初始化脚本（MySQL 8+）
CREATE DATABASE IF NOT EXISTS knot_ai_gateway DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE knot_ai_gateway;

-- =========================
-- 系统管理
-- =========================
CREATE TABLE IF NOT EXISTS departments (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  parent_id BIGINT DEFAULT NULL,
  name VARCHAR(100) NOT NULL,
  code VARCHAR(64) DEFAULT NULL,
  is_deleted TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS users (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(64) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  real_name VARCHAR(100) DEFAULT NULL,
  phone VARCHAR(32) DEFAULT NULL,
  email VARCHAR(128) DEFAULT NULL,
  status INT NOT NULL DEFAULT 1,
  last_login_time DATETIME DEFAULT NULL,
  is_deleted TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_users_username (username)
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

CREATE TABLE IF NOT EXISTS permissions (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  code VARCHAR(128) NOT NULL,
  name VARCHAR(100) NOT NULL,
  module_code VARCHAR(64) NOT NULL,
  action_code VARCHAR(64) NOT NULL,
  UNIQUE KEY uk_permissions_code (code)
);

CREATE TABLE IF NOT EXISTS user_roles (
  user_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  PRIMARY KEY (user_id, role_id)
);

CREATE TABLE IF NOT EXISTS role_permissions (
  role_id BIGINT NOT NULL,
  permission_id BIGINT NOT NULL,
  PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE IF NOT EXISTS operation_logs (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT DEFAULT NULL,
  tenant_id BIGINT DEFAULT NULL,
  module_code VARCHAR(64) NOT NULL,
  action_code VARCHAR(64) NOT NULL,
  target_id VARCHAR(64) DEFAULT NULL,
  trace_id VARCHAR(64) DEFAULT NULL,
  request_ip VARCHAR(64) DEFAULT NULL,
  user_agent VARCHAR(255) DEFAULT NULL,
  request_path VARCHAR(255) DEFAULT NULL,
  request_method VARCHAR(16) DEFAULT NULL,
  result_status VARCHAR(16) NOT NULL DEFAULT 'SUCCESS',
  error_code VARCHAR(64) DEFAULT NULL,
  error_msg VARCHAR(255) DEFAULT NULL,
  duration_ms INT NOT NULL DEFAULT 0,
  risk_level VARCHAR(16) NOT NULL DEFAULT 'LOW',
  detail_json JSON DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_operation_logs_module_time (module_code, created_at),
  KEY idx_operation_logs_user_time (user_id, created_at),
  KEY idx_operation_logs_trace_id (trace_id)
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

CREATE TABLE IF NOT EXISTS gateway_nodes (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  node_id VARCHAR(64) NOT NULL,
  host VARCHAR(128) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ONLINE',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_gateway_nodes_node_id (node_id)
);

CREATE TABLE IF NOT EXISTS backup_jobs (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  job_code VARCHAR(64) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'RUNNING',
  snapshot_ref VARCHAR(128) DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_backup_jobs_job_code (job_code)
);

-- =========================
-- 供应商与模型
-- =========================
CREATE TABLE IF NOT EXISTS providers (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  code VARCHAR(64) NOT NULL,
  name VARCHAR(100) NOT NULL,
  provider_type VARCHAR(64) NOT NULL,
  base_url VARCHAR(255) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ENABLED',
  contact_name VARCHAR(100) DEFAULT NULL,
  contact_phone VARCHAR(32) DEFAULT NULL,
  rate_limit_json JSON DEFAULT NULL,
  quota_json JSON DEFAULT NULL,
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

CREATE TABLE IF NOT EXISTS provider_discount_snapshots (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  request_id VARCHAR(64) NOT NULL,
  provider_id BIGINT NOT NULL,
  policy_id BIGINT NOT NULL,
  original_amount DECIMAL(18,6) NOT NULL,
  discount_amount DECIMAL(18,6) NOT NULL,
  final_amount DECIMAL(18,6) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_provider_discount_snapshots_req (request_id),
  KEY idx_provider_discount_snapshots_provider_time (provider_id, created_at)
);

CREATE TABLE IF NOT EXISTS models (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  provider_id BIGINT NOT NULL,
  model_code VARCHAR(128) NOT NULL,
  name VARCHAR(100) NOT NULL,
  model_type VARCHAR(64) NOT NULL,
  version VARCHAR(64) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ENABLED',
  tags_json JSON DEFAULT NULL,
  params_json JSON DEFAULT NULL,
  rate_limit_json JSON DEFAULT NULL,
  quota_json JSON DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_models_code (model_code),
  KEY idx_models_provider (provider_id)
);

CREATE TABLE IF NOT EXISTS model_versions (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  model_id BIGINT NOT NULL,
  version VARCHAR(64) NOT NULL,
  config_json JSON DEFAULT NULL,
  gray_percent INT DEFAULT 0,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_model_versions_model (model_id)
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
  app_type VARCHAR(32) DEFAULT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ENABLED',
  ip_whitelist_json JSON DEFAULT NULL,
  rate_limit_json JSON DEFAULT NULL,
  quota_json JSON DEFAULT NULL,
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
CREATE TABLE IF NOT EXISTS routing_rules (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(100) NOT NULL,
  strategy_type VARCHAR(32) NOT NULL,
  priority INT NOT NULL DEFAULT 100,
  condition_expr TEXT NOT NULL,
  target_provider_id BIGINT DEFAULT NULL,
  target_model_id BIGINT DEFAULT NULL,
  fallback_rule_id BIGINT DEFAULT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ENABLED',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_routing_rules_priority (priority, status)
);

CREATE TABLE IF NOT EXISTS routing_hit_logs (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  request_id VARCHAR(64) NOT NULL,
  rule_id BIGINT NOT NULL,
  from_target VARCHAR(128) DEFAULT NULL,
  to_target VARCHAR(128) DEFAULT NULL,
  reason VARCHAR(255) DEFAULT NULL,
  hit_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_routing_hit_rule_time (rule_id, hit_time)
);

-- =========================
-- 计费与调用明细
-- =========================
CREATE TABLE IF NOT EXISTS billing_rules (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  code VARCHAR(64) NOT NULL,
  name VARCHAR(100) NOT NULL,
  billing_mode VARCHAR(32) NOT NULL,
  unit VARCHAR(32) NOT NULL,
  unit_price DECIMAL(18,6) NOT NULL,
  ladder_json JSON DEFAULT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  effective_from DATETIME NOT NULL,
  effective_to DATETIME DEFAULT NULL,
  UNIQUE KEY uk_billing_rules_code (code)
);

CREATE TABLE IF NOT EXISTS gateway_requests (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  request_id VARCHAR(64) NOT NULL,
  app_id BIGINT NOT NULL,
  provider_id BIGINT NOT NULL,
  model_id BIGINT NOT NULL,
  billing_rule_id BIGINT DEFAULT NULL,
  status VARCHAR(32) NOT NULL,
  latency_ms INT NOT NULL DEFAULT 0,
  input_tokens INT NOT NULL DEFAULT 0,
  output_tokens INT NOT NULL DEFAULT 0,
  total_tokens INT NOT NULL DEFAULT 0,
  cost_amount DECIMAL(18,6) NOT NULL DEFAULT 0,
  error_code VARCHAR(64) DEFAULT NULL,
  request_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_gateway_requests_app_time (app_id, request_time),
  KEY idx_gateway_requests_provider_time (provider_id, request_time),
  UNIQUE KEY uk_gateway_requests_reqid (request_id)
);

CREATE TABLE IF NOT EXISTS billing_statements (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  statement_no VARCHAR(64) NOT NULL,
  statement_type VARCHAR(32) NOT NULL,
  biz_date DATE NOT NULL,
  app_id BIGINT DEFAULT NULL,
  provider_id BIGINT DEFAULT NULL,
  total_cost DECIMAL(18,6) NOT NULL DEFAULT 0,
  total_requests BIGINT NOT NULL DEFAULT 0,
  total_tokens BIGINT NOT NULL DEFAULT 0,
  status VARCHAR(32) NOT NULL DEFAULT 'GENERATED',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_billing_statements_no (statement_no)
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

CREATE TABLE IF NOT EXISTS gray_plans (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  plan_name VARCHAR(100) NOT NULL,
  target_type VARCHAR(32) NOT NULL,
  target_id BIGINT NOT NULL,
  traffic_percent INT NOT NULL,
  steps_json JSON DEFAULT NULL,
  start_time DATETIME NOT NULL,
  end_time DATETIME DEFAULT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'DRAFT'
);

-- 若库已存在旧版 gray_plans 无 steps_json，可执行：
-- ALTER TABLE gray_plans ADD COLUMN steps_json JSON DEFAULT NULL AFTER traffic_percent;

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
  sort_order  INT          NOT NULL DEFAULT 0 COMMENT '排序',
  is_enabled  TINYINT      NOT NULL DEFAULT 1 COMMENT '1=启用 0=禁用',
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
  is_system   TINYINT      NOT NULL DEFAULT 0 COMMENT '1=系统内置不可删改',
  is_enabled  TINYINT      NOT NULL DEFAULT 1 COMMENT '1=启用 0=禁用',
  remark      VARCHAR(255) DEFAULT NULL COMMENT '备注',
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_enum_category_code (category_id, item_code)
);

-- 通用操作日志表
CREATE TABLE IF NOT EXISTS operation_logs (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  module      VARCHAR(64)  NOT NULL COMMENT '模块名称,如 user、enum、provider、model等',
  operation   VARCHAR(32)  NOT NULL COMMENT '操作类型:CREATE/UPDATE/DELETE/LOGIN/LOGOUT等',
  entity_type VARCHAR(64)  DEFAULT NULL COMMENT '实体类型,如 User、EnumConfig等',
  entity_id   BIGINT       DEFAULT NULL COMMENT '实体ID',
  entity_name VARCHAR(255) DEFAULT NULL COMMENT '实体名称或标识',
  description VARCHAR(500) DEFAULT NULL COMMENT '操作描述',
  old_value   JSON         DEFAULT NULL COMMENT '操作前的值',
  new_value   JSON         DEFAULT NULL COMMENT '操作后的值',
  operator_id BIGINT       DEFAULT NULL COMMENT '操作人ID',
  operator_name VARCHAR(64) DEFAULT NULL COMMENT '操作人姓名',
  ip_address  VARCHAR(64)  DEFAULT NULL COMMENT 'IP地址',
  user_agent  VARCHAR(500) DEFAULT NULL COMMENT '浏览器UA',
  status      VARCHAR(32)  NOT NULL DEFAULT 'SUCCESS' COMMENT '操作状态:SUCCESS/FAILURE',
  error_msg   VARCHAR(1000) DEFAULT NULL COMMENT '错误信息',
  execution_time BIGINT    DEFAULT NULL COMMENT '执行时间(毫秒)',
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_module (module),
  INDEX idx_operator (operator_id),
  INDEX idx_entity (entity_type, entity_id),
  INDEX idx_created_at (created_at)
);

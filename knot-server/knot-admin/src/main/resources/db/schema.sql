-- ============================================================
-- Knot AI Gateway - Schema Definition
-- Compatible with Spring Boot spring.sql.init
-- ============================================================

-- =========================
-- 系统管理
-- =========================
CREATE TABLE IF NOT EXISTS ks_users (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(64) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  real_name VARCHAR(100) DEFAULT NULL,
  dept_code VARCHAR(64) DEFAULT NULL COMMENT '归属部门业务码（绑定 ks_departments.dept_code，非主键 id）',
  phone VARCHAR(32) DEFAULT NULL,
  email VARCHAR(128) DEFAULT NULL,
  status INT NOT NULL DEFAULT 1,
  authz_version INT NOT NULL DEFAULT 0,
  last_login_time DATETIME DEFAULT NULL,
  is_deleted TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_users_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统用户';

CREATE TABLE IF NOT EXISTS ks_departments (
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='组织部门';

CREATE TABLE IF NOT EXISTS ks_user_settings (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  setting_key VARCHAR(64) NOT NULL,
  setting_value TEXT DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_settings_user_key (user_id, setting_key),
  KEY idx_user_settings_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户个人设置';

CREATE TABLE IF NOT EXISTS ks_roles (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  code VARCHAR(64) NOT NULL,
  name VARCHAR(100) NOT NULL,
  description VARCHAR(255) DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_roles_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统角色';

CREATE TABLE IF NOT EXISTS ks_user_roles (
  user_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  PRIMARY KEY (user_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户与角色关联';

CREATE TABLE IF NOT EXISTS ks_modules (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  module_code VARCHAR(64) NOT NULL,
  module_name VARCHAR(100) NOT NULL,
  icon VARCHAR(64) DEFAULT NULL,
  sort_order INT NOT NULL DEFAULT 0,
  status TINYINT NOT NULL DEFAULT 1 COMMENT '启用状态（EnabledStatusEnum）：1-启用 0-停用',

  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_sys_modules_code (module_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统功能模块';

CREATE TABLE IF NOT EXISTS ks_menus (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  module_id BIGINT NOT NULL,
  parent_id BIGINT DEFAULT NULL,
  menu_code VARCHAR(64) NOT NULL,
  menu_name VARCHAR(100) NOT NULL,
  route_path VARCHAR(255) DEFAULT NULL,
  component_key VARCHAR(128) DEFAULT NULL,
  icon VARCHAR(64) DEFAULT NULL,
  sort_order INT NOT NULL DEFAULT 0,
  status TINYINT NOT NULL DEFAULT 1 COMMENT '启用状态（EnabledStatusEnum）：1-启用 0-停用',

  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_sys_menus_code (menu_code),
  KEY idx_sys_menus_module_parent (module_id, parent_id, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统菜单';

CREATE TABLE IF NOT EXISTS ks_permissions (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  permission_code VARCHAR(128) NOT NULL,
  permission_name VARCHAR(128) NOT NULL,
  permission_type VARCHAR(32) NOT NULL,
  module_id BIGINT NOT NULL,
  menu_id BIGINT DEFAULT NULL,
  status TINYINT NOT NULL DEFAULT 1 COMMENT '启用状态（EnabledStatusEnum）：1-启用 0-停用',

  built_in TINYINT NOT NULL DEFAULT 1,
  remark VARCHAR(255) DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_sys_permissions_code (permission_code),
  KEY idx_sys_permissions_module_type (module_id, permission_type, status),
  KEY idx_sys_permissions_menu (menu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统权限定义';

CREATE TABLE IF NOT EXISTS ks_role_permissions (
  role_id BIGINT NOT NULL,
  permission_id BIGINT NOT NULL,
  PRIMARY KEY (role_id, permission_id),
  KEY idx_sys_role_permissions_permission (permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色与权限关联';

CREATE TABLE IF NOT EXISTS ks_api_permission_bindings (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  permission_id BIGINT NOT NULL,
  http_method VARCHAR(16) NOT NULL,
  path_pattern VARCHAR(255) NOT NULL,
  controller_class VARCHAR(255) DEFAULT NULL,
  status TINYINT NOT NULL DEFAULT 1 COMMENT '启用状态（EnabledStatusEnum）：1-启用 0-停用',

  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_sys_api_permission_binding (http_method, path_pattern),
  KEY idx_sys_api_permission_permission (permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='API 接口与权限绑定';

CREATE TABLE IF NOT EXISTS ks_operation_logs (
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
  status TINYINT NOT NULL DEFAULT 1 COMMENT '执行结果（OperationLogStatusEnum）：1-成功 2-失败',
  error_msg VARCHAR(1000) DEFAULT NULL COMMENT '错误信息',
  execution_time BIGINT DEFAULT NULL COMMENT '耗时(ms)',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_module (module),
  KEY idx_operator (operator_id),
  KEY idx_entity (entity_type, entity_id),
  KEY idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统操作日志';

CREATE TABLE IF NOT EXISTS ks_scheduled_tasks (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_code VARCHAR(64) NOT NULL,
  task_name VARCHAR(128) NOT NULL,
  handler_code VARCHAR(64) NOT NULL,
  cron_expression VARCHAR(64) NOT NULL,
  execution_mode VARCHAR(32) NOT NULL DEFAULT 'SINGLE',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '启用状态（EnabledStatusEnum）：1-启用 0-停用',

  description VARCHAR(500) DEFAULT NULL,
  last_fire_at DATETIME DEFAULT NULL,
  next_fire_at DATETIME DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_scheduled_tasks_code (task_code),
  KEY idx_scheduled_tasks_status (status),
  KEY idx_scheduled_tasks_handler (handler_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='定时任务定义';

CREATE TABLE IF NOT EXISTS ks_scheduled_task_runs (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_id BIGINT NOT NULL,
  task_code VARCHAR(64) NOT NULL,
  task_name VARCHAR(128) NOT NULL,
  execution_mode VARCHAR(32) NOT NULL,
  node_id VARCHAR(128) NOT NULL,
  trigger_type VARCHAR(32) NOT NULL,
  status TINYINT NOT NULL COMMENT '运行状态（ScheduledTaskRunStatusEnum）：1-运行中 2-成功 3-失败',
  start_time DATETIME NOT NULL,
  end_time DATETIME DEFAULT NULL,
  duration_ms BIGINT DEFAULT NULL,
  affected_rows INT NOT NULL DEFAULT 0,
  error_msg VARCHAR(1000) DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_task_runs_task_time (task_code, start_time),
  KEY idx_task_runs_status_time (status, start_time),
  KEY idx_task_runs_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='定时任务执行记录';

-- =========================
-- 频控与额度（独立策略 + 资源绑定）
-- =========================
CREATE TABLE IF NOT EXISTS kb_rate_limit_policies (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  policy_code VARCHAR(64) NOT NULL,
  policy_name VARCHAR(100) NOT NULL,
  rpm INT NOT NULL DEFAULT 0 COMMENT '每分钟请求数上限；0=不限',
  tpm INT NOT NULL DEFAULT 0 COMMENT '每分钟 token 上限；0=不限',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '启用状态（EnabledStatusEnum）：1-启用 0-停用',

  remark VARCHAR(255) DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_rate_limit_policies_code (policy_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='资源频率限制策略';

CREATE TABLE IF NOT EXISTS kb_quota_policies (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  policy_code VARCHAR(64) NOT NULL,
  policy_name VARCHAR(100) NOT NULL,
  max_tokens BIGINT NOT NULL DEFAULT 0 COMMENT '窗口内 token 上限；0=不限',
  cost_limit DECIMAL(18,4) DEFAULT NULL COMMENT '窗口内成本上限；4 位小数（用户配置精度）；NULL 或 0=不限',
  currency VARCHAR(8) DEFAULT NULL COMMENT '成本币种：USD / CNY',
  quota_window VARCHAR(16) NOT NULL DEFAULT 'MONTH' COMMENT '统计窗口：MINUTE/HOUR/DAY/WEEK/MONTH，窗口结束清零',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '启用状态（EnabledStatusEnum）：1-启用 0-停用',

  remark VARCHAR(255) DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_quota_policies_code (policy_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='资源额度策略';

CREATE TABLE IF NOT EXISTS kb_resource_traffic_policies (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  resource_type VARCHAR(32) NOT NULL COMMENT 'APP / MODEL / PROVIDER / ROUTING_RULE / ROUTING_CONSUMER',
  resource_code VARCHAR(128) NOT NULL COMMENT '资源业务码（按 resource_type 指向对应表的 code），非主键 id',
  rate_limit_policy_code VARCHAR(64) DEFAULT NULL COMMENT '频控策略业务码（kb_rate_limit_policies.policy_code），非主键 id',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_resource_traffic (resource_type, resource_code),
  KEY idx_resource_traffic_rate (rate_limit_policy_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='资源与频控策略绑定';

CREATE TABLE IF NOT EXISTS kb_resource_quota_bindings (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  resource_type VARCHAR(32) NOT NULL COMMENT 'APP / PROVIDER / ROUTING_CONSUMER（仅这三层配额度）',
  resource_code VARCHAR(128) NOT NULL COMMENT '资源业务码（按 resource_type 指向对应表的 code），非主键 id',
  quota_policy_code VARCHAR(64) NOT NULL COMMENT '额度策略业务码（kb_quota_policies.policy_code），非主键 id',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_rqb_resource_policy (resource_type, resource_code, quota_policy_code),
  KEY idx_rqb_policy (quota_policy_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='资源与额度策略绑定';

-- =========================
-- 供应商与模型
-- =========================
CREATE TABLE IF NOT EXISTS kb_providers (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  code VARCHAR(32) NOT NULL,
  name VARCHAR(100) NOT NULL,
  tag VARCHAR(255) NOT NULL COMMENT '供应商分类，可多选，多个用英文逗号分隔，如 原厂,代理',
  remark VARCHAR(255) DEFAULT NULL COMMENT '备注',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_providers_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模型供应商';

CREATE TABLE IF NOT EXISTS kb_provider_accounts (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  provider_code VARCHAR(32) NOT NULL COMMENT '所属供应商 code（kb_providers.code）',
  code VARCHAR(48) NOT NULL,
  base_url VARCHAR(255) DEFAULT NULL,
  status TINYINT NOT NULL DEFAULT 1 COMMENT '启用状态（EnabledStatusEnum）：1-启用 0-停用',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_provider_accounts_code (code),
  KEY idx_provider_accounts_provider (provider_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='供应商账户配置';

CREATE TABLE IF NOT EXISTS kb_provider_credentials (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  provider_account_code VARCHAR(48) NOT NULL COMMENT '供应商账户业务码（kb_provider_accounts.code），非主键 id',
  credential_type VARCHAR(32) NOT NULL,
  auth_applier VARCHAR(64) DEFAULT NULL COMMENT '鉴权策略编码（UpstreamAuthApplier code），空则回退默认 Bearer',
  encrypted_config TEXT DEFAULT NULL COMMENT '加密后的任意键值认证配置 JSON',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '启用状态（EnabledStatusEnum）：1-启用 0-停用',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_provider_credentials_account (provider_account_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='供应商账户凭证配置';

CREATE TABLE IF NOT EXISTS kb_provider_discount_policies (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  model_code VARCHAR(128) NOT NULL COMMENT '绑定的供应商模型业务码（kb_models.model_code），非主键 id',
  discount_type VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  discount_value DECIMAL(10,4) NOT NULL,
  effective_from DATETIME NOT NULL,
  effective_to DATETIME DEFAULT NULL,
  status TINYINT NOT NULL DEFAULT 1 COMMENT '启用状态（EnabledStatusEnum）：1-启用 0-停用',
  remark VARCHAR(255) DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_provider_discount_model_time (model_code, effective_from, effective_to)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='供应商模型折扣策略';

CREATE TABLE IF NOT EXISTS kb_models (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  provider_account_code VARCHAR(48) NOT NULL COMMENT '所属供应商账户 code（kb_provider_accounts.code）',
  model_code VARCHAR(128) NOT NULL,
  upstream_model VARCHAR(128) NOT NULL COMMENT '上游模型标识：向上游发起请求时写入请求体 model 参数',
  version VARCHAR(64) NOT NULL,
  base_path VARCHAR(255) NOT NULL DEFAULT '' COMMENT '模型级路径前缀（如供应商 endpoint_id），空表示无前缀；不含 scheme/host/query/fragment',
  remark VARCHAR(255) DEFAULT NULL,
  billing_rule_code VARCHAR(64) DEFAULT NULL COMMENT '绑定计费规则 code（kb_billing_rules.code），非主键 id',
  status TINYINT NOT NULL DEFAULT 0 COMMENT '启用状态（EnabledStatusEnum）：1-启用 0-停用',
  is_deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-否 1-是；与 kb_model_pools / kb_logical_models 同款逻辑删除约定',
  tags_json JSON DEFAULT NULL,
  params_json JSON DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_models_code (model_code),
  KEY idx_models_provider_account (provider_account_code),
  KEY idx_models_billing_rule (billing_rule_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='供应商模型配置';

CREATE TABLE IF NOT EXISTS kb_model_pools (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  pool_code VARCHAR(64) NOT NULL,
  name VARCHAR(100) NOT NULL,
  logical_model_code VARCHAR(128) NOT NULL COMMENT '统一模型 code（kb_logical_models.model_code），池内模型必须全部归属该统一模型',
  selection_strategy VARCHAR(32) NOT NULL DEFAULT 'WEIGHTED',
  sticky_policy TEXT DEFAULT NULL COMMENT '池内粘性路由策略（JSON）；空表示未启用，池选择走 selection_strategy',
  status TINYINT NOT NULL DEFAULT 0 COMMENT '启用状态（EnabledStatusEnum）：1-启用 0-停用',
  is_deleted TINYINT(1) NOT NULL DEFAULT 0,
  remark VARCHAR(255) DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_model_pools_code (pool_code),
  KEY idx_model_pools_logical_status (logical_model_code, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='统一模型路由池';

CREATE TABLE IF NOT EXISTS kb_model_pool_items (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  pool_code VARCHAR(64) NOT NULL COMMENT '所属模型池 code（kb_model_pools.pool_code）',
  model_code VARCHAR(128) NOT NULL COMMENT '供应商模型 code（kb_models.model_code）',
  weight INT NOT NULL DEFAULT 100,
  priority INT NOT NULL DEFAULT 100,
  status TINYINT NOT NULL DEFAULT 1 COMMENT '启用状态（EnabledStatusEnum）：1-启用 0-停用',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_model_pool_item (pool_code, model_code),
  KEY idx_model_pool_items_pool (pool_code, status, priority),
  KEY idx_model_pool_items_model (model_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模型池中的供应商模型条目';

CREATE TABLE IF NOT EXISTS kb_logical_models (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  model_code VARCHAR(128) NOT NULL,
  model_name VARCHAR(128) NOT NULL,
  model_type VARCHAR(64) NOT NULL,
  model_family VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  description TEXT DEFAULT NULL,
  tags_json JSON DEFAULT NULL,
  use_cases_json JSON DEFAULT NULL,
  context_window INT DEFAULT NULL,
  max_output_tokens INT DEFAULT NULL,
  input_modalities_json JSON DEFAULT NULL,
  output_modalities_json JSON DEFAULT NULL,
  languages_json JSON DEFAULT NULL,
  visibility VARCHAR(32) NOT NULL DEFAULT 'PUBLIC',
  publish_status TINYINT NOT NULL DEFAULT 1 COMMENT '发布状态（LogicalModelPublishStatusEnum）：1-DRAFT 2-PUBLISHED 3-ARCHIVED',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '启用状态（EnabledStatusEnum）：1-启用 0-停用',
  is_deleted TINYINT(1) NOT NULL DEFAULT 0,
  sort_order INT NOT NULL DEFAULT 0,
  featured TINYINT NOT NULL DEFAULT 0,
  remark VARCHAR(255) DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_logical_models_code (model_code),
  KEY idx_logical_models_type_status (model_type, publish_status, status),
  KEY idx_logical_models_sort (featured, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='统一逻辑模型目录';

CREATE TABLE IF NOT EXISTS kb_provider_model_mappings (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  logical_model_code VARCHAR(128) NOT NULL COMMENT '统一模型 code（kb_logical_models.model_code）',
  provider_account_code VARCHAR(48) NOT NULL COMMENT '供应商账户 code（kb_provider_accounts.code）',
  model_code VARCHAR(128) NOT NULL COMMENT '供应商模型业务码（kb_models.model_code），非主键 id',
  provider_model_name VARCHAR(128) NOT NULL,
  status TINYINT NOT NULL DEFAULT 1 COMMENT '启用状态（EnabledStatusEnum）：1-启用 0-停用',
  priority INT NOT NULL DEFAULT 100,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_provider_model_mapping_model (model_code),
  KEY idx_logical_provider_model (logical_model_code, provider_account_code, model_code),
  KEY idx_logical_model (logical_model_code, status),
  KEY idx_provider_account_model (provider_account_code, model_code, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='统一模型与供应商模型映射';

CREATE TABLE IF NOT EXISTS kx_model_sources (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  source_code VARCHAR(64) NOT NULL,
  source_name VARCHAR(128) NOT NULL,
  source_url VARCHAR(512) NOT NULL,
  api_url VARCHAR(512) NOT NULL,
  source_type VARCHAR(32) NOT NULL DEFAULT 'COMMERCIAL_LLM',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '启用状态（EnabledStatusEnum）：1-启用 0-停用',
  last_sync_at DATETIME DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_external_model_sources_code (source_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='外部模型数据源';

CREATE TABLE IF NOT EXISTS kx_model_items (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  source_code VARCHAR(64) NOT NULL,
  model_id VARCHAR(160) NOT NULL,
  canonical_slug VARCHAR(256) DEFAULT NULL,
  model_name VARCHAR(256) NOT NULL,
  provider_name VARCHAR(128) DEFAULT NULL,
  model_url VARCHAR(512) DEFAULT NULL,
  model_created_at DATETIME DEFAULT NULL,
  description TEXT DEFAULT NULL,
  context_length INT DEFAULT NULL,
  input_modalities_json JSON DEFAULT NULL,
  output_modalities_json JSON DEFAULT NULL,
  raw_json JSON DEFAULT NULL,
  normalized_name VARCHAR(256) DEFAULT NULL,
  model_type VARCHAR(64) DEFAULT NULL,
  tags_json JSON DEFAULT NULL,
  max_completion_tokens INT DEFAULT NULL,
  logical_model_id BIGINT DEFAULT NULL,
  ignored TINYINT NOT NULL DEFAULT 0,
  sync_status TINYINT NOT NULL DEFAULT 1 COMMENT '同步状态（ExternalModelSyncStatusEnum）：1-待同步 2-已同步 3-失败',
  sync_hash VARCHAR(64) DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_external_model_item (source_code, model_id),
  KEY idx_external_model_logical_model (logical_model_id),
  KEY idx_external_model_created_time (model_created_at),
  KEY idx_external_model_ignored (ignored),
  KEY idx_external_model_sync_status (sync_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='外部模型目录条目';

-- 供应商模型与 API 协议绑定（协议 + 消耗取值逻辑）
CREATE TABLE IF NOT EXISTS kb_model_api_bindings (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  model_code VARCHAR(128) NOT NULL COMMENT '模型业务码（kb_models.model_code），非主键 id',
  protocol VARCHAR(64) NOT NULL COMMENT 'API 协议编码',
  api_path VARCHAR(255) DEFAULT NULL COMMENT '上游 API 路径，为空时使用协议默认路径',
  request_adapter VARCHAR(255) DEFAULT NULL COMMENT '上游请求适配器编码或类名',
  usage_extractor VARCHAR(255) NOT NULL DEFAULT 'DEFAULT-TOKEN' COMMENT '非流式响应 Usage 解析器编码或类名',
  stream_usage_extractor VARCHAR(255) DEFAULT NULL COMMENT '流式响应 Usage 解析器编码或类名，为空时复用 usage_extractor',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '启用状态（EnabledStatusEnum）：1-启用 0-停用',
  remark VARCHAR(255) DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_model_api_protocol (model_code, protocol),
  KEY idx_model_api_bindings_model (model_code),
  KEY idx_model_api_bindings_protocol (protocol)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模型 API 协议与用量解析绑定';

-- =========================
-- 应用管理
-- =========================
CREATE TABLE IF NOT EXISTS kb_apps (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  app_code VARCHAR(64) NOT NULL,
  name VARCHAR(100) NOT NULL,
  dept_code VARCHAR(64) DEFAULT NULL COMMENT '归属部门业务码（绑定 ks_departments.dept_code，非主键 id）',
  owner_username VARCHAR(64) DEFAULT NULL COMMENT '归属用户登录名（绑定 username，非主键 id）',
  remark VARCHAR(255) DEFAULT NULL COMMENT '备注',
  is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-否 1-是',
  app_type VARCHAR(32) DEFAULT NULL,
  status TINYINT NOT NULL DEFAULT 1 COMMENT '启用状态（EnabledStatusEnum）：1-启用 0-停用',

  ip_whitelist_json JSON DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_apps_app_code (app_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='接入应用配置';

-- =========================
-- 路由规则
-- =========================
CREATE TABLE IF NOT EXISTS kb_routing_consumers (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  consumer_code VARCHAR(32) NOT NULL,
  name VARCHAR(100) NOT NULL,
  user_username VARCHAR(64) DEFAULT NULL COMMENT '归属用户登录名（绑定 username，非主键 id）',
  secret_key VARCHAR(64) NOT NULL,
  return_usage_detail TINYINT(1) NOT NULL DEFAULT 0,
  status TINYINT NOT NULL DEFAULT 1 COMMENT '启用状态（EnabledStatusEnum）：1-启用 0-停用',

  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_routing_consumers_code (consumer_code),
  UNIQUE KEY uk_routing_consumers_secret (secret_key),
  KEY idx_routing_consumers_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='路由消费者及 API 凭证主体';

CREATE TABLE IF NOT EXISTS kb_routing_rules (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  rule_code VARCHAR(64) NOT NULL,
  name VARCHAR(100) NOT NULL,
  app_scenario VARCHAR(128) DEFAULT NULL,
  app_code VARCHAR(64) DEFAULT NULL COMMENT '绑定应用业务码（kb_apps.app_code），非主键 id',
  username VARCHAR(64) DEFAULT NULL COMMENT '归属用户登录名（绑定 username，非主键 id）',
  consumer_code VARCHAR(32) DEFAULT NULL COMMENT '绑定消费者业务码（kb_routing_consumers.consumer_code），一对一，非主键 id；空表示未绑定',
  logical_model_code VARCHAR(128) DEFAULT NULL COMMENT '统一模型 code（kb_logical_models.model_code），非必填；选中后路由目标选择器将该统一模型对应的供应商模型/模型池优先置前（仅排序，不过滤）',
  retry_policy TEXT DEFAULT NULL COMMENT '失败重试策略（JSON）；空表示走内置默认策略',
  ejection_policy TEXT DEFAULT NULL COMMENT '异常摘除策略（JSON）：窗口内反复异常（上游故障/首token过慢）的供应商模型临时移出候选链；空表示走内置默认策略（默认关闭）',
  selection_strategy VARCHAR(32) NOT NULL DEFAULT 'PRIORITY' COMMENT '路由目标选择策略（ModelPoolSelectionStrategyEnum）：PRIORITY-按优先级/主目标固定序；LEAST_LOAD-按目标在途并发动态重排（最空闲优先，主目标仍钉首）',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '启用状态（EnabledStatusEnum）：1-启用 0-停用',

  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_routing_rules_code (rule_code),
  KEY idx_routing_rules_app (app_code, status),
  KEY idx_routing_rules_consumer (consumer_code, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模型路由规则';

CREATE TABLE IF NOT EXISTS kb_routing_rule_targets (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  rule_code VARCHAR(64) NOT NULL COMMENT '路由规则业务码（kb_routing_rules.rule_code），非主键 id',
  target_type VARCHAR(32) NOT NULL,
  target_code VARCHAR(64) NOT NULL,
  priority INT NOT NULL DEFAULT 100,
  is_primary TINYINT(1) NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_routing_rule_target (rule_code, target_type, target_code),
  KEY idx_routing_rule_targets_rule (rule_code, priority)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='路由规则目标配置';

-- =========================
-- 计费与调用明细
-- =========================
CREATE TABLE IF NOT EXISTS kb_billing_rules (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  code VARCHAR(64) NOT NULL,
  model_family VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '模型族 code（ks_enum_configs.category=model_family 的 item_code）；为空表示默认规则，覆盖所有族',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '启用状态（EnabledStatusEnum）：1-启用 0-停用；删除语义见 is_deleted',
  is_deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-否 1-是；删除时同时置 status=0，恢复后保持停用',
  remark VARCHAR(255) DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_billing_rules_code (code),
  KEY idx_billing_rules_match (model_family, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='计费规则主体';

CREATE TABLE IF NOT EXISTS kb_billing_rule_versions (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  rule_code VARCHAR(64) NOT NULL COMMENT '计费规则业务码（kb_billing_rules.code），非主键 id',
  version_code VARCHAR(32) NOT NULL COMMENT '规则内人工可读版本号（v1/v2/2026-01）',
  uniq_hash VARCHAR(64) NOT NULL COMMENT '版本配置内容指纹（MD5），同一 rule_code 内唯一，防止重复版本',
  billing_mode VARCHAR(32) NOT NULL,
  pricing_plan VARCHAR(32) NOT NULL DEFAULT 'FIXED' COMMENT '进阶定价方案（PricingPlanEnum）：FIXED/TIERED/PEAK_OFF_PEAK，决定“价”，不改变用量',
  currency VARCHAR(16) NOT NULL DEFAULT 'USD',
  -- 无 DEFAULT：单位是金额公式的分母（1K_TOKENS=1000 / PER_SECOND=1，差 1000 倍），
  -- 且正确取值随 billing_mode 变（见 BillingModeEnum.defaultUnit），给不出"万能默认值"。
  -- 漏给即插入失败，胜过静默落一个 1000 倍错的口径。
  unit VARCHAR(32) NOT NULL COMMENT '计费单位（BillingUnitEnum code），由 BillingModeEnum.normalizeUnit 归一化后写入',
  config_json JSON DEFAULT NULL COMMENT '计费配置：defaultUnitPrice + basePrices + ladder + 模式扩展',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '启用状态（EnabledStatusEnum）：1-启用 0-停用',
  effective_from DATETIME NOT NULL,
  effective_to DATETIME DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_billing_rule_version_code (rule_code, version_code),
  UNIQUE KEY uk_billing_rule_version_hash (rule_code, uniq_hash),
  KEY idx_billing_rule_versions_active (rule_code, status, effective_from, effective_to)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='计费规则版本及价格配置';

-- =========================
-- 可选扩展
-- =========================
-- 插件管理已单表收口（2026-10-13）：packages / capabilities / bindings 三表删除。
-- 2026-10-25 二次收口：连「插件包」这一层分组也删了（plugin_code / plugin_name / capability_name /
-- source_type / config_json / concurrency_limit 六列全量删除，均零读取方），并把表定位钉死为
-- 「PluginDescriptor 的物化 + 运维开关」：派生列每次启动由启动期 reconcile 全覆盖写，
-- status / is_deleted 由运维持有、不被覆盖。插件定义唯一来源是 PluginDescriptor，
-- 不再由 data.sql 预置种子（种子 + INSERT IGNORE 是 entrypoint 漂移的根源）。

CREATE TABLE IF NOT EXISTS kb_plugin_instances (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  instance_code VARCHAR(64) NOT NULL COMMENT '实例业务码（PluginDescriptor.code；启动期 reconcile 校验不超此列宽）',
  instance_name VARCHAR(100) NOT NULL COMMENT '实例名称（PluginDescriptor.name）',
  entrypoint VARCHAR(255) NOT NULL COMMENT '插件实现类全限定名（IOC 稳定键；reconcile 的唯一匹配键，且是 upsert 的唯一键之一）',
  version VARCHAR(32) DEFAULT NULL COMMENT '版本（内置插件统一由 reconcile 写入）',
  capability_code VARCHAR(64) NOT NULL COMMENT '能力族码（PluginDescriptor.capabilityCode；多个插件可共享同一能力）',
  extension_point VARCHAR(64) NOT NULL COMMENT '扩展点（PluginDescriptor.extensionPoint）',
  stage_code VARCHAR(64) NOT NULL COMMENT '执行阶段（PluginDescriptor.stageCode）',
  order_no INT NOT NULL DEFAULT 100 COMMENT '同扩展点+阶段的执行顺序（PluginDescriptor.orderNo，升序）',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '启用状态（EnabledStatusEnum）：1-启用 0-停用；运维权威，reconcile 不覆盖',
  timeout_ms INT NOT NULL DEFAULT 3000 COMMENT '插件超时（毫秒）；分发器尚未读取该列，超时治理属待办',
  plugin_type VARCHAR(32) NOT NULL DEFAULT 'SYNC' COMMENT '插件执行类型（PluginTypeEnum）：SYNC-同步插件（嵌入请求链内联执行）',
  is_deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除（死行不展示）：1-已删除 0-未删除；运维权威，reconcile 不覆盖',
  orphaned TINYINT(1) NOT NULL DEFAULT 0 COMMENT '代码已移除标记（reconcile 每次启动重算）：1-孤儿 0-正常',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_plugin_instances_code (instance_code),
  UNIQUE KEY uk_plugin_instances_entrypoint (entrypoint),
  KEY idx_plugin_instances_type (plugin_type, status),
  KEY idx_plugin_instances_ext_stage_status (extension_point, stage_code, status, is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='插件实例配置（PluginDescriptor 的物化 + 运维开关）';

-- 作用域绑定表已随单表收口删除（2026-10-13）。scope 维度按决策永久移除。

CREATE TABLE IF NOT EXISTS kb_notification_templates (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  code VARCHAR(64) NOT NULL,
  name VARCHAR(100) NOT NULL,
  channel VARCHAR(32) NOT NULL,
  title_tpl VARCHAR(255) NOT NULL,
  content_tpl TEXT NOT NULL,
  status TINYINT NOT NULL DEFAULT 1 COMMENT '启用状态（EnabledStatusEnum）：1-启用 0-停用',

  UNIQUE KEY uk_notification_templates_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通知模板';

-- =========================
-- 敏感词库（内容安全）
-- =========================
-- 敏感词条：Aho-Corasick 自动机数据源。分类走 ks_enum_configs（category='moderation_category'），
-- 仅作标签维度；是否参与匹配只看本行 status / is_deleted。severity 挂在词条上而非分类。
CREATE TABLE IF NOT EXISTS kb_moderation_word_lists (
  id              BIGINT       PRIMARY KEY AUTO_INCREMENT,
  word            VARCHAR(255) NOT NULL COMMENT '原始词条（支持 emoji/全角）',
  normalized_word VARCHAR(255) NOT NULL COMMENT '归一化键（NFKC+小写+去零宽），判定走它、展示走 word',
  category_code   VARCHAR(64)  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '绑分类（ks_enum_configs.item_code，category=moderation_category），非主键 id',
  severity        TINYINT      NOT NULL DEFAULT 2 COMMENT '严重度：1-低 2-中 3-高；配合常量 SEVERITY_THRESHOLD 决定是否触发动作（见设计文档 §5.2）',
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

-- =========================
-- 枚举配置
-- =========================
-- 枚举分类表
CREATE TABLE IF NOT EXISTS ks_enum_categories (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    category    VARCHAR(64)  NOT NULL COMMENT '分类编码，如 provider_type、app_type',
    category_name VARCHAR(128) NOT NULL COMMENT '分类名称，如 供应商类型、应用类型',
    description VARCHAR(255) DEFAULT NULL COMMENT '分类描述',
    is_system   TINYINT      NOT NULL DEFAULT 0 COMMENT '1=系统内置分类，其下枚举项不可删改',
    is_enabled  TINYINT      NOT NULL DEFAULT 1 COMMENT '1=启用 0=禁用',
    is_deleted  TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=否 1=是',
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_enum_category (category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='枚举分类';

-- 枚举配置表（移除category字段，改为category_id）
CREATE TABLE IF NOT EXISTS ks_enum_configs (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    category_id BIGINT       NOT NULL COMMENT '分类ID',
    item_code   VARCHAR(64)  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '枚举编码，如 OPENAI、WEB',
    item_label  VARCHAR(128) NOT NULL COMMENT '显示名称，如 OpenAI、Web应用',
    sort_order  INT          NOT NULL DEFAULT 0 COMMENT '排序',
    is_enabled  TINYINT      NOT NULL DEFAULT 1 COMMENT '1=启用 0=禁用',
    is_deleted  TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=否 1=是',
    remark      VARCHAR(255) DEFAULT NULL COMMENT '备注',
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_enum_category_code (category_id, item_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='枚举分类项';

-- =========================
-- 路由调试预设请求用例
-- =========================
-- 替代 RoutingRuleService#defaultRequestBody 的硬编码骨架：由用户维护完整、具体的请求体 JSON，
-- 按协议归类、全局共享复用。调试面板按当前协议从预设中载入请求体。
CREATE TABLE IF NOT EXISTS kb_test_request_presets (
    id            BIGINT       PRIMARY KEY AUTO_INCREMENT,
    code          VARCHAR(64)  NOT NULL COMMENT '预设编码，人工填写，唯一',
    name          VARCHAR(100) NOT NULL COMMENT '预设名称',
    protocol_code VARCHAR(64)  NOT NULL COMMENT '关联协议 canonical code，如 CHAT_COMPLETIONS',
    logical_model_code VARCHAR(128) DEFAULT NULL COMMENT '关联统一模型 code（kb_logical_models.model_code），可空，仅作归类',
    request_body  LONGTEXT     NOT NULL COMMENT '完整请求体 JSON（具体值；model/prompt 由调试面板按目标覆盖）',
    remark        VARCHAR(255) DEFAULT NULL COMMENT '备注',
    status        TINYINT      NOT NULL DEFAULT 1 COMMENT '启用状态（EnabledStatusEnum）：1-启用 0-停用',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_test_request_presets_code (code),
    KEY idx_test_request_presets_logical (logical_model_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT = '路由调试预设请求用例（替代硬编码默认请求体）';

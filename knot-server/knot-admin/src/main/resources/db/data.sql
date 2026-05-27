-- ============================================================
-- Knot AI Gateway - Seed Data（幂等）
-- 可重复执行：主键/唯一键冲突时跳过（INSERT IGNORE）
-- ============================================================

-- =========================
-- 系统管理
-- =========================

-- 部门
INSERT IGNORE INTO departments (id, parent_id, name, code) VALUES
(1, NULL, '技术部', 'TECH'),
(2, NULL, '产品部', 'PRODUCT'),
(3, 1, 'AI平台组', 'AI_PLATFORM');

-- 用户 (password_hash = BCrypt('admin123'))
INSERT IGNORE INTO users (id, username, password_hash, real_name, status) VALUES
(1, 'admin', '$2a$10$HUYfxtiEgiRARR/fG46hEeAvcfcQ2WXMPh2NxPw5zkc06fDKeWkxi', '系统管理员', 1),
(2, 'zhangsan', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '张三', 1),
(3, 'lisi', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '李四', 1);

-- 角色
INSERT IGNORE INTO roles (id, code, name, description) VALUES
(1, 'ADMIN', '管理员', '系统管理员，拥有全部权限'),
(2, 'OPERATOR', '运维人员', '负责网关运维监控'),
(3, 'DEVELOPER', '开发人员', '应用接入开发者');

-- 权限
INSERT IGNORE INTO permissions (id, code, name, module_code, action_code) VALUES
(1,  'PROVIDER_VIEW',   '查看供应商',   'PROVIDER', 'VIEW'),
(2,  'PROVIDER_EDIT',   '编辑供应商',   'PROVIDER', 'EDIT'),
(3,  'MODEL_VIEW',      '查看模型',     'MODEL',    'VIEW'),
(4,  'MODEL_EDIT',      '编辑模型',     'MODEL',    'EDIT'),
(5,  'APP_VIEW',        '查看应用',     'APP',      'VIEW'),
(6,  'APP_EDIT',        '编辑应用',     'APP',      'EDIT'),
(7,  'ROUTE_VIEW',      '查看路由规则', 'ROUTE',    'VIEW'),
(8,  'ROUTE_EDIT',      '编辑路由规则', 'ROUTE',    'EDIT'),
(9,  'BILLING_VIEW',    '查看计费',     'BILLING',  'VIEW'),
(10, 'SECURITY_VIEW',   '查看安全监控', 'SECURITY', 'VIEW'),
(11, 'SECURITY_EDIT',   '编辑安全策略', 'SECURITY', 'EDIT'),
(12, 'PLUGIN_VIEW',     '查看插件',     'PLUGIN',   'VIEW'),
(13, 'PLUGIN_EDIT',     '编辑插件',     'PLUGIN',   'EDIT'),
(14, 'GRAY_VIEW',       '查看灰度发布', 'GRAY',     'VIEW'),
(15, 'GRAY_EDIT',       '编辑灰度发布', 'GRAY',     'EDIT'),
(16, 'SYSTEM_VIEW',     '查看系统管理', 'SYSTEM',   'VIEW'),
(17, 'SYSTEM_EDIT',     '编辑系统管理', 'SYSTEM',   'EDIT'),
(18, 'NOTIFICATION_VIEW','查看通知',    'NOTIFICATION','VIEW'),
(19, 'NOTIFICATION_EDIT','编辑通知',    'NOTIFICATION','EDIT');

-- 用户-角色
INSERT IGNORE INTO user_roles (user_id, role_id) VALUES
(1, 1),
(2, 2),
(3, 3);

-- 角色-权限
INSERT IGNORE INTO role_permissions (role_id, permission_id) VALUES
(1,1),(1,2),(1,3),(1,4),(1,5),(1,6),(1,7),(1,8),(1,9),(1,10),(1,11),(1,12),(1,13),(1,14),(1,15),(1,16),(1,17),(1,18),(1,19),
(2,1),(2,3),(2,5),(2,7),(2,9),(2,10),(2,12),(2,14),(2,16),
(3,1),(3,3),(3,5),(3,7),(3,9);

-- =========================
-- 供应商与模型
-- =========================

-- 供应商
INSERT IGNORE INTO providers (id, code, name, provider_type, base_url, status) VALUES
(1, 'openai',       'OpenAI',       'OPENAI',  'https://api.openai.com',       'ENABLED'),
(2, 'anthropic',    'Anthropic',    'ANTHROPIC','https://api.anthropic.com',     'ENABLED'),
(3, 'deepseek',     'DeepSeek',     'DEEPSEEK','https://api.deepseek.com',      'ENABLED');

-- 频控/额度策略（独立表 + 资源绑定）
INSERT IGNORE INTO rate_limit_policies (id, policy_code, policy_name, per_second, per_minute, time_window, status) VALUES
(1, 'PROVIDER-1-RL', 'PROVIDER #1 频控', 100, 5000, 'MINUTE', 'ACTIVE'),
(2, 'PROVIDER-2-RL', 'PROVIDER #2 频控', 50,  2000, 'MINUTE', 'ACTIVE'),
(3, 'PROVIDER-3-RL', 'PROVIDER #3 频控', 30,  1000, 'MINUTE', 'ACTIVE'),
(4, 'APP-1-RL',      'APP #1 频控',      10,  600,  'MINUTE', 'ACTIVE'),
(5, 'APP-2-RL',      'APP #2 频控',      5,   300,  'MINUTE', 'ACTIVE');

INSERT IGNORE INTO quota_policies (id, policy_code, policy_name, daily_limit, monthly_limit, token_limit, alert_enabled, status) VALUES
(1, 'PROVIDER-1-QT', 'PROVIDER #1 额度', 1000000, 30000000, 500000000, 1, 'ACTIVE'),
(2, 'PROVIDER-2-QT', 'PROVIDER #2 额度', 500000,  15000000, 200000000, 1, 'ACTIVE'),
(3, 'PROVIDER-3-QT', 'PROVIDER #3 额度', 200000,  6000000,  100000000, 0, 'ACTIVE'),
(4, 'APP-1-QT',      'APP #1 额度',      10000,   300000,   5000000,   1, 'ACTIVE'),
(5, 'APP-2-QT',      'APP #2 额度',      5000,    150000,   2000000,   1, 'ACTIVE');

INSERT IGNORE INTO resource_traffic_policies (id, resource_type, resource_id, rate_limit_policy_id, quota_policy_id) VALUES
(1, 'PROVIDER', 1, 1, 1),
(2, 'PROVIDER', 2, 2, 2),
(3, 'PROVIDER', 3, 3, 3),
(4, 'APP',      1, 4, 4),
(5, 'APP',      2, 5, 5);

-- 供应商凭证（encrypted_key 等为 AES-GCM 密文，前缀 ENC:；开发密钥见 application.yml knot.credential.encryption-key）
INSERT IGNORE INTO provider_credentials (id, provider_id, credential_type, encrypted_key, status) VALUES
(1, 1, 'API_KEY', 'ENC:I093hp4rWvI0txMTx5d+MwVbdLU8NQ/9X1J6r33Y3gJwGT2slww78nJbasZjfOfD', 'ACTIVE'),
(2, 2, 'API_KEY', 'ENC:RnptV2Rw7zw4QCm4qHUba0qKaxKQfsv6l3aymW/XfF3j0Fs/k02352ew7lE/tcRyax5/', 'ACTIVE'),
(3, 3, 'API_KEY', 'ENC:mFDjmYE5M54d8MWNXUcTQS9Ngk7+dv3YDuH6o0x8hv6rehtmM9I38RJUeGrpc0zF4Tw=', 'ACTIVE');

-- 供应商折扣策略
INSERT IGNORE INTO provider_discount_policies (id, provider_id, policy_name, scope_type, scope_ref_id, discount_type, discount_value, priority, effective_from, status) VALUES
(1, 1, '新用户9折',   'GLOBAL',   NULL, 'PERCENTAGE', 0.9000, 100, NOW(), 'ACTIVE'),
(2, 2, '企业客户8折', 'GLOBAL',   NULL, 'PERCENTAGE', 0.8000, 90,  NOW(), 'ACTIVE'),
(3, 3, '直减5元',     'GLOBAL',   NULL, 'FIXED',      5.0000, 100, NOW(), 'ACTIVE');

-- 模型
INSERT IGNORE INTO models (id, provider_id, model_code, name, model_type, version, status) VALUES
(1,  1, 'gpt-4o',            'GPT-4o',            'CHAT',   '2024-08-06', 'ENABLED'),
(2,  1, 'gpt-4o-mini',       'GPT-4o Mini',       'CHAT',   '2024-07-18', 'ENABLED'),
(3,  1, 'text-embedding-3-large','Text Embedding 3 Large','EMBEDDING','2024-01-01','ENABLED'),
(4,  2, 'claude-sonnet-4-20250514','Claude Sonnet 4','CHAT', '2025-05-14','ENABLED'),
(5,  2, 'claude-haiku-3-5-20241022','Claude 3.5 Haiku','CHAT','2024-10-22','ENABLED'),
(6,  3, 'deepseek-chat',     'DeepSeek Chat',     'CHAT',   '2024-08-01', 'ENABLED'),
(7,  3, 'deepseek-reasoner', 'DeepSeek Reasoner', 'CHAT',   '2025-01-20', 'ENABLED');

-- 模型 API 协议绑定（usage_extract_json 为 JSONPath 风格路径）
INSERT IGNORE INTO model_api_bindings (id, model_id, protocol, api_path, usage_extract_json, status, remark) VALUES
(1, 1, 'OPENAI_CHAT_COMPLETIONS', '/v1/chat/completions',
 JSON_OBJECT(
   'usage_path', '$.usage',
   'total_tokens', '$.usage.total_tokens',
   'cached_read_tokens', '$.usage.prompt_tokens_details.cached_tokens',
   'cached_write_tokens', '$.usage.prompt_tokens_details.cache_creation_input_tokens',
   'output_tokens', '$.usage.completion_tokens',
   'uncached_tokens', '$.usage.prompt_tokens',
   'total_input_tokens', '$.usage.input_tokens'
 ), 'ENABLED', 'GPT-4o Chat Completions'),
(2, 2, 'OPENAI_CHAT_COMPLETIONS', '/v1/chat/completions',
 JSON_OBJECT(
   'usage_path', '$.usage',
   'total_tokens', '$.usage.total_tokens',
   'cached_read_tokens', '$.usage.prompt_tokens_details.cached_tokens',
   'cached_write_tokens', '$.usage.prompt_tokens_details.cache_creation_input_tokens',
   'output_tokens', '$.usage.completion_tokens',
   'uncached_tokens', '$.usage.prompt_tokens',
   'total_input_tokens', '$.usage.input_tokens'
 ), 'ENABLED', 'GPT-4o Mini Chat Completions'),
(3, 4, 'ANTHROPIC_MESSAGES', '/v1/messages',
 JSON_OBJECT(
   'usage_path', '$.usage',
   'total_tokens', '$.usage.input_tokens + $.usage.output_tokens',
   'cached_read_tokens', '$.usage.cache_read_input_tokens',
   'cached_write_tokens', '$.usage.cache_creation_input_tokens',
   'output_tokens', '$.usage.output_tokens',
   'uncached_tokens', '$.usage.input_tokens',
   'total_input_tokens', '$.usage.input_tokens'
 ), 'ENABLED', 'Claude Sonnet Messages API'),
(4, 6, 'OPENAI_CHAT_COMPLETIONS', '/v1/chat/completions',
 JSON_OBJECT(
   'usage_path', '$.usage',
   'total_tokens', '$.usage.total_tokens',
   'cached_read_tokens', '$.usage.prompt_cache_hit_tokens',
   'cached_write_tokens', '$.usage.prompt_cache_miss_tokens',
   'output_tokens', '$.usage.completion_tokens',
   'uncached_tokens', '$.usage.prompt_tokens',
   'total_input_tokens', '$.usage.prompt_tokens'
 ), 'ENABLED', 'DeepSeek Chat（OpenAI 兼容）');

-- 模型版本
INSERT IGNORE INTO model_versions (id, model_id, version, gray_percent, status) VALUES
(1, 1, '2024-08-06', 0,   'ACTIVE'),
(2, 2, '2024-07-18', 0,   'ACTIVE'),
(3, 4, '2025-05-14', 100, 'ACTIVE'),
(4, 5, '2024-10-22', 0,   'ACTIVE'),
(5, 6, '2024-08-01', 0,   'ACTIVE'),
(6, 7, '2025-01-20', 10,  'ACTIVE');

-- =========================
-- 应用管理
-- =========================

-- 应用
INSERT IGNORE INTO apps (id, app_id, name, owner_user_id, remark, status) VALUES
(1, 'app_001', '内部知识库助手',   1, '面向内部员工的知识检索与问答', 'ENABLED'),
(2, 'app_002', '客服对话系统',     2, '对外客服场景的对话接入',       'ENABLED'),
(3, 'app_003', '代码审查工具',     3, '研发流程中的代码审查辅助',     'ENABLED');

-- 应用凭证
INSERT IGNORE INTO app_credentials (id, app_id, app_key, app_secret_hash, status) VALUES
(1, 1, 'knot_pk_001', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'ACTIVE'),
(2, 2, 'knot_pk_002', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'ACTIVE'),
(3, 3, 'knot_pk_003', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'ACTIVE');

-- 应用-模型权限
INSERT IGNORE INTO app_model_permissions (app_id, model_id) VALUES
(1,1),(1,2),(1,4),(1,6),
(2,1),(2,2),(2,4),(2,5),
(3,1),(3,2),(3,6),(3,7);

-- =========================
-- 路由规则
-- =========================

INSERT IGNORE INTO routing_consumers (id, consumer_code, name, user_id, secret_key, status) VALUES
(1, 'consumer-internal-kb', '内部知识库助手消费者', 1, 'sk-demo-gpt4o-routing-key-001', 'ENABLED'),
(2, 'consumer-research',    '模型评测消费者',       1, 'sk-demo-claude-routing-key-002', 'ENABLED'),
(3, 'consumer-cs',          '客服系统消费者',       2, 'sk-demo-deepseek-routing-key-003', 'ENABLED');

INSERT IGNORE INTO routing_rules (id, rule_code, name, app_scenario, model_types, app_id, user_id, strategy_type, status) VALUES
(1, 'gpt4o-default',    'GPT-4o默认路由',    '知识库问答', 'CHAT', 1, 1, 'PRIORITY', 'ENABLED'),
(2, 'claude-default',   'Claude默认路由',    '模型评测',   'CHAT', 1, 1, 'PRIORITY', 'ENABLED'),
(3, 'deepseek-lowcost', 'DeepSeek低成本路由', '客服对话',   'CHAT', 2, 2, 'PRIORITY', 'ENABLED');

INSERT IGNORE INTO routing_rule_consumers (id, rule_id, consumer_id) VALUES
(1, 1, 1),
(2, 2, 2),
(3, 3, 3);

INSERT IGNORE INTO routing_rule_models (id, rule_id, model_id, priority, is_primary) VALUES
(1, 1, 1, 100, 1),
(2, 1, 2, 90,  0),
(3, 2, 4, 100, 1),
(4, 3, 6, 100, 1);

-- =========================
-- 计费规则
-- =========================

INSERT IGNORE INTO billing_rules (id, code, name, billing_mode, unit, unit_price, effective_from) VALUES
(1, 'TOKEN_GPT4O',      'GPT-4o Token计费',       'TOKEN',   '1K_TOKENS', 0.005000, NOW()),
(2, 'TOKEN_GPT4O_MINI', 'GPT-4o Mini Token计费',  'TOKEN',   '1K_TOKENS', 0.000150, NOW()),
(3, 'TOKEN_CLAUDE_S4',  'Claude Sonnet 4 Token计费','TOKEN',  '1K_TOKENS', 0.003000, NOW()),
(4, 'TOKEN_DEEPSEEK',   'DeepSeek Chat Token计费','TOKEN',   '1K_TOKENS', 0.000140, NOW()),
(5, 'EMBEDDING',        'Embedding 计费',          'TOKEN',   '1K_TOKENS', 0.000130, NOW());

-- =========================
-- 安全与监控
-- =========================

INSERT IGNORE INTO security_policies (id, policy_type, policy_code, config_json, status) VALUES
(1, 'RATE_LIMIT', 'GLOBAL_RATE_LIMIT',   '{"maxRps":1000,"maxRpm":60000}',       'ENABLED'),
(2, 'IP_FILTER',  'GLOBAL_IP_WHITELIST', '{"mode":"whitelist","ips":["10.0.0.0/8","172.16.0.0/12","192.168.0.0/16"]}', 'ENABLED'),
(3, 'CONTENT',    'CONTENT_FILTER',      '{"enabled":true,"categories":["violence","hate"]}', 'ENABLED');

-- 告警
INSERT IGNORE INTO alerts (id, alert_type, level, title, status) VALUES
(1, 'QUOTA',  'WARN',  'OpenAI日配额已达80%',    'OPEN'),
(2, 'ERROR',  'ERROR', 'DeepSeek连续5分钟超时率>5%', 'OPEN'),
(3, 'AUTH',   'INFO',  'app_002凭证即将过期',    'RESOLVED');

-- =========================
-- 可选扩展
-- =========================

-- 网关节点
INSERT IGNORE INTO gateway_nodes (id, node_id, host, status) VALUES
(1, 'node-01', '10.0.1.10:9090', 'ONLINE'),
(2, 'node-02', '10.0.1.11:9090', 'ONLINE');

-- 备份任务
INSERT IGNORE INTO backup_jobs (id, job_code, status, snapshot_ref) VALUES
(1, 'BACKUP-20260501', 'COMPLETED', 'snap-20260501-001');

INSERT IGNORE INTO scheduled_tasks (
  id, task_code, task_name, handler_code, cron_expression, execution_mode, status, description
) VALUES
(1, 'operation-log-retention', '操作日志保留清理', 'OPERATION_LOG_RETENTION', '0 0 3 * * ?', 'SINGLE', 'ENABLED', '操作日志最多保留三个月'),
(2, 'schedule-run-retention', '定时任务执行记录清理', 'SCHEDULE_RUN_RETENTION', '0 30 3 * * ?', 'SINGLE', 'ENABLED', '定时任务执行记录最多保留一个月');

-- 插件
INSERT IGNORE INTO plugins (id, code, name, plugin_type, version, status) VALUES
(1, 'content-filter',  '内容安全过滤',   'FILTER',   '1.0.0', 'ENABLED'),
(2, 'audit-logger',    '审计日志插件',   'LOGGER',   '1.0.0', 'ENABLED'),
(3, 'token-counter',   'Token计数插件',  'COUNTER',  '1.0.0', 'DISABLED');

-- 灰度计划
INSERT IGNORE INTO gray_plans (id, plan_name, target_type, target_id, traffic_percent, steps_json, start_time, status) VALUES
(1, 'GPT-4o v2灰度','MODEL', 1, 10, '[10,30,50,100]', NOW(), 'DRAFT');

-- 通知模板
INSERT IGNORE INTO notification_templates (id, code, name, channel, title_tpl, content_tpl, status) VALUES
(1, 'QUOTA_ALERT',    '配额告警',   'EMAIL', '配额告警: {{appName}}',   '应用 {{appName}} 的配额已使用 {{percent}}%，请及时处理。', 'ACTIVE'),
(2, 'ERROR_ALERT',    '异常告警',   'EMAIL', '异常告警: {{providerName}}','供应商 {{providerName}} 错误率超过阈值，当前 {{errorRate}}%。', 'ACTIVE'),
(3, 'CREDENTIAL_EXPIRE','凭证过期提醒','EMAIL','凭证即将过期: {{providerName}}','供应商 {{providerName}} 的 API Key 将在 {{expireDate}} 过期。', 'ACTIVE');

-- 枚举分类（与 enum_configs.category_id 对应；is_system=1 表示系统内置分类）
INSERT IGNORE INTO enum_categories (id, category, category_name, is_system, is_enabled) VALUES
(1, 'provider_type', '供应商类型', 0, 1),
(2, 'model_type', '模型类型', 0, 1),
(3, 'app_type', '应用类型', 0, 1),
(4, 'credential_type', '凭证类型', 0, 1),
(5, 'scope_type', '折扣范围', 0, 1),
(6, 'discount_type', '折扣类型', 0, 1),
(7, 'billing_mode', '计费模式', 0, 1),
(8, 'billing_unit', '计费单位', 0, 1),
(9, 'strategy_type', '路由策略', 0, 1),
(10, 'channel', '通知渠道', 0, 1),
(11, 'plugin_type', '插件类型', 0, 1),
(12, 'gray_target_type', '灰度目标类型', 0, 1),
(13, 'alert_level', '告警级别', 1, 1),
(14, 'risk_level', '风险级别', 1, 1),
(15, 'status', '通用状态', 1, 1);

-- 枚举配置（category_id 关联 enum_categories.id；是否系统内置由分类决定）
INSERT IGNORE INTO enum_configs (category_id, item_code, item_label, sort_order, is_enabled) VALUES
(1, 'OPENAI',    'OpenAI',     1, 1),
(1, 'ANTHROPIC', 'Anthropic',  2, 1),
(1, 'DEEPSEEK',  'DeepSeek',   3, 1),
(1, 'GOOGLE',    'Google',     4, 1),
(1, 'MISTRAL',   'Mistral',    5, 1),
(1, 'CUSTOM',    '自定义',    99, 1),
(2, 'CHAT',      '对话',   1, 1),
(2, 'EMBEDDING', '向量',   2, 1),
(2, 'IMAGE',     '图像',   3, 1),
(2, 'AUDIO',     '语音',   4, 1),
(3, 'WEB',     'Web应用', 1, 1),
(3, 'MOBILE',  '移动应用', 2, 1),
(3, 'SERVICE', '微服务',   3, 1),
(3, 'OTHER',   '其他',    99, 1),
(4, 'API_KEY',      'API Key',    1, 1),
(4, 'OAUTH_TOKEN',  'OAuth Token', 2, 1),
(4, 'BEARER_TOKEN', 'Bearer Token',3, 1),
(5, 'GLOBAL', '全局',   1, 1),
(5, 'MODEL',  '按模型', 2, 1),
(5, 'APP',    '按应用', 3, 1),
(6, 'PERCENTAGE', '百分比折扣', 1, 1),
(6, 'FIXED',      '固定金额',   2, 1),
(7, 'TOKEN_BASED',    '按Token计费', 1, 1),
(7, 'REQUEST_BASED',  '按请求计费',   2, 1),
(7, 'SUBSCRIPTION',   '订阅制',      3, 1),
(8, '1K_TOKENS',  '1K Tokens',  1, 1),
(8, '1M_TOKENS',  '1M Tokens',  2, 1),
(8, 'PER_REQUEST','每次请求',    3, 1),
(9, 'PRIORITY', '优先级',   0, 1),
(9, 'FIXED',    '固定',     1, 1),
(9, 'WEIGHTED', '加权',     2, 1),
(9, 'FAILOVER', '故障转移', 3, 1),
(10, 'EMAIL',   '邮件',    1, 1),
(10, 'SMS',     '短信',    2, 1),
(10, 'WEBHOOK', 'Webhook', 3, 1),
(11, 'PRE',  '前置',  1, 1),
(11, 'POST', '后置',  2, 1),
(12, 'MODEL', '模型', 1, 1),
(12, 'APP',   '应用', 2, 1),
(13, 'CRITICAL', '严重', 1, 1),
(13, 'HIGH',     '高',   2, 1),
(13, 'MEDIUM',   '中',   3, 1),
(13, 'LOW',      '低',   4, 1),
(14, 'HIGH',   '高', 1, 1),
(14, 'MEDIUM', '中', 2, 1),
(14, 'LOW',    '低', 3, 1),
(15, 'ENABLED',   '启用',     1, 1),
(15, 'DISABLED',  '禁用',     2, 1),
(15, 'ACTIVE',    '活跃',     3, 1),
(15, 'ONLINE',    '在线',     4, 1),
(15, 'RUNNING',   '运行中',   5, 1),
(15, 'DRAFT',     '草稿',     6, 1),
(15, 'GENERATED', '已生成',   7, 1),
(15, 'SUCCESS',   '成功',     8, 1),
(15, 'FAILURE',   '失败',     9, 1),
(15, 'FAILED',    '失败(旧)', 10, 1),
(15, 'INACTIVE',  '未激活',   11, 1);

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
INSERT IGNORE INTO providers (id, code, name, provider_type, base_url, status, rate_limit_json, quota_json) VALUES
(1, 'openai',       'OpenAI',       'OPENAI',  'https://api.openai.com',       'ENABLED', '{"perSecond":100,"perMinute":5000,"timeWindow":"MINUTE"}', '{"dailyLimit":1000000,"monthlyLimit":30000000,"tokenLimit":500000000,"alertEnabled":true}'),
(2, 'anthropic',    'Anthropic',    'ANTHROPIC','https://api.anthropic.com',     'ENABLED', '{"perSecond":50,"perMinute":2000,"timeWindow":"MINUTE"}',  '{"dailyLimit":500000,"monthlyLimit":15000000,"tokenLimit":200000000,"alertEnabled":true}'),
(3, 'deepseek',     'DeepSeek',     'DEEPSEEK','https://api.deepseek.com',      'ENABLED', '{"perSecond":30,"perMinute":1000,"timeWindow":"MINUTE"}',  '{"dailyLimit":200000,"monthlyLimit":6000000,"tokenLimit":100000000,"alertEnabled":false}');

-- 供应商凭证
INSERT IGNORE INTO provider_credentials (id, provider_id, credential_type, encrypted_key, status) VALUES
(1, 1, 'API_KEY',    'sk-openai-demo-xxxxx',        'ACTIVE'),
(2, 2, 'API_KEY',    'sk-ant-api03-demo-xxxxx',     'ACTIVE'),
(3, 3, 'API_KEY',    'sk-deepseek-demo-xxxxx',      'ACTIVE');

-- 供应商折扣策略
INSERT IGNORE INTO provider_discount_policies (id, provider_id, policy_name, scope_type, scope_ref_id, discount_type, discount_value, priority, effective_from, status) VALUES
(1, 1, '新用户9折',   'GLOBAL',   NULL, 'PERCENTAGE', 0.9000, 100, NOW(), 'ACTIVE'),
(2, 2, '企业客户8折', 'GLOBAL',   NULL, 'PERCENTAGE', 0.8000, 90,  NOW(), 'ACTIVE'),
(3, 3, '直减5元',     'GLOBAL',   NULL, 'FIXED',      5.0000, 100, NOW(), 'ACTIVE');

-- 模型
INSERT IGNORE INTO models (id, provider_id, model_code, name, model_type, version, status, rate_limit_json, quota_json) VALUES
(1,  1, 'gpt-4o',            'GPT-4o',            'CHAT',   '2024-08-06', 'ENABLED', NULL, NULL),
(2,  1, 'gpt-4o-mini',       'GPT-4o Mini',       'CHAT',   '2024-07-18', 'ENABLED', NULL, NULL),
(3,  1, 'text-embedding-3-large','Text Embedding 3 Large','EMBEDDING','2024-01-01','ENABLED', NULL, NULL),
(4,  2, 'claude-sonnet-4-20250514','Claude Sonnet 4','CHAT', '2025-05-14','ENABLED',  NULL, NULL),
(5,  2, 'claude-haiku-3-5-20241022','Claude 3.5 Haiku','CHAT','2024-10-22','ENABLED',NULL, NULL),
(6,  3, 'deepseek-chat',     'DeepSeek Chat',     'CHAT',   '2024-08-01', 'ENABLED', NULL, NULL),
(7,  3, 'deepseek-reasoner', 'DeepSeek Reasoner', 'CHAT',   '2025-01-20', 'ENABLED', NULL, NULL);

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
INSERT IGNORE INTO apps (id, app_id, name, owner_user_id, status, rate_limit_json, quota_json) VALUES
(1, 'app_001', '内部知识库助手',   1, 'ENABLED', '{"perSecond":10,"perMinute":600,"timeWindow":"MINUTE"}',  '{"dailyLimit":10000,"monthlyLimit":300000,"tokenLimit":5000000,"alertEnabled":true}'),
(2, 'app_002', '客服对话系统',     2, 'ENABLED', '{"perSecond":5,"perMinute":300,"timeWindow":"MINUTE"}',   '{"dailyLimit":5000,"monthlyLimit":150000,"tokenLimit":2000000,"alertEnabled":true}'),
(3, 'app_003', '代码审查工具',     3, 'ENABLED', NULL, NULL);

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

INSERT IGNORE INTO routing_rules (id, name, strategy_type, priority, condition_expr, target_provider_id, target_model_id, status) VALUES
(1, 'GPT-4o默认路由',   'PRIORITY', 100, 'model_code="gpt-4o"',         1, 1, 'ENABLED'),
(2, 'Claude默认路由',   'PRIORITY', 100, 'model_code="claude-sonnet-4-20250514"', 2, 4, 'ENABLED'),
(3, 'DeepSeek低成本路由','PRIORITY', 90,  'model_code="deepseek-chat"',  3, 6, 'ENABLED');

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
(9, 'FIXED',    '固定',   1, 1),
(9, 'WEIGHTED', '加权',   2, 1),
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
(15, 'FAILED',    '失败',     9, 1);

-- ============================================================
-- Knot AI Gateway - Seed Data（幂等）
-- 可重复执行：主键/唯一键冲突时跳过（INSERT IGNORE）
-- ============================================================

-- =========================
-- 系统管理
-- =========================

-- 用户 (password_hash = BCrypt('admin123'))
INSERT IGNORE INTO users (id, username, password_hash, real_name, dept_id, status) VALUES
(1, 'admin', '$2a$10$HUYfxtiEgiRARR/fG46hEeAvcfcQ2WXMPh2NxPw5zkc06fDKeWkxi', '系统管理员', 1, 1),
(2, 'zhangsan', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '张三', 3, 1),
(3, 'lisi', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '李四', 2, 1);

UPDATE users SET dept_id = 1 WHERE id = 1 AND dept_id IS NULL;
UPDATE users SET dept_id = 3 WHERE id = 2 AND dept_id IS NULL;
UPDATE users SET dept_id = 2 WHERE id = 3 AND dept_id IS NULL;

-- 部门
INSERT IGNORE INTO departments (id, dept_code, dept_name, parent_id, status, sort_order, remark) VALUES
(1, 'HQ',  '总部',     NULL, 1, 10, '默认管理部门'),
(2, 'RND', '研发部',   1,    1, 20, '负责模型与平台研发'),
(3, 'OPS', '运维部',   1,    1, 30, '负责网关运维与监控');

-- 角色
INSERT IGNORE INTO roles (id, code, name, description) VALUES
(1, 'ADMIN', '管理员', '系统管理员，拥有全部权限'),
(2, 'OPERATOR', '运维人员', '负责网关运维监控'),
(3, 'DEVELOPER', '开发人员', '应用接入开发者');

-- 用户-角色
INSERT IGNORE INTO user_roles (user_id, role_id) VALUES
(1, 1),
(2, 2),
(3, 3);

INSERT IGNORE INTO sys_modules (id, module_code, module_name, icon, sort_order, status) VALUES
(1, 'system', '系统管理', 'Setting', 10, 'ENABLED'),
(2, 'model', '模型管理', 'Box', 20, 'ENABLED'),
(3, 'routing', '路由管理', 'Share', 30, 'ENABLED'),
(4, 'billing', '计费管理', 'Coin', 40, 'ENABLED');

INSERT IGNORE INTO sys_menus (id, module_id, parent_id, menu_code, menu_name, route_path, component_key, icon, sort_order, status) VALUES
(1, 1, NULL, 'system.users', '用户管理', '/system/users', 'system/UserManageView', 'User', 10, 'ENABLED'),
(2, 1, NULL, 'system.departments', '部门管理', '/system/departments', 'system/DepartmentManageView', 'OfficeBuilding', 20, 'ENABLED'),
(3, 1, NULL, 'system.role-authorizations', '角色授权', '/system/role-authorizations', 'system/RoleAuthorizationManageView', 'Lock', 30, 'ENABLED'),
(4, 1, NULL, 'system.logs', '操作日志', '/system/logs', 'system/OperationLogView', 'Document', 40, 'ENABLED'),
(5, 1, NULL, 'system.settings', '用户设置', '/system/settings', 'system/UserSettingsView', 'Tools', 50, 'ENABLED'),
(14, 1, NULL, 'system.scheduled-tasks', '定时任务', '/system/scheduled-tasks', 'system/ScheduledTaskView', 'Timer', 60, 'ENABLED'),
(15, 1, NULL, 'system.enums', '枚举管理', '/system/enums', 'system/EnumManageView', 'List', 70, 'ENABLED'),
(16, 1, NULL, 'system.plugins', '插件管理', '/system/plugins', 'PluginManageView', 'Connection', 80, 'ENABLED'),
(17, 1, NULL, 'system.apps', '应用管理', '/apps', 'AppManageView', 'Grid', 90, 'ENABLED'),
(18, 1, NULL, 'system.authorization-resources', '授权资源', '/system/authorization-resources', 'system/AuthorizationResourceManageView', 'Lock', 35, 'ENABLED');

INSERT IGNORE INTO sys_permissions (id, permission_code, permission_name, permission_type, module_id, menu_id, status, built_in, remark) VALUES
(1, 'system:user:page', '用户管理页面访问', 'PAGE', 1, 1, 'ENABLED', 1, NULL),
(2, 'system:user:view', '查看用户', 'API', 1, 1, 'ENABLED', 1, NULL),
(3, 'system:user:create', '创建用户', 'API', 1, 1, 'ENABLED', 1, NULL),
(4, 'system:user:update', '更新用户', 'API', 1, 1, 'ENABLED', 1, NULL),
(5, 'system:user:enable', '更新用户状态', 'API', 1, 1, 'ENABLED', 1, NULL),
(6, 'system:department:page', '部门管理页面访问', 'PAGE', 1, 2, 'ENABLED', 1, NULL),
(7, 'system:department:view', '查看部门', 'API', 1, 2, 'ENABLED', 1, NULL),
(8, 'system:department:create', '创建部门', 'API', 1, 2, 'ENABLED', 1, NULL),
(9, 'system:department:update', '更新部门', 'API', 1, 2, 'ENABLED', 1, NULL),
(10, 'system:department:enable', '更新部门状态', 'API', 1, 2, 'ENABLED', 1, NULL),
(11, 'system:department:delete', '删除部门', 'API', 1, 2, 'ENABLED', 1, NULL),
(12, 'system:role-authorization:page', '角色授权页面访问', 'PAGE', 1, 3, 'ENABLED', 1, NULL),
(13, 'system:role:view', '查看角色', 'API', 1, 3, 'ENABLED', 1, NULL),
(14, 'system:log:page', '操作日志页面访问', 'PAGE', 1, 4, 'ENABLED', 1, NULL),
(15, 'system:log:view', '查看操作日志', 'API', 1, 4, 'ENABLED', 1, NULL),
(16, 'system:settings:page', '用户设置页面访问', 'PAGE', 1, 5, 'ENABLED', 1, NULL),
(17, 'system:settings:update', '更新用户设置', 'API', 1, 5, 'ENABLED', 1, NULL),
(27, 'system:scheduled-task:page', '定时任务页面访问', 'PAGE', 1, 14, 'ENABLED', 1, NULL),
(28, 'system:scheduled-task:view', '查看定时任务', 'API', 1, 14, 'ENABLED', 1, NULL),
(29, 'system:enum:page', '枚举管理页面访问', 'PAGE', 1, 15, 'ENABLED', 1, NULL),
(30, 'system:enum:view', '查看枚举管理', 'API', 1, 15, 'ENABLED', 1, NULL),
(31, 'system:plugin:page', '插件管理页面访问', 'PAGE', 1, 16, 'ENABLED', 1, NULL),
(32, 'system:plugin:view', '查看插件管理', 'API', 1, 16, 'ENABLED', 1, NULL),
(33, 'system:app:page', '应用管理页面访问', 'PAGE', 1, 17, 'ENABLED', 1, NULL),
(34, 'system:app:view', '查看应用管理', 'API', 1, 17, 'ENABLED', 1, NULL),
(35, 'system:authz:role:create', '创建授权角色', 'API', 1, 3, 'ENABLED', 1, NULL),
(36, 'system:authz:role:update', '更新授权角色', 'API', 1, 3, 'ENABLED', 1, NULL),
(37, 'system:authz:role:delete', '删除授权角色', 'API', 1, 3, 'ENABLED', 1, NULL),
(38, 'system:authz:role:grant', '维护角色授权', 'API', 1, 3, 'ENABLED', 1, NULL),
(39, 'system:authz:module:view', '查看授权模块', 'API', 1, 18, 'ENABLED', 1, NULL),
(40, 'system:authz:module:create', '创建授权模块', 'API', 1, 18, 'ENABLED', 1, NULL),
(41, 'system:authz:module:update', '更新授权模块', 'API', 1, 18, 'ENABLED', 1, NULL),
(42, 'system:authz:module:delete', '删除授权模块', 'API', 1, 18, 'ENABLED', 1, NULL),
(43, 'system:authz:menu:view', '查看授权菜单', 'API', 1, 18, 'ENABLED', 1, NULL),
(44, 'system:authz:menu:create', '创建授权菜单', 'API', 1, 18, 'ENABLED', 1, NULL),
(45, 'system:authz:menu:update', '更新授权菜单', 'API', 1, 18, 'ENABLED', 1, NULL),
(46, 'system:authz:menu:delete', '删除授权菜单', 'API', 1, 18, 'ENABLED', 1, NULL),
(47, 'system:authz:permission:view', '查看授权权限', 'API', 1, 18, 'ENABLED', 1, NULL),
(48, 'system:authz:permission:create', '创建授权权限', 'API', 1, 18, 'ENABLED', 1, NULL),
(49, 'system:authz:permission:update', '更新授权权限', 'API', 1, 18, 'ENABLED', 1, NULL),
(50, 'system:authz:permission:delete', '删除授权权限', 'API', 1, 18, 'ENABLED', 1, NULL),
(51, 'system:authz:api-binding:view', '查看 API 权限绑定', 'API', 1, 18, 'ENABLED', 1, NULL),
(52, 'system:authz:api-binding:create', '创建 API 权限绑定', 'API', 1, 18, 'ENABLED', 1, NULL),
(53, 'system:authz:api-binding:update', '更新 API 权限绑定', 'API', 1, 18, 'ENABLED', 1, NULL),
(54, 'system:authz:api-binding:delete', '删除 API 权限绑定', 'API', 1, 18, 'ENABLED', 1, NULL),
(55, 'system:authorization-resource:page', '授权资源页面访问', 'PAGE', 1, 18, 'ENABLED', 1, NULL);

INSERT IGNORE INTO sys_role_permissions (role_id, permission_id)
SELECT 1, id FROM sys_permissions;

INSERT IGNORE INTO sys_role_permissions (role_id, permission_id) VALUES
(2, 1),(2, 2),(2, 6),(2, 7),(2, 14),(2, 15),(2, 16),(2, 17),
(3, 14),(3, 15),(3, 16),(3, 17);

INSERT IGNORE INTO sys_role_permissions (role_id, permission_id) VALUES
(2, 27),(2, 28),(2, 29),(2, 30),(2, 31),(2, 32),(2, 33),(2, 34),
(2, 35),(2, 36),(2, 37),(2, 38),(2, 39),(2, 40),(2, 41),(2, 42),
(2, 43),(2, 44),(2, 45),(2, 46),(2, 47),(2, 48),(2, 49),(2, 50),
(2, 51),(2, 52),(2, 53),(2, 54),(2, 55);

INSERT IGNORE INTO sys_api_permission_bindings (id, permission_id, http_method, path_pattern, controller_class, status) VALUES
(1, 2, 'POST', '/api/users', 'UserController', 'ENABLED'),
(2, 3, 'POST', '/api/users/create', 'UserController', 'ENABLED'),
(3, 5, 'PUT', '/api/users/{id}/status', 'UserController', 'ENABLED'),
(4, 4, 'PUT', '/api/users/{id}', 'UserController', 'ENABLED'),
(5, 7, 'POST', '/api/system/departments/list', 'DepartmentController', 'ENABLED'),
(6, 7, 'GET', '/api/system/departments/tree', 'DepartmentController', 'ENABLED'),
(7, 8, 'POST', '/api/system/departments', 'DepartmentController', 'ENABLED'),
(8, 9, 'PUT', '/api/system/departments/{id}', 'DepartmentController', 'ENABLED'),
(9, 10, 'PUT', '/api/system/departments/{id}/status', 'DepartmentController', 'ENABLED'),
(10, 11, 'DELETE', '/api/system/departments/{id}', 'DepartmentController', 'ENABLED'),
(11, 13, 'POST', '/api/system/roles', 'SystemController', 'ENABLED'),
(12, 15, 'POST', '/api/system/operation-logs', 'SystemController', 'ENABLED'),
(13, 15, 'POST', '/api/system/operation-logs/{id}', 'SystemController', 'ENABLED');

INSERT IGNORE INTO sys_permissions (id, permission_code, permission_name, permission_type, module_id, menu_id, status, built_in, remark) VALUES
(18, 'system:settings:view', '查看当前用户设置与授权信息', 'API', 1, 5, 'ENABLED', 1, NULL);

INSERT IGNORE INTO sys_role_permissions (role_id, permission_id) VALUES
(2, 18),
(3, 18);

INSERT IGNORE INTO sys_api_permission_bindings (id, permission_id, http_method, path_pattern, controller_class, status) VALUES
(14, 18, 'GET', '/api/user-settings/me', 'UserSettingController', 'ENABLED'),
(15, 17, 'PUT', '/api/user-settings/me', 'UserSettingController', 'ENABLED'),
(16, 18, 'GET', '/api/me/authorizations', 'CurrentUserController', 'ENABLED'),
(17, 15, 'POST', '/api/operation-logs/list', 'OperationLogController', 'ENABLED'),
(18, 15, 'GET', '/api/operation-logs/{id}', 'OperationLogController', 'ENABLED'),
(19, 15, 'GET', '/api/operation-logs/module/{module}', 'OperationLogController', 'ENABLED'),
(20, 15, 'GET', '/api/operation-logs/operator/{operatorId}', 'OperationLogController', 'ENABLED'),
(21, 15, 'GET', '/api/operation-logs/entity/{entityType}/{entityId}', 'OperationLogController', 'ENABLED'),
(22, 28, 'POST', '/api/system/scheduled-tasks/list', 'ScheduledTaskController', 'ENABLED'),
(23, 30, 'GET', '/api/enums/summaries', 'EnumController', 'ENABLED'),
(24, 30, 'GET', '/api/enums/{category}/items', 'EnumController', 'ENABLED'),
(25, 32, 'POST', '/api/plugins/list', 'PluginController', 'ENABLED'),
(26, 34, 'POST', '/api/apps/list', 'AppController', 'ENABLED'),
(27, 13, 'POST', '/api/system/authorizations/roles/list', 'AuthorizationRoleController', 'ENABLED'),
(28, 13, 'GET', '/api/system/authorizations/roles/{roleId}/snapshot', 'AuthorizationRoleController', 'ENABLED'),
(29, 35, 'POST', '/api/system/authorizations/roles', 'AuthorizationRoleController', 'ENABLED'),
(30, 36, 'PUT', '/api/system/authorizations/roles/{id}', 'AuthorizationRoleController', 'ENABLED'),
(31, 37, 'DELETE', '/api/system/authorizations/roles/{id}', 'AuthorizationRoleController', 'ENABLED'),
(32, 38, 'PUT', '/api/system/authorizations/roles/{roleId}/permissions', 'AuthorizationRoleController', 'ENABLED'),
(33, 39, 'GET', '/api/system/authorizations/modules', 'AuthorizationModuleController', 'ENABLED'),
(34, 40, 'POST', '/api/system/authorizations/modules', 'AuthorizationModuleController', 'ENABLED'),
(35, 41, 'PUT', '/api/system/authorizations/modules/{id}', 'AuthorizationModuleController', 'ENABLED'),
(36, 42, 'DELETE', '/api/system/authorizations/modules/{id}', 'AuthorizationModuleController', 'ENABLED'),
(37, 43, 'GET', '/api/system/authorizations/menus', 'AuthorizationMenuController', 'ENABLED'),
(38, 44, 'POST', '/api/system/authorizations/menus', 'AuthorizationMenuController', 'ENABLED'),
(39, 45, 'PUT', '/api/system/authorizations/menus/{id}', 'AuthorizationMenuController', 'ENABLED'),
(40, 46, 'DELETE', '/api/system/authorizations/menus/{id}', 'AuthorizationMenuController', 'ENABLED'),
(41, 47, 'GET', '/api/system/authorizations/permissions', 'AuthorizationPermissionController', 'ENABLED'),
(42, 48, 'POST', '/api/system/authorizations/permissions', 'AuthorizationPermissionController', 'ENABLED'),
(43, 49, 'PUT', '/api/system/authorizations/permissions/{id}', 'AuthorizationPermissionController', 'ENABLED'),
(44, 50, 'DELETE', '/api/system/authorizations/permissions/{id}', 'AuthorizationPermissionController', 'ENABLED'),
(45, 51, 'GET', '/api/system/authorizations/api-bindings', 'AuthorizationApiBindingController', 'ENABLED'),
(46, 52, 'POST', '/api/system/authorizations/api-bindings', 'AuthorizationApiBindingController', 'ENABLED'),
(47, 53, 'PUT', '/api/system/authorizations/api-bindings/{id}', 'AuthorizationApiBindingController', 'ENABLED'),
(48, 54, 'DELETE', '/api/system/authorizations/api-bindings/{id}', 'AuthorizationApiBindingController', 'ENABLED'),
(49, 41, 'PUT', '/api/system/authorizations/modules/{id}/status', 'AuthorizationModuleController', 'ENABLED'),
(50, 45, 'PUT', '/api/system/authorizations/menus/{id}/status', 'AuthorizationMenuController', 'ENABLED'),
(51, 49, 'PUT', '/api/system/authorizations/permissions/{id}/status', 'AuthorizationPermissionController', 'ENABLED'),
(52, 53, 'PUT', '/api/system/authorizations/api-bindings/{id}/status', 'AuthorizationApiBindingController', 'ENABLED');

UPDATE sys_menus
SET menu_code = 'system.role-authorizations',
    menu_name = '角色授权',
    route_path = '/system/role-authorizations',
    component_key = 'system/RoleAuthorizationManageView',
    sort_order = 30,
    updated_at = NOW()
WHERE id = 3;

INSERT IGNORE INTO sys_menus (id, module_id, parent_id, menu_code, menu_name, route_path, component_key, icon, sort_order, status)
VALUES (18, 1, NULL, 'system.authorization-resources', '授权资源', '/system/authorization-resources', 'system/AuthorizationResourceManageView', 'Lock', 35, 'ENABLED');

UPDATE sys_permissions
SET permission_code = 'system:role-authorization:page',
    permission_name = '角色授权页面访问',
    menu_id = 3
WHERE id = 12;

INSERT IGNORE INTO sys_permissions (id, permission_code, permission_name, permission_type, module_id, menu_id, status, built_in, remark)
VALUES (55, 'system:authorization-resource:page', '授权资源页面访问', 'PAGE', 1, 18, 'ENABLED', 1, NULL);

UPDATE sys_permissions
SET menu_id = 18
WHERE id IN (39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53, 54);

INSERT IGNORE INTO sys_role_permissions (role_id, permission_id)
SELECT role_id, 55
FROM sys_role_permissions
WHERE permission_id = 12;

INSERT IGNORE INTO sys_menus (id, module_id, parent_id, menu_code, menu_name, route_path, component_key, icon, sort_order, status) VALUES
(6, 2, NULL, 'model.providers', '供应商', '/providers', 'ProviderManageView', 'Connection', 30, 'ENABLED'),
(7, 2, NULL, 'model.models', '供应商模型', '/model-management/models', 'ModelManageView', 'Cpu', 20, 'ENABLED'),
(8, 2, NULL, 'model.model-pools', '模型池', '/model-management/model-pools', 'ModelPoolManageView', 'Cpu', 10, 'ENABLED'),
(9, 2, NULL, 'model.logical-models', '模型广场', '/model-management/logical-models', 'LogicalModelMarketplaceView', 'Cpu', 40, 'ENABLED'),
(10, 2, NULL, 'model.external-models', '外部模型', '/model-management/external-models', 'ExternalModelManageView', 'Cpu', 50, 'ENABLED'),
(11, 3, NULL, 'routing.rules', '路由规则', '/routing/rules', 'routing/RoutingRuleView', 'Share', 10, 'ENABLED'),
(12, 3, NULL, 'routing.consumers', '消费者', '/routing/consumers', 'routing/RoutingConsumerView', 'Share', 20, 'ENABLED'),
(13, 4, NULL, 'billing.rules', '计费规则', '/billing/rules', 'billing/BillingRuleView', 'Coin', 10, 'ENABLED');

INSERT IGNORE INTO sys_permissions (id, permission_code, permission_name, permission_type, module_id, menu_id, status, built_in, remark) VALUES
(19, 'model:provider:page', '供应商页面访问', 'PAGE', 2, 6, 'ENABLED', 1, NULL),
(20, 'model:model:page', '供应商模型页面访问', 'PAGE', 2, 7, 'ENABLED', 1, NULL),
(21, 'model:model-pool:page', '模型池页面访问', 'PAGE', 2, 8, 'ENABLED', 1, NULL),
(22, 'model:logical-model:page', '模型广场页面访问', 'PAGE', 2, 9, 'ENABLED', 1, NULL),
(23, 'model:external-model:page', '外部模型页面访问', 'PAGE', 2, 10, 'ENABLED', 1, NULL),
(24, 'routing:rule:page', '路由规则页面访问', 'PAGE', 3, 11, 'ENABLED', 1, NULL),
(25, 'routing:consumer:page', '消费者页面访问', 'PAGE', 3, 12, 'ENABLED', 1, NULL),
(26, 'billing:rule:page', '计费规则页面访问', 'PAGE', 4, 13, 'ENABLED', 1, NULL);

INSERT IGNORE INTO sys_role_permissions (role_id, permission_id) VALUES
(2, 19),(2, 20),(2, 21),(2, 22),(2, 23),(2, 24),(2, 25),(2, 26),
(3, 19),(3, 20),(3, 21),(3, 22),(3, 23),(3, 24),(3, 25),(3, 26);

-- =========================
-- 供应商与模型
-- =========================

-- 供应商
INSERT IGNORE INTO providers (id, code, name, provider_type, status) VALUES
(1, 'openai',       'OpenAI',       'OPENAI',   'ENABLED'),
(2, 'anthropic',    'Anthropic',    'ANTHROPIC','ENABLED'),
(3, 'deepseek',     'DeepSeek',     'DEEPSEEK', 'ENABLED'),
(4, 'qwen',         'Qwen',         'QWEN',     'ENABLED');

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
(6, 'PROVIDER', 4, 3, 3),
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
(7,  3, 'deepseek-reasoner', 'DeepSeek Reasoner', 'CHAT',   '2025-01-20', 'ENABLED'),
(8,  4, 'qwen-image',        'Qwen Image',        'IMAGE',  '2025-08-01', 'ENABLED'),
(9,  4, 'qwen-image-edit',   'Qwen Image Edit',   'IMAGE',  '2025-08-01', 'ENABLED'),
(10, 1, 'gpt-image-1',       'GPT Image 1',       'IMAGE',  '2025-04-01', 'ENABLED');

INSERT IGNORE INTO model_pools (id, pool_code, name, model_type, selection_strategy, status, remark) VALUES
(1, 'chat-premium-pool', 'Premium Chat Pool', 'CHAT', 'WEIGHTED', 'ENABLED', 'Premium chat routing pool'),
(2, 'chat-economy-pool', 'Economy Chat Pool', 'CHAT', 'WEIGHTED', 'ENABLED', 'Economy chat routing pool');

INSERT IGNORE INTO model_pool_items (id, pool_id, model_id, weight, priority, status) VALUES
(1, 1, 1, 70, 10, 'ENABLED'),
(2, 1, 4, 30, 20, 'ENABLED'),
(3, 2, 2, 60, 10, 'ENABLED'),
(4, 2, 6, 40, 20, 'ENABLED');

INSERT IGNORE INTO logical_models (
  id, model_code, model_name, model_type, model_family, version, display_name, tagline, description,
  tags_json, use_cases_json, capabilities_json, context_window, max_output_tokens,
  input_modalities_json, output_modalities_json, languages_json,
  visibility, publish_status, status, sort_order, featured,
  quality_level, latency_level, cost_level, pricing_summary
) VALUES
(1, 'knot-chat-premium', 'Knot Chat Premium', 'CHAT', 'omni', '1.0',
 'Knot Chat Premium', 'High quality chat model for complex tasks',
 'A logical chat model that routes to premium provider models by policy.',
 JSON_ARRAY('chat', 'reasoning', 'premium'),
 JSON_ARRAY('knowledge assistant', 'research', 'complex analysis'),
 JSON_OBJECT('toolCalling', true, 'vision', true, 'reasoning', true),
 200000, 8192, JSON_ARRAY('text', 'image'), JSON_ARRAY('text'), JSON_ARRAY('zh-CN', 'en-US'),
 'PUBLIC', 'PUBLISHED', 'ENABLED', 10, 1, 'HIGH', 'MEDIUM', 'HIGH', 'Premium provider route'),
(2, 'knot-chat-economy', 'Knot Chat Economy', 'CHAT', 'general', '1.0',
 'Knot Chat Economy', 'Cost effective chat model for daily workloads',
 'A logical chat model that routes to economical provider models.',
 JSON_ARRAY('chat', 'economy'),
 JSON_ARRAY('customer service', 'daily assistant'),
 JSON_OBJECT('toolCalling', false, 'vision', false, 'reasoning', false),
 128000, 4096, JSON_ARRAY('text'), JSON_ARRAY('text'), JSON_ARRAY('zh-CN', 'en-US'),
 'PUBLIC', 'PUBLISHED', 'ENABLED', 20, 0, 'MEDIUM', 'LOW', 'LOW', 'Economy provider route');

INSERT IGNORE INTO provider_model_mappings (
  id, logical_model_id, provider_id, model_id, provider_model_name, status, priority
) VALUES
(1, 1, 1, 1, 'gpt-4o', 'ENABLED', 10),
(2, 1, 2, 4, 'claude-sonnet-4-20250514', 'ENABLED', 20),
(3, 2, 1, 2, 'gpt-4o-mini', 'ENABLED', 10),
(4, 2, 3, 6, 'deepseek-chat', 'ENABLED', 20);

INSERT IGNORE INTO external_model_sources (
  id, source_code, source_name, source_url, api_url, source_type, status
) VALUES
(1, 'OPENROUTER', 'OpenRouter Models', 'https://openrouter.ai/models',
 'https://openrouter.ai/api/v1/models', 'MODEL_CATALOG', 'ENABLED');

-- 模型 API 协议绑定（usage_extractor 为 Usage 解析器编码或类名）
INSERT IGNORE INTO model_api_bindings (id, model_id, protocol, base_url, api_path, request_adapter, usage_extractor, status, remark) VALUES
(1, 1, 'CHAT_COMPLETIONS', 'https://api.openai.com', '/v1/chat/completions', 'OPENAI_COMPATIBLE', 'DEFAULT', 'ENABLED', 'GPT-4o Chat Completions'),
(2, 2, 'CHAT_COMPLETIONS', 'https://api.openai.com', '/v1/chat/completions', 'OPENAI_COMPATIBLE', 'DEFAULT', 'ENABLED', 'GPT-4o Mini Chat Completions'),
(3, 4, 'MESSAGES', 'https://api.anthropic.com', '/v1/messages', 'ANTHROPIC', 'ANTHROPIC', 'ENABLED', 'Claude Sonnet Messages API'),
(4, 6, 'CHAT_COMPLETIONS', 'https://api.deepseek.com', '/v1/chat/completions', 'OPENAI_COMPATIBLE', 'DEFAULT', 'ENABLED', 'DeepSeek Chat'),
(5, 8, 'IMAGE_GENERATIONS', 'https://dashscope.aliyuncs.com', '/api/v1/services/aigc/multimodal-generation/generation', 'QWEN', 'DEFAULT', 'ENABLED', 'Qwen Image Generation'),
(6, 9, 'IMAGE_EDITS', 'https://dashscope.aliyuncs.com', '/api/v1/services/aigc/multimodal-generation/generation', 'QWEN', 'DEFAULT', 'ENABLED', 'Qwen Image Edit'),
(7, 10, 'IMAGE_GENERATIONS', 'https://api.openai.com', '/v1/images/generations', 'OPENAI_COMPATIBLE', 'DEFAULT', 'ENABLED', 'OpenAI Image Generation'),
(8, 10, 'IMAGE_EDITS', 'https://api.openai.com', '/v1/images/edits', 'OPENAI_COMPATIBLE', 'DEFAULT', 'ENABLED', 'OpenAI Image Edit');

-- =========================
-- 应用管理
-- =========================

-- 应用
INSERT IGNORE INTO apps (id, app_id, name, dept_id, owner_user_id, remark, status) VALUES
(1, 'app_001', '内部知识库助手',   1, 1, '面向内部员工的知识检索与问答', 'ENABLED'),
(2, 'app_002', '客服对话系统',     3, 2, '对外客服场景的对话接入',       'ENABLED'),
(3, 'app_003', '代码审查工具',     2, 3, '研发流程中的代码审查辅助',     'ENABLED');

-- 应用凭证
INSERT IGNORE INTO app_credentials (id, app_id, app_key, app_secret_hash, status) VALUES
(1, 1, 'knot_pk_001', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'ACTIVE'),
(2, 2, 'knot_pk_002', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'ACTIVE'),
(3, 3, 'knot_pk_003', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'ACTIVE');

-- 应用-模型权限
INSERT IGNORE INTO app_model_permissions (app_id, model_id) VALUES
(1,1),(1,2),(1,4),(1,6),(1,10),
(2,1),(2,2),(2,4),(2,5),(2,10),
(3,1),(3,2),(3,6),(3,7),(3,10);

-- =========================
-- 路由规则
-- =========================

INSERT IGNORE INTO routing_consumers (id, consumer_code, name, user_id, secret_key, return_usage_detail, status) VALUES
(1, 'consumer-internal-kb', '内部知识库助手消费者', 1, 'sk-demo-gpt4o-routing-key-001', 0, 'ENABLED'),
(2, 'consumer-research',    '模型评测消费者',       1, 'sk-demo-claude-routing-key-002', 0, 'ENABLED'),
(3, 'consumer-cs',          '客服系统消费者',       2, 'sk-demo-deepseek-routing-key-003', 0, 'ENABLED');

INSERT IGNORE INTO routing_rules (id, rule_code, name, app_scenario, model_types, app_id, user_id, status) VALUES
(1, 'gpt4o-default',    'GPT-4o默认路由',    '知识库问答', 'CHAT', 1, 1, 'ENABLED'),
(2, 'claude-default',   'Claude默认路由',    '模型评测',   'CHAT', 1, 1, 'ENABLED'),
(3, 'deepseek-lowcost', 'DeepSeek低成本路由', '客服对话',   'CHAT', 2, 2, 'ENABLED');

INSERT IGNORE INTO routing_rule_consumers (id, rule_id, consumer_id) VALUES
(1, 1, 1),
(2, 2, 2),
(3, 3, 3);

INSERT IGNORE INTO routing_rule_targets (id, rule_id, target_type, target_id, priority, is_primary) VALUES
(1, 1, 'MODEL_POOL', 1, 100, 1),
(2, 1, 'MODEL',      2, 90,  0),
(3, 2, 'MODEL',      4, 100, 1),
(4, 3, 'MODEL_POOL', 2, 100, 1);

-- =========================
-- 计费规则
-- =========================

INSERT IGNORE INTO billing_rules (id, code, name, provider_id, logical_model_id, current_version_id, status) VALUES
(1, 'TOKEN_GPT4O',      'GPT-4o Token计费',        1, 1, 1, 'ACTIVE'),
(2, 'TOKEN_GPT4O_MINI', 'GPT-4o Mini Token计费',   1, 2, 2, 'ACTIVE'),
(3, 'TOKEN_CLAUDE_S4',  'Claude Sonnet 4 Token计费',2, 3, 3, 'ACTIVE'),
(4, 'TOKEN_DEEPSEEK',   'DeepSeek Chat Token计费', 3, 4, 4, 'ACTIVE'),
(5, 'EMBEDDING',        'Embedding 计费',          NULL, NULL, 5, 'ACTIVE');

INSERT IGNORE INTO billing_rule_versions (id, rule_id, version_no, billing_mode, currency, status, effective_from) VALUES
(1, 1, 1, 'TOKEN', 'USD', 'ACTIVE', NOW()),
(2, 2, 1, 'TOKEN', 'USD', 'ACTIVE', NOW()),
(3, 3, 1, 'TOKEN', 'USD', 'ACTIVE', NOW()),
(4, 4, 1, 'TOKEN', 'USD', 'ACTIVE', NOW()),
(5, 5, 1, 'EMBEDDING', 'USD', 'ACTIVE', NOW());

INSERT IGNORE INTO billing_rule_version_items (id, version_id, item_type, unit, unit_size, unit_price) VALUES
(1, 1, 'INPUT_TOKEN', '1K_TOKENS', 1000, 0.005000),
(2, 2, 'INPUT_TOKEN', '1K_TOKENS', 1000, 0.000150),
(3, 3, 'INPUT_TOKEN', '1K_TOKENS', 1000, 0.003000),
(4, 4, 'INPUT_TOKEN', '1K_TOKENS', 1000, 0.000140),
(5, 5, 'EMBEDDING_TOKEN', '1K_TOKENS', 1000, 0.000130);

UPDATE billing_rule_versions bv
INNER JOIN billing_rules br ON br.id = bv.rule_id
INNER JOIN billing_rule_version_items bvi ON bvi.version_id = bv.id
SET bv.version_code = MD5(JSON_OBJECT(
  'providerId', br.provider_id,
  'logicalModelId', br.logical_model_id,
  'billingMode', bv.billing_mode,
  'currency', bv.currency,
  'itemType', bvi.item_type,
  'unit', bvi.unit,
  'unitPrice', CAST(bvi.unit_price AS CHAR),
  'configJson', bv.config_json,
  'ladderJson', bv.ladder_json
))
WHERE bv.version_code IS NULL;

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
-- 备份任务
INSERT IGNORE INTO scheduled_tasks (
  id, task_code, task_name, handler_code, cron_expression, execution_mode, status, description
) VALUES
(1, 'operation-log-retention', '操作日志保留清理', 'OPERATION_LOG_RETENTION', '0 0 3 * * ?', 'SINGLE', 'ENABLED', '操作日志最多保留三个月'),
(2, 'schedule-run-retention', '定时任务执行记录清理', 'SCHEDULE_RUN_RETENTION', '0 30 3 * * ?', 'SINGLE', 'ENABLED', '定时任务执行记录最多保留一个月'),
(3, 'openrouter-model-sync', 'OpenRouter 模型同步', 'OPENROUTER_MODEL_SYNC', '0 0 4 * * ?', 'SINGLE', 'DISABLED', '同步 OpenRouter 模型到外部模型库');

-- 插件
INSERT IGNORE INTO plugin_packages (
  id, plugin_code, plugin_name, version, source_type, entrypoint, manifest_json, status
) VALUES
(1, 'builtin-gateway-audit', '网关请求响应审计插件', '1.0.0', 'BUILTIN', 'org.chobit.knot.gateway.plugin.builtin.GatewayRequestLoggingPlugin',
 JSON_OBJECT('pluginId', 'builtin-gateway-audit', 'extensionPoint', 'GATEWAY_EXCHANGE'), 'ACTIVE'),
(2, 'builtin-provider-audit', '上游请求响应审计插件', '1.0.0', 'BUILTIN', 'org.chobit.knot.gateway.plugin.builtin.UpstreamRequestLoggingPlugin',
 JSON_OBJECT('pluginId', 'builtin-provider-audit', 'extensionPoint', 'UPSTREAM_EXCHANGE'), 'ACTIVE');

INSERT IGNORE INTO plugin_capabilities (
  id, package_id, capability_code, capability_name, extension_point, stage_code, order_hint, status
) VALUES
(1, 1, 'gateway-request-response-log', '网关请求响应日志', 'GATEWAY_EXCHANGE', 'GATEWAY_REQUEST', 100, 'ACTIVE'),
(2, 1, 'gateway-request-response-log', '网关请求响应日志', 'GATEWAY_EXCHANGE', 'GATEWAY_RESPONSE', 100, 'ACTIVE'),
(3, 1, 'gateway-request-response-log', '网关请求响应日志', 'GATEWAY_EXCHANGE', 'GATEWAY_ERROR', 100, 'ACTIVE'),
(4, 2, 'provider-request-response-log', '上游请求响应日志', 'UPSTREAM_EXCHANGE', 'UPSTREAM_REQUEST', 100, 'ACTIVE'),
(5, 2, 'provider-request-response-log', '上游请求响应日志', 'UPSTREAM_EXCHANGE', 'UPSTREAM_RESPONSE', 100, 'ACTIVE'),
(6, 2, 'provider-request-response-log', '上游请求响应日志', 'UPSTREAM_EXCHANGE', 'UPSTREAM_ERROR', 100, 'ACTIVE');

INSERT IGNORE INTO plugin_instances (
  id, package_id, capability_id, instance_code, instance_name, config_json, status, fail_mode, timeout_ms, concurrency_limit
) VALUES
(1, 1, 1, 'gateway-request-log', '网关请求日志插件', JSON_OBJECT('sink', 'LOG', 'plannedSink', 'KAFKA'), 'ACTIVE', 'FAIL_OPEN', 3000, 0),
(2, 1, 2, 'gateway-response-log', '网关响应日志插件', JSON_OBJECT('sink', 'LOG', 'plannedSink', 'KAFKA'), 'ACTIVE', 'FAIL_OPEN', 3000, 0),
(3, 1, 3, 'gateway-error-log', '网关异常日志插件', JSON_OBJECT('sink', 'LOG', 'plannedSink', 'KAFKA'), 'ACTIVE', 'FAIL_OPEN', 3000, 0),
(4, 2, 4, 'provider-request-log', '上游请求日志插件', JSON_OBJECT('sink', 'LOG', 'plannedSink', 'KAFKA'), 'ACTIVE', 'FAIL_OPEN', 3000, 0),
(5, 2, 5, 'provider-response-log', '上游响应日志插件', JSON_OBJECT('sink', 'LOG', 'plannedSink', 'KAFKA'), 'ACTIVE', 'FAIL_OPEN', 3000, 0),
(6, 2, 6, 'provider-error-log', '上游异常日志插件', JSON_OBJECT('sink', 'LOG', 'plannedSink', 'KAFKA'), 'ACTIVE', 'FAIL_OPEN', 3000, 0);

INSERT IGNORE INTO plugin_bindings (
  id, instance_id, scope_type, scope_ref_id, stage_code, order_no, status, binding_config_json
) VALUES
(1, 1, 'GLOBAL', NULL, 'GATEWAY_REQUEST', 100, 'ACTIVE', JSON_OBJECT('maskApiKey', true)),
(2, 2, 'GLOBAL', NULL, 'GATEWAY_RESPONSE', 100, 'ACTIVE', JSON_OBJECT()),
(3, 3, 'GLOBAL', NULL, 'GATEWAY_ERROR', 100, 'ACTIVE', JSON_OBJECT()),
(4, 4, 'GLOBAL', NULL, 'UPSTREAM_REQUEST', 100, 'ACTIVE', JSON_OBJECT()),
(5, 5, 'GLOBAL', NULL, 'UPSTREAM_RESPONSE', 100, 'ACTIVE', JSON_OBJECT()),
(6, 6, 'GLOBAL', NULL, 'UPSTREAM_ERROR', 100, 'ACTIVE', JSON_OBJECT());

INSERT IGNORE INTO plugin_config_versions (
  id, instance_id, version_no, version_code, config_json, operator_id
) VALUES
(1, 1, 1, MD5('gateway-request-log'), JSON_OBJECT('sink', 'LOG', 'plannedSink', 'KAFKA'), 1),
(2, 2, 1, MD5('gateway-response-log'), JSON_OBJECT('sink', 'LOG', 'plannedSink', 'KAFKA'), 1),
(3, 3, 1, MD5('gateway-error-log'), JSON_OBJECT('sink', 'LOG', 'plannedSink', 'KAFKA'), 1),
(4, 4, 1, MD5('provider-request-log'), JSON_OBJECT('sink', 'LOG', 'plannedSink', 'KAFKA'), 1),
(5, 5, 1, MD5('provider-response-log'), JSON_OBJECT('sink', 'LOG', 'plannedSink', 'KAFKA'), 1),
(6, 6, 1, MD5('provider-error-log'), JSON_OBJECT('sink', 'LOG', 'plannedSink', 'KAFKA'), 1);

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
(10, 'channel', '通知渠道', 0, 1),
(11, 'plugin_source_type', '插件来源类型', 0, 1),
(12, 'plugin_scope_type', '插件作用范围', 0, 1),
(24, 'plugin_extension_point', '插件扩展点', 0, 1),
(25, 'plugin_stage_code', '插件执行阶段', 0, 1),
(26, 'plugin_fail_mode', '插件失败策略', 0, 1),
(27, 'plugin_result_status', '插件结果状态', 0, 1),
(13, 'alert_level', '告警级别', 1, 1),
(14, 'risk_level', '风险级别', 1, 1),
(15, 'status', '通用状态', 1, 1),
(16, 'logical_model_visibility', '统一模型可见性', 0, 1),
(17, 'logical_model_publish_status', '统一模型发布状态', 0, 1),
(18, 'logical_model_quality_level', '统一模型质量等级', 0, 1),
(19, 'logical_model_latency_level', '统一模型延迟等级', 0, 1),
(20, 'logical_model_cost_level', '统一模型成本等级', 0, 1),
(22, 'model_api_protocol', '模型接口类型', 0, 1),
(23, 'model_pool_selection_strategy', '模型池选择策略', 1, 1);

-- 枚举配置（category_id 关联 enum_categories.id；是否系统内置由分类决定）
INSERT IGNORE INTO enum_categories (id, category, category_name, is_system, is_enabled) VALUES
(9, 'billing_currency', '计费币种', 0, 1),
(21, 'billing_item_type', '计费价格项', 0, 1);

INSERT IGNORE INTO enum_configs (category_id, item_code, item_label, sort_order, is_enabled) VALUES
(7, 'TOKEN',          'Token', 1, 1),
(7, 'REQUEST',        '请求', 2, 1),
(7, 'IMAGE',          '图片', 3, 1),
(7, 'AUDIO',          '音频', 4, 1),
(7, 'VIDEO',          '视频', 5, 1),
(7, 'EMBEDDING',      'Embedding', 6, 1),
(7, 'TIERED',         '阶梯', 7, 1),
(7, 'FREE',           '免费', 8, 1),
(7, 'CUSTOM',         '自定义', 9, 1),
(8, 'PER_TOKEN',      '单 Token', 3, 1),
(9, 'USD',            'USD', 1, 1),
(9, 'CNY',            'CNY', 2, 1),
(21, 'INPUT_TOKEN',       '输入 Token', 1, 1),
(21, 'OUTPUT_TOKEN',      '输出 Token', 2, 1),
(21, 'CACHE_READ_TOKEN',  '缓存读取 Token', 3, 1),
(21, 'CACHE_WRITE_TOKEN', '缓存写入 Token', 4, 1),
(21, 'REQUEST',           '请求', 5, 1),
(21, 'IMAGE',             '图片', 6, 1),
(21, 'AUDIO_MINUTE',      '音频分钟', 7, 1),
(21, 'VIDEO_SECOND',      '视频秒数', 8, 1),
(21, 'EMBEDDING_TOKEN',   'Embedding Token', 9, 1),
(21, 'TIERED_USAGE',      '阶梯用量', 10, 1),
(21, 'FREE',              '免费', 11, 1),
(21, 'CUSTOM',            '自定义', 12, 1),
(22, 'CHAT_COMPLETIONS',       'Chat Completions', 1, 1),
(22, 'RESPONSES',              'Responses', 2, 1),
(22, 'MESSAGES',               'Messages', 3, 1),
(22, 'COMPLETIONS',            'Completions', 4, 1),
(22, 'EMBEDDINGS',             'Embeddings', 5, 1),
(22, 'IMAGE_GENERATIONS',      'Image Generations', 6, 1),
(22, 'IMAGE_EDITS',            'Image Edits', 7, 1),
(22, 'IMAGE_VARIATIONS',       'Image Variations', 8, 1),
(22, 'AUDIO_TRANSCRIPTIONS',   'Audio Transcriptions', 9, 1),
(22, 'AUDIO_TRANSLATIONS',     'Audio Translations', 10, 1),
(22, 'AUDIO_SPEECH',           'Audio Speech', 11, 1),
(22, 'VIDEO_GENERATIONS',      'Video Generations', 12, 1),
(22, 'RERANK',                 'Rerank', 13, 1),
(22, 'MODERATIONS',            'Moderations', 14, 1),
(22, 'CUSTOM',                 '自定义', 99, 1),
(23, 'WEIGHTED',               '权重', 1, 1),
(23, 'PRIORITY',               '优先级', 2, 1),
(23, 'RANDOM',                 '随机', 3, 1),
(1, 'OPENAI',    'OpenAI',     1, 1),
(1, 'ANTHROPIC', 'Anthropic',  2, 1),
(1, 'DEEPSEEK',  'DeepSeek',   3, 1),
(1, 'QWEN',      'Qwen',       4, 1),
(1, 'ZHIPU',     'Zhipu',      5, 1),
(1, 'GOOGLE',    'Google',     6, 1),
(1, 'MISTRAL',   'Mistral',    7, 1),
(1, 'CUSTOM',    '自定义',    99, 1),
(2, 'CHAT',       '对话',     1, 1),
(2, 'TEXT',       '文本',     2, 1),
(2, 'REASONING',  '推理',     3, 1),
(2, 'MULTIMODAL', '多模态',   4, 1),
(2, 'EMBEDDING',  '向量',     5, 1),
(2, 'RERANK',     '重排',     6, 1),
(2, 'IMAGE',      '图像',     7, 1),
(2, 'AUDIO',      '语音',     8, 1),
(2, 'VIDEO',      '视频',     9, 1),
(2, 'DOCUMENT',   '文档理解', 10, 1),
(2, 'OCR',        'OCR',      11, 1),
(2, 'MODERATION', '安全审核', 12, 1),
(2, 'UTILITY',    '工具辅助', 13, 1),
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
(8, '1K_TOKENS',  '1K Tokens',  1, 1),
(8, '1M_TOKENS',  '1M Tokens',  2, 1),
(8, 'PER_REQUEST','每次请求',    3, 1),
(8, 'PER_IMAGE',  '每张图片',    4, 1),
(8, 'PER_MINUTE', '每分钟',      5, 1),
(8, 'PER_SECOND', '每秒',        6, 1),
(10, 'EMAIL',   '邮件',    1, 1),
(10, 'SMS',     '短信',    2, 1),
(10, 'WEBHOOK', 'Webhook', 3, 1),
(11, 'BUILTIN', '内置', 1, 1),
(11, 'LOCAL_JAR', '本地 JAR', 2, 1),
(11, 'REMOTE_REGISTRY', '远程仓库', 3, 1),
(12, 'GLOBAL', '全局', 1, 1),
(12, 'APP', '应用', 2, 1),
(12, 'RULE', '路由规则', 3, 1),
(12, 'PROVIDER', '供应商', 4, 1),
(12, 'MODEL', '模型', 5, 1),
(12, 'POOL', '模型池', 6, 1),
(24, 'GATEWAY_EXCHANGE', '网关请求处理链路', 1, 1),
(24, 'UPSTREAM_EXCHANGE', '上游请求处理链路', 2, 1),
(25, 'GATEWAY_REQUEST', '网关请求阶段', 1, 1),
(25, 'GATEWAY_RESPONSE', '网关响应阶段', 2, 1),
(25, 'GATEWAY_ERROR', '网关异常阶段', 3, 1),
(25, 'UPSTREAM_REQUEST', '上游请求阶段', 4, 1),
(25, 'UPSTREAM_RESPONSE', '上游响应阶段', 5, 1),
(25, 'UPSTREAM_ERROR', '上游异常阶段', 6, 1),
(26, 'FAIL_OPEN', '失败放行', 1, 1),
(26, 'FAIL_CLOSE', '失败阻断', 2, 1),
(27, 'SUCCESS', '成功', 1, 1),
(27, 'SKIPPED', '跳过', 2, 1),
(27, 'FAILED', '失败', 3, 1),
(27, 'TIMEOUT', '超时', 4, 1),
(27, 'OPEN_CIRCUIT', '熔断', 5, 1),
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
(15, 'INACTIVE',  '未激活',   11, 1),
(16, 'PUBLIC',    '公开',     1, 1),
(16, 'INTERNAL',  '内部',     2, 1),
(16, 'PRIVATE',   '私有',     3, 1),
(17, 'DRAFT',     '草稿',     1, 1),
(17, 'PUBLISHED', '已发布',   2, 1),
(17, 'ARCHIVED',  '已下架',   3, 1),
(18, 'HIGH',      '高',       1, 1),
(18, 'MEDIUM',    '中',       2, 1),
(18, 'LOW',       '低',       3, 1),
(19, 'LOW',       '低',       1, 1),
(19, 'MEDIUM',    '中',       2, 1),
(19, 'HIGH',      '高',       3, 1),
(20, 'LOW',       '低',       1, 1),
(20, 'MEDIUM',    '中',       2, 1),
(20, 'HIGH',      '高',       3, 1);

-- ============================================================
-- Knot AI Gateway - Seed Data（幂等）
-- 可重复执行：主键/唯一键冲突时跳过（INSERT IGNORE）
--
-- 分区顺序（依赖自上而下，勿随意调整）：
--   1. 系统管理        用户 / 部门 / 角色 / 模块 / 菜单
--   2. 授权与权限      权限 -> API 绑定 -> 角色授权（ADMIN 全量在文件末尾权威块）
--   3. 供应商与模型
--   4. 应用管理
--   5. 路由规则
--   6. 计费规则
--   7. 安全与监控
--   8. 扩展能力        定时任务 / 插件 / 通知模板
--   9. 枚举字典        ks_enum_categories + ks_enum_configs
--  10. 权威授权块      ADMIN 全量授权，必须保持在文件最后
-- ============================================================

-- =========================
-- 系统管理
-- =========================

-- 用户 (password_hash = BCrypt('admin123'))
INSERT IGNORE INTO ks_users (id, username, password_hash, real_name, dept_id, status) VALUES
(1, 'admin', '$2a$10$HUYfxtiEgiRARR/fG46hEeAvcfcQ2WXMPh2NxPw5zkc06fDKeWkxi', '系统管理员', 1, 1),
(2, 'zhangsan', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '张三', 3, 1),
(3, 'lisi', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '李四', 2, 1);

-- 部门
INSERT IGNORE INTO ks_departments (id, dept_code, dept_name, parent_id, status, sort_order, remark) VALUES
(1, 'HQ',  '总部',     NULL, 1, 10, '默认管理部门'),
(2, 'RND', '研发部',   1,    1, 20, '负责模型与平台研发'),
(3, 'OPS', '运维部',   1,    1, 30, '负责网关运维与监控');

-- 角色
INSERT IGNORE INTO ks_roles (id, code, name, description) VALUES
(1, 'ADMIN', '管理员', '系统管理员，拥有全部权限'),
(2, 'OPERATOR', '运维人员', '负责网关运维监控'),
(3, 'DEVELOPER', '开发人员', '应用接入开发者');

-- 用户-角色
INSERT IGNORE INTO ks_user_roles (user_id, role_id) VALUES
(1, 1),
(2, 2),
(3, 3);

-- 模块（左侧一级菜单分组，ks_menus.module_id / ks_permissions.module_id 引用）
INSERT IGNORE INTO ks_modules (id, module_code, module_name, icon, sort_order, status) VALUES
(1, 'system', '系统管理', 'Setting', 90, 'ENABLED'),
(2, 'model', '模型管理', 'Box', 20, 'ENABLED'),
(3, 'routing', '路由管理', 'Share', 30, 'ENABLED'),
(4, 'billing', '计费管理', 'Coin', 40, 'ENABLED');

-- 菜单（menu_id 被权限与前端路由引用；按 module_id + sort_order 排列）
INSERT IGNORE INTO ks_menus (id, module_id, parent_id, menu_code, menu_name, route_path, component_key, icon, sort_order, status) VALUES
(1, 1, NULL, 'system.users', '用户管理', '/system/users', 'system/UserManageView', 'User', 10, 'ENABLED'),
(2, 1, NULL, 'system.departments', '部门管理', '/system/departments', 'system/DepartmentManageView', 'OfficeBuilding', 20, 'ENABLED'),
(3, 1, NULL, 'system.role-authorizations', '角色授权', '/system/role-authorizations', 'system/RoleAuthorizationManageView', 'Lock', 30, 'ENABLED'),
(18, 1, NULL, 'system.authorization-resources', '授权资源', '/system/authorization-resources', 'system/AuthorizationResourceManageView', 'Lock', 35, 'ENABLED'),
(4, 1, NULL, 'system.logs', '操作日志', '/system/logs', 'system/OperationLogView', 'Document', 40, 'ENABLED'),
(5, 1, NULL, 'system.settings', '用户设置', '/system/settings', 'system/UserSettingsView', 'Tools', 50, 'ENABLED'),
(14, 1, NULL, 'system.scheduled-tasks', '定时任务', '/system/scheduled-tasks', 'system/ScheduledTaskView', 'Timer', 60, 'ENABLED'),
(15, 1, NULL, 'system.enums', '枚举管理', '/system/enums', 'system/EnumManageView', 'List', 70, 'ENABLED'),
(16, 1, NULL, 'system.plugins', '插件管理', '/system/plugins', 'PluginManageView', 'Connection', 80, 'ENABLED'),
(17, 1, NULL, 'system.apps', '应用管理', '/apps', 'AppManageView', 'Grid', 90, 'ENABLED'),
(8, 2, NULL, 'model.model-pools', '模型池', '/model-management/model-pools', 'ModelPoolManageView', 'Cpu', 10, 'ENABLED'),
(7, 2, NULL, 'model.models', '供应商模型', '/model-management/models', 'ModelManageView', 'Cpu', 20, 'ENABLED'),
(6, 2, NULL, 'model.providers', '供应商账户', '/providers', 'ProviderManageView', 'Connection', 30, 'ENABLED'),
(9, 2, NULL, 'model.logical-models', '统一模型', '/model-management/logical-models', 'LogicalModelMarketplaceView', 'Cpu', 40, 'ENABLED'),
(10, 2, NULL, 'model.external-models', '外部模型', '/model-management/external-models', 'ExternalModelManageView', 'Cpu', 50, 'ENABLED'),
(19, 2, NULL, 'model.provider-profiles', '供应商信息', '/model-management/provider-profiles', 'ProviderProfileManageView', 'Connection', 60, 'ENABLED'),
(11, 3, NULL, 'routing.rules', '路由规则', '/routing/rules', 'routing/RoutingRuleView', 'Share', 10, 'ENABLED'),
(12, 3, NULL, 'routing.consumers', '消费者', '/routing/consumers', 'routing/RoutingConsumerView', 'Share', 20, 'ENABLED'),
(13, 4, NULL, 'billing.rules', '计费规则', '/billing/rules', 'billing/BillingRuleView', 'Coin', 10, 'ENABLED'),
(20, 4, NULL, 'billing.reports', '计费报表', '/billing/reports', 'billing/BillingReportView', 'Odometer', 20, 'ENABLED'),
(21, 4, NULL, 'billing.reconciliation', '计费对账', '/billing/reconciliation', 'billing/ReconciliationView', 'Document', 30, 'ENABLED');

-- =========================
-- 授权与权限
-- =========================
-- 默认拒绝：AdminAuthorizationInterceptor 按 ks_api_permission_bindings 判定，未绑定的接口一律 403。
-- 绑定条目由 .workbuddy/audit/gen_api_bindings.py 扫描 controller 生成，勿手工编辑；新增接口必须补绑定。
-- 顺序约束：ks_permissions -> ks_api_permission_bindings -> ks_role_permissions。

-- 权限（PAGE=页面访问，API=接口权限；新增权限在末尾追加 id）
INSERT IGNORE INTO ks_permissions (id, permission_code, permission_name, permission_type, module_id, menu_id, status, built_in, remark) VALUES
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
(18, 'system:settings:view', '查看当前用户设置与授权信息', 'API', 1, 5, 'ENABLED', 1, NULL),
(19, 'model:provider:page', '供应商页面访问', 'PAGE', 2, 6, 'ENABLED', 1, NULL),
(20, 'model:model:page', '供应商模型页面访问', 'PAGE', 2, 7, 'ENABLED', 1, NULL),
(21, 'model:model-pool:page', '模型池页面访问', 'PAGE', 2, 8, 'ENABLED', 1, NULL),
(22, 'model:logical-model:page', '统一模型页面访问', 'PAGE', 2, 9, 'ENABLED', 1, NULL),
(23, 'model:external-model:page', '外部模型页面访问', 'PAGE', 2, 10, 'ENABLED', 1, NULL),
(24, 'routing:rule:page', '路由规则页面访问', 'PAGE', 3, 11, 'ENABLED', 1, NULL),
(25, 'routing:consumer:page', '消费者页面访问', 'PAGE', 3, 12, 'ENABLED', 1, NULL),
(26, 'billing:rule:page', '计费规则页面访问', 'PAGE', 4, 13, 'ENABLED', 1, NULL),
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
(55, 'system:authorization-resource:page', '授权资源页面访问', 'PAGE', 1, 18, 'ENABLED', 1, NULL),
(56, 'system:user:reset-password', '重置用户密码', 'API', 1, 1, 'ENABLED', 1, NULL),
(57, 'model:provider-profile:page', '供应商信息页面访问', 'PAGE', 2, 19, 'ENABLED', 1, NULL),
(58, 'system:doc:view', '接口文档查看', 'API', 1, NULL, 'ENABLED', 1, NULL),
(59, 'system:app:create', '应用创建', 'API', 1, 17, 'ENABLED', 1, NULL),
(60, 'system:app:update', '应用更新', 'API', 1, 17, 'ENABLED', 1, NULL),
(61, 'system:app:delete', '应用删除', 'API', 1, 17, 'ENABLED', 1, NULL),
(62, 'system:authz:api-binding:enable', 'API 权限绑定更新状态', 'API', 1, 18, 'ENABLED', 1, NULL),
(63, 'system:authz:menu:enable', '授权菜单更新状态', 'API', 1, 18, 'ENABLED', 1, NULL),
(64, 'system:authz:module:enable', '授权模块更新状态', 'API', 1, 18, 'ENABLED', 1, NULL),
(65, 'system:authz:permission:enable', '授权权限更新状态', 'API', 1, 18, 'ENABLED', 1, NULL),
(66, 'system:authz:role:view', '授权角色查看', 'API', 1, 3, 'ENABLED', 1, NULL),
(67, 'billing:rule:view', '计费规则查看', 'API', 4, 13, 'ENABLED', 1, NULL),
(68, 'billing:rule:create', '计费规则创建', 'API', 4, 13, 'ENABLED', 1, NULL),
(69, 'billing:rule:update', '计费规则更新', 'API', 4, 13, 'ENABLED', 1, NULL),
(70, 'billing:rule:enable', '计费规则更新状态', 'API', 4, 13, 'ENABLED', 1, NULL),
(71, 'billing:rule:delete', '计费规则删除', 'API', 4, 13, 'ENABLED', 1, NULL),
(72, 'system:enum:create', '枚举创建', 'API', 1, 15, 'ENABLED', 1, NULL),
(73, 'system:enum:update', '枚举更新', 'API', 1, 15, 'ENABLED', 1, NULL),
(74, 'system:enum:delete', '枚举删除', 'API', 1, 15, 'ENABLED', 1, NULL),
(75, 'model:external-model:view', '外部模型查看', 'API', 2, 10, 'ENABLED', 1, NULL),
(76, 'model:external-model:sync', '外部模型同步', 'API', 2, 10, 'ENABLED', 1, NULL),
(77, 'model:external-model:create', '外部模型创建', 'API', 2, 10, 'ENABLED', 1, NULL),
(78, 'model:external-model:delete', '外部模型删除', 'API', 2, 10, 'ENABLED', 1, NULL),
(79, 'model:logical-model:view', '统一模型查看', 'API', 2, 9, 'ENABLED', 1, NULL),
(80, 'model:logical-model:create', '统一模型创建', 'API', 2, 9, 'ENABLED', 1, NULL),
(81, 'model:logical-model:update', '统一模型更新', 'API', 2, 9, 'ENABLED', 1, NULL),
(82, 'model:logical-model:enable', '统一模型更新状态', 'API', 2, 9, 'ENABLED', 1, NULL),
(83, 'model:logical-model:delete', '统一模型删除', 'API', 2, 9, 'ENABLED', 1, NULL),
(84, 'model:model:view', '供应商模型查看', 'API', 2, 7, 'ENABLED', 1, NULL),
(85, 'model:model:create', '供应商模型创建', 'API', 2, 7, 'ENABLED', 1, NULL),
(86, 'model:model:update', '供应商模型更新', 'API', 2, 7, 'ENABLED', 1, NULL),
(87, 'model:model:enable', '供应商模型更新状态', 'API', 2, 7, 'ENABLED', 1, NULL),
(88, 'model:model-pool:view', '模型池查看', 'API', 2, 8, 'ENABLED', 1, NULL),
(89, 'model:model-pool:create', '模型池创建', 'API', 2, 8, 'ENABLED', 1, NULL),
(90, 'model:model-pool:update', '模型池更新', 'API', 2, 8, 'ENABLED', 1, NULL),
(91, 'model:model-pool:enable', '模型池更新状态', 'API', 2, 8, 'ENABLED', 1, NULL),
(92, 'model:model-pool:delete', '模型池删除', 'API', 2, 8, 'ENABLED', 1, NULL),
(93, 'system:module-catalog:view', '模块目录查看', 'API', 1, 18, 'ENABLED', 1, NULL),
(94, 'system:notification:view', '通知查看', 'API', 1, NULL, 'ENABLED', 1, NULL),
(95, 'system:notification:create', '通知创建', 'API', 1, NULL, 'ENABLED', 1, NULL),
(96, 'system:notification:send', '通知发送', 'API', 1, NULL, 'ENABLED', 1, NULL),
(97, 'system:plugin:create', '插件创建', 'API', 1, 16, 'ENABLED', 1, NULL),
(98, 'system:plugin:enable', '插件更新状态', 'API', 1, 16, 'ENABLED', 1, NULL),
(99, 'model:provider:view', '供应商账户查看', 'API', 2, 6, 'ENABLED', 1, NULL),
(100, 'model:provider:create', '供应商账户创建', 'API', 2, 6, 'ENABLED', 1, NULL),
(101, 'model:provider:update', '供应商账户更新', 'API', 2, 6, 'ENABLED', 1, NULL),
(102, 'model:provider:enable', '供应商账户更新状态', 'API', 2, 6, 'ENABLED', 1, NULL),
(103, 'model:provider-profile:view', '供应商信息查看', 'API', 2, 19, 'ENABLED', 1, NULL),
(104, 'model:provider-profile:create', '供应商信息创建', 'API', 2, 19, 'ENABLED', 1, NULL),
(105, 'model:provider-profile:update', '供应商信息更新', 'API', 2, 19, 'ENABLED', 1, NULL),
(106, 'model:provider-profile:delete', '供应商信息删除', 'API', 2, 19, 'ENABLED', 1, NULL),
(107, 'routing:consumer:view', '消费者查看', 'API', 3, 12, 'ENABLED', 1, NULL),
(108, 'routing:consumer:create', '消费者创建', 'API', 3, 12, 'ENABLED', 1, NULL),
(109, 'routing:consumer:update', '消费者更新', 'API', 3, 12, 'ENABLED', 1, NULL),
(110, 'routing:consumer:enable', '消费者更新状态', 'API', 3, 12, 'ENABLED', 1, NULL),
(111, 'routing:consumer:rotate-secret', '消费者轮换密钥', 'API', 3, 12, 'ENABLED', 1, NULL),
(112, 'routing:rule:view', '路由规则查看', 'API', 3, 11, 'ENABLED', 1, NULL),
(113, 'routing:rule:create', '路由规则创建', 'API', 3, 11, 'ENABLED', 1, NULL),
(114, 'routing:rule:update', '路由规则更新', 'API', 3, 11, 'ENABLED', 1, NULL),
(115, 'routing:rule:enable', '路由规则更新状态', 'API', 3, 11, 'ENABLED', 1, NULL),
(116, 'routing:rule:test', '路由规则测试', 'API', 3, 11, 'ENABLED', 1, NULL),
(117, 'system:scheduled-task:create', '定时任务创建', 'API', 1, 14, 'ENABLED', 1, NULL),
(118, 'system:scheduled-task:update', '定时任务更新', 'API', 1, 14, 'ENABLED', 1, NULL),
(119, 'system:scheduled-task:trigger', '定时任务触发', 'API', 1, 14, 'ENABLED', 1, NULL),
(120, 'system:security:view', '安全策略查看', 'API', 1, NULL, 'ENABLED', 1, NULL),
(121, 'system:security:update', '安全策略更新', 'API', 1, NULL, 'ENABLED', 1, NULL),
(122, 'system:security:evict', '安全策略清理缓存', 'API', 1, NULL, 'ENABLED', 1, NULL),
(123, 'model:external-model:ignore', '外部模型忽略', 'API', 2, 10, 'ENABLED', 1, NULL),
(124, 'billing:report:page', '计费报表页面访问', 'PAGE', 4, 20, 'ENABLED', 1, NULL),
(125, 'billing:report:view', '计费报表查看', 'API', 4, 20, 'ENABLED', 1, NULL),
(126, 'billing:reconciliation:page', '计费对账页面访问', 'PAGE', 4, 21, 'ENABLED', 1, NULL),
(127, 'billing:rule:preview', '计费规则方案试算', 'API', 4, 13, 'ENABLED', 1, NULL),
(128, 'routing:preset:view', '预设请求查看', 'API', 3, 11, 'ENABLED', 1, NULL),
(129, 'routing:preset:create', '预设请求创建', 'API', 3, 11, 'ENABLED', 1, NULL),
(130, 'routing:preset:update', '预设请求更新', 'API', 3, 11, 'ENABLED', 1, NULL),
(131, 'routing:preset:delete', '预设请求删除', 'API', 3, 11, 'ENABLED', 1, NULL),
(132, 'routing:preset:enable', '预设请求更新状态', 'API', 3, 11, 'ENABLED', 1, NULL);

-- API 权限绑定（默认拒绝：未绑定接口一律 403；由 .workbuddy/audit/gen_api_bindings.py 生成）
INSERT IGNORE INTO ks_api_permission_bindings (id, permission_id, http_method, path_pattern, controller_class, status) VALUES
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
(13, 15, 'POST', '/api/system/operation-logs/{id}', 'SystemController', 'ENABLED'),
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
(52, 53, 'PUT', '/api/system/authorizations/api-bindings/{id}/status', 'AuthorizationApiBindingController', 'ENABLED'),
(53, 56, 'PUT', '/api/users/{id}/reset-password', 'UserController', 'ENABLED'),
(54, 58, 'POST', '/api/docs/openapi.json', 'ApiDocController', 'ENABLED'),
(55, 58, 'POST', '/api/docs/changelog', 'ApiDocController', 'ENABLED'),
(56, 59, 'POST', '/api/apps', 'AppController', 'ENABLED'),
(57, 60, 'PUT', '/api/apps/{id}', 'AppController', 'ENABLED'),
(58, 61, 'DELETE', '/api/apps/{id}', 'AppController', 'ENABLED'),
(59, 67, 'POST', '/api/billing/rules', 'BillingController', 'ENABLED'),
(60, 67, 'GET', '/api/billing/mode-capabilities', 'BillingController', 'ENABLED'),
(61, 68, 'POST', '/api/billing', 'BillingController', 'ENABLED'),
(62, 69, 'PUT', '/api/billing/rules/{id}', 'BillingController', 'ENABLED'),
(63, 70, 'PUT', '/api/billing/rules/{id}/status', 'BillingController', 'ENABLED'),
(64, 71, 'DELETE', '/api/billing/rules/{id}', 'BillingController', 'ENABLED'),
(65, 67, 'POST', '/api/billing/reconciliation', 'BillingController', 'ENABLED'),
(66, 30, 'POST', '/api/system/enums/list', 'EnumConfigController', 'ENABLED'),
(67, 30, 'POST', '/api/system/enums/category-summaries', 'EnumConfigController', 'ENABLED'),
(68, 30, 'GET', '/api/system/enums/items/{category}', 'EnumConfigController', 'ENABLED'),
(69, 30, 'GET', '/api/system/enums/operation-logs/{category}', 'EnumConfigController', 'ENABLED'),
(70, 30, 'POST', '/api/system/enums/categories', 'EnumConfigController', 'ENABLED'),
(71, 72, 'POST', '/api/system/enums', 'EnumConfigController', 'ENABLED'),
(72, 73, 'PUT', '/api/system/enums/{id}', 'EnumConfigController', 'ENABLED'),
(73, 74, 'DELETE', '/api/system/enums/{id}', 'EnumConfigController', 'ENABLED'),
(74, 75, 'GET', '/api/external-models/sources', 'ExternalModelController', 'ENABLED'),
(75, 75, 'POST', '/api/external-models/items/list', 'ExternalModelController', 'ENABLED'),
(76, 75, 'GET', '/api/external-models/items/{id}', 'ExternalModelController', 'ENABLED'),
(77, 76, 'POST', '/api/external-models/sources/{sourceCode}/sync', 'ExternalModelController', 'ENABLED'),
(78, 77, 'POST', '/api/external-models/items/{id}/logical-model', 'ExternalModelController', 'ENABLED'),
(79, 75, 'POST', '/api/external-models/items/logical-models', 'ExternalModelController', 'ENABLED'),
(80, 78, 'DELETE', '/api/external-models/items/{id}', 'ExternalModelController', 'ENABLED'),
(81, 78, 'POST', '/api/external-models/items/batch-delete', 'ExternalModelController', 'ENABLED'),
(82, 79, 'GET', '/api/logical-models/check-code', 'LogicalModelController', 'ENABLED'),
(83, 79, 'GET', '/api/logical-models/{id}', 'LogicalModelController', 'ENABLED'),
(84, 79, 'POST', '/api/logical-models/list', 'LogicalModelController', 'ENABLED'),
(85, 80, 'POST', '/api/logical-models', 'LogicalModelController', 'ENABLED'),
(86, 81, 'PUT', '/api/logical-models/{id}', 'LogicalModelController', 'ENABLED'),
(87, 82, 'PUT', '/api/logical-models/{id}/status', 'LogicalModelController', 'ENABLED'),
(88, 83, 'DELETE', '/api/logical-models/{id}', 'LogicalModelController', 'ENABLED'),
(89, 84, 'GET', '/api/models/check-code', 'ModelController', 'ENABLED'),
(90, 84, 'GET', '/api/models/usage-extractors', 'ModelController', 'ENABLED'),
(91, 84, 'GET', '/api/models/request-adapters', 'ModelController', 'ENABLED'),
(92, 84, 'GET', '/api/models/api-protocols', 'ModelController', 'ENABLED'),
(93, 84, 'GET', '/api/models/types', 'ModelController', 'ENABLED'),
(94, 84, 'GET', '/api/models/{id}', 'ModelController', 'ENABLED'),
(95, 84, 'POST', '/api/models/list', 'ModelController', 'ENABLED'),
(96, 85, 'POST', '/api/models', 'ModelController', 'ENABLED'),
(97, 86, 'PUT', '/api/models/{id}', 'ModelController', 'ENABLED'),
(98, 87, 'PUT', '/api/models/{id}/status', 'ModelController', 'ENABLED'),
(99, 88, 'GET', '/api/model-pools/check-code', 'ModelPoolController', 'ENABLED'),
(100, 88, 'GET', '/api/model-pools/{id}', 'ModelPoolController', 'ENABLED'),
(101, 88, 'POST', '/api/model-pools/list', 'ModelPoolController', 'ENABLED'),
(102, 89, 'POST', '/api/model-pools', 'ModelPoolController', 'ENABLED'),
(103, 90, 'PUT', '/api/model-pools/{id}', 'ModelPoolController', 'ENABLED'),
(104, 91, 'PUT', '/api/model-pools/{id}/status', 'ModelPoolController', 'ENABLED'),
(105, 92, 'DELETE', '/api/model-pools/{id}', 'ModelPoolController', 'ENABLED'),
(106, 93, 'POST', '/api/modules', 'ModuleCatalogController', 'ENABLED'),
(107, 94, 'POST', '/api/notifications/templates/list', 'NotificationController', 'ENABLED'),
(108, 95, 'POST', '/api/notifications/templates', 'NotificationController', 'ENABLED'),
(109, 96, 'POST', '/api/notifications/send', 'NotificationController', 'ENABLED'),
(110, 95, 'POST', '/api/notifications/policies', 'NotificationController', 'ENABLED'),
(111, 97, 'POST', '/api/plugins', 'PluginController', 'ENABLED'),
(112, 98, 'PUT', '/api/plugins/{id}/status', 'PluginController', 'ENABLED'),
(113, 99, 'POST', '/api/provider-accounts/list', 'ProviderController', 'ENABLED'),
(114, 99, 'GET', '/api/provider-accounts/credential-types', 'ProviderController', 'ENABLED'),
(115, 99, 'GET', '/api/provider-accounts/suggest-code', 'ProviderController', 'ENABLED'),
(116, 99, 'GET', '/api/provider-accounts/check-code', 'ProviderController', 'ENABLED'),
(117, 99, 'GET', '/api/provider-accounts/options/{id}', 'ProviderController', 'ENABLED'),
(118, 99, 'GET', '/api/provider-accounts/{id}', 'ProviderController', 'ENABLED'),
(119, 100, 'POST', '/api/provider-accounts', 'ProviderController', 'ENABLED'),
(120, 101, 'PUT', '/api/provider-accounts/{id}', 'ProviderController', 'ENABLED'),
(121, 102, 'PUT', '/api/provider-accounts/{id}/status', 'ProviderController', 'ENABLED'),
(122, 99, 'POST', '/api/provider-accounts/{id}/discount-policies/list', 'ProviderController', 'ENABLED'),
(123, 100, 'POST', '/api/provider-accounts/{id}/discount-policies', 'ProviderController', 'ENABLED'),
(124, 101, 'PUT', '/api/provider-accounts/{id}/discount-policies/{policyId}', 'ProviderController', 'ENABLED'),
(125, 103, 'POST', '/api/provider-profiles/list', 'ProviderProfileController', 'ENABLED'),
(126, 103, 'GET', '/api/provider-profiles/{id}', 'ProviderProfileController', 'ENABLED'),
(127, 103, 'GET', '/api/provider-profiles/check-code', 'ProviderProfileController', 'ENABLED'),
(128, 104, 'POST', '/api/provider-profiles', 'ProviderProfileController', 'ENABLED'),
(129, 105, 'PUT', '/api/provider-profiles/{id}', 'ProviderProfileController', 'ENABLED'),
(130, 106, 'DELETE', '/api/provider-profiles/{id}', 'ProviderProfileController', 'ENABLED'),
(131, 107, 'POST', '/api/routing-consumers/list', 'RoutingConsumerController', 'ENABLED'),
(132, 107, 'GET', '/api/routing-consumers/check-code', 'RoutingConsumerController', 'ENABLED'),
(133, 108, 'POST', '/api/routing-consumers', 'RoutingConsumerController', 'ENABLED'),
(134, 109, 'PUT', '/api/routing-consumers/{id}', 'RoutingConsumerController', 'ENABLED'),
(135, 110, 'PUT', '/api/routing-consumers/{id}/status', 'RoutingConsumerController', 'ENABLED'),
(136, 111, 'POST', '/api/routing-consumers/{id}/rotate-secret', 'RoutingConsumerController', 'ENABLED'),
(137, 112, 'GET', '/api/routing-rules/debug-capabilities', 'RoutingRuleController', 'ENABLED'),
(138, 112, 'POST', '/api/routing-rules/list', 'RoutingRuleController', 'ENABLED'),
(139, 112, 'GET', '/api/routing-rules/check-code', 'RoutingRuleController', 'ENABLED'),
(140, 113, 'POST', '/api/routing-rules', 'RoutingRuleController', 'ENABLED'),
(141, 114, 'PUT', '/api/routing-rules/{id}', 'RoutingRuleController', 'ENABLED'),
(142, 115, 'PUT', '/api/routing-rules/{id}/status', 'RoutingRuleController', 'ENABLED'),
(143, 116, 'POST', '/api/routing-rules/{id}/test', 'RoutingRuleController', 'ENABLED'),
(144, 117, 'POST', '/api/system/scheduled-tasks', 'ScheduledTaskController', 'ENABLED'),
(145, 118, 'PUT', '/api/system/scheduled-tasks/{id}', 'ScheduledTaskController', 'ENABLED'),
(146, 119, 'POST', '/api/system/scheduled-tasks/{id}/trigger', 'ScheduledTaskController', 'ENABLED'),
(147, 28, 'POST', '/api/system/scheduled-tasks/runs', 'ScheduledTaskController', 'ENABLED'),
(148, 28, 'POST', '/api/system/scheduled-tasks/handlers', 'ScheduledTaskController', 'ENABLED'),
(149, 120, 'POST', '/api/security/overview', 'SecurityController', 'ENABLED'),
(150, 121, 'PUT', '/api/security/policies', 'SecurityController', 'ENABLED'),
(151, 120, 'POST', '/api/security/alerts', 'SecurityController', 'ENABLED'),
(152, 122, 'POST', '/api/security/cache/evict', 'SecurityController', 'ENABLED'),
(153, 15, 'POST', '/api/system/log-types', 'SystemController', 'ENABLED'),
(154, 15, 'POST', '/api/system/logs', 'SystemController', 'ENABLED'),
(155, 123, 'POST', '/api/external-models/items/{id}/ignored', 'ExternalModelController', 'ENABLED'),
(156, 125, 'GET', '/api/billing/report/summary', 'BillingController', 'ENABLED'),
(157, 127, 'POST', '/api/billing/rules/{id}/preview', 'BillingController', 'ENABLED'),
(158, 128, 'POST', '/api/test-request-presets/list', 'TestRequestPresetController', 'ENABLED'),
(159, 129, 'POST', '/api/test-request-presets', 'TestRequestPresetController', 'ENABLED'),
(160, 130, 'PUT', '/api/test-request-presets/{id}', 'TestRequestPresetController', 'ENABLED'),
(161, 131, 'DELETE', '/api/test-request-presets/{id}', 'TestRequestPresetController', 'ENABLED'),
(162, 128, 'GET', '/api/test-request-presets/options', 'TestRequestPresetController', 'ENABLED'),
(163, 132, 'PUT', '/api/test-request-presets/{id}/status', 'TestRequestPresetController', 'ENABLED');

-- 角色授权（OPERATOR 全量 / DEVELOPER 只读；ADMIN 由文件末尾权威块全量授予）
INSERT IGNORE INTO ks_role_permissions (role_id, permission_id) VALUES
(2, 1),(2, 2),(2, 6),(2, 7),(2, 14),(2, 15),(2, 16),(2, 17),
(2, 18),(2, 19),(2, 20),(2, 21),(2, 22),(2, 23),(2, 24),(2, 25),
(2, 26),(2, 27),(2, 28),(2, 29),(2, 30),(2, 31),(2, 32),(2, 33),
(2, 34),(2, 35),(2, 36),(2, 37),(2, 38),(2, 39),(2, 40),(2, 41),
(2, 42),(2, 43),(2, 44),(2, 45),(2, 46),(2, 47),(2, 48),(2, 49),
(2, 50),(2, 51),(2, 52),(2, 53),(2, 54),(2, 55),(2, 57),(3, 57),
(2, 58),(2, 59),(2, 60),(2, 61),(2, 62),(2, 63),(2, 64),(2, 65),
(2, 66),(2, 67),(2, 68),(2, 69),(2, 70),(2, 71),(2, 72),(2, 73),
(2, 74),(2, 75),(2, 76),(2, 77),(2, 78),(2, 79),(2, 80),(2, 81),
(2, 82),(2, 83),(2, 84),(2, 85),(2, 86),(2, 87),(2, 88),(2, 89),
(2, 90),(2, 91),(2, 92),(2, 93),(2, 94),(2, 95),(2, 96),(2, 97),
(2, 98),(2, 99),(2, 100),(2, 101),(2, 102),(2, 103),(2, 104),(2, 105),
(2, 106),(2, 107),(2, 108),(2, 109),(2, 110),(2, 111),(2, 112),(2, 113),
(2, 114),(2, 115),(2, 116),(2, 117),(2, 118),(2, 119),(2, 120),(2, 121),
(2, 122),(2, 123),(2, 124),(2, 125),(2, 126),(2, 127),(2, 128),(2, 129),
(2, 130),(2, 131),(2, 132),(3, 14),(3, 15),(3, 16),(3, 17),(3, 18),
(3, 19),(3, 20),(3, 21),(3, 22),(3, 23),(3, 24),(3, 25),(3, 26),
(3, 58),(3, 66),(3, 67),(3, 75),(3, 79),(3, 84),(3, 88),(3, 93),
(3, 94),(3, 99),(3, 103),(3, 107),(3, 112),(3, 120),(3, 124),(3, 125),
(3, 126),(3, 127),(3, 128);

-- 存量库修补：把「授权资源页面访问」补给已持有「角色授权页面访问」(id=12) 的角色。
-- 注意：ADMIN 的 55 由文件末尾的权威授权块保证，不依赖本句。
INSERT IGNORE INTO ks_role_permissions (role_id, permission_id)
SELECT role_id, 55
FROM ks_role_permissions
WHERE permission_id = 12;

-- =========================
-- 供应商与模型
-- =========================

-- 供应商
-- 供应商信息 = 供应商类型的主数据（供应商账户通过 provider_code 关联）
-- tag 语义为供应商分类：原厂 / 云厂商 / 代理
INSERT IGNORE INTO kb_providers (id, code, name, tag) VALUES
(1, 'openai',       'OpenAI',       '原厂'),
(2, 'anthropic',    'Anthropic',    '原厂'),
(3, 'deepseek',     'DeepSeek',     '原厂'),
(4, 'qwen',         'Qwen',         '原厂'),
(5, 'zhipu',        'Zhipu',        '原厂'),
(6, 'openrouter',   'OpenRouter',   '云厂商');

INSERT IGNORE INTO kb_provider_accounts (id, provider_code, code, status) VALUES
(1, 'openai',    'openai-default',    'ENABLED'),
(2, 'anthropic', 'anthropic-default', 'ENABLED'),
(3, 'deepseek',  'deepseek-default',  'ENABLED'),
(4, 'qwen',      'qwen-default',      'ENABLED');

-- 频控/额度策略（独立表 + 资源绑定）
INSERT IGNORE INTO kb_rate_limit_policies (id, policy_code, policy_name, per_second, per_minute, time_window, status) VALUES
(1, 'PROVIDER-1-RL', 'PROVIDER #1 频控', 100, 5000, 'MINUTE', 'ACTIVE'),
(2, 'PROVIDER-2-RL', 'PROVIDER #2 频控', 50,  2000, 'MINUTE', 'ACTIVE'),
(3, 'PROVIDER-3-RL', 'PROVIDER #3 频控', 30,  1000, 'MINUTE', 'ACTIVE'),
(4, 'APP-1-RL',      'APP #1 频控',      10,  600,  'MINUTE', 'ACTIVE'),
(5, 'APP-2-RL',      'APP #2 频控',      5,   300,  'MINUTE', 'ACTIVE');

INSERT IGNORE INTO kb_quota_policies (id, policy_code, policy_name, daily_limit, monthly_limit, token_limit, alert_enabled, status) VALUES
(1, 'PROVIDER-1-QT', 'PROVIDER #1 额度', 1000000, 30000000, 500000000, 1, 'ACTIVE'),
(2, 'PROVIDER-2-QT', 'PROVIDER #2 额度', 500000,  15000000, 200000000, 1, 'ACTIVE'),
(3, 'PROVIDER-3-QT', 'PROVIDER #3 额度', 200000,  6000000,  100000000, 0, 'ACTIVE'),
(4, 'APP-1-QT',      'APP #1 额度',      10000,   300000,   5000000,   1, 'ACTIVE'),
(5, 'APP-2-QT',      'APP #2 额度',      5000,    150000,   2000000,   1, 'ACTIVE');

INSERT IGNORE INTO kb_resource_traffic_policies (id, resource_type, resource_id, rate_limit_policy_id, quota_policy_id) VALUES
(1, 'PROVIDER', 1, 1, 1),
(2, 'PROVIDER', 2, 2, 2),
(3, 'PROVIDER', 3, 3, 3),
(6, 'PROVIDER', 4, 3, 3),
(4, 'APP',      1, 4, 4),
(5, 'APP',      2, 5, 5);

-- 供应商凭证（认证配置整体以 AES-GCM 密文保存；前缀 ENC:）
-- 密文解密后必须是 JSON 对象形态（如 {"apiKey":"sk-..."}），与 ProviderCredentialSupport.saveAuthConfig
-- 的写入契约一致；旧版种子密文存的是裸密钥字符串，会导致 JsonKit.fromJson 解析失败并静默降级为空 apiKey。
-- 注意：密文由 KNOT_CREDENTIAL_ENCRYPTION_KEY 派生密钥加密，更换密钥后必须重新生成（见
-- docs/database/migration/2026-09-23-fix-provider-credential-config-format.sql 的生成说明）。
INSERT IGNORE INTO kb_provider_credentials (id, provider_account_id, credential_type, encrypted_config, status) VALUES
(1, 1, 'api-key', 'ENC:Gxdt09+Mk+BnH3+meLh03rvVO/lr/5vjxcjK0RNW0PgB0X/vYX0QzaixD9xPh/gKVYTC/0JSNl4lU7zy+g==', 'ACTIVE'),
(2, 2, 'api-key', 'ENC:v3v8kgxDLYoAKyNICNxGKU655xOH3Sd1k2qtSS+bgYcvtq/fXGyufZ1gA+Qr2LMBT1eRTJ6teFCPY5r/OvTjqw==', 'ACTIVE'),
(3, 3, 'api-key', 'ENC:pnMjBuMkVI19v432F8xRJ1U976XGPXwM19DakGTc8GoKMEvIy/6QcOMUbwwsRbXMJ4kqXsY+X+sKrnBuuijw', 'ACTIVE');

-- 供应商折扣策略
INSERT IGNORE INTO kb_provider_discount_policies (id, provider_account_id, policy_name, scope_type, scope_ref_id, discount_type, discount_value, priority, effective_from, status) VALUES
(1, 1, '新用户9折',   'GLOBAL',   NULL, 'PERCENTAGE', 0.9000, 100, NOW(), 'ACTIVE'),
(2, 2, '企业客户8折', 'GLOBAL',   NULL, 'PERCENTAGE', 0.8000, 90,  NOW(), 'ACTIVE'),
(3, 3, '直减5元',     'GLOBAL',   NULL, 'FIXED',      5.0000, 100, NOW(), 'ACTIVE');

-- 模型
INSERT IGNORE INTO kb_models (id, provider_account_code, model_code, version, base_url, status) VALUES
(1,  'openai-default',    'gpt-4o',            '2024-08-06', 'https://api.openai.com', 'ENABLED'),
(2,  'openai-default',    'gpt-4o-mini',       '2024-07-18', 'https://api.openai.com', 'ENABLED'),
(3,  'openai-default',    'text-embedding-3-large','2024-01-01','https://api.openai.com','ENABLED'),
(4,  'anthropic-default', 'claude-sonnet-4-20250514','2025-05-14','https://api.anthropic.com','ENABLED'),
(5,  'anthropic-default', 'claude-haiku-3-5-20241022','2024-10-22','https://api.anthropic.com','ENABLED'),
(6,  'deepseek-default',  'deepseek-chat',     '2024-08-01', 'https://api.deepseek.com', 'ENABLED'),
(7,  'deepseek-default',  'deepseek-reasoner', '2025-01-20', 'https://api.deepseek.com', 'ENABLED'),
(8,  'qwen-default',      'qwen-image',        '2025-08-01', 'https://dashscope.aliyuncs.com', 'ENABLED'),
(9,  'qwen-default',      'qwen-image-edit',   '2025-08-01', 'https://dashscope.aliyuncs.com', 'ENABLED'),
(10, 'openai-default',    'gpt-image-1',       '2025-04-01', 'https://api.openai.com', 'ENABLED');

INSERT IGNORE INTO kb_model_pools (id, pool_code, name, model_type, selection_strategy, status, remark) VALUES
(1, 'chat-premium-pool', 'Premium Chat Pool', 'CHAT', 'WEIGHTED', 'ENABLED', 'Premium chat routing pool'),
(2, 'chat-economy-pool', 'Economy Chat Pool', 'CHAT', 'WEIGHTED', 'ENABLED', 'Economy chat routing pool');

INSERT IGNORE INTO kb_model_pool_items (id, pool_code, model_code, weight, priority, status) VALUES
(1, 'chat-premium-pool', 'gpt-4o',                  70, 10, 'ENABLED'),
(2, 'chat-premium-pool', 'claude-sonnet-4-20250514', 30, 20, 'ENABLED'),
(3, 'chat-economy-pool', 'gpt-4o-mini',             60, 10, 'ENABLED'),
(4, 'chat-economy-pool', 'deepseek-chat',           40, 20, 'ENABLED');

INSERT IGNORE INTO kb_logical_models (
  id, model_code, model_name, model_type, model_family, display_name, description,
  tags_json, use_cases_json, context_window, max_output_tokens,
  input_modalities_json, output_modalities_json, languages_json,
  visibility, publish_status, status, sort_order, featured,
  remark
) VALUES
(1, 'knot-chat-premium', 'Knot Chat Premium', 'CHAT', NULL,
 'Knot Chat Premium',
 'A logical chat model that routes to premium provider models by policy.',
 JSON_ARRAY('chat', 'reasoning', 'premium'),
 JSON_ARRAY('knowledge assistant', 'research', 'complex analysis'),
 200000, 8192, JSON_ARRAY('text', 'image'), JSON_ARRAY('text'), JSON_ARRAY('zh-CN', 'en-US'),
  'PUBLIC', 'PUBLISHED', 'ENABLED', 10, 1, 'Premium provider route'),
(2, 'knot-chat-economy', 'Knot Chat Economy', 'CHAT', NULL,
 'Knot Chat Economy',
 'A logical chat model that routes to economical provider models.',
 JSON_ARRAY('chat', 'economy'),
 JSON_ARRAY('customer service', 'daily assistant'),
 128000, 4096, JSON_ARRAY('text'), JSON_ARRAY('text'), JSON_ARRAY('zh-CN', 'en-US'),
  'PUBLIC', 'PUBLISHED', 'ENABLED', 20, 0, 'Economy provider route');

INSERT IGNORE INTO kb_provider_model_mappings (
  id, logical_model_code, provider_account_code, model_id, provider_model_name, status, priority
) VALUES
(1, 'knot-chat-premium', 'openai-default',    1, 'gpt-4o', 'ENABLED', 10),
(2, 'knot-chat-premium', 'anthropic-default', 4, 'claude-sonnet-4-20250514', 'ENABLED', 20),
(3, 'knot-chat-economy', 'openai-default',    2, 'gpt-4o-mini', 'ENABLED', 10),
(4, 'knot-chat-economy', 'deepseek-default',  6, 'deepseek-chat', 'ENABLED', 20);

INSERT IGNORE INTO kx_model_sources (
  id, source_code, source_name, source_url, api_url, source_type, status
) VALUES
(1, 'OPENROUTER', 'OpenRouter Models', 'https://openrouter.ai/models',
 'https://openrouter.ai/api/v1/models', 'MODEL_CATALOG', 'ENABLED');

-- 模型 API 协议绑定（usage_extractor 为 Usage 解析器编码或类名）
INSERT IGNORE INTO kb_model_api_bindings (id, model_id, protocol, api_path, request_adapter, usage_extractor, status, remark) VALUES
(1, 1, 'CHAT_COMPLETIONS', '/v1/chat/completions', 'OPENAI_COMPATIBLE', 'DEFAULT', 'ENABLED', 'GPT-4o Chat Completions'),
(2, 2, 'CHAT_COMPLETIONS', '/v1/chat/completions', 'OPENAI_COMPATIBLE', 'DEFAULT', 'ENABLED', 'GPT-4o Mini Chat Completions'),
(3, 4, 'MESSAGES', '/v1/messages', 'ANTHROPIC', 'ANTHROPIC', 'ENABLED', 'Claude Sonnet Messages API'),
(4, 6, 'CHAT_COMPLETIONS', '/v1/chat/completions', 'OPENAI_COMPATIBLE', 'DEFAULT', 'ENABLED', 'DeepSeek Chat'),
(5, 8, 'IMAGE_GENERATIONS', '/api/v1/services/aigc/multimodal-generation/generation', 'QWEN', 'DEFAULT', 'ENABLED', 'Qwen Image Generation'),
(6, 9, 'IMAGE_EDITS', '/api/v1/services/aigc/multimodal-generation/generation', 'QWEN', 'DEFAULT', 'ENABLED', 'Qwen Image Edit'),
(7, 10, 'IMAGE_GENERATIONS', '/v1/images/generations', 'OPENAI_COMPATIBLE', 'DEFAULT', 'ENABLED', 'OpenAI Image Generation'),
(8, 10, 'IMAGE_EDITS', '/v1/images/edits', 'OPENAI_COMPATIBLE', 'DEFAULT', 'ENABLED', 'OpenAI Image Edit');

-- =========================
-- 应用管理
-- =========================

-- 应用
INSERT IGNORE INTO kb_apps (id, app_code, name, dept_id, owner_user_id, remark, status) VALUES
(1, 'app_001', '内部知识库助手',   1, 1, '面向内部员工的知识检索与问答', 'ENABLED'),
(2, 'app_002', '客服对话系统',     3, 2, '对外客服场景的对话接入',       'ENABLED'),
(3, 'app_003', '代码审查工具',     2, 3, '研发流程中的代码审查辅助',     'ENABLED');

-- 应用凭证
INSERT IGNORE INTO kb_app_credentials (id, app_code, app_key, app_secret_hash, status) VALUES
(1, 'app_001', 'knot_pk_001', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'ACTIVE'),
(2, 'app_002', 'knot_pk_002', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'ACTIVE'),
(3, 'app_003', 'knot_pk_003', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'ACTIVE');

-- 应用-模型权限
INSERT IGNORE INTO kb_app_model_permissions (app_id, model_id) VALUES
(1,1),(1,2),(1,4),(1,6),(1,10),
(2,1),(2,2),(2,4),(2,5),(2,10),
(3,1),(3,2),(3,6),(3,7),(3,10);

-- =========================
-- 路由规则
-- =========================

INSERT IGNORE INTO kb_routing_consumers (id, consumer_code, name, user_id, secret_key, return_usage_detail, status) VALUES
(1, 'consumer-internal-kb', '内部知识库助手消费者', 1, 'sk-demo-gpt4o-routing-key-001', 0, 'ENABLED'),
(2, 'consumer-research',    '模型评测消费者',       1, 'sk-demo-claude-routing-key-002', 0, 'ENABLED'),
(3, 'consumer-cs',          '客服系统消费者',       2, 'sk-demo-deepseek-routing-key-003', 0, 'ENABLED');

INSERT IGNORE INTO kb_routing_rules (id, rule_code, name, app_scenario, model_types, app_id, user_id, status) VALUES
(1, 'gpt4o-default',    'GPT-4o默认路由',    '知识库问答', 'CHAT', 1, 1, 'ENABLED'),
(2, 'claude-default',   'Claude默认路由',    '模型评测',   'CHAT', 1, 1, 'ENABLED'),
(3, 'deepseek-lowcost', 'DeepSeek低成本路由', '客服对话',   'CHAT', 2, 2, 'ENABLED');

INSERT IGNORE INTO kb_routing_rule_consumers (id, rule_id, consumer_id) VALUES
(1, 1, 1),
(2, 2, 2),
(3, 3, 3);

INSERT IGNORE INTO kb_routing_rule_targets (id, rule_id, target_type, target_id, priority, is_primary) VALUES
(1, 1, 'MODEL_POOL', 1, 100, 1),
(2, 1, 'MODEL',      2, 90,  0),
(3, 2, 'MODEL',      4, 100, 1),
(4, 3, 'MODEL_POOL', 2, 100, 1);

-- =========================
-- 计费规则
-- =========================

-- 两表模型：规则主体只存身份与绑定；价格配置全部进版本 config_json
-- model_family 取 ks_enum_configs(category='model_family') 的 item_code；NULL = 默认规则，覆盖所有族
INSERT IGNORE INTO kb_billing_rules (id, code, model_family, status) VALUES
(1, 'TOKEN_GPT4O',      'gpt',      'ACTIVE'),
(2, 'TOKEN_GPT4O_MINI', 'gpt',      'ACTIVE'),
(3, 'TOKEN_CLAUDE_S4',  'claude',   'ACTIVE'),
(4, 'TOKEN_DEEPSEEK',   'deepseek', 'ACTIVE'),
(5, 'EMBEDDING',        NULL,       'ACTIVE');

INSERT IGNORE INTO kb_billing_rule_versions (id, rule_id, version_code, uniq_hash, billing_mode, pricing_plan, currency, unit, config_json, status, effective_from) VALUES
(1, 1, 'v1', 'seed-token-gpt4o-v1',      'TOKEN',     'FIXED', 'USD', '1K_TOKENS', '{"basePrices":{"cacheRead":0.00125,"cacheWrite":0.005,"input":0.005,"output":0.015}}', 'ACTIVE', NOW()),
(2, 2, 'v1', 'seed-token-gpt4o-mini-v1', 'TOKEN',     'FIXED', 'USD', '1K_TOKENS', '{"basePrices":{"cacheRead":0.000025,"cacheWrite":0.00015,"input":0.00015,"output":0.0006}}', 'ACTIVE', NOW()),
(3, 3, 'v1', 'seed-token-claude-s4-v1',  'TOKEN',     'FIXED', 'USD', '1K_TOKENS', '{"basePrices":{"cacheRead":0.0003,"cacheWrite":0.00375,"input":0.003,"output":0.015}}', 'ACTIVE', NOW()),
(4, 4, 'v1', 'seed-token-deepseek-v1',   'TOKEN',     'FIXED', 'USD', '1K_TOKENS', '{"basePrices":{"cacheRead":0.000014,"cacheWrite":0.00014,"input":0.00014,"output":0.00028}}', 'ACTIVE', NOW()),
(5, 5, 'v1', 'seed-embedding-v1',        'EMBEDDING', 'FIXED', 'USD', '1K_TOKENS', '{"defaultUnitPrice":0.00013}', 'ACTIVE', NOW());

-- =========================
-- 安全与监控
-- =========================

INSERT IGNORE INTO kb_security_policies (id, policy_type, policy_code, config_json, status) VALUES
(1, 'RATE_LIMIT', 'GLOBAL_RATE_LIMIT',   '{"maxRps":1000,"maxRpm":60000}',       'ENABLED'),
(2, 'IP_FILTER',  'GLOBAL_IP_WHITELIST', '{"mode":"whitelist","ips":["10.0.0.0/8","172.16.0.0/12","192.168.0.0/16"]}', 'ENABLED'),
(3, 'CONTENT',    'CONTENT_FILTER',      '{"enabled":true,"categories":["violence","hate"]}', 'ENABLED');

-- 告警
INSERT IGNORE INTO ks_alerts (id, alert_type, level, title, status) VALUES
(1, 'QUOTA',  'WARN',  'OpenAI日配额已达80%',    'OPEN'),
(2, 'ERROR',  'ERROR', 'DeepSeek连续5分钟超时率>5%', 'OPEN'),
(3, 'AUTH',   'INFO',  'app_002凭证即将过期',    'RESOLVED');

-- =========================
-- 扩展能力
-- =========================

-- 定时任务（handler_code 关联任务处理器；DISABLED 的不参与调度）
INSERT IGNORE INTO ks_scheduled_tasks (
  id, task_code, task_name, handler_code, cron_expression, execution_mode, status, description
) VALUES
(1, 'operation-log-retention', '操作日志保留清理', 'OPERATION_LOG_RETENTION', '0 0 3 * * ?', 'SINGLE', 'ENABLED', '操作日志最多保留三个月'),
(2, 'schedule-run-retention', '定时任务执行记录清理', 'SCHEDULE_RUN_RETENTION', '0 30 3 * * ?', 'SINGLE', 'ENABLED', '定时任务执行记录最多保留一个月'),
(3, 'openrouter-model-sync', 'OpenRouter 模型同步', 'OPENROUTER_MODEL_SYNC', '0 0 4 * * ?', 'SINGLE', 'DISABLED', '同步 OpenRouter 模型到外部模型库');

-- 插件
INSERT IGNORE INTO kb_plugin_packages (
  id, plugin_code, plugin_name, version, source_type, entrypoint, manifest_json, status
) VALUES
(1, 'builtin-gateway-audit', '网关请求响应审计插件', '1.0.0', 'BUILTIN', 'org.chobit.knot.gateway.plugin.builtin.GatewayRequestLoggingPlugin',
 JSON_OBJECT('pluginId', 'builtin-gateway-audit', 'extensionPoint', 'GATEWAY_EXCHANGE'), 'ACTIVE'),
(2, 'builtin-provider-audit', '上游请求响应审计插件', '1.0.0', 'BUILTIN', 'org.chobit.knot.gateway.plugin.builtin.UpstreamRequestLoggingPlugin',
 JSON_OBJECT('pluginId', 'builtin-provider-audit', 'extensionPoint', 'UPSTREAM_EXCHANGE'), 'ACTIVE');

INSERT IGNORE INTO kb_plugin_capabilities (
  id, plugin_code, capability_code, capability_name, extension_point, stage_code, order_hint, status
) VALUES
(1, 'builtin-gateway-audit', 'gateway-request-response-log', '网关请求响应日志', 'GATEWAY_EXCHANGE', 'GATEWAY_REQUEST', 100, 'ACTIVE'),
(2, 'builtin-gateway-audit', 'gateway-request-response-log', '网关请求响应日志', 'GATEWAY_EXCHANGE', 'GATEWAY_RESPONSE', 100, 'ACTIVE'),
(3, 'builtin-gateway-audit', 'gateway-request-response-log', '网关请求响应日志', 'GATEWAY_EXCHANGE', 'GATEWAY_ERROR', 100, 'ACTIVE'),
(4, 'builtin-provider-audit', 'provider-request-response-log', '上游请求响应日志', 'UPSTREAM_EXCHANGE', 'UPSTREAM_REQUEST', 100, 'ACTIVE'),
(5, 'builtin-provider-audit', 'provider-request-response-log', '上游请求响应日志', 'UPSTREAM_EXCHANGE', 'UPSTREAM_RESPONSE', 100, 'ACTIVE'),
(6, 'builtin-provider-audit', 'provider-request-response-log', '上游请求响应日志', 'UPSTREAM_EXCHANGE', 'UPSTREAM_ERROR', 100, 'ACTIVE');

INSERT IGNORE INTO kb_plugin_instances (
  id, plugin_code, capability_id, instance_code, instance_name, config_json, status, fail_mode, timeout_ms, concurrency_limit
) VALUES
(1, 'builtin-gateway-audit', 1, 'gateway-request-log', '网关请求日志插件', JSON_OBJECT('sink', 'LOG', 'plannedSink', 'KAFKA'), 'ACTIVE', 'FAIL_OPEN', 3000, 0),
(2, 'builtin-gateway-audit', 2, 'gateway-response-log', '网关响应日志插件', JSON_OBJECT('sink', 'LOG', 'plannedSink', 'KAFKA'), 'ACTIVE', 'FAIL_OPEN', 3000, 0),
(3, 'builtin-gateway-audit', 3, 'gateway-error-log', '网关异常日志插件', JSON_OBJECT('sink', 'LOG', 'plannedSink', 'KAFKA'), 'ACTIVE', 'FAIL_OPEN', 3000, 0),
(4, 'builtin-provider-audit', 4, 'provider-request-log', '上游请求日志插件', JSON_OBJECT('sink', 'LOG', 'plannedSink', 'KAFKA'), 'ACTIVE', 'FAIL_OPEN', 3000, 0),
(5, 'builtin-provider-audit', 5, 'provider-response-log', '上游响应日志插件', JSON_OBJECT('sink', 'LOG', 'plannedSink', 'KAFKA'), 'ACTIVE', 'FAIL_OPEN', 3000, 0),
(6, 'builtin-provider-audit', 6, 'provider-error-log', '上游异常日志插件', JSON_OBJECT('sink', 'LOG', 'plannedSink', 'KAFKA'), 'ACTIVE', 'FAIL_OPEN', 3000, 0);

INSERT IGNORE INTO kb_plugin_bindings (
  id, instance_code, scope_type, scope_ref_id, stage_code, order_no, status, binding_config_json
) VALUES
(1, 'gateway-request-log', 'GLOBAL', NULL, 'GATEWAY_REQUEST', 100, 'ACTIVE', JSON_OBJECT('maskApiKey', true)),
(2, 'gateway-response-log', 'GLOBAL', NULL, 'GATEWAY_RESPONSE', 100, 'ACTIVE', JSON_OBJECT()),
(3, 'gateway-error-log', 'GLOBAL', NULL, 'GATEWAY_ERROR', 100, 'ACTIVE', JSON_OBJECT()),
(4, 'provider-request-log', 'GLOBAL', NULL, 'UPSTREAM_REQUEST', 100, 'ACTIVE', JSON_OBJECT()),
(5, 'provider-response-log', 'GLOBAL', NULL, 'UPSTREAM_RESPONSE', 100, 'ACTIVE', JSON_OBJECT()),
(6, 'provider-error-log', 'GLOBAL', NULL, 'UPSTREAM_ERROR', 100, 'ACTIVE', JSON_OBJECT());

INSERT IGNORE INTO kb_plugin_config_versions (
  id, instance_code, version_no, version_code, config_json, operator_id
) VALUES
(1, 'gateway-request-log', 1, MD5('gateway-request-log'), JSON_OBJECT('sink', 'LOG', 'plannedSink', 'KAFKA'), 1),
(2, 'gateway-response-log', 1, MD5('gateway-response-log'), JSON_OBJECT('sink', 'LOG', 'plannedSink', 'KAFKA'), 1),
(3, 'gateway-error-log', 1, MD5('gateway-error-log'), JSON_OBJECT('sink', 'LOG', 'plannedSink', 'KAFKA'), 1),
(4, 'provider-request-log', 1, MD5('provider-request-log'), JSON_OBJECT('sink', 'LOG', 'plannedSink', 'KAFKA'), 1),
(5, 'provider-response-log', 1, MD5('provider-response-log'), JSON_OBJECT('sink', 'LOG', 'plannedSink', 'KAFKA'), 1),
(6, 'provider-error-log', 1, MD5('provider-error-log'), JSON_OBJECT('sink', 'LOG', 'plannedSink', 'KAFKA'), 1);

-- 通知模板
INSERT IGNORE INTO kb_notification_templates (id, code, name, channel, title_tpl, content_tpl, status) VALUES
(1, 'QUOTA_ALERT',    '配额告警',   'EMAIL', '配额告警: {{appName}}',   '应用 {{appName}} 的配额已使用 {{percent}}%，请及时处理。', 'ACTIVE'),
(2, 'ERROR_ALERT',    '异常告警',   'EMAIL', '异常告警: {{providerName}}','供应商 {{providerName}} 错误率超过阈值，当前 {{errorRate}}%。', 'ACTIVE'),
(3, 'CREDENTIAL_EXPIRE','凭证过期提醒','EMAIL','凭证即将过期: {{providerName}}','供应商 {{providerName}} 的 API Key 将在 {{expireDate}} 过期。', 'ACTIVE');

-- =========================
-- 枚举字典
-- =========================
-- 只有仍在 DB 维护的分类放这里；已迁代码枚举（/api/common/enums）的不再落库。

-- 枚举分类（与 ks_enum_configs.category_id 对应；is_system=1 表示系统内置分类）
-- 模型族枚举分类（category_id=28）：计费规则作用域与统一模型均引用该族 code（item_code）。
-- 该分类可动态维护，新增族在枚举管理页操作即可。
INSERT IGNORE INTO ks_enum_categories (id, category, category_name, is_system, is_enabled) VALUES
(3, 'app_type', '应用类型', 0, 1),
(5, 'scope_type', '折扣范围', 0, 1),
(6, 'discount_type', '折扣类型', 0, 1),
(8, 'billing_unit', '计费单位', 0, 1),
(9, 'billing_currency', '计费币种', 0, 1),
(10, 'channel', '通知渠道', 0, 1),
(11, 'plugin_source_type', '插件来源类型', 0, 1),
(12, 'plugin_scope_type', '插件作用范围', 0, 1),
(13, 'alert_level', '告警级别', 1, 1),
(14, 'risk_level', '风险级别', 1, 1),
(15, 'status', '通用状态', 1, 1),
(16, 'logical_model_visibility', '统一模型可见性', 0, 1),
(17, 'logical_model_publish_status', '统一模型发布状态', 0, 1),
(23, 'model_pool_selection_strategy', '模型池选择策略', 1, 1),
(24, 'plugin_extension_point', '插件扩展点', 0, 1),
(25, 'plugin_stage_code', '插件执行阶段', 0, 1),
(26, 'plugin_fail_mode', '插件失败策略', 0, 1),
(27, 'plugin_result_status', '插件结果状态', 0, 1),
(28, 'model_family', '模型族', 0, 1);

-- 供应商类型（category 1）已移除：供应商品牌主数据以 kb_providers 表为唯一来源，
-- 前端下拉/标签走 useProviderTypeOptions（/api/provider-profiles）
-- 通用状态字典：仅保留运行态杂项（操作日志 SUCCESS/FAILURE 等）；
-- EntityStatusEnum（ENABLED/DISABLED/ACTIVE/INACTIVE/DELETED）已迁代码枚举 /api/common/enums
INSERT IGNORE INTO ks_enum_configs (category_id, item_code, item_label, sort_order, is_enabled) VALUES
(3, 'WEB',     'Web应用', 1, 1),
(3, 'MOBILE',  '移动应用', 2, 1),
(3, 'SERVICE', '微服务',   3, 1),
(3, 'OTHER',   '其他',    99, 1),
(5, 'GLOBAL', '全局',   1, 1),
(5, 'MODEL',  '按模型', 2, 1),
(5, 'APP',    '按应用', 3, 1),
(6, 'PERCENTAGE', '百分比折扣', 1, 1),
(6, 'FIXED',      '固定金额',   2, 1),
(8, '1K_TOKENS',     '千 Token', 1, 1),
(8, '1M_TOKENS',     '百万 Token', 2, 1),
(8, 'PER_TOKEN',     '单 Token', 3, 1),
(8, 'PER_REQUEST',   '按请求', 4, 1),
(8, 'PER_IMAGE',     '按图片', 5, 1),
(8, 'PER_MINUTE',    '按分钟', 6, 1),
(8, 'PER_SECOND',    '按秒', 7, 1),
(8, 'CUSTOM',        '自定义', 99, 1),
(9, 'USD',            'USD', 1, 1),
(9, 'CNY',            'CNY', 2, 1),
(10, 'EMAIL',   '邮件',    1, 1),
(10, 'SMS',     '短信',    2, 1),
(10, 'WEBHOOK', 'Webhook', 3, 1),
(11, 'BUILTIN', '内置', 1, 1),
(11, 'LOCAL_JAR', '本地 JAR', 2, 1),
(11, 'REMOTE_REGISTRY', '远程仓库', 3, 1),
(12, 'GLOBAL', '全局', 1, 1),
(12, 'APP', '应用', 2, 1),
(12, 'RULE', '路由规则', 3, 1),
(12, 'PROVIDER', '供应商账户', 4, 1),
(12, 'MODEL', '模型', 5, 1),
(12, 'POOL', '模型池', 6, 1),
(13, 'CRITICAL', '严重', 1, 1),
(13, 'HIGH',     '高',   2, 1),
(13, 'MEDIUM',   '中',   3, 1),
(13, 'LOW',      '低',   4, 1),
(14, 'HIGH',   '高', 1, 1),
(14, 'MEDIUM', '中', 2, 1),
(14, 'LOW',    '低', 3, 1),
(15, 'ONLINE',    '在线',     4, 1),
(15, 'RUNNING',   '运行中',   5, 1),
(15, 'DRAFT',     '草稿',     6, 1),
(15, 'GENERATED', '已生成',   7, 1),
(15, 'SUCCESS',   '成功',     8, 1),
(15, 'FAILURE',   '失败',     9, 1),
(15, 'FAILED',    '失败(旧)', 10, 1),
(16, 'PUBLIC',    '公开',     1, 1),
(16, 'INTERNAL',  '内部',     2, 1),
(16, 'PRIVATE',   '私有',     3, 1),
(17, 'DRAFT',     '草稿',     1, 1),
(17, 'PUBLISHED', '已发布',   2, 1),
(17, 'ARCHIVED',  '已下架',   3, 1),
(23, 'WEIGHTED',      '权重', 1, 1),
(23, 'PRIORITY',      '优先级', 2, 1),
(23, 'RANDOM',        '随机', 3, 1),
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
(28, 'gpt',      'GPT（OpenAI）',        1,  1),
(28, 'claude',   'Claude（Anthropic）',   2,  1),
(28, 'gemini',   'Gemini（Google）',      3,  1),
(28, 'gemma',    'Gemma（Google）',       4,  1),
(28, 'grok',     'Grok（xAI）',           5,  1),
(28, 'llama',    'Llama（Meta）',         6,  1),
(28, 'mistral',  'Mistral',               7,  1),
(28, 'cohere',   'Cohere',                8,  1),
(28, 'phi',      'Phi（Microsoft）',       9,  1),
(28, 'deepseek', 'DeepSeek',              10, 1),
(28, 'qwen',     '通义千问（Qwen）',      11, 1),
(28, 'glm',      '智谱 GLM',              12, 1),
(28, 'minimax',  'MiniMax',               13, 1),
(28, 'hailuo',   '海螺 AI（Hailuo）',     14, 1),
(28, 'moonshot', 'Kimi（Moonshot）',      15, 1),
(28, 'yi',       '零一万物（Yi）',        16, 1),
(28, 'step',     '阶跃星辰（Step）',      17, 1),
(28, 'baichuan', '百川（Baichuan）',      18, 1),
(28, 'doubao',   '豆包（Doubao）',        19, 1),
(28, 'hunyuan',  '混元（Hunyuan）',       20, 1),
(28, 'ernie',    '文心一言（ERNIE）',     21, 1),
(28, 'spark',    '讯飞星火（Spark）',     22, 1);

-- ============================================================
-- 权威授权块（必须保持在文件最后）
-- ============================================================

-- ADMIN 角色拥有全部权限。按角色编码关联并全量授予，保证新增权限在
-- 应用重启执行 data.sql 后也会同步到 ADMIN，不会因中段快照而漏授权。
INSERT IGNORE INTO ks_role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM ks_roles r
         CROSS JOIN ks_permissions p
WHERE r.code = 'ADMIN';

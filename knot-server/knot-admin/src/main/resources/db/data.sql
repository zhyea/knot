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
--   7. 扩展能力        定时任务 / 插件 / 通知模板
--   8. 枚举字典        ks_enum_categories + ks_enum_configs
--   9. 权威授权块      ADMIN 全量授权，必须保持在文件最后
-- ============================================================

-- =========================
-- 系统管理
-- =========================

-- 用户 (password_hash = BCrypt('admin123'))
INSERT IGNORE INTO ks_users (id, username, password_hash, real_name, dept_code, status) VALUES
(1, 'admin', '$2a$10$HUYfxtiEgiRARR/fG46hEeAvcfcQ2WXMPh2NxPw5zkc06fDKeWkxi', '系统管理员', 'HQ',  1),
(2, 'zhangsan', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '张三', 'OPS', 1),
(3, 'lisi', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '李四', 'RND', 1);

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
(1, 'system', '系统管理', 'Setting', 90, 1),
(2, 'model', '模型管理', 'Box', 20, 1),
(3, 'routing', '路由管理', 'Share', 30, 1),
(4, 'billing', '计费管理', 'Coin', 40, 1);

-- 菜单（menu_id 被权限与前端路由引用；按 module_id + sort_order 排列）
INSERT IGNORE INTO ks_menus (id, module_id, parent_id, menu_code, menu_name, route_path, component_key, icon, sort_order, status) VALUES
(1, 1, NULL, 'system.users', '用户管理', '/system/users', 'system/UserManageView', 'User', 10, 1),
(2, 1, NULL, 'system.departments', '部门管理', '/system/departments', 'system/DepartmentManageView', 'OfficeBuilding', 20, 1),
(3, 1, NULL, 'system.role-authorizations', '角色授权', '/system/role-authorizations', 'system/RoleAuthorizationManageView', 'Lock', 30, 1),
(18, 1, NULL, 'system.authorization-resources', '授权资源', '/system/authorization-resources', 'system/AuthorizationResourceManageView', 'Lock', 35, 1),
(4, 1, NULL, 'system.logs', '操作日志', '/system/logs', 'system/OperationLogView', 'Document', 40, 1),
(5, 1, NULL, 'system.settings', '用户设置', '/system/settings', 'system/UserSettingsView', 'Tools', 50, 1),
(14, 1, NULL, 'system.scheduled-tasks', '定时任务', '/system/scheduled-tasks', 'system/ScheduledTaskView', 'Timer', 60, 1),
(15, 1, NULL, 'system.enums', '枚举管理', '/system/enums', 'system/EnumManageView', 'List', 70, 1),
(16, 1, NULL, 'system.plugins', '插件管理', '/system/plugins', 'PluginManageView', 'Connection', 80, 1),
(17, 1, NULL, 'system.apps', '应用管理', '/apps', 'AppManageView', 'Grid', 90, 1),
(8, 2, NULL, 'model.model-pools', '模型池', '/model-management/model-pools', 'ModelPoolManageView', 'Cpu', 10, 1),
(7, 2, NULL, 'model.models', '供应商模型', '/model-management/models', 'ModelManageView', 'Cpu', 20, 1),
(6, 2, NULL, 'model.providers', '供应商账户', '/model-management/provider-accounts', 'ProviderManageView', 'Connection', 30, 1),
(9, 2, NULL, 'model.logical-models', '统一模型', '/model-management/logical-models', 'LogicalModelMarketplaceView', 'Cpu', 40, 1),
(10, 2, NULL, 'model.external-models', '外部模型', '/model-management/external-models', 'ExternalModelManageView', 'Cpu', 50, 1),
(19, 2, NULL, 'model.provider-profiles', '供应商信息', '/model-management/provider-profiles', 'ProviderProfileManageView', 'Connection', 60, 1),
(23, 2, NULL, 'model.model-families', '模型族', '/model-management/model-families', 'ModelFamilyView', 'Cpu', 70, 1),
(11, 3, NULL, 'routing.rules', '路由规则', '/routing/rules', 'routing/RoutingRuleView', 'Share', 10, 1),
(12, 3, NULL, 'routing.consumers', '消费者', '/routing/consumers', 'routing/RoutingConsumerView', 'Share', 20, 1),
(13, 4, NULL, 'billing.rules', '计费规则', '/billing/rules', 'billing/BillingRuleView', 'Coin', 10, 1),
(20, 4, NULL, 'billing.reports', '计费报表', '/billing/reports', 'billing/BillingReportView', 'Odometer', 20, 1),
(22, 3, NULL, 'routing.presets', '预设请求', '/routing/presets', 'routing/TestRequestPresetView', 'Share', 30, 1),
(24, 3, NULL, 'moderation.words', '敏感词管理', '/routing/moderation-words', 'moderation/ModerationWordManageView', 'Warning', 40, 1);

-- =========================
-- 授权与权限
-- =========================
-- 默认拒绝：AdminAuthorizationInterceptor 按 ks_api_permission_bindings 判定，未绑定的接口一律 403。
-- 绑定条目由 .workbuddy/audit/gen_api_bindings.py 扫描 controller 生成，勿手工编辑；新增接口必须补绑定。
-- 顺序约束：ks_permissions -> ks_api_permission_bindings -> ks_role_permissions。

-- 权限（PAGE=页面访问，API=接口权限；新增权限在末尾追加 id）
INSERT IGNORE INTO ks_permissions (id, permission_code, permission_name, permission_type, module_id, menu_id, status, built_in, remark) VALUES
(1, 'system:user:page', '用户管理页面访问', 'PAGE', 1, 1, 1, 1, NULL),
(2, 'system:user:view', '查看用户', 'API', 1, 1, 1, 1, NULL),
(3, 'system:user:create', '创建用户', 'API', 1, 1, 1, 1, NULL),
(4, 'system:user:update', '更新用户', 'API', 1, 1, 1, 1, NULL),
(5, 'system:user:enable', '更新用户状态', 'API', 1, 1, 1, 1, NULL),
(6, 'system:department:page', '部门管理页面访问', 'PAGE', 1, 2, 1, 1, NULL),
(7, 'system:department:view', '查看部门', 'API', 1, 2, 1, 1, NULL),
(8, 'system:department:create', '创建部门', 'API', 1, 2, 1, 1, NULL),
(9, 'system:department:update', '更新部门', 'API', 1, 2, 1, 1, NULL),
(10, 'system:department:enable', '更新部门状态', 'API', 1, 2, 1, 1, NULL),
(11, 'system:department:delete', '删除部门', 'API', 1, 2, 1, 1, NULL),
(12, 'system:role-authorization:page', '角色授权页面访问', 'PAGE', 1, 3, 1, 1, NULL),
(13, 'system:role:view', '查看角色', 'API', 1, 3, 1, 1, NULL),
(14, 'system:log:page', '操作日志页面访问', 'PAGE', 1, 4, 1, 1, NULL),
(15, 'system:log:view', '查看操作日志', 'API', 1, 4, 1, 1, NULL),
-- 用户设置用 preference 关键字（robin 2026-10-17 定：settings 关键字留给「系统设置」，
-- 免得两者撞名 —— ks_permissions.permission_code 有唯一键，撞名会被 INSERT IGNORE 静默丢弃）
(16, 'system:preference:page', '用户设置页面访问', 'PAGE', 1, 5, 1, 1, NULL),
(17, 'system:preference:update', '更新用户设置', 'API', 1, 5, 1, 1, NULL),
(18, 'system:preference:view', '查看当前用户设置与授权信息', 'API', 1, 5, 1, 1, NULL),
(19, 'model:provider:page', '供应商页面访问', 'PAGE', 2, 6, 1, 1, NULL),
(20, 'model:model:page', '供应商模型页面访问', 'PAGE', 2, 7, 1, 1, NULL),
(21, 'model:model-pool:page', '模型池页面访问', 'PAGE', 2, 8, 1, 1, NULL),
(22, 'model:logical-model:page', '统一模型页面访问', 'PAGE', 2, 9, 1, 1, NULL),
(23, 'model:external-model:page', '外部模型页面访问', 'PAGE', 2, 10, 1, 1, NULL),
(24, 'routing:rule:page', '路由规则页面访问', 'PAGE', 3, 11, 1, 1, NULL),
(25, 'routing:consumer:page', '消费者页面访问', 'PAGE', 3, 12, 1, 1, NULL),
(26, 'billing:rule:page', '计费规则页面访问', 'PAGE', 4, 13, 1, 1, NULL),
(27, 'system:scheduled-task:page', '定时任务页面访问', 'PAGE', 1, 14, 1, 1, NULL),
(28, 'system:scheduled-task:view', '查看定时任务', 'API', 1, 14, 1, 1, NULL),
(29, 'system:enum:page', '枚举管理页面访问', 'PAGE', 1, 15, 1, 1, NULL),
(30, 'system:enum:view', '查看枚举管理', 'API', 1, 15, 1, 1, NULL),
(31, 'system:plugin:page', '插件管理页面访问', 'PAGE', 1, 16, 1, 1, NULL),
(32, 'system:plugin:view', '查看插件管理', 'API', 1, 16, 1, 1, NULL),
(33, 'system:app:page', '应用管理页面访问', 'PAGE', 1, 17, 1, 1, NULL),
(34, 'system:app:view', '查看应用管理', 'API', 1, 17, 1, 1, NULL),
(35, 'system:authz:role:create', '创建授权角色', 'API', 1, 3, 1, 1, NULL),
(36, 'system:authz:role:update', '更新授权角色', 'API', 1, 3, 1, 1, NULL),
(37, 'system:authz:role:delete', '删除授权角色', 'API', 1, 3, 1, 1, NULL),
(38, 'system:authz:role:grant', '维护角色授权', 'API', 1, 3, 1, 1, NULL),
(39, 'system:authz:module:view', '查看授权模块', 'API', 1, 18, 1, 1, NULL),
(40, 'system:authz:module:create', '创建授权模块', 'API', 1, 18, 1, 1, NULL),
(41, 'system:authz:module:update', '更新授权模块', 'API', 1, 18, 1, 1, NULL),
(42, 'system:authz:module:delete', '删除授权模块', 'API', 1, 18, 1, 1, NULL),
(43, 'system:authz:menu:view', '查看授权菜单', 'API', 1, 18, 1, 1, NULL),
(44, 'system:authz:menu:create', '创建授权菜单', 'API', 1, 18, 1, 1, NULL),
(45, 'system:authz:menu:update', '更新授权菜单', 'API', 1, 18, 1, 1, NULL),
(46, 'system:authz:menu:delete', '删除授权菜单', 'API', 1, 18, 1, 1, NULL),
(47, 'system:authz:permission:view', '查看授权权限', 'API', 1, 18, 1, 1, NULL),
(48, 'system:authz:permission:create', '创建授权权限', 'API', 1, 18, 1, 1, NULL),
(49, 'system:authz:permission:update', '更新授权权限', 'API', 1, 18, 1, 1, NULL),
(50, 'system:authz:permission:delete', '删除授权权限', 'API', 1, 18, 1, 1, NULL),
(51, 'system:authz:api-binding:view', '查看 API 权限绑定', 'API', 1, 18, 1, 1, NULL),
(52, 'system:authz:api-binding:create', '创建 API 权限绑定', 'API', 1, 18, 1, 1, NULL),
(53, 'system:authz:api-binding:update', '更新 API 权限绑定', 'API', 1, 18, 1, 1, NULL),
(54, 'system:authz:api-binding:delete', '删除 API 权限绑定', 'API', 1, 18, 1, 1, NULL),
(55, 'system:authorization-resource:page', '授权资源页面访问', 'PAGE', 1, 18, 1, 1, NULL),
(56, 'system:user:reset-password', '重置用户密码', 'API', 1, 1, 1, 1, NULL),
(57, 'model:provider-profile:page', '供应商信息页面访问', 'PAGE', 2, 19, 1, 1, NULL),
(58, 'system:doc:view', '接口文档查看', 'API', 1, NULL, 1, 1, NULL),
(59, 'system:app:create', '应用创建', 'API', 1, 17, 1, 1, NULL),
(60, 'system:app:update', '应用更新', 'API', 1, 17, 1, 1, NULL),
(61, 'system:app:delete', '应用删除', 'API', 1, 17, 1, 1, NULL),
(62, 'system:authz:api-binding:enable', 'API 权限绑定更新状态', 'API', 1, 18, 1, 1, NULL),
(63, 'system:authz:menu:enable', '授权菜单更新状态', 'API', 1, 18, 1, 1, NULL),
(64, 'system:authz:module:enable', '授权模块更新状态', 'API', 1, 18, 1, 1, NULL),
(65, 'system:authz:permission:enable', '授权权限更新状态', 'API', 1, 18, 1, 1, NULL),
(66, 'system:authz:role:view', '授权角色查看', 'API', 1, 3, 1, 1, NULL),
(67, 'billing:rule:view', '计费规则查看', 'API', 4, 13, 1, 1, NULL),
(68, 'billing:rule:create', '计费规则创建', 'API', 4, 13, 1, 1, NULL),
(69, 'billing:rule:update', '计费规则更新', 'API', 4, 13, 1, 1, NULL),
(70, 'billing:rule:enable', '计费规则更新状态', 'API', 4, 13, 1, 1, NULL),
(71, 'billing:rule:delete', '计费规则删除', 'API', 4, 13, 1, 1, NULL),
(143, 'billing:rule:restore', '计费规则恢复', 'API', 4, 13, 1, 1, NULL),
(72, 'system:enum:create', '枚举创建', 'API', 1, 15, 1, 1, NULL),
(73, 'system:enum:update', '枚举更新', 'API', 1, 15, 1, 1, NULL),
(74, 'system:enum:delete', '枚举删除', 'API', 1, 15, 1, 1, NULL),
(75, 'model:external-model:view', '外部模型查看', 'API', 2, 10, 1, 1, NULL),
(76, 'model:external-model:sync', '外部模型同步', 'API', 2, 10, 1, 1, NULL),
(77, 'model:external-model:create', '外部模型创建', 'API', 2, 10, 1, 1, NULL),
(78, 'model:external-model:delete', '外部模型删除', 'API', 2, 10, 1, 1, NULL),
(79, 'model:logical-model:view', '统一模型查看', 'API', 2, 9, 1, 1, NULL),
(80, 'model:logical-model:create', '统一模型创建', 'API', 2, 9, 1, 1, NULL),
(81, 'model:logical-model:update', '统一模型更新', 'API', 2, 9, 1, 1, NULL),
(82, 'model:logical-model:enable', '统一模型更新状态', 'API', 2, 9, 1, 1, NULL),
(83, 'model:logical-model:delete', '统一模型删除', 'API', 2, 9, 1, 1, NULL),
(84, 'model:model:view', '供应商模型查看', 'API', 2, 7, 1, 1, NULL),
(85, 'model:model:create', '供应商模型创建', 'API', 2, 7, 1, 1, NULL),
(86, 'model:model:update', '供应商模型更新', 'API', 2, 7, 1, 1, NULL),
(87, 'model:model:enable', '供应商模型更新状态', 'API', 2, 7, 1, 1, NULL),
(88, 'model:model-pool:view', '模型池查看', 'API', 2, 8, 1, 1, NULL),
(89, 'model:model-pool:create', '模型池创建', 'API', 2, 8, 1, 1, NULL),
(90, 'model:model-pool:update', '模型池更新', 'API', 2, 8, 1, 1, NULL),
(91, 'model:model-pool:enable', '模型池更新状态', 'API', 2, 8, 1, 1, NULL),
(92, 'model:model-pool:delete', '模型池删除', 'API', 2, 8, 1, 1, NULL),
(93, 'system:module-catalog:view', '模块目录查看', 'API', 1, 18, 1, 1, NULL),
(94, 'system:notification:view', '通知查看', 'API', 1, NULL, 1, 1, NULL),
(95, 'system:notification:create', '通知创建', 'API', 1, NULL, 1, 1, NULL),
(96, 'system:notification:send', '通知发送', 'API', 1, NULL, 1, 1, NULL),
(97, 'system:plugin:create', '插件创建', 'API', 1, 16, 1, 1, NULL),
(98, 'system:plugin:enable', '插件更新状态', 'API', 1, 16, 1, 1, NULL),
(99, 'model:provider:view', '供应商账户查看', 'API', 2, 6, 1, 1, NULL),
(100, 'model:provider:create', '供应商账户创建', 'API', 2, 6, 1, 1, NULL),
(101, 'model:provider:update', '供应商账户更新', 'API', 2, 6, 1, 1, NULL),
(102, 'model:provider:enable', '供应商账户更新状态', 'API', 2, 6, 1, 1, NULL),
(103, 'model:provider-profile:view', '供应商信息查看', 'API', 2, 19, 1, 1, NULL),
(104, 'model:provider-profile:create', '供应商信息创建', 'API', 2, 19, 1, 1, NULL),
(105, 'model:provider-profile:update', '供应商信息更新', 'API', 2, 19, 1, 1, NULL),
(106, 'model:provider-profile:delete', '供应商信息删除', 'API', 2, 19, 1, 1, NULL),
(107, 'routing:consumer:view', '消费者查看', 'API', 3, 12, 1, 1, NULL),
(108, 'routing:consumer:create', '消费者创建', 'API', 3, 12, 1, 1, NULL),
(109, 'routing:consumer:update', '消费者更新', 'API', 3, 12, 1, 1, NULL),
(110, 'routing:consumer:enable', '消费者更新状态', 'API', 3, 12, 1, 1, NULL),
(111, 'routing:consumer:rotate-secret', '消费者轮换密钥', 'API', 3, 12, 1, 1, NULL),
(112, 'routing:rule:view', '路由规则查看', 'API', 3, 11, 1, 1, NULL),
(113, 'routing:rule:create', '路由规则创建', 'API', 3, 11, 1, 1, NULL),
(114, 'routing:rule:update', '路由规则更新', 'API', 3, 11, 1, 1, NULL),
(115, 'routing:rule:enable', '路由规则更新状态', 'API', 3, 11, 1, 1, NULL),
(116, 'routing:rule:test', '路由规则测试', 'API', 3, 11, 1, 1, NULL),
(117, 'system:scheduled-task:create', '定时任务创建', 'API', 1, 14, 1, 1, NULL),
(118, 'system:scheduled-task:update', '定时任务更新', 'API', 1, 14, 1, 1, NULL),
(119, 'system:scheduled-task:trigger', '定时任务触发', 'API', 1, 14, 1, 1, NULL),
(123, 'model:external-model:ignore', '外部模型忽略', 'API', 2, 10, 1, 1, NULL),
(124, 'billing:report:page', '计费报表页面访问', 'PAGE', 4, 20, 1, 1, NULL),
(125, 'billing:report:view', '计费报表查看', 'API', 4, 20, 1, 1, NULL),
(127, 'billing:rule:preview', '计费规则方案试算', 'API', 4, 13, 1, 1, NULL),
(128, 'routing:preset:view', '预设请求查看', 'API', 3, 22, 1, 1, NULL),
(129, 'routing:preset:create', '预设请求创建', 'API', 3, 22, 1, 1, NULL),
(130, 'routing:preset:update', '预设请求更新', 'API', 3, 22, 1, 1, NULL),
(131, 'routing:preset:delete', '预设请求删除', 'API', 3, 22, 1, 1, NULL),
(132, 'routing:preset:enable', '预设请求更新状态', 'API', 3, 22, 1, 1, NULL),
(133, 'routing:preset:page', '预设请求页面访问', 'PAGE', 3, 22, 1, 1, NULL),
(134, 'model:logical-model:restore', '统一模型恢复', 'API', 2, 9, 1, 1, NULL),
(135, 'model:model-pool:restore', '模型池恢复', 'API', 2, 8, 1, 1, NULL),
(136, 'model:model-family:page', '模型族页面访问', 'PAGE', 2, 23, 1, 1, NULL),
(137, 'model:model-family:view', '模型族查看', 'API', 2, 23, 1, 1, NULL),
(138, 'model:model-family:create', '模型族创建', 'API', 2, 23, 1, 1, NULL),
(139, 'model:model-family:update', '模型族更新', 'API', 2, 23, 1, 1, NULL),
(140, 'model:model-family:enable', '模型族更新状态', 'API', 2, 23, 1, 1, NULL),
(141, 'model:model-family:delete', '模型族删除', 'API', 2, 23, 1, 1, NULL),
-- kb_models 逻辑删除（2026-10-07）：供应商模型删除 / 恢复（model_code 唯一性按物理行，删除后只能恢复）
(142, 'model:model:delete', '供应商模型删除', 'API', 2, 7, 1, 1, NULL),
-- 敏感词管理（内容安全模块，模块 5 / 菜单 24）
(144, 'moderation:word:page', '敏感词管理页面访问', 'PAGE', 3, 24, 1, 1, NULL),
(145, 'moderation:word:view', '敏感词查看', 'API', 3, 24, 1, 1, NULL),
(146, 'moderation:word:create', '敏感词创建', 'API', 3, 24, 1, 1, NULL),
(147, 'moderation:word:update', '敏感词更新', 'API', 3, 24, 1, 1, NULL),
(148, 'moderation:word:enable', '敏感词更新状态', 'API', 3, 24, 1, 1, NULL),
(149, 'moderation:word:delete', '敏感词删除', 'API', 3, 24, 1, 1, NULL),
(150, 'moderation:word:import', '敏感词导入', 'API', 3, 24, 1, 1, NULL),
-- 插件删除（PluginController DELETE /api/plugins/{id} 此前漏绑 → 前端删除按钮恒 403）
(151, 'system:plugin:delete', '插件删除', 'API', 1, 16, 1, 1, NULL),
-- 网关摘除管理面（B4）：路由目标摘除查看 / 管理（恢复 + 手动摘除），模块 3 / 菜单 11（路由规则组）
(152, 'routing:target:outage:view', '路由目标摘除查看', 'API', 3, 11, 1, 1, NULL),
(153, 'routing:target:outage:manage', '路由目标摘除管理', 'API', 3, 11, 1, 1, NULL);

-- API 权限绑定（默认拒绝：未绑定接口一律 403；由 .workbuddy/audit/gen_api_bindings.py 生成）
INSERT IGNORE INTO ks_api_permission_bindings (id, permission_id, http_method, path_pattern, controller_class, status) VALUES
(1, 2, 'POST', '/api/users', 'UserController', 1),
(2, 3, 'POST', '/api/users/create', 'UserController', 1),
(3, 5, 'PUT', '/api/users/{id}/status', 'UserController', 1),
(4, 4, 'PUT', '/api/users/{id}', 'UserController', 1),
(5, 7, 'POST', '/api/system/departments/list', 'DepartmentController', 1),
(6, 7, 'GET', '/api/system/departments/tree', 'DepartmentController', 1),
(7, 8, 'POST', '/api/system/departments', 'DepartmentController', 1),
(8, 9, 'PUT', '/api/system/departments/{id}', 'DepartmentController', 1),
(9, 10, 'PUT', '/api/system/departments/{id}/status', 'DepartmentController', 1),
(10, 11, 'DELETE', '/api/system/departments/{id}', 'DepartmentController', 1),
(11, 13, 'POST', '/api/system/roles', 'SystemController', 1),
(12, 15, 'POST', '/api/system/operation-logs', 'SystemController', 1),
(14, 18, 'GET', '/api/user-settings/me', 'UserSettingController', 1),
(15, 17, 'PUT', '/api/user-settings/me', 'UserSettingController', 1),
(16, 18, 'GET', '/api/me/authorizations', 'CurrentUserController', 1),
(17, 15, 'POST', '/api/operation-logs/list', 'OperationLogController', 1),
(18, 15, 'GET', '/api/operation-logs/{id}', 'OperationLogController', 1),
(19, 15, 'GET', '/api/operation-logs/module/{module}', 'OperationLogController', 1),
(20, 15, 'GET', '/api/operation-logs/operator/{operatorId}', 'OperationLogController', 1),
(21, 15, 'GET', '/api/operation-logs/entity/{entityType}/{entityId}', 'OperationLogController', 1),
(22, 28, 'POST', '/api/system/scheduled-tasks/list', 'ScheduledTaskController', 1),
(25, 32, 'POST', '/api/plugins/list', 'PluginController', 1),
(26, 34, 'POST', '/api/apps/list', 'AppController', 1),
(27, 13, 'POST', '/api/system/authorizations/roles/list', 'AuthorizationRoleController', 1),
(28, 13, 'GET', '/api/system/authorizations/roles/{roleId}/snapshot', 'AuthorizationRoleController', 1),
(29, 35, 'POST', '/api/system/authorizations/roles', 'AuthorizationRoleController', 1),
(30, 36, 'PUT', '/api/system/authorizations/roles/{id}', 'AuthorizationRoleController', 1),
(31, 37, 'DELETE', '/api/system/authorizations/roles/{id}', 'AuthorizationRoleController', 1),
(32, 38, 'PUT', '/api/system/authorizations/roles/{roleId}/permissions', 'AuthorizationRoleController', 1),
(33, 39, 'GET', '/api/system/authorizations/modules', 'AuthorizationModuleController', 1),
(34, 40, 'POST', '/api/system/authorizations/modules', 'AuthorizationModuleController', 1),
(35, 41, 'PUT', '/api/system/authorizations/modules/{id}', 'AuthorizationModuleController', 1),
(36, 42, 'DELETE', '/api/system/authorizations/modules/{id}', 'AuthorizationModuleController', 1),
(37, 43, 'GET', '/api/system/authorizations/menus', 'AuthorizationMenuController', 1),
(38, 44, 'POST', '/api/system/authorizations/menus', 'AuthorizationMenuController', 1),
(39, 45, 'PUT', '/api/system/authorizations/menus/{id}', 'AuthorizationMenuController', 1),
(40, 46, 'DELETE', '/api/system/authorizations/menus/{id}', 'AuthorizationMenuController', 1),
(41, 47, 'GET', '/api/system/authorizations/permissions', 'AuthorizationPermissionController', 1),
(42, 48, 'POST', '/api/system/authorizations/permissions', 'AuthorizationPermissionController', 1),
(43, 49, 'PUT', '/api/system/authorizations/permissions/{id}', 'AuthorizationPermissionController', 1),
(44, 50, 'DELETE', '/api/system/authorizations/permissions/{id}', 'AuthorizationPermissionController', 1),
(45, 51, 'GET', '/api/system/authorizations/api-bindings', 'AuthorizationApiBindingController', 1),
(46, 52, 'POST', '/api/system/authorizations/api-bindings', 'AuthorizationApiBindingController', 1),
(47, 53, 'PUT', '/api/system/authorizations/api-bindings/{id}', 'AuthorizationApiBindingController', 1),
(48, 54, 'DELETE', '/api/system/authorizations/api-bindings/{id}', 'AuthorizationApiBindingController', 1),
(49, 64, 'PUT', '/api/system/authorizations/modules/{id}/status', 'AuthorizationModuleController', 1),
(50, 63, 'PUT', '/api/system/authorizations/menus/{id}/status', 'AuthorizationMenuController', 1),
(51, 65, 'PUT', '/api/system/authorizations/permissions/{id}/status', 'AuthorizationPermissionController', 1),
(52, 62, 'PUT', '/api/system/authorizations/api-bindings/{id}/status', 'AuthorizationApiBindingController', 1),
(53, 56, 'PUT', '/api/users/{id}/reset-password', 'UserController', 1),
(54, 58, 'POST', '/api/docs/openapi.json', 'ApiDocController', 1),
(55, 58, 'POST', '/api/docs/changelog', 'ApiDocController', 1),
(56, 59, 'POST', '/api/apps', 'AppController', 1),
(57, 60, 'PUT', '/api/apps/{id}', 'AppController', 1),
(58, 61, 'DELETE', '/api/apps/{id}', 'AppController', 1),
(59, 67, 'POST', '/api/billing/rules', 'BillingController', 1),
(60, 67, 'GET', '/api/billing/mode-capabilities', 'BillingController', 1),
(61, 68, 'POST', '/api/billing', 'BillingController', 1),
(62, 69, 'PUT', '/api/billing/rules/{id}', 'BillingController', 1),
(63, 70, 'PUT', '/api/billing/rules/{id}/status', 'BillingController', 1),
(64, 71, 'DELETE', '/api/billing/rules/{id}', 'BillingController', 1),
(66, 30, 'POST', '/api/system/enums/list', 'EnumConfigController', 1),
(67, 30, 'POST', '/api/system/enums/category-summaries', 'EnumConfigController', 1),
(68, 30, 'GET', '/api/system/enums/items/{category}', 'EnumConfigController', 1),
(69, 30, 'GET', '/api/system/enums/operation-logs/{category}', 'EnumConfigController', 1),
(70, 30, 'POST', '/api/system/enums/categories', 'EnumConfigController', 1),
(71, 72, 'POST', '/api/system/enums', 'EnumConfigController', 1),
(72, 73, 'PUT', '/api/system/enums/{id}', 'EnumConfigController', 1),
(73, 74, 'DELETE', '/api/system/enums/{id}', 'EnumConfigController', 1),
(74, 75, 'GET', '/api/external-models/sources', 'ExternalModelController', 1),
(75, 75, 'POST', '/api/external-models/items/list', 'ExternalModelController', 1),
(76, 75, 'GET', '/api/external-models/items/{id}', 'ExternalModelController', 1),
(77, 76, 'POST', '/api/external-models/sources/{sourceCode}/sync', 'ExternalModelController', 1),
(78, 77, 'POST', '/api/external-models/items/{id}/logical-model', 'ExternalModelController', 1),
(79, 75, 'POST', '/api/external-models/items/logical-models', 'ExternalModelController', 1),
(80, 78, 'DELETE', '/api/external-models/items/{id}', 'ExternalModelController', 1),
(81, 78, 'POST', '/api/external-models/items/batch-delete', 'ExternalModelController', 1),
(82, 79, 'GET', '/api/logical-models/check-code', 'LogicalModelController', 1),
(83, 79, 'GET', '/api/logical-models/{id}', 'LogicalModelController', 1),
(84, 79, 'POST', '/api/logical-models/list', 'LogicalModelController', 1),
(85, 80, 'POST', '/api/logical-models', 'LogicalModelController', 1),
(86, 81, 'PUT', '/api/logical-models/{id}', 'LogicalModelController', 1),
(87, 82, 'PUT', '/api/logical-models/{id}/status', 'LogicalModelController', 1),
(88, 83, 'DELETE', '/api/logical-models/{id}', 'LogicalModelController', 1),
(89, 84, 'GET', '/api/models/check-code', 'ModelController', 1),
(90, 84, 'GET', '/api/models/usage-extractors', 'ModelController', 1),
(91, 84, 'GET', '/api/models/request-adapters', 'ModelController', 1),
(92, 84, 'GET', '/api/models/api-protocols', 'ModelController', 1),
(93, 84, 'GET', '/api/models/types', 'ModelController', 1),
(94, 84, 'GET', '/api/models/{id}', 'ModelController', 1),
(95, 84, 'POST', '/api/models/list', 'ModelController', 1),
(96, 85, 'POST', '/api/models', 'ModelController', 1),
(97, 86, 'PUT', '/api/models/{id}', 'ModelController', 1),
(98, 87, 'PUT', '/api/models/{id}/status', 'ModelController', 1),
(99, 88, 'GET', '/api/model-pools/check-code', 'ModelPoolController', 1),
(100, 88, 'GET', '/api/model-pools/{id}', 'ModelPoolController', 1),
(101, 88, 'POST', '/api/model-pools/list', 'ModelPoolController', 1),
(102, 89, 'POST', '/api/model-pools', 'ModelPoolController', 1),
(103, 90, 'PUT', '/api/model-pools/{id}', 'ModelPoolController', 1),
(104, 91, 'PUT', '/api/model-pools/{id}/status', 'ModelPoolController', 1),
(105, 92, 'DELETE', '/api/model-pools/{id}', 'ModelPoolController', 1),
(106, 93, 'POST', '/api/modules', 'ModuleCatalogController', 1),
(107, 94, 'POST', '/api/notifications/templates/list', 'NotificationController', 1),
(108, 95, 'POST', '/api/notifications/templates', 'NotificationController', 1),
(112, 98, 'PUT', '/api/plugins/{id}/status', 'PluginController', 1),
(113, 99, 'POST', '/api/provider-accounts/list', 'ProviderController', 1),
(114, 99, 'GET', '/api/provider-accounts/credential-types', 'ProviderController', 1),
(115, 99, 'GET', '/api/provider-accounts/suggest-code', 'ProviderController', 1),
(116, 99, 'GET', '/api/provider-accounts/check-code', 'ProviderController', 1),
(118, 99, 'GET', '/api/provider-accounts/{id}', 'ProviderController', 1),
(119, 100, 'POST', '/api/provider-accounts', 'ProviderController', 1),
(120, 101, 'PUT', '/api/provider-accounts/{id}', 'ProviderController', 1),
(121, 102, 'PUT', '/api/provider-accounts/{id}/status', 'ProviderController', 1),
(125, 103, 'POST', '/api/provider-profiles/list', 'ProviderProfileController', 1),
(126, 103, 'GET', '/api/provider-profiles/{id}', 'ProviderProfileController', 1),
(127, 103, 'GET', '/api/provider-profiles/check-code', 'ProviderProfileController', 1),
(128, 104, 'POST', '/api/provider-profiles', 'ProviderProfileController', 1),
(129, 105, 'PUT', '/api/provider-profiles/{id}', 'ProviderProfileController', 1),
(130, 106, 'DELETE', '/api/provider-profiles/{id}', 'ProviderProfileController', 1),
(131, 107, 'POST', '/api/routing-consumers/list', 'RoutingConsumerController', 1),
(132, 107, 'GET', '/api/routing-consumers/check-code', 'RoutingConsumerController', 1),
(133, 108, 'POST', '/api/routing-consumers', 'RoutingConsumerController', 1),
(134, 109, 'PUT', '/api/routing-consumers/{id}', 'RoutingConsumerController', 1),
(135, 110, 'PUT', '/api/routing-consumers/{id}/status', 'RoutingConsumerController', 1),
(136, 111, 'POST', '/api/routing-consumers/{id}/rotate-secret', 'RoutingConsumerController', 1),
(137, 112, 'GET', '/api/routing-rules/debug-capabilities', 'RoutingRuleController', 1),
(138, 112, 'POST', '/api/routing-rules/list', 'RoutingRuleController', 1),
(139, 112, 'GET', '/api/routing-rules/check-code', 'RoutingRuleController', 1),
(140, 113, 'POST', '/api/routing-rules', 'RoutingRuleController', 1),
(141, 114, 'PUT', '/api/routing-rules/{id}', 'RoutingRuleController', 1),
(142, 115, 'PUT', '/api/routing-rules/{id}/status', 'RoutingRuleController', 1),
(143, 116, 'POST', '/api/routing-rules/{id}/test', 'RoutingRuleController', 1),
(144, 117, 'POST', '/api/system/scheduled-tasks', 'ScheduledTaskController', 1),
(145, 118, 'PUT', '/api/system/scheduled-tasks/{id}', 'ScheduledTaskController', 1),
(146, 119, 'POST', '/api/system/scheduled-tasks/{id}/trigger', 'ScheduledTaskController', 1),
(147, 28, 'POST', '/api/system/scheduled-tasks/runs', 'ScheduledTaskController', 1),
(148, 28, 'POST', '/api/system/scheduled-tasks/handlers', 'ScheduledTaskController', 1),
(153, 15, 'POST', '/api/system/log-types', 'SystemController', 1),
(154, 15, 'POST', '/api/system/logs', 'SystemController', 1),
(155, 123, 'POST', '/api/external-models/items/{id}/ignored', 'ExternalModelController', 1),
(156, 125, 'GET', '/api/billing/report/summary', 'BillingController', 1),
(157, 127, 'POST', '/api/billing/rules/{id}/preview', 'BillingController', 1),
(158, 128, 'POST', '/api/test-request-presets/list', 'TestRequestPresetController', 1),
(159, 129, 'POST', '/api/test-request-presets', 'TestRequestPresetController', 1),
(160, 130, 'PUT', '/api/test-request-presets/{id}', 'TestRequestPresetController', 1),
(161, 131, 'DELETE', '/api/test-request-presets/{id}', 'TestRequestPresetController', 1),
(162, 128, 'GET', '/api/test-request-presets/options', 'TestRequestPresetController', 1),
(163, 132, 'PUT', '/api/test-request-presets/{id}/status', 'TestRequestPresetController', 1),
(164, 67, 'GET', '/api/billing/rules/{id}', 'BillingController', 1),
(165, 99, 'GET', '/api/provider-accounts/auth-appliers', 'ProviderController', 1),
(166, 134, 'PUT', '/api/logical-models/{id}/restore', 'LogicalModelController', 1),
(167, 135, 'PUT', '/api/model-pools/{id}/restore', 'ModelPoolController', 1),
(168, 137, 'POST', '/api/model-families/list', 'ModelFamilyController', 1),
(169, 137, 'GET', '/api/model-families/check-code', 'ModelFamilyController', 1),
(170, 137, 'GET', '/api/model-families/{id}', 'ModelFamilyController', 1),
(171, 138, 'POST', '/api/model-families', 'ModelFamilyController', 1),
(172, 139, 'PUT', '/api/model-families/{id}', 'ModelFamilyController', 1),
(173, 140, 'PUT', '/api/model-families/{id}/status', 'ModelFamilyController', 1),
(174, 141, 'DELETE', '/api/model-families/{id}', 'ModelFamilyController', 1),
(175, 34, 'POST', '/api/apps/options', 'AppController', 1),
(176, 67, 'POST', '/api/billing/options', 'BillingController', 1),
(177, 7, 'POST', '/api/system/departments/options', 'DepartmentController', 1),
(178, 79, 'POST', '/api/logical-models/options', 'LogicalModelController', 1),
(179, 84, 'POST', '/api/models/options', 'ModelController', 1),
(180, 88, 'POST', '/api/model-pools/options', 'ModelPoolController', 1),
(181, 99, 'POST', '/api/provider-accounts/options', 'ProviderController', 1),
(182, 107, 'POST', '/api/routing-consumers/options', 'RoutingConsumerController', 1),
-- 流式测试与阻塞测试 / 计费裁判同属「路由规则测试」(routing:rule:test=116)，不再借创建权限(113)，
-- 否则 DEVELOPER（持 116 不持 113）只能阻塞测试、流式测试被拒，语义不一致。
(183, 116, 'POST', '/api/routing-rules/{id}/test/stream', 'RoutingRuleController', 1),
(215, 116, 'POST', '/api/routing-rules/{id}/test/billing-verdict', 'RoutingRuleController', 1),
(185, 128, 'GET', '/api/test-request-presets/{id}', 'TestRequestPresetController', 1),
(186, 107, 'GET', '/api/routing-consumers/{id}', 'RoutingConsumerController', 1),
-- 消费者详情（按业务码）：路由规则↔消费者 1:1 后，测试抽屉按规则的 consumerCode 取密钥，
-- 走 routing:consumer:view(107)，与按 id 取详情同一权限粒度（两者都返回 secretKey）。
(216, 107, 'GET', '/api/routing-consumers/code/{code}', 'RoutingConsumerController', 1),
(187, 66, 'POST', '/api/system/authorizations/roles/options', 'AuthorizationRoleController', 1),
(188, 103, 'POST', '/api/provider-profiles/options', 'ProviderProfileController', 1),
(189, 142, 'DELETE', '/api/models/{id}', 'ModelController', 1),
(190, 86, 'PUT', '/api/models/{id}/restore', 'ModelController', 1),
(191, 143, 'PUT', '/api/billing/rules/{id}/restore', 'BillingController', 1),
(192, 2, 'POST', '/api/users/options-by-username', 'UserController', 1),
(193, 84, 'POST', '/api/models/{modelCode}/discount-policies/list', 'ModelController', 1),
(194, 85, 'POST', '/api/models/{modelCode}/discount-policies', 'ModelController', 1),
(195, 86, 'PUT', '/api/models/{modelCode}/discount-policies/{policyId}', 'ModelController', 1),
-- 敏感词管理（/api/moderation-words，默认拒绝：未绑定一律 403）
(196, 145, 'GET', '/api/moderation-words/check-code', 'ModerationWordController', 1),
(197, 145, 'GET', '/api/moderation-words/{id}', 'ModerationWordController', 1),
(198, 145, 'POST', '/api/moderation-words/list', 'ModerationWordController', 1),
(199, 146, 'POST', '/api/moderation-words', 'ModerationWordController', 1),
(200, 147, 'PUT', '/api/moderation-words/{id}', 'ModerationWordController', 1),
(201, 148, 'PUT', '/api/moderation-words/{id}/status', 'ModerationWordController', 1),
(202, 149, 'DELETE', '/api/moderation-words/{id}', 'ModerationWordController', 1),
(203, 147, 'PUT', '/api/moderation-words/{id}/restore', 'ModerationWordController', 1),
(204, 150, 'POST', '/api/moderation-words/import', 'ModerationWordController', 1),
(205, 145, 'GET', '/api/moderation-words/export', 'ModerationWordController', 1),
(206, 151, 'DELETE', '/api/plugins/{id}', 'PluginController', 1),
-- 路由调试协议下发（前端协议下拉数据源，与调试准入校验同源）
(208, 112, 'GET', '/api/routing-rules/{id}/debug-protocols', 'RoutingRuleController', 1),
-- 网关摘除管理面（B4）：查看 / 恢复 / 手动摘除
(209, 152, 'GET', '/api/gateway/outages', 'GatewayOutageController', 1),
(210, 153, 'POST', '/api/gateway/outages/release', 'GatewayOutageController', 1),
(211, 153, 'POST', '/api/gateway/outages/eject', 'GatewayOutageController', 1);

-- 角色授权（OPERATOR 全量 / DEVELOPER 只读；ADMIN 由文件末尾权威块全量授予）
INSERT IGNORE INTO ks_role_permissions (role_id, permission_id) VALUES
(2, 1), (2, 2), (2, 6), (2, 7), (2, 14), (2, 15), (2, 16), (2, 17),
(2, 18), (2, 19), (2, 20), (2, 21), (2, 22), (2, 23), (2, 24), (2, 25),
(2, 26), (2, 27), (2, 28), (2, 29), (2, 30), (2, 31), (2, 32), (2, 33),
(2, 34), (2, 35), (2, 36), (2, 37), (2, 38), (2, 39), (2, 40), (2, 41),
(2, 42), (2, 43), (2, 44), (2, 45), (2, 46), (2, 47), (2, 48), (2, 49),
(2, 50), (2, 51), (2, 52), (2, 53), (2, 54), (2, 55), (2, 57), (3, 57),
(2, 58), (2, 59), (2, 60), (2, 61), (2, 62), (2, 63), (2, 64), (2, 65),
(2, 66), (2, 67), (2, 68), (2, 69), (2, 70), (2, 71), (2, 72), (2, 73),
(2, 74), (2, 75), (2, 76), (2, 77), (2, 78), (2, 79), (2, 80), (2, 81),
(2, 82), (2, 83), (2, 84), (2, 85), (2, 86), (2, 87), (2, 88), (2, 89),
(2, 90), (2, 91), (2, 92), (2, 93), (2, 94), (2, 95), (2, 96), (2, 97),
(2, 98), (2, 99), (2, 100), (2, 101), (2, 102), (2, 103), (2, 104), (2, 105),
(2, 106), (2, 107), (2, 108), (2, 109), (2, 110), (2, 111), (2, 112), (2, 113),
(2, 114), (2, 115), (2, 116), (2, 117), (2, 118), (2, 119), (2, 120), (2, 121),
(2, 122), (2, 123), (2, 124), (2, 125), (2, 127), (2, 128), (2, 129),
(2, 130), (2, 131), (2, 132), (2, 133), (2, 134), (2, 135), (2, 136), (2, 137), (2, 138), (2, 139),
(2, 140), (2, 141), (3, 14), (3, 15), (3, 16), (3, 17), (3, 18),
(3, 19), (3, 20), (3, 21), (3, 22), (3, 23), (3, 24), (3, 25), (3, 26),
(3, 58), (3, 66), (3, 67), (3, 75), (3, 79), (3, 84), (3, 88), (3, 93),
(3, 94), (3, 99), (3, 103), (3, 107), (3, 112), (3, 120), (3, 124), (3, 125),
(3, 127), (3, 128), (3, 133), (3, 136), (3, 137),
-- 网关摘除管理面（B4）：OPERATOR 全量（查看 + 管理），DEVELOPER 仅查看；ADMIN 由末尾权威块全量授予
(2, 152), (2, 153), (3, 152);

-- 存量库修补：把「授权资源页面访问」补给已持有「角色授权页面访问」(id=12) 的角色。
-- 注意：ADMIN 的 55 由文件末尾的权威授权块保证，不依赖本句。
INSERT IGNORE INTO ks_role_permissions (role_id, permission_id)
SELECT role_id, 55
FROM ks_role_permissions
WHERE permission_id = 12;

-- 敏感词管理权限补给内置角色（按 permission_code 关联，避免依赖硬编码 id）：
--   OPERATOR(2)  全量（与既有 27-55 全量口径一致）
--   DEVELOPER(3) 仅只读 view
--   ADMIN        由文件末尾权威授权块全量授予，不依赖本句。
INSERT IGNORE INTO ks_role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM ks_roles r
  CROSS JOIN ks_permissions p
WHERE r.code IN ('OPERATOR', 'DEVELOPER')
  AND p.permission_code LIKE 'moderation:word:%'
  AND (r.code = 'OPERATOR' OR p.permission_code = 'moderation:word:view');

-- 插件删除权限补给内置角色（修复前端删除按钮恒 403 的既有缺陷）
INSERT IGNORE INTO ks_role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM ks_roles r
  CROSS JOIN ks_permissions p
WHERE r.code = 'OPERATOR'
  AND p.permission_code = 'system:plugin:delete';

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
(6, 'openrouter',   'OpenRouter',   '云厂商'),
(7, 'aws',          'AWS Bedrock',  '云厂商');

-- base_url 是上游 host 的唯一来源；模型级只用 base_path 承载路径前缀。
INSERT IGNORE INTO kb_provider_accounts (id, provider_code, code, base_url, status) VALUES
(1, 'openai',    'openai-default',     'https://api.openai.com',         1),
(2, 'anthropic', 'anthropic-default',  'https://api.anthropic.com',      1),
(3, 'deepseek',  'deepseek-default',   'https://api.deepseek.com',       1),
(4, 'qwen',      'qwen-default',       'https://dashscope.aliyuncs.com', 1),
-- Bedrock 两类端点各占一个账户：endpointType 挂在凭据上，而 host/SigV4 service 由它决定，
-- 一个账户只能对应一种端点形态（runtime 走 Converse，mantle 走 OpenAI 兼容）。
-- base_url 刻意留 NULL：host 由 AwsSigV4AuthApplier.baseUrl() 按 region + endpointType 派生；
-- 需要走 VPC / 私有代理时，在此填 VPC 端点地址，派生逻辑自动让位于显式配置。
(5, 'aws',       'aws-bedrock-runtime', NULL, 1),
(6, 'aws',       'aws-bedrock-mantle',  NULL, 1);

-- 频控/额度策略（独立表 + 资源绑定）
INSERT IGNORE INTO kb_rate_limit_policies (id, policy_code, policy_name, rpm, tpm, status) VALUES
(1, 'MODEL-1-RL',        'MODEL #1 限流',         60,  200000, 1),
(2, 'MODEL-4-RL',        'MODEL #4 限流',         30,  100000, 1),
(3, 'ROUTING_RULE-1-RL', 'ROUTING_RULE #1 限流', 120,  500000, 1);

INSERT IGNORE INTO kb_quota_policies (id, policy_code, policy_name, max_tokens, cost_limit, currency, quota_window, status) VALUES
(1, 'PROVIDER-1-QT', 'PROVIDER #1 额度', 500000000, 20000.000000, 'USD', 'MONTH', 1),
(2, 'PROVIDER-2-QT', 'PROVIDER #2 额度', 200000000,  8000.000000, 'USD', 'MONTH', 1),
(3, 'PROVIDER-3-QT', 'PROVIDER #3 额度', 100000000,  3000.000000, 'CNY', 'MONTH', 1),
(4, 'APP-1-QT',      'APP #1 额度',        5000000,   500.000000, 'USD', 'MONTH', 1),
(5, 'APP-2-QT',      'APP #2 额度',         200000,    20.000000, 'USD', 'DAY',   1);

INSERT IGNORE INTO kb_resource_traffic_policies (id, resource_type, resource_code, rate_limit_policy_code) VALUES
(1, 'PROVIDER', 'openai-default', NULL),
(2, 'PROVIDER', 'anthropic-default', NULL),
(3, 'PROVIDER', 'deepseek-default', NULL),
(6, 'PROVIDER', 'qwen-default', NULL),
(4, 'APP', 'app_001', NULL),
(5, 'APP', 'app_002', NULL),
(7, 'MODEL', 'gpt-4o', 'MODEL-1-RL'),
(8, 'MODEL', 'claude-sonnet-4-20250514', 'MODEL-4-RL'),
(9, 'ROUTING_RULE', 'gpt4o-default', 'ROUTING_RULE-1-RL');

-- 额度走子表：一个资源可挂多套不同统计窗口的额度策略（每窗口一份 token + 成本上限）
INSERT IGNORE INTO kb_resource_quota_bindings (resource_type, resource_code, quota_policy_code) VALUES
('PROVIDER', 'openai-default', 'PROVIDER-1-QT'),
('PROVIDER', 'anthropic-default', 'PROVIDER-2-QT'),
('PROVIDER', 'deepseek-default', 'PROVIDER-3-QT'),
('PROVIDER', 'qwen-default', 'PROVIDER-3-QT'),
('APP', 'app_001', 'APP-1-QT'),
('APP', 'app_002', 'APP-2-QT');

-- 供应商凭证（认证配置整体以 AES-GCM 密文保存；前缀 ENC:）
-- 密文解密后必须是 JSON 对象形态（如 {"apiKey":"sk-..."}），与 ProviderCredentialSupport.saveAuthConfig
-- 的写入契约一致；旧版种子密文存的是裸密钥字符串，会导致 JsonKit.fromJson 解析失败并静默降级为空 apiKey。
-- 注意：密文由 KNOT_CREDENTIAL_ENCRYPTION_KEY 派生密钥加密，更换密钥后必须重新生成（见
-- docs/database/migration/2026-09-23-fix-provider-credential-config-format.sql 的生成说明）。
INSERT IGNORE INTO kb_provider_credentials (id, provider_account_code, credential_type, auth_applier, encrypted_config, status) VALUES
(1, 'openai-default', 'api-key', 'BEARER', 'ENC:v2:1na94bnxGhusl+EaNaQZSZneQAWMS1Cr6KU8ADFSxZlrrn6dYGvvAENb/SFRGeyPIyP4l2xJLCdMJIDy8ixFxF54BldGzycpz57QyXs=', 1),
(2, 'anthropic-default', 'api-key', 'ANTHROPIC_API_KEY', 'ENC:v2:iLhkQvqvTW3/dlz82dKa2so3n9NNHdfWoj7MAI4FSnRoBJ0GcDyrr1SNeQHSwj6yBGH0Eudnq5cpsSVd7OSzVGvv175De8wvz15/gtKKW+k=', 1),
(3, 'deepseek-default', 'api-key', 'BEARER', 'ENC:v2:Peyhq6iUxlogc923e8TG+uvHLyJgRm0iJNTusPvs/xVOwdBEBE9v4Qs3vN7/1Dx1lfxC1FpGfbL7S2EhTBePSLN6zemUGIIoYyvpt/r9gg==', 1),
-- ⚠ 占位凭据：AK/SK 是假值，上线前必须在管理台换成真实密钥（密文由开发默认密钥加密，
-- 同其它种子；生产环境换了 KNOT_CREDENTIAL_ENCRYPTION_KEY 后须重新生成）
(4, 'aws-bedrock-runtime', 'aws-auth', 'AWS_SIGV4', 'ENC:v2:RBcQiZXA4xtu1JzdUeeUYK0N8McJfBysNTivpk84EbZtuX6w2y+og03WhpCBSMeOGRgSOQi3xrwLtD8QJvd8woJFeJU2Ywqk7VyCMzKb2DMTemI5TyHsr9sO3H3h23MwMBQpWnsc/ZTZ2cO5xyCcpZtpgQM+lENnniJKzWhJ8gFS1UayJqyFMpc15+dN+JpWJ59XhEE3FrM61/jLnDwwZYECvwTOI5A=', 1),
(5, 'aws-bedrock-mantle', 'aws-auth', 'AWS_SIGV4', 'ENC:v2:i3tPFtFrDBtlI7tOb528ptatgvdcq146hlSMHrFP2hvjRktj64+md+piG6HQxP2w6WMLshEjAZe2G0Ja+S30mSf9uD9b1/XBYBykWm1q+UxVS/aBZFoV9spWbcwrSiAtn1bw4AFPH7q1FJSXZU542CHyH2s8zDDZhWkymGQxntEU79egcNHkdopmfqopvnrs65bv38fjSChprOWE+/LvrPOSqxWoHw==', 1);

-- 折扣策略（绑定供应商模型业务码 kb_models.model_code）
-- 折扣是模型级概念：同一供应商账户下的不同模型可以有不同折扣，故种子特意让
-- openai-default 下的 gpt-4o 与 gpt-4o-mini 各持一条不同策略，用于验证该语义。
INSERT IGNORE INTO kb_provider_discount_policies (id, model_code, discount_type, discount_value, effective_from, status) VALUES
(1, 'gpt-4o',            'PERCENTAGE', 0.9000, NOW(), 1),
(2, 'gpt-4o-mini',       'PERCENTAGE', 0.7500, NOW(), 1),
(3, 'claude-sonnet-4-20250514', 'PERCENTAGE', 0.8000, NOW(), 1),
(4, 'deepseek-chat',     'FIXED',      5.0000, NOW(), 1);

-- 模型
-- 上游 host 一律来自 kb_provider_accounts.base_url；base_path 只放模型级路径前缀
-- （如供应商给每个模型分配的 endpoint_id），空串表示无前缀。
INSERT IGNORE INTO kb_models (id, provider_account_code, model_code, upstream_model, version, base_path, status) VALUES
(1, 'openai-default', 'gpt-4o', 'gpt-4o', '2024-08-06', '', 1),
(2, 'openai-default', 'gpt-4o-mini', 'gpt-4o-mini', '2024-07-18', '', 1),
(3, 'openai-default', 'text-embedding-3-large', 'text-embedding-3-large', '2024-01-01', '', 1),
(4, 'anthropic-default', 'claude-sonnet-4-20250514', 'claude-sonnet-4-20250514', '2025-05-14', '', 1),
(5, 'anthropic-default', 'claude-haiku-3-5-20241022', 'claude-haiku-3-5-20241022', '2024-10-22', '', 1),
(6, 'deepseek-default', 'deepseek-chat', 'deepseek-chat', '2024-08-01', '', 1),
(7, 'deepseek-default', 'deepseek-reasoner', 'deepseek-reasoner', '2025-01-20', '', 1),
(8, 'qwen-default', 'qwen-image', 'qwen-image', '2025-08-01', '', 1),
(9, 'qwen-default', 'qwen-image-edit', 'qwen-image-edit', '2025-08-01', '', 1),
(10, 'openai-default', 'gpt-image-1', 'gpt-image-1', '2025-04-01', '', 1),
-- Bedrock：账户 base_url 留 NULL，host 由凭据里的 region + endpointType 派生
(11, 'aws-bedrock-runtime', 'bedrock-claude-sonnet-4', 'anthropic.claude-sonnet-4-20250514-v1:0', '2025-05-14', '', 1),
(12, 'aws-bedrock-mantle', 'bedrock-gpt56-luna', 'openai.gpt-5.6-luna', '2026-01-01', '', 1);

INSERT IGNORE INTO kb_model_pools (id, pool_code, name, logical_model_code, selection_strategy, status, remark) VALUES
(1, 'chat-premium-pool', 'Premium Chat Pool', 'knot-chat-premium', 'WEIGHTED', 1, 'Premium chat routing pool'),
(2, 'chat-economy-pool', 'Economy Chat Pool', 'knot-chat-economy', 'WEIGHTED', 1, 'Economy chat routing pool');

INSERT IGNORE INTO kb_model_pool_items (id, pool_code, model_code, weight, priority, status) VALUES
(1, 'chat-premium-pool', 'gpt-4o', 70, 10, 1),
(2, 'chat-premium-pool', 'claude-sonnet-4-20250514', 30, 20, 1),
(3, 'chat-economy-pool', 'gpt-4o-mini', 60, 10, 1),
(4, 'chat-economy-pool', 'deepseek-chat', 40, 20, 1);

INSERT IGNORE INTO kb_logical_models (
  id, model_code, model_name, model_type, model_family, description,
  tags_json, use_cases_json, context_window, max_output_tokens,
  input_modalities_json, output_modalities_json, languages_json,
  visibility, publish_status, status, sort_order, featured,
  remark
) VALUES
(1, 'knot-chat-premium', 'Knot Chat Premium', 'CHAT', NULL,
  'A logical chat model that routes to premium provider models by policy.',
  JSON_ARRAY('chat', 'reasoning', 'premium'),
  JSON_ARRAY('knowledge assistant', 'research', 'complex analysis'),
  200000, 8192, JSON_ARRAY('text', 'image'), JSON_ARRAY('text'), JSON_ARRAY('zh-CN', 'en-US'),
  'PUBLIC', 2, 1, 10, 1, 'Premium provider route'),
(2, 'knot-chat-economy', 'Knot Chat Economy', 'CHAT', NULL,
  'A logical chat model that routes to economical provider models.',
  JSON_ARRAY('chat', 'economy'),
  JSON_ARRAY('customer service', 'daily assistant'),
  128000, 4096, JSON_ARRAY('text'), JSON_ARRAY('text'), JSON_ARRAY('zh-CN', 'en-US'),
  'PUBLIC', 2, 1, 20, 0, 'Economy provider route');

INSERT IGNORE INTO kb_provider_model_mappings (
  id, logical_model_code, provider_account_code, model_code, provider_model_name, status, priority
) VALUES
(1, 'knot-chat-premium', 'openai-default', 'gpt-4o', 'gpt-4o', 1, 10),
(2, 'knot-chat-premium', 'anthropic-default', 'claude-sonnet-4-20250514', 'claude-sonnet-4-20250514', 1, 20),
(3, 'knot-chat-economy', 'openai-default', 'gpt-4o-mini', 'gpt-4o-mini', 1, 10),
(4, 'knot-chat-economy', 'deepseek-default', 'deepseek-chat', 'deepseek-chat', 1, 20);

INSERT IGNORE INTO kx_model_sources (
  id, source_code, source_name, source_url, api_url, source_type, status
) VALUES
(1, 'OPENROUTER', 'OpenRouter Models', 'https://openrouter.ai/models',
  'https://openrouter.ai/api/v1/models', 'MODEL_CATALOG', 1);

-- 模型 API 协议绑定（usage_extractor 为 Usage 解析器编码或类名；request_adapter 为 UpstreamAdapter code）
-- ⚠ 2026-10-25 适配器重命名：OPENAI_COMPATIBLE/ANTHROPIC/BEDROCK_CONVERSE 三个旧 code 已废弃，
--   新值见 docs/database/migration/2026-10-25-upstream-adapter-rename.sql（新库直接用新值播种）
INSERT IGNORE INTO kb_model_api_bindings (id, model_code, protocol, api_path, request_adapter, usage_extractor, status, remark) VALUES
(1, 'gpt-4o', 'CHAT_COMPLETIONS', '/v1/chat/completions', 'PASSTHROUGH', 'DEFAULT-TOKEN', 1, 'GPT-4o Chat Completions'),
(2, 'gpt-4o-mini', 'CHAT_COMPLETIONS', '/v1/chat/completions', 'PASSTHROUGH', 'DEFAULT-TOKEN', 1, 'GPT-4o Mini Chat Completions'),
-- claude-sonnet-4 上游即 Anthropic Messages 端点：请求体与响应都无需协议转换，绑 PASSTHROUGH
-- （旧 ANTHROPIC 适配器在 MESSAGES 协议下同样是纯透传，行为等价；转换职责归 ChatCompletionsToMessages 一族）
(3, 'claude-sonnet-4-20250514', 'MESSAGES', '/v1/messages', 'PASSTHROUGH', 'ANTHROPIC', 1, 'Claude Sonnet Messages API'),
(4, 'deepseek-chat', 'CHAT_COMPLETIONS', '/v1/chat/completions', 'PASSTHROUGH', 'DEFAULT-TOKEN', 1, 'DeepSeek Chat'),
-- 图像家族绑定显式配 IMAGE 提取器（2026-10-24 改进清单 2.2b：删 protocol→extractor 猜测后，
-- 这些行原先靠「IMAGE_* 协议猜 IMAGE 提取器」才走对计费口径，现改为显式配置，删猜测零行为变化）
(5, 'qwen-image', 'IMAGE_GENERATIONS', '/api/v1/services/aigc/multimodal-generation/generation', 'QWEN', 'IMAGE', 1, 'Qwen Image Generation'),
(6, 'qwen-image-edit', 'IMAGE_EDITS', '/api/v1/services/aigc/multimodal-generation/generation', 'QWEN', 'IMAGE', 1, 'Qwen Image Edit'),
(7, 'gpt-image-1', 'IMAGE_GENERATIONS', '/v1/images/generations', 'PASSTHROUGH', 'IMAGE', 1, 'OpenAI Image Generation'),
(8, 'gpt-image-1', 'IMAGE_EDITS', '/v1/images/edits', 'PASSTHROUGH', 'IMAGE', 1, 'OpenAI Image Edit'),
-- Bedrock runtime：api_path 留 NULL，由 AWS_BEDROCK_CHAT_COMPLETIONS_TO_CONVERSE 适配器按 /model/{模型ID}/converse 推导（模型 ID 里的 : 会编码成 %3A）
--   usage_extractor 走 DEFAULT-TOKEN：整包路径的用量提取吃的是 handleResponse 的产物，
--   usage 已被适配器转成 OpenAI snake_case 形状，通用提取器别名表可直接读出（2026-10-25 删专用提取器）
(9, 'bedrock-claude-sonnet-4', 'CHAT_COMPLETIONS', NULL, 'AWS_BEDROCK_CHAT_COMPLETIONS_TO_CONVERSE', 'DEFAULT-TOKEN', 1, 'Bedrock Converse（runtime 端点）'),
-- Bedrock mantle：不支持 Converse，只暴露 OpenAI 兼容，故直接复用 PASSTHROUGH 适配器（零新适配器代码）
(10, 'bedrock-gpt56-luna', 'CHAT_COMPLETIONS', NULL, 'PASSTHROUGH', 'DEFAULT-TOKEN', 1, 'Bedrock mantle OpenAI 兼容端点');

-- =========================
-- 应用管理
-- =========================

-- 应用
INSERT IGNORE INTO kb_apps (id, app_code, name, dept_code, owner_username, remark, status) VALUES
(1, 'app_001', '内部知识库助手', 'HQ', 'admin', '面向内部员工的知识检索与问答', 1),
(2, 'app_002', '客服对话系统', 'OPS', 'zhangsan', '对外客服场景的对话接入', 1),
(3, 'app_003', '代码审查工具', 'RND', 'lisi', '研发流程中的代码审查辅助', 1);

-- =========================
-- 路由规则
-- =========================

INSERT IGNORE INTO kb_routing_consumers (id, consumer_code, name, user_username, secret_key, return_usage_detail, status) VALUES
(1, 'consumer-internal-kb', '内部知识库助手消费者', 'admin', 'sk-demo-gpt4o-routing-key-001', 0, 1),
(2, 'consumer-research', '模型评测消费者', 'admin', 'sk-demo-claude-routing-key-002', 0, 1),
(3, 'consumer-cs', '客服系统消费者', 'zhangsan', 'sk-demo-deepseek-routing-key-003', 0, 1);

INSERT IGNORE INTO kb_routing_rules (id, rule_code, name, app_scenario, app_code, username, consumer_code, status) VALUES
(1, 'gpt4o-default', 'GPT-4o默认路由', '知识库问答', 'app_001', 'admin', 'consumer-internal-kb', 1),
(2, 'claude-default', 'Claude默认路由', '模型评测', 'app_001', 'admin', 'consumer-research', 1),
(3, 'deepseek-lowcost', 'DeepSeek低成本路由', '客服对话', 'app_002', 'zhangsan', 'consumer-cs', 1);

INSERT IGNORE INTO kb_routing_rule_targets (id, rule_code, target_type, target_code, priority, is_primary)
SELECT 1, 'gpt4o-default',    'MODEL_POOL', mp.pool_code, 100, 1 FROM kb_model_pools mp WHERE mp.id = 1
UNION ALL
SELECT 2, 'gpt4o-default',    'MODEL', m.model_code, 90, 0 FROM kb_models m WHERE m.id = 2
UNION ALL
SELECT 3, 'claude-default',   'MODEL', m.model_code, 100, 1 FROM kb_models m WHERE m.id = 4
UNION ALL
SELECT 4, 'deepseek-lowcost', 'MODEL_POOL', mp.pool_code, 100, 1 FROM kb_model_pools mp WHERE mp.id = 2;

-- =========================
-- JEV 计费裁判预置（路由规则测试抽屉「计费校验」tab 的裁判链路）
-- =========================
-- 裁判专用链路，与被测规则完全隔离：独立账户 / 模型 / 绑定 / 规则 / 消费者。
-- ⚠ id 隔离（2026-10-23 整理）：本段所有显式 id 必须避开户主块（供应商/模型/路由区）已占用的 id；
--   否则 INSERT IGNORE 会把同 id 行静默跳过，导致裁判链路数据缺失。曾发生 id=5 / id=11 / id=9 撞主块的冲突。
-- 缺任一项 → 裁判区 available:false + 可执行提示，不炸测试链路（设计稿 §12.1）。
--
-- ⚠ 凭证纪律（§6.1）：消费者 secret_key 是裁判唯一的 Bearer 凭据，
--   仅经 Authorization 头进入 DecisionsTransport，不进请求体、不进日志、不下前端。
--   占位密钥仅为本地/冒烟可跑；接真实 OpenRouter 须换成 management key
--   （普通 key 调DECISIONS 端点会 403）。换钥在管理台「消费者」页改，无需动种子。

-- 1) 供应商账户：OpenRouter 承载TypeSafe JEV 模型
--    openrouter provider 已在供应商种子中（id=6），此处只补账户。
INSERT IGNORE INTO kb_provider_accounts (id, provider_code, code, base_url, status) VALUES
(7, 'openrouter', 'openrouter-jev', 'https://openrouter.ai/api/v1', 1);

-- 2) 模型：DECISIONS 协议专用（JEV 只吃 decisions 入参，不参与对话路由）
INSERT IGNORE INTO kb_models (id, provider_account_code, model_code, upstream_model, version, base_path, status, remark) VALUES
(13, 'openrouter-jev', 'jev-billing-judge', 'typesafe/jev-1.13-20260917', '2026-09-17', '', 1,
 'JEV 计费裁判专用模型；仅供 jev-billing-judge 规则使用');

-- 3) 模型 API 绑定：DECISIONS 协议（入参 state/questions，出参 answers 映射）
--    api_path 留空：走协议默认路径，由网关 DECISIONS 端点统一路由。
INSERT IGNORE INTO kb_model_api_bindings (id, model_code, protocol, api_path, request_adapter, usage_extractor, status, remark) VALUES
(11, 'jev-billing-judge', 'DECISIONS', NULL, 'PASSTHROUGH', 'DEFAULT-TOKEN', 1,
 'JEV 决策判定绑定；questions 支持 noul(判定)/choice(单选)/score(有序评分)');

-- 4) 路由规则：裁判规则（配置 knot.jev.judge-rule-code 指向本rule_code）
INSERT IGNORE INTO kb_routing_rules (id, rule_code, name, app_scenario, app_code, username, consumer_code, status) VALUES
(4, 'jev-billing-judge', 'JEV计费裁判规则', '计费校验辅助判定', NULL, 'admin', 'jev-judge-client', 1);

INSERT IGNORE INTO kb_routing_rule_targets (id, rule_code, target_type, target_code, priority, is_primary)
SELECT 5, 'jev-billing-judge', 'MODEL', m.model_code, 100, 1 FROM kb_models m WHERE m.id = 13;

-- 5) 消费者：裁判专用
--    return_usage_detail=0：裁判响应是判定结论，不需要回传上游用量明细。
INSERT IGNORE INTO kb_routing_consumers (id, consumer_code, name, user_username, secret_key, return_usage_detail, status) VALUES
(4, 'jev-judge-client', 'JEV计费裁判消费者', 'admin', 'sk-demo-jev-judge-key-004', 0, 1);

-- 路由调试预设请求用例
-- 请求体中的 model 会由调试面板按所选目标覆盖；messages/input/prompt 等内容可直接编辑后测试。
-- 音频转录/翻译与图片编辑/变体需要本地文件，请将示例占位路径替换为实际文件路径。

INSERT IGNORE INTO kb_test_request_presets (
  code, name, protocol_code, request_body, remark, status
) VALUES
('builtin-chat-completions-stream', '标准对话（Chat Completions，流式）', 'CHAT_COMPLETIONS',
  '{"model":"model-name","messages":[{"role":"user","content":"你好，请用一句话介绍这个模型。"}],"temperature":0.2,"max_tokens":256,"stream":true,"stream_options":{"include_usage":true}}',
  '适用于 OpenAI 兼容的 SSE 对话接口；通过 stream_options 请求流式 usage。', 1),
('builtin-chat-completions-basic', '标准对话（Chat Completions，非流式）', 'CHAT_COMPLETIONS',
  '{"model":"model-name","messages":[{"role":"user","content":"你好，请用一句话介绍这个模型。"}],"temperature":0.2,"max_tokens":256,"stream":false}',
  '适用于 OpenAI 兼容的对话接口。', 1),
('builtin-responses-stream', '标准响应（Responses，流式）', 'RESPONSES',
  '{"model":"model-name","input":"你好，请用一句话介绍这个模型。","max_output_tokens":256,"stream":true}',
  '适用于 OpenAI Responses SSE 接口。', 1),
('builtin-responses-basic', '标准响应（Responses，非流式）', 'RESPONSES',
  '{"model":"model-name","input":"你好，请用一句话介绍这个模型。","max_output_tokens":256,"stream":false}',
  '适用于 OpenAI Responses 接口。', 1),
('builtin-messages-stream', '消息对话（Messages，流式）', 'MESSAGES',
  '{"model":"model-name","messages":[{"role":"user","content":"你好，请用一句话介绍这个模型。"}],"max_tokens":256,"temperature":0.2,"stream":true}',
  '适用于 Anthropic Messages SSE 接口。', 1),
('builtin-messages-basic', '消息对话（Messages，非流式）', 'MESSAGES',
  '{"model":"model-name","messages":[{"role":"user","content":"你好，请用一句话介绍这个模型。"}],"max_tokens":256,"temperature":0.2,"stream":false}',
  '适用于 Anthropic Messages 接口。', 1),
('builtin-completions-stream', '文本补全（Completions，流式）', 'COMPLETIONS',
  '{"model":"model-name","prompt":"请完成这句话：人工智能正在","max_tokens":64,"temperature":0.2,"stream":true}',
  '适用于传统文本补全 SSE 接口。', 1),
('builtin-completions-basic', '文本补全（Completions，非流式）', 'COMPLETIONS',
  '{"model":"model-name","prompt":"请完成这句话：人工智能正在","max_tokens":64,"temperature":0.2,"stream":false}',
  '适用于传统文本补全接口。', 1),
('builtin-embeddings-basic', '文本向量（Embeddings）', 'EMBEDDINGS',
  '{"model":"model-name","input":["这是一段用于向量化的示例文本。"],"encoding_format":"float"}',
  '适用于文本向量化接口。', 1),
('builtin-image-generations-basic', '文生图（Image Generations）', 'IMAGE_GENERATIONS',
  '{"model":"model-name","prompt":"一只坐在窗边读书的橘猫，柔和自然光，简洁插画风格","n":1,"size":"1024x1024"}',
  '适用于文生图接口。', 1),
('builtin-image-edits-file', '图片编辑（Image Edits）', 'IMAGE_EDITS',
  '{"model":"model-name","prompt":"将背景替换为晴朗的蓝天","image":"/tmp/knot-example.png","size":"1024x1024"}',
  '请将 image 替换为可访问的本地图片路径；调试请求会以 multipart 发送。', 1),
('builtin-image-variations-file', '图片变体（Image Variations）', 'IMAGE_VARIATIONS',
  '{"model":"model-name","image":"/tmp/knot-example.png","n":1,"size":"1024x1024"}',
  '请将 image 替换为可访问的本地图片路径。', 1),
('builtin-audio-transcriptions-file', '音频转录（Audio Transcriptions）', 'AUDIO_TRANSCRIPTIONS',
  '{"model":"model-name","file":"/tmp/knot-example.mp3","language":"zh","response_format":"json"}',
  '请将 file 替换为实际音频文件路径。', 1),
('builtin-audio-translations-file', '音频翻译（Audio Translations）', 'AUDIO_TRANSLATIONS',
  '{"model":"model-name","file":"/tmp/knot-example.mp3","response_format":"json"}',
  '请将 file 替换为实际音频文件路径。', 1),
('builtin-audio-speech-stream', '语音合成（Audio Speech，流式）', 'AUDIO_SPEECH',
  '{"model":"model-name","input":"你好，这是一段语音合成测试。","voice":"alloy","stream":true,"stream_format":"sse"}',
  '适用于支持 SSE 音频输出的语音合成接口。', 1),
('builtin-audio-speech-basic', '语音合成（Audio Speech，非流式）', 'AUDIO_SPEECH',
  '{"model":"model-name","input":"你好，这是一段语音合成测试。","voice":"alloy","response_format":"mp3","stream":false}',
  '适用于语音合成接口。', 1),
('builtin-video-generations-basic', '视频生成（Video Generations）', 'VIDEO_GENERATIONS',
  '{"model":"model-name","prompt":"一条小路穿过春天的森林，镜头缓慢向前推进","size":"1280x720","duration":5}',
  '适用于视频生成接口。', 1),
('builtin-rerank-basic', '文档重排（Rerank）', 'RERANK',
  '{"model":"model-name","query":"网关如何选择上游模型？","documents":["网关根据路由规则选择模型。","计费规则负责计算调用成本。","预设请求用于快速调试接口。"],"top_n":2,"return_documents":true}',
  '适用于文档重排接口。', 1),
('builtin-moderations-basic', '内容审核（Moderations）', 'MODERATIONS',
  '{"model":"model-name","input":"这是一段用于内容安全审核的示例文本。"}',
  '适用于内容审核接口。', 1),
('builtin-decisions-basic', '决策判定（Decisions）', 'DECISIONS',
  '{"model":"model-name","state":{"customer_tier":"enterprise","ticket":"点击支付后结算页空白，换了两个浏览器都一样。"},"questions":{"is_bug":{"type":"noul","instructions":"客户是否在报告软件缺陷？","criteria":{"true":"客户描述了损坏或异常的产品行为。","false":"客户在提问或请求新功能。"}},"team":{"type":"choice","instructions":"这个工单应该由哪个团队负责？","criteria":{"account":"登录、权限或资料相关问题。","payments":"结算、账单或支付处理问题。"}},"urgency":{"type":"score","instructions":"这个工单有多紧急？","criteria":["可以等到下一个版本","本周内应修复","正在阻塞营收"]}}}',
  '适用于 AlphaDecisions 决策接口（如 typesafe/jev）：入参为待评估 state 与 questions，出参为 answers 映射。questions 支持 noul（判定）/choice（单选）/score（有序评分）三类。', 1);

-- =========================
-- 计费规则
-- =========================

-- 两表模型：规则主体只存身份与绑定；价格配置全部进版本 config_json
-- model_family 取 ks_enum_configs(category='model_family') 的 item_code；NULL = 默认规则，覆盖所有族
INSERT IGNORE INTO kb_billing_rules (id, code, model_family, status) VALUES
(1, 'TOKEN_GPT4O',      'gpt',      1),
(2, 'TOKEN_GPT4O_MINI', 'gpt',      1),
(3, 'TOKEN_CLAUDE_S4',  'claude',   1),
(4, 'TOKEN_DEEPSEEK',   'deepseek', 1),
(5, 'EMBEDDING',        NULL,       1);

INSERT IGNORE INTO kb_billing_rule_versions (id, rule_code, version_code, uniq_hash, billing_mode, pricing_plan, currency, unit, config_json, status, effective_from) VALUES
(1, 'TOKEN_GPT4O',      'v1', 'seed-token-gpt4o-v1',      'TOKEN',     'FIXED', 'USD', '1K_TOKENS', '{"basePrices":{"cacheRead":0.00125,"cacheWrite":0.005,"input":0.005,"output":0.015}}', 1, NOW()),
(2, 'TOKEN_GPT4O_MINI', 'v1', 'seed-token-gpt4o-mini-v1', 'TOKEN',     'FIXED', 'USD', '1K_TOKENS', '{"basePrices":{"cacheRead":0.000025,"cacheWrite":0.00015,"input":0.00015,"output":0.0006}}', 1, NOW()),
(3, 'TOKEN_CLAUDE_S4',  'v1', 'seed-token-claude-s4-v1',  'TOKEN',     'FIXED', 'USD', '1K_TOKENS', '{"basePrices":{"cacheRead":0.0003,"cacheWrite":0.00375,"input":0.003,"output":0.015}}', 1, NOW()),
(4, 'TOKEN_DEEPSEEK',   'v1', 'seed-token-deepseek-v1',   'TOKEN',     'FIXED', 'USD', '1K_TOKENS', '{"basePrices":{"cacheRead":0.000014,"cacheWrite":0.00014,"input":0.00014,"output":0.00028}}', 1, NOW()),
(5, 'EMBEDDING',        'v1', 'seed-embedding-v1',        'EMBEDDING', 'FIXED', 'USD', '1K_TOKENS', '{"defaultUnitPrice":0.00013}', 1, NOW());

-- =========================
-- 扩展能力
-- =========================

-- 定时任务（handler_code 关联任务处理器；DISABLED 的不参与调度）
INSERT IGNORE INTO ks_scheduled_tasks (
  id, task_code, task_name, handler_code, cron_expression, execution_mode, status, description
) VALUES
(1, 'operation-log-retention', '操作日志保留清理', 'OPERATION_LOG_RETENTION', '0 0 3 * * ?', 'SINGLE', 1, '操作日志最多保留三个月'),
(2, 'schedule-run-retention', '定时任务执行记录清理', 'SCHEDULE_RUN_RETENTION', '0 30 3 * * ?', 'SINGLE', 1, '定时任务执行记录最多保留一个月'),
(3, 'openrouter-model-sync', 'OpenRouter 模型同步', 'OPENROUTER_MODEL_SYNC', '0 0 4 * * ?', 'SINGLE', 0, '同步 OpenRouter 模型到外部模型库');

-- 插件
-- 插件管理已单表收口（2026-10-13）：packages / capabilities / bindings 三表删除，
-- 展示信息与执行阶段已反范式冗余进 kb_plugin_instances。status 改为 EnabledStatusEnum(1/0)，
-- orphaned/is_deleted 初始均为 0（启动期 PluginReconcileRunner 按 entrypoint 校正）。
INSERT IGNORE INTO kb_plugin_instances (
  id, plugin_code, instance_code, instance_name, plugin_name, entrypoint, source_type, version,
  capability_code, capability_name, extension_point, stage_code, order_no,
  config_json, status, timeout_ms, concurrency_limit, plugin_type, is_deleted, orphaned
) VALUES
(1, 'builtin-gateway-audit', 'gateway-request-log', '网关请求日志插件', '网关请求响应审计插件',
  'org.chobit.knot.gateway.plugin.builtin.GatewayRequestLoggingPlugin', 'BUILTIN', '1.0.0',
  'gateway-request-response-log', '网关请求响应日志', 'GATEWAY_EXCHANGE', 'GATEWAY_REQUEST', 100,
  NULL, 1, 3000, 0, 'SYNC', 0, 0),
(2, 'builtin-gateway-audit', 'gateway-response-log', '网关响应日志插件', '网关请求响应审计插件',
  'org.chobit.knot.gateway.plugin.builtin.GatewayResponseLoggingPlugin', 'BUILTIN', '1.0.0',
  'gateway-request-response-log', '网关请求响应日志', 'GATEWAY_EXCHANGE', 'GATEWAY_RESPONSE', 100,
  NULL, 1, 3000, 0, 'SYNC', 0, 0),
(3, 'builtin-gateway-audit', 'gateway-error-log', '网关异常日志插件', '网关请求响应审计插件',
  'org.chobit.knot.gateway.plugin.builtin.GatewayErrorLoggingPlugin', 'BUILTIN', '1.0.0',
  'gateway-request-response-log', '网关请求响应日志', 'GATEWAY_EXCHANGE', 'GATEWAY_ERROR', 100,
  NULL, 1, 3000, 0, 'SYNC', 0, 0),
(4, 'builtin-provider-audit', 'provider-request-log', '上游请求日志插件', '上游请求响应审计插件',
  'org.chobit.knot.gateway.plugin.builtin.UpstreamRequestLoggingPlugin', 'BUILTIN', '1.0.0',
  'provider-request-response-log', '上游请求响应日志', 'UPSTREAM_EXCHANGE', 'UPSTREAM_REQUEST', 100,
  NULL, 1, 3000, 0, 'SYNC', 0, 0),
(5, 'builtin-provider-audit', 'provider-response-log', '上游响应日志插件', '上游请求响应审计插件',
  'org.chobit.knot.gateway.plugin.builtin.UpstreamResponseLoggingPlugin', 'BUILTIN', '1.0.0',
  'provider-request-response-log', '上游请求响应日志', 'UPSTREAM_EXCHANGE', 'UPSTREAM_RESPONSE', 100,
  NULL, 1, 3000, 0, 'SYNC', 0, 0),
(6, 'builtin-provider-audit', 'provider-error-log', '上游异常日志插件', '上游请求响应审计插件',
  'org.chobit.knot.gateway.plugin.builtin.UpstreamErrorLoggingPlugin', 'BUILTIN', '1.0.0',
  'provider-request-response-log', '上游请求响应日志', 'UPSTREAM_EXCHANGE', 'UPSTREAM_ERROR', 100,
  NULL, 1, 3000, 0, 'SYNC', 0, 0),
-- 敏感词审核（order_no=200 > 审计日志 100：被拦截的请求/响应也先落审计，再由本插件抛 400）
(7, 'builtin-moderation', 'sensitive-word-request-check', '敏感词入站检测插件', '敏感词审核插件',
  'org.chobit.knot.gateway.plugin.moderation.SensitiveWordRequestPlugin', 'BUILTIN', '1.0.0',
  'sensitive-word-moderation', '敏感词审核', 'GATEWAY_EXCHANGE', 'GATEWAY_REQUEST', 200,
  NULL, 1, 3000, 0, 'SYNC', 0, 0),
(8, 'builtin-moderation', 'sensitive-word-response-check', '敏感词出站检测插件', '敏感词审核插件',
  'org.chobit.knot.gateway.plugin.moderation.SensitiveWordResponsePlugin', 'BUILTIN', '1.0.0',
  'sensitive-word-moderation', '敏感词审核', 'GATEWAY_EXCHANGE', 'GATEWAY_RESPONSE', 200,
  NULL, 1, 3000, 0, 'SYNC', 0, 0);

-- 通知模板
INSERT IGNORE INTO kb_notification_templates (id, code, name, channel, title_tpl, content_tpl, status) VALUES
(1, 'QUOTA_ALERT', '配额告警', 'EMAIL', '配额告警: {{appName}}', '应用 {{appName}} 的配额已使用 {{percent}}%，请及时处理。', 1),
(2, 'ERROR_ALERT', '异常告警', 'EMAIL', '异常告警: {{providerName}}','供应商 {{providerName}} 错误率超过阈值，当前 {{errorRate}}%。', 1),
(3, 'CREDENTIAL_EXPIRE','凭证过期提醒','EMAIL','凭证即将过期: {{providerName}}','供应商 {{providerName}} 的 API Key 将在 {{expireDate}} 过期。', 1);

-- =========================
-- 枚举字典
-- =========================
-- 只有仍在 DB 维护的分类放这里；已迁代码枚举（/api/enum-options）的不再落库。

-- 枚举分类（与 ks_enum_configs.category_id 对应；is_system=1 表示系统内置分类）
-- 模型族枚举分类（category_id=28）：计费规则作用域与统一模型均引用该族 code（item_code）。
-- 该分类可动态维护，新增族在枚举管理页操作即可。
INSERT IGNORE INTO ks_enum_categories (id, category, category_name, is_system, is_enabled) VALUES
(5, 'scope_type', '折扣范围', 0, 1),
(6, 'discount_type', '折扣类型', 0, 1),
(10, 'channel', '通知渠道', 0, 1),
(28, 'model_family', '模型族', 0, 1),
(29, 'timezone', '时区', 0, 1),
(30, 'moderation_category', '敏感词分类', 1, 1);

-- 敏感词分类（category_id=30）：kb_moderation_word_lists.category_code 引用 item_code。
-- is_system=1 的分类其下枚举项不可删改（词条行已引用该 code）。
INSERT IGNORE INTO ks_enum_configs (category_id, item_code, item_label, sort_order, is_enabled) VALUES
(30, 'POLITICS', '涉政',   1, 1),
(30, 'VIOLENCE', '暴恐',   2, 1),
(30, 'PORN',     '色情',   3, 1),
(30, 'AD',       '广告推广', 4, 1),
(30, 'ABUSE',    '侮辱辱骂', 5, 1),
(30, 'CUSTOM',   '自定义',  6, 1);

-- 供应商类型（category 1）已移除：供应商品牌主数据以 kb_providers 表为唯一来源，
-- 前端下拉/标签走 useProviderTypeOptions（/api/provider-profiles）
-- 通用状态字典：仅保留运行态杂项（操作日志 SUCCESS/FAILURE 等）；
-- EntityStatusEnum（ENABLED/DISABLED/ACTIVE/INACTIVE/DELETED）已迁代码枚举 /api/enum-options
INSERT IGNORE INTO ks_enum_configs (category_id, item_code, item_label, sort_order, is_enabled) VALUES
(5, 'GLOBAL', '全局',   1, 1),
(5, 'MODEL',  '按模型', 2, 1),
(5, 'APP',    '按应用', 3, 1),
(6, 'PERCENTAGE', '百分比折扣', 1, 1),
(6, 'FIXED',      '固定金额',   2, 1),
(10, 'EMAIL',   '邮件',    1, 1),
(10, 'SMS',     '短信',    2, 1),
(10, 'WEBHOOK', 'Webhook', 3, 1),
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

-- 时区（category_id=29）：取值统一用 UTC 偏移（UTC 时区）；仅供「用户设置」展示与存储，
-- 不参与业务分支，故按「展示型枚举归 DB 维护」口径落在 ks_enum_configs。
INSERT IGNORE INTO ks_enum_configs (category_id, item_code, item_label, sort_order, is_enabled) VALUES
(29, 'UTC-12:00', 'UTC-12:00 国际日期变更线西（贝克岛）',   1, 1),
(29, 'UTC-11:00', 'UTC-11:00 美属萨摩亚（帕果帕果）',       2, 1),
(29, 'UTC-10:00', 'UTC-10:00 夏威夷（檀香山）',             3, 1),
(29, 'UTC-09:30', 'UTC-09:30 马克萨斯群岛',                 4, 1),
(29, 'UTC-09:00', 'UTC-09:00 阿拉斯加（安克雷奇）',         5, 1),
(29, 'UTC-08:00', 'UTC-08:00 太平洋时间（洛杉矶）',         6, 1),
(29, 'UTC-07:00', 'UTC-07:00 山地时间（丹佛）',             7, 1),
(29, 'UTC-06:00', 'UTC-06:00 中部时间（芝加哥）',           8, 1),
(29, 'UTC-05:00', 'UTC-05:00 东部时间（纽约）',             9, 1),
(29, 'UTC-04:00', 'UTC-04:00 大西洋时间（圣地亚哥）',      10, 1),
(29, 'UTC-03:30', 'UTC-03:30 纽芬兰（圣约翰斯）',          11, 1),
(29, 'UTC-03:00', 'UTC-03:00 巴西利亚（圣保罗）',          12, 1),
(29, 'UTC-02:00', 'UTC-02:00 费尔南多·迪诺罗尼亚',         13, 1),
(29, 'UTC-01:00', 'UTC-01:00 亚速尔群岛',                  14, 1),
(29, 'UTC+00:00', 'UTC+00:00 格林尼治标准时间（伦敦）',    15, 1),
(29, 'UTC+01:00', 'UTC+01:00 中欧时间（柏林）',            16, 1),
(29, 'UTC+02:00', 'UTC+02:00 东欧时间（雅典）',            17, 1),
(29, 'UTC+03:00', 'UTC+03:00 莫斯科',                      18, 1),
(29, 'UTC+03:30', 'UTC+03:30 德黑兰',                      19, 1),
(29, 'UTC+04:00', 'UTC+04:00 迪拜',                        20, 1),
(29, 'UTC+04:30', 'UTC+04:30 喀布尔',                      21, 1),
(29, 'UTC+05:00', 'UTC+05:00 塔什干',                      22, 1),
(29, 'UTC+05:30', 'UTC+05:30 印度标准时间（孟买）',        23, 1),
(29, 'UTC+05:45', 'UTC+05:45 加德满都',                    24, 1),
(29, 'UTC+06:00', 'UTC+06:00 达卡',                        25, 1),
(29, 'UTC+06:30', 'UTC+06:30 仰光',                        26, 1),
(29, 'UTC+07:00', 'UTC+07:00 曼谷 / 雅加达',               27, 1),
(29, 'UTC+08:00', 'UTC+08:00 北京时间（上海）',            28, 1),
(29, 'UTC+08:45', 'UTC+08:45 尤克拉',                      29, 1),
(29, 'UTC+09:00', 'UTC+09:00 东京 / 首尔',                 30, 1),
(29, 'UTC+09:30', 'UTC+09:30 阿德莱德',                    31, 1),
(29, 'UTC+10:00', 'UTC+10:00 悉尼 / 关岛',                 32, 1),
(29, 'UTC+10:30', 'UTC+10:30 豪勋爵岛',                    33, 1),
(29, 'UTC+11:00', 'UTC+11:00 所罗门群岛（霍尼亚拉）',      34, 1),
(29, 'UTC+12:00', 'UTC+12:00 新西兰（奥克兰）',            35, 1),
(29, 'UTC+12:45', 'UTC+12:45 查塔姆群岛',                  36, 1),
(29, 'UTC+13:00', 'UTC+13:00 萨摩亚（阿皮亚）',            37, 1),
(29, 'UTC+14:00', 'UTC+14:00 基里巴斯（圣诞岛）',          38, 1);

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

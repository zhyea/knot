-- 模型族独立维护入口（数据仍存 ks_enum_configs，category='model_family'）
-- 新增：模型管理模块下的「模型族」菜单 + model:model-family:* 权限 + API 绑定 + 角色授权。
-- 幂等：全部走 INSERT IGNORE / 条件插入，可重复执行。

-- 菜单（模型管理模块 module_id=2，排序 70）
INSERT IGNORE INTO ks_menus (id, module_id, parent_id, menu_code, menu_name, route_path, component_key, icon, sort_order, status) VALUES
(23, 2, NULL, 'model.model-families', '模型族', '/model-management/model-families', 'ModelFamilyView', 'Cpu', 70, 'ENABLED');

-- 权限
INSERT IGNORE INTO ks_permissions (id, permission_code, permission_name, permission_type, module_id, menu_id, status, built_in, remark) VALUES
(136, 'model:model-family:page', '模型族页面访问', 'PAGE', 2, 23, 'ENABLED', 1, NULL),
(137, 'model:model-family:view', '模型族查看', 'API', 2, 23, 'ENABLED', 1, NULL),
(138, 'model:model-family:create', '模型族创建', 'API', 2, 23, 'ENABLED', 1, NULL),
(139, 'model:model-family:update', '模型族更新', 'API', 2, 23, 'ENABLED', 1, NULL),
(140, 'model:model-family:enable', '模型族更新状态', 'API', 2, 23, 'ENABLED', 1, NULL),
(141, 'model:model-family:delete', '模型族删除', 'API', 2, 23, 'ENABLED', 1, NULL);

-- API 绑定（默认拒绝：未绑定接口一律 403）
INSERT IGNORE INTO ks_api_permission_bindings (id, permission_id, http_method, path_pattern, controller_class, status) VALUES
(168, 137, 'POST', '/api/model-families/list', 'ModelFamilyController', 'ENABLED'),
(169, 137, 'GET', '/api/model-families/check-code', 'ModelFamilyController', 'ENABLED'),
(170, 137, 'GET', '/api/model-families/{id}', 'ModelFamilyController', 'ENABLED'),
(171, 138, 'POST', '/api/model-families', 'ModelFamilyController', 'ENABLED'),
(172, 139, 'PUT', '/api/model-families/{id}', 'ModelFamilyController', 'ENABLED'),
(173, 140, 'PUT', '/api/model-families/{id}/status', 'ModelFamilyController', 'ENABLED'),
(174, 141, 'DELETE', '/api/model-families/{id}', 'ModelFamilyController', 'ENABLED');

-- OPERATOR 全量 / DEVELOPER 只读
INSERT IGNORE INTO ks_role_permissions (role_id, permission_id) VALUES
(2, 136),(2, 137),(2, 138),(2, 139),(2, 140),(2, 141),
(3, 136),(3, 137);

-- ADMIN 全量（与 data.sql 末尾权威块同口径）
INSERT IGNORE INTO ks_role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM ks_roles r
         CROSS JOIN ks_permissions p
WHERE r.code = 'ADMIN' AND p.id BETWEEN 136 AND 141;

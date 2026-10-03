-- 回滚：恢复 DB 枚举分类（2026-10-02）
-- 对应迁移：2026-10-02-retire-code-enums.sql
-- 仅在需要回退「10 个枚举迁代码枚举」时使用。恢复后前端仍走 /api/common/enums，
-- 本脚本仅恢复 DB 字典数据，不改变代码枚举的权威地位。
-- 注意：业务表若已写入代码枚举的 code（如 kb_logical_models.visibility='PUBLIC'），
--       恢复 DB 分类后这些 code 可重新被字典解析，无需额外处理。

INSERT IGNORE INTO ks_enum_categories (id, category, category_name, is_system, is_enabled) VALUES
(3,  'app_type',                     '应用类型',           0, 1),
(8,  'billing_unit',                 '计费单位',           0, 1),
(9,  'billing_currency',             '计费币种',           0, 1),
(12, 'plugin_scope_type',            '插件作用范围',       0, 1),
(15, 'status',                       '通用状态',           1, 1),
(16, 'logical_model_visibility',     '统一模型可见性',     0, 1),
(17, 'logical_model_publish_status', '统一模型发布状态',   0, 1),
(23, 'model_pool_selection_strategy','模型池选择策略',     1, 1),
(24, 'plugin_extension_point',       '插件扩展点',         0, 1),
(25, 'plugin_stage_code',            '插件执行阶段',       0, 1);

INSERT IGNORE INTO ks_enum_configs (category_id, item_code, item_label, sort_order, is_enabled) VALUES
(3,  'WEB',     'Web应用',   1, 1),
(3,  'MOBILE',  '移动应用',   2, 1),
(3,  'SERVICE', '微服务',     3, 1),
(3,  'OTHER',   '其他',      99, 1),
(8,  '1K_TOKENS',   '千 Token',   1, 1),
(8,  '1M_TOKENS',   '百万 Token', 2, 1),
(8,  'PER_TOKEN',   '单 Token',   3, 1),
(8,  'PER_REQUEST', '按请求',     4, 1),
(8,  'PER_IMAGE',   '按图片',     5, 1),
(8,  'PER_MINUTE',  '按分钟',     6, 1),
(8,  'PER_SECOND',  '按秒',       7, 1),
(8,  'CUSTOM',      '自定义',    99, 1),
(9,  'USD', 'USD', 1, 1),
(9,  'CNY', 'CNY', 2, 1),
(12, 'GLOBAL',   '全局',     1, 1),
(12, 'APP',      '应用',     2, 1),
(12, 'RULE',     '路由规则', 3, 1),
(12, 'PROVIDER', '供应商账户', 4, 1),
(12, 'MODEL',    '模型',     5, 1),
(12, 'POOL',     '模型池',   6, 1),
(15, 'ONLINE',    '在线',     4, 1),
(15, 'RUNNING',   '运行中',   5, 1),
(15, 'DRAFT',     '草稿',     6, 1),
(15, 'GENERATED', '已生成',   7, 1),
(15, 'SUCCESS',   '成功',     8, 1),
(15, 'FAILURE',   '失败',     9, 1),
(15, 'FAILED',    '失败(旧)', 10, 1),
(16, 'PUBLIC',    '公开', 1, 1),
(16, 'INTERNAL',  '内部', 2, 1),
(16, 'PRIVATE',   '私有', 3, 1),
(17, 'DRAFT',     '草稿',   1, 1),
(17, 'PUBLISHED', '已发布', 2, 1),
(17, 'ARCHIVED',  '已下架', 3, 1),
(23, 'WEIGHTED', '权重',   1, 1),
(23, 'PRIORITY', '优先级', 2, 1),
(23, 'RANDOM',   '随机',   3, 1),
(24, 'GATEWAY_EXCHANGE',   '网关请求处理链路', 1, 1),
(24, 'UPSTREAM_EXCHANGE',  '上游请求处理链路', 2, 1),
(25, 'GATEWAY_REQUEST',    '网关请求阶段', 1, 1),
(25, 'GATEWAY_RESPONSE',   '网关响应阶段', 2, 1),
(25, 'GATEWAY_ERROR',      '网关异常阶段', 3, 1),
(25, 'UPSTREAM_REQUEST',   '上游请求阶段', 4, 1),
(25, 'UPSTREAM_RESPONSE',  '上游响应阶段', 5, 1),
(25, 'UPSTREAM_ERROR',     '上游异常阶段', 6, 1);

-- 回滚后核对（应恢复 10 个分类、53 条 configs）
-- SELECT COUNT(*) FROM ks_enum_categories WHERE id IN (3,8,9,12,15,16,17,23,24,25);
-- SELECT COUNT(*) FROM ks_enum_configs    WHERE category_id IN (3,8,9,12,15,16,17,23,24,25);

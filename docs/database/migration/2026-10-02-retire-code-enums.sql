-- 退役 DB 枚举分类（2026-10-02）
-- 迁移版本：2026-10-02-retire-code-enums （方案：枚举代码化 第一批 6 分类 + status 拆分 + 逻辑模型状态 + app_type）
--
-- 背景：以下 10 个枚举分类已迁移为后端 Java 代码枚举（GET /api/common/enums，EnumOptionRegistry），
--       由 enum 权威控制 code 与取值，避免"双源漂移"（管理员改 DB 但运行时逻辑不变）：
--   id=3   app_type                      → 孤儿分类，无消费方（直接删除）
--   id=8   billing_unit                  → BillingUnitEnum
--   id=9   billing_currency              → CurrencyCodeEnum
--   id=12  plugin_scope_type             → PluginScopeType
--   id=15  status                        → 按领域拆分（OperationLogStatusEnum / ScheduledTaskRunStatusEnum /
--                                          RoutingTestStatusEnum / EntityStatusEnum），DB 不再保留统一 status
--   id=16  logical_model_visibility      → LogicalModelVisibilityEnum
--   id=17  logical_model_publish_status  → LogicalModelPublishStatusEnum
--   id=23  model_pool_selection_strategy → ModelPoolSelectionStrategyEnum
--   id=24  plugin_extension_point        → PluginExtensionPoint
--   id=25  plugin_stage_code             → PluginStageCode
--
-- 前端已切到代码枚举通道（useEnumOptions / EnumControl），不再消费这些 DB 分类。
--
-- 保留（本轮不动）：scope_type / discount_type / channel / plugin_source_type / alert_level /
--                  risk_level / plugin_fail_mode / plugin_result_status / model_family（动态主数据）。
--
-- 幂等性：本脚本可重复执行，DELETE 作用于固定 id 集合，二次执行影响行数为 0，不报错/不重复插入。
-- 全新实例以更新后的 data.sql 种子建库，已不含这些分类，无需执行本脚本。

-- ---- 0. 执行前快照（迁移前人工/脚本核对，确认要删的行；非执行语句）----
-- SELECT 'ks_enum_categories' AS tbl, COUNT(*) AS cnt FROM ks_enum_categories WHERE id IN (3,8,9,12,15,16,17,23,24,25);
-- SELECT 'ks_enum_configs'    AS tbl, COUNT(*) AS cnt FROM ks_enum_configs    WHERE category_id IN (3,8,9,12,15,16,17,23,24,25);
-- SELECT category_id, item_code, item_label FROM ks_enum_configs WHERE category_id IN (3,8,9,12,15,16,17,23,24,25) ORDER BY category_id, sort_order;

-- ---- 1. 安全前置检查：确认业务表不再引用这些分类的 code ----
-- 这些分类历史上无外键约束，仅作字典展示。下列为常见引用点，迁移前应返回 0 行；若非 0，说明仍有代码在读这些枚举，须先修复再迁移。
--   SELECT COUNT(*) FROM kb_routing_rules        WHERE scope_type          NOT IN (SELECT item_code FROM ks_enum_configs WHERE category_id = 5);
--   SELECT COUNT(*) FROM kb_billing_rules        WHERE currency            NOT IN ('USD','CNY');
--   SELECT COUNT(*) FROM kb_model_pools          WHERE selection_strategy  NOT IN ('PRIORITY','RANDOM','WEIGHTED');
--   SELECT COUNT(*) FROM kb_logical_models       WHERE visibility          NOT IN ('PUBLIC','INTERNAL','PRIVATE');
--   SELECT COUNT(*) FROM kb_logical_models       WHERE publish_status      NOT IN ('DRAFT','PUBLISHED','ARCHIVED');
--   SELECT COUNT(*) FROM kb_plugins              WHERE scope_type          NOT IN (SELECT item_code FROM ks_enum_configs WHERE category_id = 5);

-- ---- 2. 先删子表（configs），再删父表（categories），避免约束冲突 ----
DELETE FROM ks_enum_configs WHERE category_id IN (3,8,9,12,15,16,17,23,24,25);

DELETE FROM ks_enum_categories WHERE id IN (3,8,9,12,15,16,17,23,24,25);

-- ---- 3. 执行后核对（应均为 0）----
-- SELECT COUNT(*) FROM ks_enum_categories WHERE id IN (3,8,9,12,15,16,17,23,24,25);
-- SELECT COUNT(*) FROM ks_enum_configs    WHERE category_id IN (3,8,9,12,15,16,17,23,24,25);

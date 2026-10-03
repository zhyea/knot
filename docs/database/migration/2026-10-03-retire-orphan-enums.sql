-- 退役 5 个零消费的孤儿 DB 枚举分类（2026-10-03）
-- 迁移版本：2026-10-03-retire-orphan-enums （方案：枚举代码化 阶段四「清理收口」）
--
-- 背景：以下 5 个分类自建库起就只存在于 ks_enum_categories/ks_enum_configs，
--       前后端 + mapper 全工程**零消费方**（唯一命中是 enums.ts 里的注释文字），
--       属于"可配置但没人配置"的误配置入口 —— 管理员能在枚举管理页改它们，
--       却不会影响任何运行时行为（典型的双源/死数据噪声）。
--   id=11  plugin_source_type   '插件来源类型'
--   id=13  alert_level          '告警级别'（原 is_system=1）
--   id=14  risk_level           '风险级别'（原 is_system=1）
--   id=26  plugin_fail_mode     '插件失败策略'
--   id=27  plugin_result_status '插件结果状态'
--
-- 处置口径：本次是**删分类**，不是把它们改造成代码枚举 —— 因为根本没有业务分支读它们，
--           也就没有"正确取值"可提取。若将来真要支持（如告警级别进功能），
--           届时再按标准路径新建 Java enum + 注册 EnumOptionRegistry。
--
-- 保留（仍有真实消费方，不在本脚本范围）：
--   scope_type / discount_type  → ProviderDiscountFormDialog + kb_provider_discount_policies
--   channel                     → NotifyTemplateFormDialog（通知模板渠道）
--   model_family                → ModelFamilyResolver 动态加载，运营需动态加族
--
-- 幂等性：DELETE 作用于固定 id 集合，可重复执行（二次影响 0 行）。
-- 全新实例以更新后的 data.sql 建库，已不含这些分类，无需执行本脚本。
-- 既有库必须先跑本脚本再启应用（KNOT_SQL_INIT_MODE=ALWAYS 重放 data.sql 不会删已存在的行）。

-- ---- 0. 执行前快照（非执行语句，迁移前人工/脚本核对）----
-- SELECT 'ks_enum_categories' AS tbl, COUNT(*) AS cnt FROM ks_enum_categories WHERE id IN (11,13,14,26,27);
-- SELECT 'ks_enum_configs'    AS tbl, COUNT(*) AS cnt FROM ks_enum_configs    WHERE category_id IN (11,13,14,26,27);
-- SELECT category_id, item_code, item_label FROM ks_enum_configs WHERE category_id IN (11,13,14,26,27) ORDER BY category_id, sort_order;
-- 预期：5 个分类、17 条 configs（11→3、13→4、14→3、26→2、27→5）。

-- ---- 1. 安全前置检查：确认没有业务表存了这些分类的 code ----
-- 孤儿分类无消费方，下列检查预期全部为 0；任一非 0 说明存在未发现的读取方，须先查清再迁移。
--   SELECT COUNT(*) FROM kb_plugins        WHERE source_type NOT IN ('BUILTIN','LOCAL_JAR','REMOTE_REGISTRY');
--   SELECT COUNT(*) FROM kb_alerts          WHERE alert_level NOT IN ('CRITICAL','HIGH','MEDIUM','LOW');
--   SELECT COUNT(*) FROM kb_risk_records    WHERE risk_level  NOT IN ('HIGH','MEDIUM','LOW');
--   SELECT COUNT(*) FROM kr_plugin_instances WHERE fail_mode  NOT IN ('FAIL_OPEN','FAIL_CLOSE');
--   SELECT COUNT(*) FROM kr_plugin_execution_logs WHERE result_status
--          NOT IN ('SUCCESS','SKIPPED','FAILED','TIMEOUT','OPEN_CIRCUIT');

-- ---- 2. 先删子表（configs），再删父表（categories）----
DELETE FROM ks_enum_configs WHERE category_id IN (11,13,14,26,27);

DELETE FROM ks_enum_categories WHERE id IN (11,13,14,26,27);

-- ---- 3. 执行后核对（应均为 0）----
-- SELECT COUNT(*) FROM ks_enum_categories WHERE id IN (11,13,14,26,27);
-- SELECT COUNT(*) FROM ks_enum_configs    WHERE category_id IN (11,13,14,26,27);
-- ---- 4. 剩余分类应恰为 4 个 ----
-- SELECT id, category FROM ks_enum_categories ORDER BY id;  -- 预期: 5/6/10/28

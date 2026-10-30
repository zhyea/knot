-- 插件改 opt-in：存量实例默认停用
--
-- 背景（2026-10-30）：插件实例在 2026-10-25 重构后由 PluginDescriptor 物化，
-- 新行默认 status=1（启用）。现改为默认停用（opt-in）——只有运维在管理端明确启用的插件才跑。
--
-- reconcile 的 upsert 不覆盖 status（运维权威），所以仅改默认值无法影响已物化的存量行；
-- 本脚本把「当前仍启用」的存量实例一次性改为停用，使「插件都默认关闭」在运行中的库也成立。
--
-- 可逆：本脚本只是把 status 置 0，运维在管理端 /api/plugins/{id}/status 重新启用即可，无数据丢失。
--
-- 幂等性：UPDATE 以 status=1 为条件，重复执行对已是 0 的行无副作用。
--
-- 执行顺序：本脚本与「schema.sql 将 status 默认值改 0 + PluginInstanceMapper upsert 字面量改 0」
-- 配套；既可在启新 jar 前跑、也可后跑（reconcile 不覆盖存量 status，故先后顺序不影响结果）。

-- ── 1. 存量启用中的插件实例改为停用（仅影响 is_deleted=0 且未标记孤儿的正常行）──
UPDATE kb_plugin_instances
SET status = 0
WHERE status = 1
  AND is_deleted = 0
  AND orphaned = 0;

-- ── 验证（人工执行，不在迁移流程内）─────────────────────────────────────
-- 期望：仍启用（status=1 且正常）的行数 = 0；停用行数 = 全部正常插件数
-- SELECT
--   SUM(status = 1 AND is_deleted = 0 AND orphaned = 0) AS still_enabled,
--   SUM(status = 0 AND is_deleted = 0 AND orphaned = 0) AS disabled_normal
-- FROM kb_plugin_instances;

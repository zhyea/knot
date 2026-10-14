-- =====================================================================
-- 敏感词插件模块归位迁移（2026-10-14）
-- ---------------------------------------------------------------------
-- 背景：敏感词审核从「knot-gateway 内置阶段」改为「同步插件」，并把 871 行代码
--       从 knot-gateway 迁到 knot-plugin（org.chobit.knot.gateway.plugin.moderation），
--       DB 实现 DbModerationWordSource 落 knot-service。
--       动因：knot-admin 不依赖 knot-gateway，两插件不在 admin 的 IOC 中，
--       PluginReconcileRunner 会把 kb_plugin_instances 行反复标成 orphaned=1，
--       与 gateway 侧反复对冲。详见设计文档 §2.1。
--
-- ⚠ dev 库 knot 已实证（2026-10-14 查询）：
--   id 7/8  entrypoint=org.chobit.knot.gateway.moderation.*   orphaned=1（admin 标孤儿）
--   id 9/10 entrypoint=org.chobit.knot.gateway.plugin.moderation.*  orphaned=0
--           （迁包后 gateway 启动，reconcile 发现新 entrypoint 无行而自动补，
--            instance_code 是 deriveInstanceCode 生成的长串、order_no=100）
--   → 4 行指向同一份代码，而分发按 entrypoint 取行，
--     同一个 handler 会被调用两次：命中判定跑两遍、审计事件发两条。
--   → 本迁移把 7/8 归位到新 entrypoint（保 data.sql 的可读 code 与 order_no=200），
--     再删掉 9/10 这对 reconcile 自动生成的重复行。
--
-- 幂等：REPLACE 只匹配旧前缀，DELETE 目标行删完即 0 行，可重复执行。
-- 执行顺序：先跑本迁移，再启动应用（data.sql 已同步为新 entrypoint）。
-- =====================================================================

SET @db = DATABASE();

-- =====================================================================
-- 段一：插件实例 entrypoint 换包（data.sql 种子行归位，清孤儿标记）
-- =====================================================================
-- 旧值：org.chobit.knot.gateway.moderation.SensitiveWordRequestPlugin
--        org.chobit.knot.gateway.moderation.SensitiveWordResponsePlugin
-- 新值：org.chobit.knot.gateway.plugin.moderation.SensitiveWordRequestPlugin
--        org.chobit.knot.gateway.plugin.moderation.SensitiveWordResponsePlugin
UPDATE kb_plugin_instances
SET entrypoint = REPLACE(entrypoint, 'org.chobit.knot.gateway.moderation.',
                         'org.chobit.knot.gateway.plugin.moderation.'),
    orphaned = 0
WHERE entrypoint LIKE 'org.chobit.knot.gateway.moderation.%';

-- =====================================================================
-- 段二：删除 reconcile 自动生成的重复行
-- =====================================================================
-- 只删 instance_code 形如 sensitive-word-moderation-* 的长串行（deriveInstanceCode 产物），
-- 不动 data.sql 种子行（instance_code = sensitive-word-request-check / -response-check）。
-- 必须在段一之后执行：段一把种子行归位到新 entrypoint 后，重复行才需要清。
DELETE FROM kb_plugin_instances
WHERE entrypoint LIKE 'org.chobit.knot.gateway.plugin.moderation.%'
  AND instance_code LIKE 'sensitive-word-moderation-%';

-- 频控 / 额度策略字段简化（2026-10-05）
--
-- 口径调整：
--   模型 / 路由规则层 —— 只做限流：RPM（每分钟请求数）+ TPM（每分钟 token）
--   应用 / 供应商账户 / 消费者层 —— 只做限额：max_tokens（token 上限）+ cost_limit（成本上限，带币种）
--   限额增加统计窗口 quota_window（MINUTE/HOUR/DAY/WEEK/MONTH），窗口结束即清零
-- 因此 per_second / per_minute / time_window 与 daily_limit / monthly_limit / token_limit / alert_enabled 全部废弃。
--
-- 权威 schema: knot-server/knot-admin/src/main/resources/db/schema.sql
-- 执行顺序：先跑本脚本，再重启应用（schema.sql 的 CREATE TABLE IF NOT EXISTS 不会改已有列）

-- ---------- kb_rate_limit_policies ----------
ALTER TABLE kb_rate_limit_policies DROP COLUMN IF EXISTS per_second;
ALTER TABLE kb_rate_limit_policies DROP COLUMN IF EXISTS per_minute;
ALTER TABLE kb_rate_limit_policies DROP COLUMN IF EXISTS time_window;

ALTER TABLE kb_rate_limit_policies ADD COLUMN IF NOT EXISTS rpm INT NOT NULL DEFAULT 0 COMMENT '每分钟请求数上限；0=不限' AFTER policy_name;
ALTER TABLE kb_rate_limit_policies ADD COLUMN IF NOT EXISTS tpm INT NOT NULL DEFAULT 0 COMMENT '每分钟 token 上限；0=不限' AFTER rpm;

-- ---------- kb_quota_policies ----------
ALTER TABLE kb_quota_policies DROP COLUMN IF EXISTS daily_limit;
ALTER TABLE kb_quota_policies DROP COLUMN IF EXISTS monthly_limit;
ALTER TABLE kb_quota_policies DROP COLUMN IF EXISTS token_limit;
ALTER TABLE kb_quota_policies DROP COLUMN IF EXISTS alert_enabled;

ALTER TABLE kb_quota_policies ADD COLUMN IF NOT EXISTS max_tokens BIGINT NOT NULL DEFAULT 0 COMMENT '窗口内 token 上限；0=不限' AFTER policy_name;
ALTER TABLE kb_quota_policies ADD COLUMN IF NOT EXISTS currency VARCHAR(8) DEFAULT NULL COMMENT '成本币种：USD / CNY' AFTER policy_name;
ALTER TABLE kb_quota_policies ADD COLUMN IF NOT EXISTS cost_limit DECIMAL(18,4) DEFAULT NULL COMMENT '窗口内成本上限；4 位小数（用户配置精度）；NULL 或 0=不限' AFTER currency;
ALTER TABLE kb_quota_policies ADD COLUMN IF NOT EXISTS quota_window VARCHAR(16) NOT NULL DEFAULT 'MONTH' COMMENT '统计窗口：MINUTE/HOUR/DAY/WEEK/MONTH，窗口结束清零' AFTER cost_limit;
UPDATE kb_quota_policies SET quota_window = 'MONTH' WHERE quota_window IS NULL OR quota_window NOT IN ('MINUTE','HOUR','DAY','WEEK','MONTH');

-- 精度：成本上限按 4 位小数配置（quota 层只要求到 0.0001 粒度，已足够贴合实际预算口径）。
-- 注意这与「计费侧成本 8 位」「计数缩放因子 1e-8」是两件事：
--   * DB / 前端输入只负责「用户能配到几位」→ 4 位
--   * 内部计数仍按 1e-8 累加，否则单次 1e-8 的成本会被截断成 0，额度永不累加
ALTER TABLE kb_quota_policies MODIFY COLUMN cost_limit DECIMAL(18,4) DEFAULT NULL COMMENT '窗口内成本上限；4 位小数（用户配置精度）；NULL 或 0=不限';

-- ---------- 绑定表归一：每层只保留自己那一类策略 ----------
-- 模型与路由规则不再配额度
UPDATE kb_resource_traffic_policies SET quota_policy_id = NULL
WHERE resource_type IN ('MODEL', 'ROUTING_RULE') AND quota_policy_id IS NOT NULL;
-- 应用、供应商账户与消费者不再配限流
UPDATE kb_resource_traffic_policies SET rate_limit_policy_id = NULL
WHERE resource_type IN ('APP', 'PROVIDER', 'ROUTING_CONSUMER') AND rate_limit_policy_id IS NOT NULL;

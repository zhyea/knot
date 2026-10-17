-- ============================================================
-- 2026-10-17 · 新增系统级（全局）设置表 ks_system_settings
--
-- 背景：路由规则计费校验按「全局配置的 jev 路由规则」执行判定，需要一个全管理端
--       共用的配置载体。与 ks_user_settings（用户级）分工，本表承载系统级配置。
--
-- 说明：schema.sql 的 CREATE TABLE IF NOT EXISTS 只对新建库生效，既有库不回补，
--       故在此幂等建表并补录种子行；重复执行无副作用（唯一键 setting_key）。
--
-- 约束：本表只存非敏感配置。凭据/密钥一律留在各自归属表（如 kb_routing_consumers），
--       绝不落本表 —— 裁判的 secretKey 由消费者 code 现取。
--
-- ⚠ 配套迁移：同目录 `2026-10-17-permission-code-settings-to-preference.sql`
--   （权限 code 关键字分工：用户设置=preference / 系统设置=settings，并补插
--    system:settings:{view,update} 与 routing:rule:billing-verify 及其 API 绑定）。
--   **两个脚本都要跑**：只跑本脚本不跑权限迁移，data.sql 补插的新权限会被唯一键
--   静默丢弃、留下孤儿绑定；只跑权限迁移不跑本脚本，data.sql 会在下面的种子 INSERT
--   处失败并中断，其后的「权威授权块」不执行 → 新权限没授给 ADMIN。
-- ============================================================

CREATE TABLE IF NOT EXISTS ks_system_settings (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  setting_key VARCHAR(64) NOT NULL,
  setting_value TEXT DEFAULT NULL,
  remark VARCHAR(255) DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_system_settings_key (setting_key)
);

-- 计费校验（JEV 裁判）默认配置。
-- 总开关默认关闭；judge_consumer_code / judge_rule_code 有意不预置——
-- 缺失即「未配置」，由实施方在系统中配好 jev 模型、DECISIONS 绑定与指向它的路由规则后写入。
INSERT IGNORE INTO ks_system_settings (setting_key, setting_value, remark) VALUES
('billing_verify.enabled',          'false', '计费校验裁判总开关，未配置裁判规则前保持关闭'),
('billing_verify.judge_timeout_ms', '5000',  '裁判调用超时（毫秒）'),
('billing_verify.max_details',      '20',    '喂给裁判的 state 明细条数上限，防止 413');

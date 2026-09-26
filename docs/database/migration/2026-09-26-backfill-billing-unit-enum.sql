-- 补齐 DB 枚举分类 billing_unit（2026-09-26）
--
-- 背景：ks_enum_configs 的 billing_unit（category_id=8）此前只有 PER_TOKEN 一项，
--       而 BillingModeEnum.supportedUnits 共涉及 7 个单位，前端 unitsByMode 也用到 7 个。
--       单位下拉要走 DB 枚举（useEnums），必须先补齐，否则下拉只剩 1 项。
--
-- 决策：单位与价格项的展示文案留在 DB（管理员可维护），
--       「模式 -> 单位」关系由 BillingModeEnum 承载并经 GET /api/billing/mode-capabilities 下发。

INSERT IGNORE INTO ks_enum_configs (category_id, item_code, item_label, sort_order, is_enabled) VALUES
(8, '1K_TOKENS',     '千 Token', 1, 1),
(8, '1M_TOKENS',     '百万 Token', 2, 1),
(8, 'PER_TOKEN',     '单 Token', 3, 1),
(8, 'PER_REQUEST',   '按请求', 4, 1),
(8, 'PER_IMAGE',     '按图片', 5, 1),
(8, 'PER_MINUTE',    '按分钟', 6, 1),
(8, 'PER_SECOND',    '按秒', 7, 1),
(8, 'CUSTOM',        '自定义', 99, 1);

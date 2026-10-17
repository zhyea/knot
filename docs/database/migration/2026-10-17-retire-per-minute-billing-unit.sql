-- ============================================================================
-- 2026-10-17 · 退役 PER_MINUTE（按分钟）计费单位 —— robin 拍板
--
-- 背景：
--   PER_MINUTE 的基数 60 是全部计费单位里唯一的非 10 的幂，「用量 × 单价 ÷ 60」
--   会产生不终止除法（1 秒 = 0.0166... 分钟），金额链路必须靠 DECIMAL128 兜底；
--   且按分钟计费在任何环境都没有真实规则使用（dev 库 5 条规则全为 TOKEN/EMBEDDING
--   且都是 1K_TOKENS，ks_enum_configs 字典行也已在 2026-10-02 全删）。
--   退役后所有单位基数都是 10 的幂（1 / 1e3 / 1e6），金额除法恒定除尽。
--   音频与视频统一按秒计费（BillingModeEnum.AUDIO 的默认/唯一单位改为 PER_SECOND）。
--
-- 代码侧同步：BillingUnitEnum 删除 PER_MINUTE 常量；BillingModeEnum.AUDIO 改
--   PER_SECOND、CUSTOM 单位列表去掉 PER_MINUTE；BillingUnitEnumTest 钉住
--   「所有 unitSize 都是 10 的幂」不变式。存量规则若仍写 PER_MINUTE，
--   unitSizeOf 会在运行期抛 BusinessException（显性失败，不静默按错基数计费）。
--
-- 本脚本做什么：**纯守卫，不改任何数据**。若 kb_billing_rule_versions 里还存在
--   unit='PER_MINUTE' 的规则版本，直接报错中断——必须人工处理后再上线。
--
-- 为什么不自动折算：
--   config_json 里的价格藏在多层结构里（defaultUnitPrice / basePrices.* /
--   resolutionPrices.* / tiered 的 tier[].unitPrice / 高低峰 phase 单价），
--   用 SQL JSON 函数批量乘 60 极易漏字段，漏一个就是静默资损，宁可让人工重建。
--
-- 人工处理指引（守卫触发时）：
--   1. 看下方审计 SELECT 的输出与报错里的冲突键值，定位全部受影响规则；
--   2. 按 PER_SECOND 重建：单价 = 原每分钟价 ÷ 60，unit 改 'PER_SECOND'
--      （也可在管理端直接把规则改成 AUDIO + 按秒，重新录入单价）；
--   3. 重跑本脚本确认通过，再启动服务。
--
-- 幂等性：只读守卫 + 一次必败的自复制插入，可重复执行；零 PER_MINUTE 行时
--   完全无操作（INSERT 影响 0 行）。
-- 权限：只需 kb_billing_rule_versions 的 SELECT + INSERT（本仓应用账号即有），
--   不建临时表、不建存储过程，mysql 客户端与 JDBC 均可执行。
-- ks_enum_configs 的 billing_unit 字典行已于 2026-10-02-retire-code-enums.sql
--   删除，本脚本不碰 ks_ 表。
-- ============================================================================

-- 审计：列出全部仍按分钟计费的规则版本（正常应为空结果集）
SELECT v.rule_code, v.version_code, v.billing_mode, v.unit, v.config_json
FROM kb_billing_rule_versions v
WHERE v.unit = 'PER_MINUTE';

-- 守卫：把 PER_MINUTE 行原样复制一份。零行时是无操作；有行时必撞
-- uk_billing_rule_version_code / uk_billing_rule_version_hash / 主键，
-- 整条语句失败（语句级原子性，不落任何数据），错误信息直接点名冲突行。
INSERT INTO kb_billing_rule_versions
SELECT * FROM kb_billing_rule_versions WHERE unit = 'PER_MINUTE';

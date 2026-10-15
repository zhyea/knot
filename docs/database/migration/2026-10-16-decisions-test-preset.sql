-- ============================================================
-- 2026-10-16 · 补充 DECISIONS 协议路由调试预设请求用例
--
-- 背景：新增 DECISIONS 协议（OpenRouter AlphaDecisions，jev 类决策模型）后，
--       路由调试面板的预设用例缺少该协议条目，调试 DECISIONS 绑定时无可直接套用的请求体。
--
-- 说明：data.sql 的种子数据只对「新建库」生效，既有库不会回补，故在此幂等补录。
--       code 与 data.sql 保持一致（builtin-decisions-basic），重复执行无副作用。
-- ============================================================

INSERT IGNORE INTO kb_test_request_presets (
  code, name, protocol_code, request_body, remark, status
) VALUES
('builtin-decisions-basic', '决策判定（Decisions）', 'DECISIONS',
  '{"model":"model-name","state":{"customer_tier":"enterprise","ticket":"点击支付后结算页空白，换了两个浏览器都一样。"},"questions":{"is_bug":{"type":"noul","instructions":"客户是否在报告软件缺陷？","criteria":{"true":"客户描述了损坏或异常的产品行为。","false":"客户在提问或请求新功能。"}},"team":{"type":"choice","instructions":"这个工单应该由哪个团队负责？","criteria":{"account":"登录、权限或资料相关问题。","payments":"结算、账单或支付处理问题。"}},"urgency":{"type":"score","instructions":"这个工单有多紧急？","criteria":["可以等到下一个版本","本周内应修复","正在阻塞营收"]}}}',
  '适用于 AlphaDecisions 决策接口（如 typesafe/jev）：入参为待评估 state 与 questions，出参为 answers 映射。questions 支持 noul（判定）/choice（单选）/score（有序评分）三类。', 1);

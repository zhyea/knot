-- 2026-10-01 计费规则作用域由「统一模型」改为「模型族」
-- 适用库：knot（MariaDB 10.6 / MySQL 8）
-- 说明：计费规则不再绑定到具体统一模型，改为绑定模型族（ks_enum_configs.category=model_family 的 item_code）。
--       一条规则覆盖该族下所有统一模型。模型族作为枚举维护，不单独建表。
--       既有规则的逻辑模型作用域不再可靠（统一模型的 model_family 自动填充值有误），故全部置为 NULL（默认规则）。
--       统一模型的 model_family 自动填充值同样清空，改由枚举驱动的表单重新选族。
-- 注意：真实计费链路是 kb_models.billing_rule_code -> kb_billing_rules.code，本迁移不改该链路。

ALTER TABLE kb_billing_rules
    DROP INDEX idx_billing_rules_match,
    DROP COLUMN logical_model_code,
    ADD COLUMN model_family VARCHAR(64) DEFAULT NULL
        COMMENT '模型族 code（ks_enum_configs.category=model_family 的 item_code）；为空表示默认规则，覆盖所有族',
    ADD KEY idx_billing_rules_match (model_family, status);

-- 既有规则的逻辑模型作用域不可信，统一转为默认规则（NULL）
UPDATE kb_billing_rules SET model_family = NULL WHERE model_family IS NOT NULL;

-- 清空统一模型上错误的自动填充模型族，交由枚举表单重新选族
UPDATE kb_logical_models SET model_family = NULL WHERE model_family IS NOT NULL;

-- 模型族枚举分类（category_id=28）
INSERT IGNORE INTO ks_enum_categories (id, category, category_name, is_system, is_enabled) VALUES
(28, 'model_family', '模型族', 0, 1);

INSERT IGNORE INTO ks_enum_configs (category_id, item_code, item_label, sort_order, is_enabled) VALUES
(28, 'gpt',      'GPT（OpenAI）',        1,  1),
(28, 'claude',   'Claude（Anthropic）',   2,  1),
(28, 'gemini',   'Gemini（Google）',      3,  1),
(28, 'gemma',    'Gemma（Google）',       4,  1),
(28, 'grok',     'Grok（xAI）',           5,  1),
(28, 'llama',    'Llama（Meta）',         6,  1),
(28, 'mistral',  'Mistral',               7,  1),
(28, 'cohere',   'Cohere',                8,  1),
(28, 'phi',      'Phi（Microsoft）',       9,  1),
(28, 'deepseek', 'DeepSeek',              10, 1),
(28, 'qwen',     '通义千问（Qwen）',      11, 1),
(28, 'glm',      '智谱 GLM',              12, 1),
(28, 'minimax',  'MiniMax',               13, 1),
(28, 'hailuo',   '海螺 AI（Hailuo）',     14, 1),
(28, 'moonshot', 'Kimi（Moonshot）',      15, 1),
(28, 'yi',       '零一万物（Yi）',        16, 1),
(28, 'step',     '阶跃星辰（Step）',      17, 1),
(28, 'baichuan', '百川（Baichuan）',      18, 1),
(28, 'doubao',   '豆包（Doubao）',        19, 1),
(28, 'hunyuan',  '混元（Hunyuan）',       20, 1),
(28, 'ernie',    '文心一言（ERNIE）',     21, 1),
(28, 'spark',    '讯飞星火（Spark）',     22, 1);

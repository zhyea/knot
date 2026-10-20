# 路由规则计费校验方案（v6 · JEV 作自动裁判 · 输入契约「已产出 + 依据」· 双盲判定）

> ⛔ **2026-10-17 下午：本方案已整体拆除（robin 拍板「直接拆除，不打补丁，稍后再准备更好的方案」）。**
>
> 拆除导火索：robin 判定「**不要把自检逻辑嵌到网关服务里，就如 RoutingRuleBillingDryRunService**」——
> 该服务把事实层、自检驱动、结果组装一锅端，是「补丁式堆叠」的形态。叠加当日三次契约纠正
> （`knot_extend` 是网关自产的被告 / JEV 只吃「已产出 + 依据」/ 自检结论双盲隔离），
> robin 决定现有骨架（端点、dry-run 服务、自检、dto/vo 类型、PlatformSetting 配置底座）**全部删除，新方案另行设计**。
>
> **本文档不再生效，保留为设计过程记录。** 对新方案仍有直接价值、勿从零再议的部分：
> §0 的 v5→v6 纠正表、§1.3 判定域划分（robin 认知基础）、§4 knot_extend 网关自产实证、
> §5.3 state 输入契约（进/不进硬约束）、§5.4 cost_plausible 语义嗅探定位、§8 的「自检全绿但 JEV 判 FAIL」失明场景。
>
> **后继方案：`routing-test-drawer-jev-billing-verdict-design.md`（v7，2026-10-17 晚立）**——
> 触发点移至管理端测试抽屉（正是本方案被拆的架构处方），配置载体改 `knot.jev.*` 配置项（不重建表），
> 继承本文档全部判定域/契约遗产。

> 文档状态：~~设计稿 v6，待评审~~ → **已拆除**。
> 版本链：v1（10-12）→ v2 → v3 → v4 → v5（10-17 上午）→ **v6（10-17 中午）**：
> v4 按 OpenRouter `POST /api/alpha/decisions` 官方 OpenAPI 规范校准判定层契约；
> v5 采纳「全局配置的 jev 路由规则」+「新增系统设置表」；
> **v6 采纳 robin 三次纠正 / 拍板 —— ① `knot_extend` 是网关自己产的、不是上游返回的，它是本次校验的被测对象；② 交给 JEV 的只能是「已产出内容 + 底层依据」，不能是二次运算的内容；③ 自检结论不进 JEV state（双盲判定）。**
> 契约依据：OpenRouter Decisions 参考（`DecisionsRequest` / `DecisionsResponse` / 四类 question / 错误码）。

---

## 0. v5 → v6 变更摘要（robin 三次纠正 / 拍板）

| # | v5 表述 | v6 订正 | 依据 |
|---|---|---|---|
| 1 | 判定层输入 = `BillingFact + 自检` | ❌ 整体作废。JEV 输入 = **已产出（`knot_extend`）+ 底层依据（上游 usage 原文 / 规则快照 / 上游 cost / 请求侧属性 / 命中层级）**，禁二次运算内容 | robin：「给 jev 的内容应该是已产出内容以及底层依据，不能是二次运算的内容」 |
| 2 | 链路中不出现 `knot_extend` | ✅ 它是**被告**（被测对象），必须由前端从抽屉响应回传（SSE 下是独立事件，需解析）；`BillingFact` 加 `gatewayBilling` 承载 | robin：「knot_extend 不是上游返回的」——代码实证它是网关自产 |
| 3 | `cost_plausible` 问「金额量级与用量×单价是否相符」 | ❌ 这是让 LLM 做算术复核。改为「依据 → 已产出账的推导是否**语义可信、场景相称**」的嗅探；算术精确归自检 | 「能算准的进自检，讲语义的进 JEV」 |
| 4 | 自检结论进 state | ❌ **双盲**：自检与 JEV 各自对同一事实独立下结论，人在抽屉对照两区；JEV 不被代码结论锚定 | robin：「交给 jev 的不应该有自检内容」 |
| 5 | 上游对照只有 `UPSTREAM_COST_DEVIATION`（重算值 vs 上游 cost） | ✅ 保留；另加 `GATEWAY_RECOMPUTE_MATCH`（**网关账 vs 重算账全等**）——唯一能抓住「网关热路径自己算错」的自检项 | v6 缺口分析：影子重算读同一份规则表，配置级错误两边同错 |

### v4 → v5 变更（保留备查）

| # | 变更 | 说明 |
|---|---|---|
| 1 | 裁判规则改为全局配置 | 由系统设置表统一配一条「指向 jev 的裁判路由规则」，全管理端共用 |
| 2 | 新增 `ks_system_settings` 表 | 按 `ks_` 系统域命名，沿用 `ks_user_settings` 表形；Java 类按命名规范（禁 `System` 词根）叫 `PlatformSetting*`，Mapper 操作 `ks_system_settings` 表 |
| 3 | 设置表绝不存密钥 | 只存 consumer code + rule code，调用时 `RoutingConsumerMapper.getByCode` 现取 secretKey |
| 4 | 网关地址不进设置表 | 复用请求透传的 `gatewayBaseUrl`，保证裁判与被测同实例 |

---

## 1. 核心目的

**以 JEV 决策模型为自动裁判，对任意模型任意协议的路由调试结果，校验网关当次产出的计费结果（`knot_extend.billing`）是否正确。**

### 1.1 三条链路（v6 重组）

```
被告链（已产出，原样记录，不再计算）
   knot_extend.billing（网关当次算的账）+ knot_extend.usage（归一化用量视图）
   + 底层依据：上游原始 usage 原文、上游自报 usage.cost、命中规则快照、命中层级、请求侧计费属性
        │
        │  （被告链是 JEV 的唯一输入；影子链的结果一律不进 JEV）
        ▼
判定链 —— JevBillingJudge
   读全局配置 → 网关 POST /v1/decisions，state = 被告链全部，questions = 4 条
        │
        ▼
影子链（自检专用，代码精确）
   BillingFact 重算（extractor → 三级回退取规则 → normalize）
   → BillingFactChecks 七项（含 GATEWAY_RECOMPUTE_MATCH：网关账 vs 重算账）
```

### 1.2 边界原则

| 原则 | 内容 |
|---|---|
| 能算准的进自检 | 算术正确性（明细公式、汇总、单位换算、网关账与重算账一致性）由代码判定，**权威** |
| 讲语义的进 JEV | 规则恰当性、推导可信度、场景相称性、风险刻度由 JEV 判定，**辅助** |
| 裁判吃事实不吃观点 | JEV 只见「已产出 + 底层依据」，不见重算值、不见自检结论——否则是拿另一个考生的答案对答案 |
| 双盲 | 自检与 JEV 互不可见，各自对同一事实独立下结论；两边独立同错概率低，人对照两区 |

### 1.3 为什么自检全绿还必须问 JEV（robin 的认知基础）

> 「代码如果能够计算准确，自检就不该报错，所以交给 JEV 来判断。」

这句话同时划定了两条判定域的边界：

- **自检的判定域 = 算术正确性（可确定性）**：明细公式、汇总一致、单位换算、网关账与重算账一致。代码算对了，这些项就该全绿。
- **自检全绿 ≠ 计费正确**：算术无误只是必要条件。规则选得恰不恰当（EMBEDDING 模型套 CHAT 规则，算式全对但场景荒谬）、用量形态与计费模式相不相称、这笔账讲不讲得通——这些**语义正确性**代码判不了：代码没有常识，只能比对数字。
- **JEV 的判定域 = 语义正确性**：自检全 PASS 的案件，JEV 是唯一可能发现问题的人。因此双盲不是形式——JEV 若见了自检结论，「自检全绿 → 裁判照抄全绿」，裁判价值归零。

**推论（门禁设计）**：

1. 必须构造「**自检全 PASS 但 JEV 判 FAIL**」的反向用例（如规则与场景明显错配）——证明 JEV 不是代码结论的复读机；
2. 「自检 FAIL 但 JEV 判 PASS」的分歧案件同样要能展示——双盲让两种分歧都暴露给人，由人裁决；
3. 若某条 JEV 语义判断日后可被确定性化，下沉自检层（§10-8 边界演进）。

---

## 2. 为什么 JEV 适合当裁判（能力来自四种 question 类型）

| JEV 题型 | 本方案的用法 | 答案形态（v4 契约校准） |
|---|---|---|
| `noul` | 是非判定：归一化是否成立 / 账是否语义可信 | `{type:"noul", noul:0~1}` —— **无 confidence 字段** |
| `choice` | 状态归类：规则命中是否合适 | `{type:"choice", choice:"APPROPRIATE", confidence?, probabilities?}` |
| `score` | 风险刻度：整体风险多高 | `{type:"score", score:<连续值>, legend?, probabilities?, confidence?}` |
| `box` | 本方案不用（图像框选，与计费无关） | — |

**核心优势**：一次调用同时拿到四个维度的**结构化、带概率**答案，代码可直接消费，不需要解析自然语言。

---

## 3. 架构总览

```
① 抽屉跑路由测试（真实网关一次调用；消费者 return_usage_detail 已开）
        │
        ▼  网关响应 = 上游响应体 + knot_extend{usage, billing} + 并列 upstreamUsage
        │  （knot_extend 由网关自产注入，不是上游返回的：见 §4）
        │
        ▼  POST /api/routing-rules/{id}/test/billing-verify
        │  { targetCode, protocol, responseBody, requestBody, knotExtend, stream? }
        │
        ├─▶ ② 被告链：原样记录已产出 + 底层依据（不算钱）
        │     produced:  knot_extend.billing / knot_extend.usage
        │     evidence:  upstreamUsage 原文 / upstreamCost / 规则快照 / 命中层级 / 请求侧属性
        │
        └─▶ ③ 影子链：BillingFact 重算（生产同口径，仅服务自检与事实展示）
              binding → UsageExtractor → 三级回退取规则 → UsageNormalizationSupport.normalize
                │
                ▼
              BillingFactChecks 七项（代码精确，权威）
              USAGE_NOT_EMPTY / TOTAL_TOKENS_CONSISTENT / COST_FORMULA_MATCH /
              GATEWAY_RECOMPUTE_MATCH（新）/ RULE_MODE_MATCH /
              RULE_SOURCE_EXPECTED / UPSTREAM_COST_DEVIATION(cost 缺失则跳过)
                │
                ▼
        ④ 判定层 JevBillingJudge（双盲：不见自检结论、不见重算值）
           ├─ 读全局配置（PlatformSetting / ks_system_settings）：enabled / judge_consumer_code / judge_rule_code
           ├─ RoutingConsumerMapper.getByCode → secretKey ；校验 rule 存在且启用
           └─ 网关 POST /v1/decisions（复用本次请求透传的 gatewayBaseUrl，与被测同实例）
              body = { state: 被告链, questions: 4 条, provider: 偏好, session_id, trace }
                │
                ▼
        ⑤ 返回 { produced, evidence, recompute对照, checks[7], verdict, trace }
                │
                ▼ 抽屉三区：计费事实 / 自检 / 裁判结论（自检区与裁判区各自独立，人对照）
```

---

## 4. 被测对象：`knot_extend`（网关自产，不是上游返回的）

robin 纠正：「knot_extend 不是上游返回的」。代码实证：

| 事实 | 位置 |
|---|---|
| 产出方是**网关自己**：`routing.returnUsageDetail()` 为 true 时，`KnotExtendPayload.of(result.usage())` 注入响应 | `GatewayRequestHandler` |
| 流式下由 `ProxyStreamingBody` 在 `data: [DONE]` 之前插入**独立 SSE 事件**（上游不发 `[DONE]` 时流结束处兜底）；上游原始 usage 以并列 `upstreamUsage` 字段透出 | `ProxyStreamingBody` |
| 结构 = `{ usage: 归一化用量（跨厂商一致，业务侧以此为准）, billing: 网关按计费规则算出的总价与明细 }`；金额为十进制普通字符串（2026-10-17 契约） | `KnotExtendPayload` / `NormalizedUsage` |

**角色定位：被告。** 本次校验要判断「这个账算得对不对」的那个账，就是网关对当次调用真实算出并返回给调用方的 `knot_extend.billing`。影子重算值（`BillingFact.computed`）是**基准**，上游自报 `usage.cost` 是**外部证人**（可选，仅部分厂商有）——三者构成三角，但只有「被告 + 依据」进 JEV。

### 4.1 被告如何入链（批次 2a）

- `RoutingBillingVerifyRequest` 新增 `knotExtend` 字段：前端把抽屉里已显示的那次响应的 `knot_extend` 原样回传（**流式是独立 SSE 事件，前端需解析提取**；整包是响应体顶层字段直接取）。
- `BillingFact` 新增 `gatewayBilling`：被告账快照（totalCost / currency / 明细行），供事实展示区对照与 `GATEWAY_RECOMPUTE_MATCH` 使用。
- 端点仍**不发第二次上游请求**：被告就是「已在抽屉里显示过的那次调用」的产出。

### 4.2 前提：`return_usage_detail` 必须开

抽屉测试走真实网关用的是请求带入的 consumer secretKey；该消费者 `return_usage_detail` 未开时，响应里**根本没有 `knot_extend`**（被告缺席），校验退化为「只有影子链」并明确提示。保证机制（强制开启 / 测试专用调试消费者）见 §10 待确认。

---

## 5. 判定层 JevBillingJudge

### 5.1 自检项（代码精确，七项）

| 自检项 | 判定内容 | 失败含义 |
|---|---|---|
| `USAGE_NOT_EMPTY` | 解析用量非全 0 | **静默归零**（最危险） |
| `TOTAL_TOKENS_CONSISTENT` | `totalTokens == input+output`（上游未给 total 需补齐） | 口径缺失或补齐失效 |
| `COST_FORMULA_MATCH` | 明细 `amount × unitPrice ÷ unitSize == cost`，且汇总 == `totalCost` | 计算器 / 单位换算出错 |
| **`GATEWAY_RECOMPUTE_MATCH`**（v6 新增） | **网关账（`knot_extend.billing`）与同口径重算账全等**（1 ppm 容差） | **网关热路径自己算错**——规则取错 / 单价版本错 / 汇率错。影子重算读同一份规则表，配置级错误两边同错，唯此项能抓「当次调用算错」 |
| `RULE_MODE_MATCH` | 计费模式 vs 用量形态 | 配置错配 |
| `RULE_SOURCE_EXPECTED` | `matchedBy` 是否为期望层级 | 模型没绑规则，悄悄走默认 |
| `UPSTREAM_COST_DEVIATION` | knot 费用 vs 上游 `cost` 量级（**cost 缺失 → SKIP**） | 数量级异常提示 |

> 自检三态纪律不变：判不了的一律 **SKIP 并说明原因，绝不充 PASS**。
> 自检结论**只进抽屉自检区**，不进 JEV state（双盲，§5.3）。

### 5.2 请求契约锚点（`DecisionsRequest`）

必填：`model` / `state` / `questions`；可选：`provider` / `session_id`(≤256) / `trace` / `user`(≤256)。

| 题型 | 必填 | criteria 形态 |
|---|---|---|
| `noul` | `type` + `instructions` | 映射，**须同时含 `true` 与 `false`** |
| `choice` | `type` + `instructions` + `criteria` | 映射：选项名 → 说明 |
| `score` | `type` + `instructions` + `criteria` | **有序数组**，minItems 1 |
| `box` | `type` + `instructions` | 无 criteria |

> `model` 无需我们指定：网关 `GatewayRequestHandler.withTargetModel` 会自动覆盖为 `kb_models.upstream_model`（沿用 Decisions 接入的既定行为）。

### 5.3 `state` 设计（v6 终态契约：已产出 + 底层依据，禁二次运算）

```json
{
  "subject": {
    "modelCode": "gpt-4o", "protocol": "CHAT_COMPLETIONS", "stream": false,
    "ruleSource": "EXPLICIT"
  },

  "produced": {
    "_comment": "已产出：网关对当次调用真实算出的 knot_extend，原样呈现，不再计算",
    "usage":  { "inputTokens": 123, "outputTokens": 45, "totalTokens": 168,
                "cacheReadTokens": 0, "cacheWriteTokens": 0 },
    "billing": { "totalCost": "0.00129", "currency": "USD",
                 "details": [ { "type": "uncachedInput", "tokens": 123, "price": "0.005", "cost": "0.000615" },
                              { "type": "output",        "tokens": 45,  "price": "0.015", "cost": "0.000675" } ] }
  },

  "evidence": {
    "_comment": "底层依据：算出这笔账所用的原材料与参数",
    "upstreamUsage":     { "prompt_tokens": 123, "completion_tokens": 45, "total_tokens": 168 },
    "upstreamCost":      "0.00021",
    "billingRule":       { "code": "TOKEN_GPT4O", "billingMode": "TOKEN", "unit": "1K_TOKENS",
                           "currency": "USD", "pricingPlan": "FIXED",
                           "matchedBy": "EXPLICIT", "versionCode": "v1" },
    "requestAttributes": { "size": "1024x1024" }
  }
}
```

**硬约束（v6）**：

| 不进 state | 原因 |
|---|---|
| `computed`（admin 侧重算金额） | 二次运算；且影子与生产读同一份 `kb_billing_rules`，配置级错误两边同错，对答案无意义 |
| `parsedUsage`（extractor 影子解析用量） | 影子链路产物，与 `knot_extend.usage` 同源同口径 |
| 自检结论 PASS/FAIL | 双盲：JEV 是独立裁判，不读别的老师的阅卷结果 |

**裁判逻辑本质**：JEV 复核「依据 → 已产出账」的推导是否成立，而不是拿另一个考生的答案对答案。

### 5.4 `questions` 设计（四条，全部契约合法；v6 改写两题）

```json
{
  "usage_parse_ok": {
    "type": "noul",
    "instructions": "对照上游原始 usage 与网关归一化后的用量视图（knot_extend.usage）：归一化是否完整、正确地覆盖了上游上报的计费维度？",
    "criteria": { "true":  "与上游口径一致、无维度丢失或字段误读",
                  "false": "存在维度丢失、别名误读或口径不一致" }
  },
  "rule_match_ok": {
    "type": "choice",
    "instructions": "针对该模型与本次用量形态，命中的计费规则是否合适？",
    "criteria": { "APPROPRIATE":  "计费模式与用量形态匹配，且命中层级符合预期",
                  "FALLBACK_ONLY": "只命中兜底（模型族/默认）规则，模型未显式绑定",
                  "MODE_MISMATCH": "计费模式与用量形态不匹配（如 token 用量套媒体规则）",
                  "MISSING":       "未命中任何计费规则" }
  },
  "cost_plausible": {
    "type": "noul",
    "instructions": "以上游用量、命中规则单价与计费单位为凭据审视：网关对本次调用算出的这笔账（produced.billing），其推导是否语义可信、场景是否相称？",
    "criteria": { "true":  "账目与依据相称，推导路径合理，用量形态与计费场景相称",
                  "false": "账目与依据相悖，或用量形态与计费场景明显不相称" }
  },
  "risk_level": {
    "type": "score",
    "instructions": "综合以上，本次计费结果的错误风险有多高？",
    "criteria": [ "风险很低，可直接信任", "存在轻微疑点，建议复核", "存在明显问题，需立即排查" ]
  }
}
```

> **`cost_plausible` 定位（v6 改写）**：不是算术复核——精确验算归自检（`COST_FORMULA_MATCH` / `GATEWAY_RECOMPUTE_MATCH`，代码全等）。JEV 在此题做的是**语义嗅探**：用量与单价能否在常识层面支持这笔账、场景是否荒谬（如 EMBEDDING 模型命中 CHAT 规则、零用量却出了账、上游报了 cost 而网关账与之天差地别）。LLM 不算钱，只看这账讲不讲得通。
>
> 四题均不引用自检结论——JEV 不知道代码怎么判（双盲）。

### 5.5 调用加固参数（v4 新增，来自规范）

| 参数 | 取值建议 | 理由 |
|---|---|---|
| `provider` | `{ "data_collection": "deny" }` | 只保留数据合规约束。上游 provider 白名单（`only`）与 `allow_fallbacks` 已移除（2026-10-20）：上游 provider 选择无法从 knot 侧约束；`data_collection:deny` 防止计费/用量数据被留存训练 |
| `provider.max_price` | `{ "prompt": "...", "completion": "..." }` | 可选，给裁判调用设价格上限做成本兜底 |
| `session_id` | 本次被测请求的 trace/请求 id（≤256） | 把「被测请求」与「裁判调用」在观测侧串起来 |
| `trace` | `{ "trace_id": ..., "trace_name":"knot-billing-verify", "generation_name":"judge-<被测模型码>" }` | 规范要求 known key 有特殊处理，可直接进入 Broadcast/日志关联 |

> 谨慎项：`require_parameters` 不要开——若带了 provider 不支持的参数会直接报错；我们只用上述安全项。

### 5.6 answers 契约与解析（v4 订正）

```json
"verdict": {
  "judgeModel": "jev-decision",
  "judgeGenerationId": "gen-dec-1789738314-X5e5eKGQdvR9rblyX250",
  "available": true,
  "answers": {
    "usage_parse_ok": { "type": "noul", "noul": 0.94 },
    "rule_match_ok":  { "type": "choice", "choice": "APPROPRIATE", "confidence": 0.88,
                        "probabilities": { "APPROPRIATE": 0.88, "FALLBACK_ONLY": 0.09, "MODE_MISMATCH": 0.02, "MISSING": 0.01 } },
    "cost_plausible": { "type": "noul", "noul": 0.91 },
    "risk_level":     { "type": "score", "score": 0.12, "confidence": 0.90,
                        "legend": { "0": "风险很低，可直接信任", "1": "存在轻微疑点，建议复核", "2": "存在明显问题，需立即排查" },
                        "probabilities": { "0": 0.90, "1": 0.09, "2": 0.01 } }
  },
  "summary": "解析与金额无明显异常，风险低"
}
```

**解析要点（防御式）**：
- `noul` 只有 `noul` 值，**别读 confidence**。
- `choice` 必含 `type`+`choice`；`confidence` / `probabilities` **schema 可选**，缺失时按「无置信度」展示，不报错。
- `score` 的 `score` 是**连续 double**，`probabilities` 与 `legend` 用**字符串键**（"0"/"1"/"2"）。
- 缺失某个 question 的答案 → 该题标「未返回」，不整体失败。

### 5.7 错误码映射与降级（v4 新增）

| 码 | 含义 | 裁判侧对策 |
|---|---|---|
| 400 | 入参不合法 | 视为配置错误：校验 questions 模板与 state 结构，**契约单测应覆盖** |
| 401 / 403 | 鉴权失败 / 非 management key | 明确提示「裁判凭据无权」，降为不可用 |
| 402 | 余额不足 | 提示充值；与「故障」区分开，不要误报成系统异常 |
| **413** | 载荷过大 | ⚠ 触发 §5.8 的 state 截断策略并重试一次；仍失败则降级 |
| 429 | 限流 | 指数退避最多 1 次；仍失败则降级（不要挂在调试主流程上） |
| 500 / 502 / 503 | 上游故障 | 降级，`available:false` |
| 524 / 529 | 边缘超时 / provider 过载 | 降级，**不要**长时间阻塞抽屉（受 `timeout-ms` 约束） |

**统一原则**：判定失败**绝不阻塞**——被告链、影子链与自检结果照常返回。

### 5.8 `state` 体积与 413 规避

- 只承载判定必需字段；**不塞**原始请求体/响应体正文、完整 answers、超长 context。
- `produced.billing.details` 超过 N 条（建议 **20**，读全局配置 `billing_verify.max_details`）只保留前 20 + 汇总；`evidence.upstreamUsage` 只保留数值键。
- 数值统一裁剪小数位，避免长浮点序列化膨胀。
- 目标：单次 judge body 控制在**数 KB 级**，从源头远离 413。

---

## 6. 后端改动清单

| 模块 | 文件 | 改动 |
|---|---|---|
| knot-service | `service/RoutingRuleBillingDryRunService.java` | 事实层（**已落地**） |
| knot-service | `service/support/BillingFactChecks.java` | 自检层六项（**已落地**，`UPSTREAM_COST_DEVIATION` 支持 SKIP）；**批次 2a 加 `GATEWAY_RECOMPUTE_MATCH`** |
| knot-service | `dto/routing/BillingFact{,.Subject,.Usage,.RuleRef,.Amount,.AmountLine}` | 事实数据类型（**已落地**）；**批次 2a 加 `gatewayBilling`** |
| knot-service | `dto/routing/BillingCheckCode` / `BillingCheckStatus` / `BillingCheckResult` / `BillingRuleSource` | 自检枚举与结论（**已落地**） |
| knot-service | `vo/routing/RoutingBillingVerifyRequest` / `RoutingBillingVerifyResult` / `RoutingBillingVerdict` | 端点契约（**已落地**，Verdict 为骨架）；**批次 2a：Request 加 `knotExtend` 回传字段** |
| knot-service | `service/support/JevBillingJudge.java` | 构造 body（**state = 被告链，v6 契约**，含 provider/session_id/trace）、读全局设置解析裁判 consumer+rule、调用、防御式 answers 解析、错误码映射、state 截断、降级 |
| knot-service | `service/PlatformSettingService.java` | 全局设置读写 `billing_verify.*`（**已落地**，表 = `ks_system_settings`，类名按命名规范） |
| knot-dal | `entity/PlatformSettingEntity.java` / `mapper/PlatformSettingMapper.java`(+xml) | 系统设置表实体与 Mapper（**已落地**） |
| knot-dal | `mapper/RoutingConsumerMapper.java` | **新增 `getByCode`**（现有只有 `getBySecretKey`），用于按消费者 code 现取 secretKey |
| knot-admin | `controller/RoutingRuleController.java` | 端点 `POST /api/routing-rules/{id}/test/billing-verify`（**已落地**） |
| knot-admin | `controller/PlatformSettingController.java` | `GET/PUT /api/system-settings`，仅暴露 `billing_verify.*` 本大类 key（**已落地**，权限已绑） |
| knot-front | `RoutingRuleTestDrawer.vue` | **批次 2a**：从抽屉响应解析 `knot_extend`（**流式独立 SSE 事件**）并回传；**批次 3**：三区展示 |
| knot-adapter | `model/NormalizedUsage` / `NormalizedBillingAmount` / `NormalizedUsageDetail` | **金额改字符串契约**（见下节；**已落地**） |
| knot-adapter | `usage/calculator/AbstractBillingModeCalculator` + 三个计算器 | 去掉 8 位小数钳制，改 `DECIMAL128`（**已落地**） |
| knot-service | `service/support/BillingFactChecks` | 复算同步 `DECIMAL128`，容差去掉绝对下限（**已落地**） |
| knot-common | `constants/enums/BillingUnitEnum` / `BillingModeEnum` | 退役 `PER_MINUTE`（**已落地**，见下节） |
| knot-service | `service/BillingService` | 管理端离线试算同契约（**已落地**） |
| knot-gateway | `traffic/QuotaDimension` | `COST_SCALE` 8 → **12**（**已落地**） |
| knot-gateway | `runtime/GatewayRequestHandler` | `usageOf` String → BigDecimal 还原（**已落地**）；**被告注入方**（`KnotExtendPayload.of`，已存在） |

> **命名说明**：表名 `ks_system_settings` 与 API 路径 `/api/system-settings` 按「系统设置」语义保留，Java 类一律用 `PlatformSetting*` —— 规范 4.4 禁用 `System` 作为类名词根（先例：`SystemService`→`OperationLogService`）。
>
> **权限 code 关键字分工（robin 2026-10-17 拍板）**：`system:` 前缀下，**用户设置 = `preference`，系统设置 = `settings`**，两者绝不共用。原「用户设置」占用的 `system:settings:{page,update,view}` 已改为 `system:preference:*`；「系统设置」新权限落 `system:settings:{view,update}`。`ks_permissions.permission_code` 有唯一键而 data.sql 用 `INSERT IGNORE`——撞名会被**静默丢弃**，且 `ks_api_permission_bindings` 无外键，会留下**永久 403 且无报错**的孤儿绑定。既有库必须跑 `2026-10-17-permission-code-settings-to-preference.sql`（**与建表脚本两个都要跑**）。
>
> **DB 迁移**：`2026-10-17-system-settings.sql`（建 `ks_system_settings` + 种子行，幂等，**已落地**）、`2026-10-17-retire-per-minute-billing-unit.sql`（纯守卫，**已落地**）。

### 金额对外契约（2026-10-17 robin 拍板，已落地）

`knot_extend.billing` 里 **`detail[].cost` / `detail[].price` 与 `totalCost` 一律十进制普通字符串，不设小数位上限**。

| 项 | 落地口径 |
|---|---|
| 精度（除尽） | 取精确商自然位数（单价小数位 + 最多 6 位移位），不补齐不截断 |
| 精度（除不尽） | `MathContext.DECIMAL128`（34 位有效数字）兜底——当前全部单位基数是 10 的幂（PER_MINUTE 退役后），恒定除尽，实际不会触发 |
| 唯一转换点 | `NormalizedBillingAmount.plainMoney`（`toPlainString()`），明细与总价共用 |
| 科学计数法 | 只在 BigDecimal 调整指数 < -6 时触发（`1E-8`）；6 位小数单价（1.5e-6）留 BigDecimal 也是普通表示 |
| 记账链路 | `GatewayRequestHandler.usageOf` 唯一一次 String → BigDecimal 还原；空/非法按「无成本」 |

守卫用例：`TokenBillingModeCalculatorTest.sixDecimalUnitPriceIsNotFlattenedToZero` / `tinyCostIsPlainDecimalNotScientificNotation`；`BillingServiceTokenBillingTest.tinyUnitPriceIsNotFlattenedToZeroByScaleClamp`；`GatewayTrafficGuardTest.scaledAmountRejectsAmountBeyondCountableRange` / `costWithTinyAmountIsNotTruncatedToZero`；`BillingUnitEnumTest.everyUnitSizeIsPowerOfTen`。

### 三次拍板：退役 PER_MINUTE（已落地）

`PER_MINUTE=60` 是唯一非 10 的幂基数，会逼出「秒 ÷ 60」的不终止除法；dev 库 5 条规则全为 TOKEN/EMBEDDING × 1K_TOKENS，字典行 10-02 已删，前端零硬编码——**零使用，可退役**。落地：`BillingUnitEnum` 删常量、`BillingModeEnum.AUDIO` 改 `PER_SECOND`、守卫迁移 `2026-10-17-retire-per-minute-billing-unit.sql`（存量行必撞唯一键硬失败，零行无操作）。未来按分钟计费须在**配置期**折成每秒价，60 不进金额公式。

---

## 7. 前端展示（`RoutingRuleTestDrawer.vue`）

三区（对所有模型/协议生效，非 jev 专用）：

1. **计费事实区** — 上游 usage ↔ `knot_extend.usage`（网关归一化视图）对照；命中规则（`matchedBy`）；**被告账**（`knot_extend.billing` 明细原样）；上游 `cost` 对照行（缺失显示「上游未返回」）；重算对照（影子链算式摊开，**仅本区展示**）。
2. **自检区** — 七项 PASS/FAIL/**SKIP** 标签，FAIL 高亮 + 失败含义。
3. **裁判结论区** — `noul` 概率条、`choice` 选中项 + 置信度（缺失则标「无置信度」）、`score` 刻度 + legend 文案；顶部 summary。`available=false` 时显示「裁判未接入/不可用」+ 具体原因（如「余额不足 402」）。

> **双盲提示**：裁判区与自检区**互不引用**——JEV 没有看过自检结论。两区同时亮红灯 = 两个独立裁判同指问题，可信度远高于单边结论；仅一边亮 = 优先相信自检（代码精确），JEV 结论供语义参考。

保留人工判断，不做自动阻断。

---

## 8. 验证门禁

| 门 | 命令 | 判据 |
|---|---|---|
| 后端 | `mvn -f knot-server/pom.xml test` | BUILD SUCCESS。新增单测：① dry-run 事实正确；② 自检七项含 **cost 缺失→SKIP**；③ **`GATEWAY_RECOMPUTE_MATCH` 反向用例**（被告账被篡改 → FAIL，且自检其余项不因此误判）；④ questions 模板契约合法（noul 必含 true/false、score 为数组 minItems 1）；⑤ **answers 防御式解析**（缺 confidence/probabilities/legend 不报错）；⑥ 错误码→降级映射（含 402/413/429）；⑦ state 截断生效；⑧ **state 内容断言**（`computed` / `parsedUsage` / 自检结论**绝不出现**在 judge body 里）；⑨ 全局设置解析（配置缺失/消费者不存在/规则未启用 → `available:false` 且原因明确，**不抛异常**）；⑩ `getByCode` 取到的 secretKey **不落日志** |
| 迁移实证 | 探针在旧库跑两个 10-17 迁移 | 幂等（二轮结果不变）；种子行存在；既有 `ks_*` 表零改动（Information Schema 前后计数一致） |
| 前端类型 | `npx vue-tsc --noEmit` | EXIT=0 |
| 前端构建 | `npx vite build` | EXIT=0 |
| 真启冒烟 | 一次性库 + admin 18092 + 本地假上游（**假上游按真实四类 answers 形态返回**）<br>① gpt-4o（非 jev）→ 三区齐全<br>② DECISIONS/jev → 同样三区（证明通用）<br>③ 故意解绑规则 → 自检 FAIL + 裁判 `FALLBACK_ONLY`<br>④ 抹掉 usage → `USAGE_NOT_EMPTY` FAIL<br>⑤ **篡改回传的 knot_extend.billing 金额 → `GATEWAY_RECOMPUTE_MATCH` FAIL + 裁判 `cost_plausible=false`/高风险（双盲两侧同时咬到）**<br>⑥ **规则单价配错 10 倍 + 上游未返回 cost → 自检全绿（`GATEWAY_RECOMPUTE_MATCH` 两边同错不 FAIL、`UPSTREAM_COST_DEVIATION` SKIP），唯裁判 `cost_plausible=false`/高风险——证明 JEV 不可替代**<br>⑦ 假上游返回 402/429 → 裁判区降级且给出对应原因<br>⑧ 关闭 judge → 事实/自检照出 | 全部符合预期 |
| 清理 | 杀进程 / DROP 一次性库 | 零残留 |

> ⚠ ③④⑤ 是**反向与降级验证**，必须做，否则证明不了校验有效、降级安全。⑤ 同时证明双盲有效：自检与 JEV 在互不知晓的前提下同指一处。

---

## 9. 实施批次

0. **批次 0（全局配置底座）**：`ks_system_settings` 建表 + 迁移 + `PlatformSetting*` 实体/Mapper/Service + `RoutingConsumerMapper.getByCode` + `GET/PUT /api/system-settings`（含权限绑定）。**已落地。**
1. **批次 1（影子链 + 端点骨架）**：事实层 + 自检层六项 + `billing-verify` 端点（judge 恒定不可用）。**已落地。**
2. **批次 2a（被告入链，v6 新增）**：`RoutingBillingVerifyRequest` 加 `knotExtend` 回传字段 + `BillingFact` 加 `gatewayBilling` + 自检加 `GATEWAY_RECOMPUTE_MATCH` + 前端解析 SSE 独立事件回传。门禁：`mvn test` + `vue-tsc` + 反向用例（篡改金额必 FAIL）。
3. **批次 2b（判定层）**：`JevBillingJudge`（**state = 被告链，双盲**）+ 契约 + 防御式解析 + 错误码映射 + 截断 + 降级。门禁：`mvn test`（含 state 内容断言）。
4. **批次 3（前端三区）**：事实区补被告账与重算对照、自检区七项、裁判区。门禁：`vue-tsc` + `vite build`。
5. **批次 4（冒烟）**：七场景含反向降级 + 双盲同指 +（可选）配置 UI + 清理零残留。

---

## 10. 待确认 / 风险

1. **裁判调用路径 —— 已定（robin 指令）**：走**网关 `/v1/decisions`**。全局配「consumer code + 路由规则 code」，裁判必经网关（复用鉴权/路由/观测，且 dogfooding）。原「admin 直连 OpenRouter」备选作废。
2. **`return_usage_detail` 保证机制（v6 新增待确认）**：测试用的消费者若未开该开关，响应里没有 `knot_extend`，被告缺席。选项：a) 测试链路强制要求/代开；b) 专用调试消费者；c) 被告缺失时明确降级提示。倾向 a 或 b——否则校验核心场景不可用。
3. ~~`provider.only:["TypeSafe"]` 是否过严~~ **已移除（2026-10-20）**：上游 provider 白名单无法从 knot 侧约束，路由规则已锁定模型；相关配置与下发一并删除。
4. **避免递归**：judge 请求自身产生计费事件，**仅测试抽屉显式触发**，生产链路不调用。
5. ~~`allow_fallbacks:false` 的代价~~ **已移除（2026-10-20）**：不再下发该开关，上游 provider 选择交由上游自行决定。
6. **真实 OpenRouter 未验证**（继承）：需 management key，沙箱无凭据；配真 key 后需复核真实 answers 形态、耗时与 answer 缺失率。
7. **LLM 判定是辅助非终裁**：概率会浮动，抽屉保持「人读结论」，不自动阻断/改账。
8. **边界演进**：某条 JEV 语义问题若被证明可确定性化，应下沉到自检层。
9. **`ks_` 域敏感**（继承）：只新增 `ks_system_settings` 一张表，不改动任何既有 `ks_` 表（尤其权限三表零改动）。
10. **新管理接口必须补权限绑定**（继承）：admin 接口**默认拒绝**；`GET/PUT /api/system-settings` 的绑定已随权限迁移落地。
11. **`getByCode` 属新增 mapper 方法**：返回实体含 `secretKey`，**严禁**打进日志/返回给前端。
12. **裁判规则需人工先配好**：应用方须先建「jev 模型 + DECISIONS 绑定 + 指向它的路由规则 + 消费者」，再把两个 code 配进设置表；缺任一步 → `available=false` 并给出可执行的提示文案。

---

## 附：批次 1 落地纪要（影子链）

| 议题 | 结论与理由 |
|---|---|
| 复用生产链路 | 提取器 / 归一化 / 三级回退全部复用 knot-adapter。**不另写一套解析与算钱**——否则「校验通过」只证明影子链路自洽 |
| 不用 `UsageExtractorRegistry` | 它在 knot-gateway，admin 侧不依赖 gateway 模块，故在 knot-service 按同序自行组装 |
| 金额独立复算 | 自检只用计算器**输出**的明细反算，不复用计算器内部逻辑——复用等于让被验对象自证。容差 1 ppm |
| 端点自带响应体 | **不发第二次上游请求**：重打一次既多花真实费用，也与抽屉显示结果对不上 |
| MODEL_POOL 目标 | 必须显式指定 `modelCode`：池本身无用量口径，未指定则明确拒绝 |
| `{id}` 不是装饰 | 用 `RoutingRuleMapper.getById` 校验规则确实存在 |
| 自检只有三态 | 判不了的一律 SKIP 并说明原因，绝不充 PASS |
| TOKEN 模式恒出 4 条明细 | 未缓存输入 / 缓存读 / 缓存写 / 输出，零量也出，口径稳定 |

> ⚠ **顺带发现的既有授权缺口（本方案未改，建议单独立项）**：
> `data.sql` 中 `POST /api/routing-rules/{id}/test/stream` 绑定的是 permission_id **113（`routing:rule:create`）**，
> 而同为测试类的同步端点绑定的是 **116（`routing:rule:test`）**。
> 即：**持有「创建」权限即可越过「测试」权限调用流式调试接口**。属越权缺口，不在本方案改动范围。

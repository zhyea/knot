# 路由规则测试 · JEV 计费裁判方案（v7 · 管理端编排 · 双盲判定）

> 日期：2026-10-17 晚 · 状态：设计稿，待 robin 评审
> 版本链：v6（10-17 中午，**已整体拆除**，文档 `routing-test-drawer-billing-verify-design.md` 打⛔ 保留为过程记录）→ **v7（本稿）**
> v7 相对 v6 的重生理由与不变遗产见 §0。

## 0. v6 → v7：为什么拆、为什么这样重生

### 0.1 v6 死因（robin 拍板）

「**不要把自检逻辑嵌到网关服务里，就如 `RoutingRuleBillingDryRunService`**」——该服务把事实层、自检驱动、结果组装一锅端，是补丁式堆叠形态。**v7 的架构处方：触发点移到管理端测试抽屉（被测响应之后），拆成「薄编排 + 纯函数协作者」，网关只当被告产出方与 JEV 调用通道，不碰裁判逻辑。**

### 0.2 v7 落实的三项决策（robin 2026-10-17 晚拍板）

| # | 决策 | 内容 |
|---|---|---|
| 1 | JEV 调用路径 | **经网关 `POST /v1/decisions`**（复用鉴权/路由/计费/观测，dogfooding；admin 直连 OpenRouter 备选作废） |
| 2 | 触发方式 | **测试返回即自动判**——`knot_extend` 在场就送 JEV，不阻塞抽屉主流程（两步走，见 §3.2） |
| 3 | 自检范围 | **自检 + JEV 双盲对照**——本地确定性重算做算术权威区，JEV 管语义区，两区互不可见 |

### 0.3 从 v6 继承、勿从零再议的遗产

| 遗产 | 出处 |
|---|---|
| 判定域划分（自检管算术可确定性 / JEV 管语义；自检全绿 ≠ 计费正确） | v6 §1.2-1.3（robin 认知基础） |
| `knot_extend` 是网关自产的**被告**（三角：被告账 / 影子重算基准 / 上游 cost 证人） | v6 §4（代码实证表） |
| `state` 输入契约：进「已产出 + 底层依据」，禁 `computed`/`parsedUsage`/自检结论 | v6 §5.3 硬约束表 |
| `cost_plausible` 是语义嗅探不是算术复核；questions 四条与防御式解析 | v6 §5.4 / §5.6 |
| 自检七项（含 `GATEWAY_RECOMPUTE_MATCH`）与三态纪律（SKIP 不充 PASS） | v6 §5.1 |
| 失明场景⑥（单价错 10 倍 + 上游无 cost → 自检全绿唯 JEV 咬到） | v6 §8 |

### 0.4 v7 相对 v6 的实质差异

| 维度 | v6（已拆） | v7 |
|---|---|---|
| 触发点 | 独立端点 + 一锅端 dry-run 服务 | 测试响应之后自动触发，编排服务薄壳化 |
| 配置载体 | `ks_system_settings` 表 + `PlatformSetting*`（**已随拆除删除**） | **部署级配置项 `knot.jev.*`**（类 `GatewayRuntimeProperties` 模式），不建表、不加权限面 |
| 被告保证 | §4.2 待确认（消费者没开则被告缺席） | **已闭环**：测试头设施（`X-Knot-Test-Return-Usage`，10-17 交付）使抽屉默认拿到 `knot_extend`；仅用户手动取消勾选且消费者没开时缺席 → 明确降级提示 |
| 模块边界 | service 一锅端 | `service` 顶层只放 `@Service` 编排；事实/自检/裁判协作者进 `service.support`（命名规范） |
| 异步性 | 同步端点 | 裁判**不阻塞**测试主流程：测试结果先渲染，裁判结果后到再渲染第三区 |

---

## 1. 核心目的

对路由规则测试抽屉的当次调用，以其真实产出的 `knot_extend.billing`（网关账）为**被告**，双通道校验：

- **自检区（代码，权威）**：同口径影子重算 + 七项确定性校验，抓算术错误；
- **裁判区（JEV，辅助）**：以「被告 + 底层依据」为输入做语义嗅探，抓自检原理上抓不到的场景性错误（规则错配、账目与依据相悖）。

人对照两区下结论，**不自动阻断、不改账**。

---

## 2. 链路总览

```
① 抽屉发测试请求（Authorization: Bearer 消费者密钥 + Rule 头 [+ X-Knot-Test-Return-Usage]）
      │
      ▼  网关响应：上游响应体 + knot_extend{usage, billing} + 并列 upstreamUsage（原始 usage 原文）
      │  （knot_extend 由网关自产注入——被告；详见 v6 §4 实证表）
      ▼
② 抽屉渲染测试结果；若 knot_extend 在场 → 自动发第二步（不阻塞①的渲染）
      │  POST /api/routing-rules/{id}/test/billing-verdict
      │  { modelCode, protocol, targetCode, knotExtend, upstreamUsage, requestBody }
      ▼
③ 编排服务 RoutingTestBillingVerdictService（knot-service，薄壳）
      ├─▶ 被告链：原样记录已产出（knot_extend）+ 底层依据（upstreamUsage 原文 / 规则快照 / 请求侧属性）——不算钱
      ├─▶ 影子链：BillingFactAssembler 离线重算（adapter 提取器 → 三级回退取规则 → 归一化，生产同口径）
      │     └─▶ BillingFactChecks 七项（代码精确，权威；SKIP 三态纪律）
      └─▶ 判定链：JevBillingJudge → 网关 POST /v1/decisions
            state = 被告链全部（不见重算值、不见自检结论——双盲）
            questions = 四条（usage_parse_ok / rule_match_ok / cost_plausible / risk_level）
      ▼
④ 返回 { produced, evidence, checks[7], verdict, trace } → 抽屉三区
```

### 2.1 判定域划分（robin 认知基础，v6 §1.3 原文保留）

> 「代码如果能够计算准确，自检就不该报错，所以交给 JEV 来判断。」

- 自检判定域 = **算术正确性**（可确定性）：明细公式、汇总一致、单位换算、网关账与重算账全等。代码算对了这些项就该全绿。
- 自检全绿 ≠ 计费正确：规则选得恰不恰当（EMBEDDING 模型套 CHAT 规则，算式全对但场景荒谬）、用量形态与计费模式相不相称——**语义正确性代码判不了**。
- JEV 判定域 = **语义正确性**：自检全 PASS 的案件，JEV 是唯一可能发现问题的人。双盲不是形式——JEV 若见了自检结论，「自检全绿 → 裁判照抄全绿」，裁判价值归零。

---

## 3. 触发与前提

### 3.1 前提：`knot_extend` 在场

| 场景 | knot_extend | 行为 |
|---|---|---|
| 消费者 `return_usage_detail` 已开 | 有（网关按配置注入） | 正常判 |
| 消费者没开 + 抽屉勾选「测试时临时开启」（**默认勾选**） | 有（测试头生效） | 正常判 |
| 消费者没开 + 用户**手动取消勾选** | **无** | 链路短路：裁判区显示「本次响应无消费详情，未发起裁判」+ 一键提示（勾选临时开启后重测） |

### 3.2 两步走：裁判不阻塞主流程

DECISIONS 无流式，判官调用可能数百 ms 至数秒。若同步嵌在 `/test` 响应里，抽屉主流程被拖慢。

- **第一步**（既有链路，零改动）：`/test` 或 `/test/stream` 返回，抽屉立即渲染测试结果；
- **第二步**（新增）：前端检测到 `knot_extend` 后**自动**打 `billing-verdict` 端点，结果到达渲染裁判区（loading → 结果/降级）。

流式在 `complete` SSE 事件之后触发（此时 `knot_extend` 独立事件已到手）。

### 3.3 防递归

judge 请求自身也产生计费事件（DECISIONS 模型走 TOKEN 计费），但裁判**只由测试抽屉显式触发**，judge 响应不触发裁判——无递归。生产链路不调用裁判。

---

## 4. 被告与依据（入链契约）

### 4.1 端点契约

```
POST /api/routing-rules/{id}/test/billing-verdict
```

| 字段 | 来源 | 说明 |
|---|---|---|
| `modelCode` | 调试目标 | 被测模型业务码 |
| `protocol` | 抽屉选择 | 接口协议 |
| `targetCode` | 响应头 `X-Knot-Model` | 实际承接模型 |
| `knotExtend` | 抽屉响应**原样回传**（整包=响应体顶层字段；流式=独立 SSE 事件，前端解析提取） | 被告：`{usage, billing}` |
| `upstreamUsage` | 响应里与 `knot_extend` 并列的原始 usage 原文 | 依据：上游口径 |
| `requestBody` | 抽屉请求体 | 依据：请求侧计费属性（size 等） |

**不发第二次上游请求**：被告就是「已在抽屉里显示过的那次调用」的产出（v6 附录纪律保留）。

### 4.2 `state` 设计（继承 v6 §5.3 终态契约，硬约束不变）

```json
{
  "subject":    { "modelCode": "...", "protocol": "...", "stream": false, "ruleSource": "EXPLICIT" },
  "produced":   { "usage": { …knot_extend.usage 原样… },
                  "billing": { "totalCost": "0.00129", "currency": "USD", "details": [ … ] } },
  "evidence":   { "upstreamUsage": { …原文数值键… }, "upstreamCost": "…",
                  "billingRule": { "code": "…", "billingMode": "…", "unit": "…", "currency": "…",
                                   "pricingPlan": "…", "matchedBy": "…", "versionCode": "…" },
                  "requestAttributes": { "size": "1024x1024" } }
}
```

| 不进 state | 原因 |
|---|---|
| `computed`（管理端侧重算金额） | 二次运算；且影子与生产读同一份 `kb_billing_rules`，配置级错误两边同错，对答案无意义 |
| `parsedUsage`（影子解析用量） | 影子链路产物，与 `knot_extend.usage` 同源同口径 |
| 自检结论 PASS/FAIL | 双盲：JEV 是独立裁判，不读别的老师的阅卷结果 |

---

## 5. 自检层（代码精确，七项，权威）

`BillingFactChecks`（`service/support`，纯函数）：输入 `BillingFact`（含 `gatewayBilling` 被告账），输出 `BillingCheckResult[]`。

| 自检项 | 判定内容 | 失败含义 |
|---|---|---|
| `USAGE_NOT_EMPTY` | 解析用量非全 0 | **静默归零**（最危险） |
| `TOTAL_TOKENS_CONSISTENT` | `totalTokens == input+output`（上游未给需补齐） | 口径缺失或补齐失效 |
| `COST_FORMULA_MATCH` | 明细 `amount × unitPrice ÷ unitSize == cost`，汇总 == `totalCost` | 计算器 / 单位换算出错 |
| **`GATEWAY_RECOMPUTE_MATCH`** | **被告账与同口径重算账全等**（1 ppm 容差） | **网关热路径自己算错**——规则取错 / 单价版本错。影子读同一份规则表，配置级错误两边同错，唯此项抓「当次调用算错」 |
| `RULE_MODE_MATCH` | 计费模式 vs 用量形态 | 配置错配 |
| `RULE_SOURCE_EXPECTED` | `matchedBy` 是否为期望层级 | 模型没绑规则，悄悄走默认 |
| `UPSTREAM_COST_DEVIATION` | knot 费用 vs 上游 `cost` 量级（**cost 缺失 → SKIP**） | 数量级异常提示 |

三态纪律：判不了的一律 **SKIP 并说明原因，绝不充 PASS**。自检结论只进抽屉自检区，不进 JEV state。

**影子链复用生产链路**（v6 附录纪律）：提取器 / 归一化 / 三级回退全部复用 knot-adapter，不另写一套解析与算钱——否则「校验通过」只证明影子链路自洽。金额独立复算：只用计算器**输出**的明细反算，复用计算器内部逻辑等于让被验对象自证。

---

## 6. 判定层 JevBillingJudge

### 6.1 调用形态（经网关，dogfooding）

- 读配置 `knot.jev.judge-rule-code` + `knot.jev.judge-consumer-code`（见 §7）；
- `RoutingConsumerMapper.getByCode` 现取裁判消费者 secretKey（**凭据不落地第二处、不落日志、不下前端**）；
- 校验裁判规则存在且启用；任一不满足 → `available:false` + 可执行原因（**不抛异常炸抽屉**）；
- 管理端向**网关** `POST /v1/decisions`（`gatewayBaseUrl` 透传，与被测同实例）：
  `Authorization: Bearer <裁判消费者 secretKey>` + `Rule: <judge-rule-code>`。

### 6.2 questions 四条（继承 v6 §5.4，契约合法）

| key | 题型 | 判定 |
|---|---|---|
| `usage_parse_ok` | noul | 归一化是否完整正确覆盖上游计费维度 |
| `rule_match_ok` | choice | 命中规则是否合适（APPROPRIATE / FALLBACK_ONLY / MODE_MISMATCH / MISSING） |
| `cost_plausible` | noul | **语义嗅探**：账与依据是否相称、场景是否荒谬（不算术复核） |
| `risk_level` | score | 综合风险刻度（有序数组 criteria） |

### 6.3 调用加固参数（继承 v6 §5.5）

`provider = { allow_fallbacks: false, only: ["TypeSafe"], data_collection: "deny" }`——**`allow_fallbacks:false` 最关键**：回退到普通聊天模型则 answers 形态崩坏；`data_collection:deny` 防计费数据被留存训练。`session_id` 串被测请求 id；`trace` 带 `trace_name:"knot-billing-verdict"`。不开 `require_parameters`。

### 6.4 answers 防御式解析（继承 v6 §5.6）

noul 只读 `noul` 值（无 confidence）；choice 的 confidence/probabilities schema 可选，缺失按「无置信度」展示；score 为连续 double、probabilities/legend 用字符串键；**缺某题答案 → 该题标「未返回」，不整体失败**。

### 6.5 错误码映射与降级（继承 v6 §5.7）

| 码 | 对策 |
|---|---|
| 400 | 契约错误，单测覆盖 questions 模板 |
| 401/403 | 「裁判凭据无权」，`available:false` |
| 402 | 余额不足提示（与故障区分） |
| 413 | 触发 state 截断（§6.6）重试一次，仍败降级 |
| 429 | 指数退避最多 1 次，仍败降级 |
| 500/502/503/524/529 | 降级 `available:false` |

**统一原则：判定失败绝不阻塞**——被告链、影子链与自检照常返回。

### 6.6 state 体积与 413 规避

只承载判定必需字段；`details` 超过 20 条只保留前 20 + 汇总；`upstreamUsage` 只保留数值键；目标单次 body 数 KB 级。

---

## 7. 配置与 DB 预置（不建表）

### 7.1 配置项（`application.yml` / 环境变量，管理端）

```yaml
knot:
  jev:
    judge-rule-code: jev-billing-judge      # 指向 jev 模型的裁判路由规则 code
    judge-consumer-code: jev-judge-client  # 裁判专用消费者 code（调 /v1/decisions 用）
```

- 类 `GatewayRuntimeProperties` 模式（管理端已有先例：`gatewayBaseUrl` 透传）；
- **不建 `ks_system_settings`**（v6 的配置底座已随拆除删除，且这是部署级静态配置，不是用户可调运行时设置）；
- 配置缺失 / 消费者不存在 / 规则未启用 → 裁判区 `available:false` + 可执行提示，其余两区不受影响。

### 7.2 DB 预置清单（一次性库冒烟与正式库各配一次）

| # | 资产 | 值 |
|---|---|---|
| 1 | provider account | `base_url=https://openrouter.ai`，auth=Bearer（**management key**，普通 key 403） |
| 2 | model | `model_type=DECISION`，`upstream_model=typesafe/jev-1.13-20260917` |
| 3 | binding | `protocol=DECISIONS`，`api_path=/api/alpha/decisions`，`usage_extractor=DEFAULT-TOKEN` |
| 4 | 路由规则 | `rule_code=jev-billing-judge`，目标=上述模型，enabled |
| 5 | 消费者 | `consumer_code=jev-judge-client`，绑定上述规则，`return_usage_detail` 随意（裁判响应不需要） |

裁判调用自身产生 TOKEN 计费事件——冒烟时用假上游即可闭环，不花真钱。

---

## 8. 后端改动清单

| 模块 | 文件 | 职责 |
|---|---|---|
| knot-service | `service/RoutingTestBillingVerdictService.java` | **@Service 薄编排**：组装被告/依据 → 影子重算 → 自检 → 调 JEV → 聚合。不含业务逻辑本体 |
| knot-service | `service/support/BillingFactAssembler.java` | 影子链：adapter 提取器 + 三级回退 + 归一化，产出 `BillingFact`（含 `gatewayBilling`） |
| knot-service | `service/support/BillingFactChecks.java` | 七项自检，纯函数，三态 |
| knot-service | `service/support/JevBillingJudge.java` | DECISIONS 调用 / 解析 / 加固参数 / 错误码降级 / state 截断 |
| knot-service | `dto/routing/BillingFact{,.Subject,.Usage,.RuleRef,.Amount,.AmountLine}` | 事实数据类型 |
| knot-service | `dto/routing/BillingCheckCode` / `BillingCheckStatus` / `BillingCheckResult` / `BillingRuleSource` | 自检枚举与结论 |
| knot-service | `vo/routing/RoutingBillingVerdictRequest` / `RoutingBillingVerdictResult` / `RoutingBillingVerdict` | 端点契约（verdict 含 answers/summary/available/reason） |
| knot-admin | `controller/RoutingRuleController.java` | `POST /{id}/test/billing-verdict` |
| knot-admin | `db/data.sql` | 权限绑定（复用 `routing:rule:test`，**不新增权限**——与 `/test` 同语义同权限） |
| knot-front | `RoutingRuleTestDrawer.vue` | 测试结果后自动触发；三区展示；降级/缺席态 |

**零 DB schema 改动、零迁移**（预置是数据不是结构）。命名遵守规范：service 顶层仅 `@Service`，协作者全进 `service.support`；禁 `System`/`Common` 等泛化词根。

---

## 9. 前端展示（抽屉三区）

1. **计费事实区** — 上游 usage ↔ `knot_extend.usage` 对照；命中规则（matchedBy）；**被告账明细原样**；上游 cost 对照行（缺失显示「上游未返回」）；重算算式摊开（影子链，仅本区）。
2. **自检区** — 七项 PASS/FAIL/**SKIP** 标签，FAIL 高亮 + 失败含义。
3. **裁判结论区** — noul 概率条、choice 选中项 + 置信度（缺失标「无置信度」）、score 刻度 + legend；顶部 summary；`available:false` 显示原因（如「裁判未配置：knot.jev.judge-rule-code 缺失」「余额不足 402」）。

双盲提示（v6 §7 保留）：两区互不引用——JEV 没看过自检结论。两区同亮红灯 = 两个独立裁判同指问题，可信度远高于单边；仅一边亮 = 优先相信自检（代码精确），JEV 供语义参考。保留人工判断，不自动阻断。

---

## 10. 验证门禁

| 门 | 判据 |
|---|---|
| 后端 `mvn -f knot-server/pom.xml test` | BUILD SUCCESS。新增单测：① 事实组装正确（含被告账快照）；② 七项各态 + **cost 缺失→SKIP**；③ **`GATEWAY_RECOMPUTE_MATCH` 反向用例**（篡改被告金额 → FAIL 且不误伤其余项）；④ questions 模板契约合法（noul 必含 true/false、score 数组 minItems 1）；⑤ answers 防御式解析（缺 confidence/probabilities/legend 不报错、缺题标未返回）；⑥ 错误码→降级映射（含 402/413/429）；⑦ state 截断生效；⑧ **state 内容断言**（`computed`/`parsedUsage`/自检结论绝不出现）；⑨ 配置缺失/消费者不存在/规则未启用 → `available:false` 原因明确不抛异常；⑩ secretKey 不落日志 |
| 前端类型 / 构建 | `vue-tsc --noEmit` EXIT=0；`vite build` EXIT=0 |
| 真启冒烟（一次性库 + admin 18092 + 假上游按真实四类 answers 返回） | 八场景见下 |
| 清理 | 杀进程 / DROP 一次性库，零残留 |

冒烟八场景（继承 v6 §8，⑤⑥ 是有效性核心证据）：

1. 普通模型（非 jev）测试 → 三区齐全（证明通用，非 jev 专用）；
2. DECISIONS/jev 模型测试 → 同样三区；
3. 故意解绑计费规则 → 自检 `RULE_SOURCE_EXPECTED` FAIL + 裁判 `FALLBACK_ONLY`；
4. 抹掉 usage → `USAGE_NOT_EMPTY` FAIL；
5. **篡改回传的 `knot_extend.billing` 金额 → `GATEWAY_RECOMPUTE_MATCH` FAIL + 裁判 `cost_plausible=false`/高风险（双盲两侧同时咬到）**；
6. **规则单价配错 10 倍 + 上游未返回 cost → 自检全绿（重算两边同错不 FAIL、cost 偏离 SKIP），唯裁判 `cost_plausible=false`/高风险——证明 JEV 不可替代**；
7. 假上游返回 402/429 → 裁判区降级且给出对应原因；
8. 不配 `knot.jev.*` → 裁判区 `available:false` + 配置提示，事实/自检照出。

---

## 11. 实施批次

| 批次 | 内容 | 门禁 |
|---|---|---|
| 1 | 影子链：`BillingFact` 家族 + `BillingFactAssembler` + `BillingFactChecks` 七项 | `mvn test`（七项各态 + 篡改反向 + cost SKIP） |
| 2 | `JevBillingJudge`：配置读取 + 调用 + 防御解析 + 降级 + 截断 | `mvn test`（契约/解析/降级/state 断言/secretKey 不落日志） |
| 3 | 编排服务 + 端点 + 权限绑定 + 前端三区 + 自动触发 | `mvn test` + `vue-tsc` + `vite build` |
| 4 | DB 预置 + 冒烟八场景 + 零残留 | 冒烟全过 + 汇报 |

---

## 12. 待确认 / 风险

1. **裁判规则需人工预置**（§7.2）：缺任一步 → `available:false` + 可执行提示，不炸链路。
2. **真实 OpenRouter 未验证**（继承）：需 management key，沙箱无凭据；冒烟走假上游。配真 key 后复核真实 answers 形态/耗时/缺失率。
3. **`provider.only:["TypeSafe"]` 会合并账号级允许列表**：换供应商需改代码——建议抽成配置项（批次 2 落地时设为 `knot.jev.provider-only`，默认 `TypeSafe`）。
4. **LLM 判定是辅助非终裁**：概率浮动，抽屉保持「人读结论」。
5. **边界演进**：某条 JEV 语义问题若被证明可确定性化，下沉自检层。
6. **DB 遗留**（与本文档无关的存量）：dev 库 `ks_system_settings` 死表 + 死权限 154/155/156 + 死绑定 212/213/214（v6 拆除遗留）——本方案不新建表，建议单开一轮清理。

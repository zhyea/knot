# knot_extend.billing.detail.type 取值盘点（2026-10-12）

> 起因：一次调试请求返回 `detail: [{"type":"TOKEN","tokens":295,...}]`，而该模型绑定的规则是**高低峰计费**。
> 本文先穷举 `detail[]` 中 `type` 的全部取值来源，再给出根因与待拍板的收敛方向。**本轮只盘点，未改代码。**

## 1. 结论速览

| 问题 | 结论 |
|---|---|
| `type` 有几套取值域 | **三套**，由 `BillingModeCalculator` 实现类决定，彼此语义不通 |
| 这份响应为什么是 `TOKEN` | 走的是 `DefaultTokenUsageExtractor → SimpleBillingModeCalculator`，`type` 直接取 `kb_billing_rules.billing_mode` 原值 |
| 谁是 calculator 的选择者 | **`UsageExtractor`（厂商/协议提取器）**，不是 `billing_mode`——这是根因 |
| 高低峰为什么看不出来 | `NormalizedUsage` 与 `NormalizedUsageDetail` 都没带 `pricingPlan` / `phase` / `reason` / `multiplier`；管理端 `PricingPreviewResult` 才有 |
| 更大的隐患 | OpenAI 系上游永远拿不到 `basePrices.{input,output,cacheRead}`，只按 `defaultUnitPrice` 收总量费 |

## 2. `type` 取值全表

`detail` 元素类型：`NormalizedUsageDetail(String type, Long tokens, BigDecimal cost, BigDecimal price)`
（`knot-adapter/.../model/NormalizedUsageDetail.java`）

### 域 A — `TokenBillingModeCalculator`（6 个，内部价格维度）

`knot-adapter/.../usage/calculator/TokenBillingModeCalculator.java:72-83`

| type | 触发条件 | tokens 语义 | 单价来源 |
|---|---|---|---|
| `uncachedInput` | 恒定输出 | `inputTokens − cacheRead − cacheWriteTotal` | `PriceKind.INPUT` |
| `cachedRead` | 恒定输出 | Cache Read token | `PriceKind.CACHE_READ` |
| `cachedWrite` | `cacheWriteTtlSplit()==false` | Cache Write 单字段 | `PriceKind.CACHE_WRITE`，缺省回退 input 价 |
| `cachedWrite5m` | `cacheWriteTtlSplit()==true` | 5m 写 | `PriceKind.CACHE_WRITE_5M` |
| `cachedWrite1h` | `cacheWriteTtlSplit()==true` | 1h 写 | `PriceKind.CACHE_WRITE_1H` |
| `output` | 恒定输出 | output token（含 reasoning） | `PriceKind.OUTPUT` |

> 只有这一套是按价格维度拆分的真明细；`cachedWrite` 与 `cachedWrite5m/1h` 互斥。

### 域 B — `SimpleBillingModeCalculator`（= 计费模式名，8 个 + 脏值）

`knot-adapter/.../usage/calculator/SimpleBillingModeCalculator.java:47`
`type = normalizeDetailType(rule.getBillingMode(), mode)` → **`kb_billing_rules.billing_mode` 原样 trim**，为空才回退 `mode.code()`。

取值即 `BillingModeEnum.code()`（`knot-common/.../enums/BillingModeEnum.java`）：
`TOKEN`、`REQUEST`、`IMAGE`、`AUDIO`、`VIDEO`、`EMBEDDING`、`FREE`、`CUSTOM`。

要点：**明细恒为 1 条，`tokens` 字段实际是「计费量」非 token**（REQUEST=1、IMAGE=张数、AUDIO=秒、TOKEN=total_tokens），单价恒走 `resolveDefaultPrice`（`defaultUnitPrice`），**完全绕过 `basePrices`**，也不区分 input/output/cache。

### 域 C — `VideoBillingModeCalculator`（模式名 + 可选分辨率后缀）

`knot-adapter/.../usage/calculator/VideoBillingModeCalculator.java:41-43`
`type = normalizeDetailType(...) [+ ":" + resolution]`，例如 `VIDEO`、`VIDEO:720P`、`VIDEO:1080P`；`resolution` 由 `normalizeResolution` 产出，非白名单时 fallback 到原值大写（`2560x1440` → `2560X1440`），故域 C 是**开放集合**。

### 三域汇总

| 域 | 生产者 | 命名风格 | 是否开放集合 | 明细条数 |
|---|---|---|---|---|
| A | TokenBillingModeCalculator | camelCase 价格维度 | 否（6 个） | 4 或 5 |
| B | SimpleBillingModeCalculator | UPPER 模式名 | 半开放（列值自由文本） | 恒 1 |
| C | VideoBillingModeCalculator | 模式名 + `:suffix` | **是** | 恒 1 |

## 3. 根因：calculator 由 extractor 决定，不由 billing_mode 决定

```
UsageExtractorRegistry.extract            knot-gateway/.../upstream/usage/UsageExtractorRegistry.java:56-75
  ├─ resolveExtractorCode()               :97-124  ← 选【提取器】
  │     binding.usage_extractor / stream_usage_extractor
  │     provider_code == ANTHROPIC  → ANTHROPIC
  │     protocol ∈ {IMAGE_*}        → IMAGE
  │     protocol == VIDEO_GENERATIONS → VIDEO
  │     其余                        → DEFAULT
  ├─ BillingUsage billing = extractor.extractUsageBody(body)
  └─ UsageNormalizationSupport.normalize(..., extractor.calculator())   :70
        └─ UsageNormalizationSupport:41  calculator.calculate(context)
```

提取器与 calculator 的静态绑定（`knot-adapter/.../usage/*UsageExtractor.java`）：

| extractor | code | `calculator()` 返回 | 因此 type 域 |
|---|---|---|---|
| DefaultTokenUsageExtractor | `DEFAULT` | **SimpleBillingModeCalculator** | B |
| AnthropicUsageExtractor | `ANTHROPIC` | **TokenBillingModeCalculator** | A |
| ImageUsageExtractor | `IMAGE` | SimpleBillingModeCalculator（构造注入） | B |
| VideoUsageExtractor | `VIDEO` | VideoBillingModeCalculator | C |

`UsageNormalizationSupport:31-36` 解析出的 `modeEnum` **只用于补算 totalTokens**，从不参与选择 calculator。

### 本次响应还原

deepseek 是 OpenAI 兼容协议、provider 非 ANTHROPIC、binding 未指定 extractor → `DEFAULT` → `SimpleBillingModeCalculator`：

- `type = rule.billing_mode = "TOKEN"` ← 与价格维度同名，纯属撞名
- `tokens = firstAmount(usage.totalTokens(), usage.inputTokens()) = 295`
- `price = defaultUnitPrice × 1e6 / unitSize = 0.15`
- `cost = 295 × unitPrice / unitSize = 0.00004425`

**即：这条规则配的 `basePrices.input/output`、缓存细分、input/output 分项全部没参与计算**，`kb_billing_rules.billing_mode=TOKEN` 只是被抄进了 `type` 字符串。
走 ANTHROPIC 上游的同一条规则则会算出完全不同的金额与 5 条明细。

> `billing.pricingPlan == PEAK_OFF_PEAK` 在域 B 下**仍然生效**（`PeakOffPeakPricing.resolveDefaultPrice` 会乘 `multiplier` 或取 ABSOLUTE 相位 `unitPrices.input`），
> 所以结论不是「没算」，而是**算完了但对端完全看不出是按高峰还是低峰收的**。

## 4. 高低峰为何不可见

| 层 | 有没有相位信息 | 出处 |
|---|---|---|
| `PhaseDecision(phase, reason, multiplier, matchedRule)` | 有 | `knot-common/.../pricing/PhaseDecision.java` |
| 管理端预览 `PricingPreviewResult` | 有（`phase`/`reason`/`multiplier`/`timezone`/`occurredAt`） | `knot-service/.../vo/billing/PricingPreviewResult.java:25-30` |
| 网关输出 `knot_extend.billing` | **完全没有** | `NormalizedUsage.java` / `NormalizedUsageDetail.java` |

`NormalizedUsage` 当前只有 `{totalTokens, totalCost, currency, billingVersion, detail}`，没有一个字段说明「这笔钱用了什么方案、命中哪个相位、倍率多少」。

## 5. 待拍板的收敛方向（未实施）

以下四项可拆可合，等确认后再动：

**① calculator 改按 `rule.billing_mode` 选（推荐做）**
把「提取量」与「怎么计价」解耦：extractor 只产出 `BillingUsage`，由 `UsageNormalizationSupport` 依据 `modeEnum` 从 `BillingModeCalculator` 目录里取。
收益：TOKEN 规则在任何上游都得到一致的 4~6 条明细 + `basePrices` 生效；风险：OpenAI 系上游的历史账单金额会变（此前是总量 × defaultUnitPrice）。

**② `type` 收敛为单一枚举**
建议统一到「计费量维度」：`input`/`output`/`cachedRead`/`cachedWrite`/`cachedWrite5m`/`cachedWrite1h`/`request`/`image`/`audio`/`video`/`embedding`/`custom`，
并把分辨率等修饰移到独立字段（如 `detail.qualifier = "720P"`），杜绝 `A:B` 拼接。

**③ 补齐 `pricingPlan` / `phase` / `reason` / `multiplier`**
建议放 `billing` 层级（一次请求只有一个相位），而不是塞进每条 detail。`billingVersion` 顺势抬到 `v2`。

**④ `tokens` 字段名去歧义**
非 TOKEN 模式下它的值是张数/秒数/次，建议改为 `amount`，或加 `unit` 字段说明。

> 影响面提醒：`knot_extend` 是对外契约，前端 `knot-front` 目前**零引用**（已 grep `uncachedInput|cachedWrite5m|detail.type` 无命中），改动不牵连前端；但下游调用方若有解析需告知。

## 6. 附：Follow-up

- [ ] `reasoning` token 未独立计价，当前并入 `output`（OUTPUT 单价）。
- [ ] `VideoBillingModeCalculator:54` 命中的分辨率价绕过 `PricingPlan`，PEAK_OFF_PEAK 放开到非 TOKEN 前必须先处理。
- [ ] `HolidayCalendar` 首期为 `EMPTY`（网关侧 `pricingPlan(plan)` 未传日历），节假日判定在线上不生效。

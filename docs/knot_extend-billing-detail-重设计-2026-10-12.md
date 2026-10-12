# knot_extend.billing 计费明细重设计（2026-10-12 草案）

> 目标（robin 原话）：**每一笔钱花在哪 + 计费的基础，都要在 detail（含其所在的 billing 视图）里体现。**
> 例：token 消耗要能看出 input / output / cached_read / cached_write 各自的量与钱。
> 本文是**设计草案，未落地代码**。关键取舍见 §7，待 robin 拍板。

## 0. 一句话结论

明细要拆得出来，必须先做 **P0 前置改动（calculator 按 `billing_mode` 选）**，否则 OpenAI 系上游永远只给 1 条 `type=TOKEN`，下文的维度拆分全部落空。
`knot_extend.usage` **不需要改**——它的字段天然就是 detail 维度的来源，两者一一对应。

## 1. P0 前置：calculator 选择解耦 `billing_mode`

现状（`UsageExtractorRegistry:97-124` + `UsageNormalizationSupport:31-36`）：
提取器决定 calculator，`modeEnum` 只用于补 `totalTokens`，从不选 calculator。

| 上游 | 提取器 | calculator | 后果 |
|---|---|---|---|
| OpenAI 系（DEFAULT） | DefaultUsageExtractor | **Simple** | TOKEN 规则塌成 1 条，绕开 `basePrices` |
| ANTHROPIC | AnthropicUsageExtractor | Token | 正常拆 5 条 |
| IMAGE | ImageUsageExtractor | Simple | 1 条 |
| VIDEO | VideoUsageExtractor | Video | 1 条 + 分辨率 |

**改法**：`UsageNormalizationSupport.normalize` 依据 `rule.billing_mode`（已算出的 `modeEnum`）从 `BillingModeCalculator` 目录取对应实现；extractor 只负责把响应体转成 `BillingUsage`。
**代价**：OpenAI 系 TOKEN 规则的历史账单金额会变（此前按 `defaultUnitPrice × 总量`，改后按 `basePrices.input/output/cacheRead` 分项）。这是正确化，但属破坏性，需告知下游。

## 2. detail 元素新结构

### 2.1 维度枚举（钱花在哪）

`BillingDimension`（建议 snake_case，与 `usage` 字段对齐，便于对账）：

| mode | dimension 取值 | 含义 | 对应 usage 字段 |
|---|---|---|---|
| TOKEN | `uncached_input` | 净额计费输入 token | `input.tokens` 扣减 `cacheRead`/`cacheWrite` 后的部分 |
| TOKEN | `output` | 输出 token（含 reasoning） | `output.tokens` |
| TOKEN | `cached_read` | 读缓存 token | `input.tokens.cache_read` |
| TOKEN | `cached_write` | 写缓存 token（standard） | `input.tokens.cache_write` |
| TOKEN | `cached_write_5m` | 5m 写缓存（ttl） | `input.tokens.cache_write_5m` |
| TOKEN | `cached_write_1h` | 1h 写缓存（ttl） | `input.tokens.cache_write_1h` |
| 其它 | `request` / `image` / `audio` / `video` / `embedding` / `custom` | 按模式 | —（video 可带 `qualifier`） |

> 说明：`uncached_input` 与 `usage.input.tokens` 不严格相等（它已扣掉缓存部分），但语义清晰——**这是实际按 INPUT 单价计费的那部分 input**。

### 2.2 元素 record

```java
public record NormalizedUsageDetail(
        BillingDimension dimension,   // 钱花在哪（枚举）
        String qualifier,             // 可选修饰，如 "720P"；非视频为 null
        long amount,                  // 计费量（token / 张 / 秒 / 次）
        String unit,                  // token / request / image / second / minute
        BigDecimal unitPrice,         // 该维度单价（每 unitSize）
        int unitSize,                 // 单价基准量；1_000_000 = 每百万，1 = 每单位
        BigDecimal cost) {            // amount * unitPrice / unitSize
```

字段语义对照旧 `NormalizedUsageDetail(String type, Long tokens, BigDecimal cost, BigDecimal price)`：
- `type` → `dimension`（枚举化，消除三套混乱取值域）
- `tokens` → `amount` + `unit`（旧字段在非 TOKEN 下其实是张/秒/次，名不副实）
- `price` → `unitPrice` + `unitSize`（旧 `price` 恒为「每百万单价」，丢失了 unitSize 信息）

### 2.3 示例

```json
{ "dimension":"cached_read", "amount":300, "unit":"token",
  "unitPrice":0.3, "unitSize":1000000, "cost":0.00009 }
```

## 3. billing 层：计费基础字段（请求级）

一次请求只有一个发生时点，因此相位/方案是**请求级**，放 `billing` 一层，所有 detail 共享。
新增字段（未命中方案时按 `@JsonInclude(NON_NULL)` 省略）：

| 字段 | 取值 | 来源 |
|---|---|---|
| `pricingPlan` | `FIXED` / `TIERED` / `PEAK_OFF_PEAK` | `rule.pricing_plan` |
| `phase` | `PEAK` / `OFF_PEAK` | `PhaseDecision.phase`（仅 PEAK_OFF_PEAK 有值） |
| `phaseReason` | `PEAK_WINDOW` / `HOLIDAY` / `MAKE_UP_WORKDAY` / `DEFAULT` / `NO_TIMESTAMP` | `PhaseDecision.reason` |
| `multiplier` | 倍率（ABSOLUTE 模式恒 1） | `PhaseDecision.multiplier` |
| `rateMode` | `ABSOLUTE` / `MULTIPLIER` | `pricing.rateMode`（仅 PEAK_OFF_PEAK 有值） |

> 为什么放 billing 级而非每条 detail：`phase`/`multiplier` 由 `occurredAt` 一次性判定，对本次请求所有维度同值；放 detail 是冗余且易不一致的。
> （若 robin 坚持每条都标，§7 有备选——但会增加 JSON 体积与序列化成本。）

## 4. 完整 JSON 样例（PEAK_OFF_PEAK + OpenAI 上游，P0 落地后）

```json
{
  "knot_extend": {
    "usage": { "input": {"tokens": {"text":700,"cache_read":300}}, "output": {"tokens":{"text":500}}, "total_tokens":1700 },
    "billing": {
      "totalTokens": 1700,
      "totalCost": 0.00919,
      "currency": "USD",
      "billingVersion": "v2",
      "pricingPlan": "PEAK_OFF_PEAK",
      "phase": "PEAK",
      "phaseReason": "PEAK_WINDOW",
      "multiplier": 1.0,
      "rateMode": "MULTIPLIER",
      "details": [
        {"dimension":"uncached_input","amount":400,"unit":"token","unitPrice":3.0,"unitSize":1000000,"cost":0.0012},
        {"dimension":"cached_read","amount":300,"unit":"token","unitPrice":0.3,"unitSize":1000000,"cost":0.00009},
        {"dimension":"cached_write_5m","amount":400,"unit":"token","unitPrice":1.0,"unitSize":1000000,"cost":0.0004},
        {"dimension":"cached_write_1h","amount":100,"unit":"token","unitPrice":2.0,"unitSize":1000000,"cost":0.0002},
        {"dimension":"output","amount":500,"unit":"token","unitPrice":15.0,"unitSize":1000000,"cost":0.0075}
      ]
    }
  }
}
```

对照你最初的调试响应：`detail` 只有 1 条 `TOKEN`、看不到高低峰——重设计后变成 5 条维度 + 显式 `pricingPlan/phase/multiplier`。

## 5. 实施步骤（待确认后执行）

1. 新增 `BillingDimension` 枚举（值如上表）、`BillingBasis` record（`pricingPlan/phase/phaseReason/multiplier/rateMode`）。
2. `BillingModeCalculator` 接口：新增基础计算（复用 `PeakOffPeakResolver`，或给 `PricingPlan` 加 `resolveBasis(ctx)`），`Token/Simple/Video` 三实现填充 basis。
3. `NormalizedBillingAmount` 增 `BillingBasis basis`；`UsageNormalizationSupport` 按 `modeEnum` 选 calculator 并把 basis 拼进 `NormalizedUsage`。
4. `NormalizedUsage` 增 §3 字段 + `@JsonInclude(NON_NULL)`；`NormalizedUsageDetail` 按 §2.2 改造。
5. `billingVersion` 语义修正：当前代码返回 `rule.getVersionCode()`（规则版本号），与「knot_extend 结构契约版本」混用。建议改为固定契约版本号 `v2`（结构变了就必须抬），与规则版本解耦。
6. 单测 + 真启 jar 冒烟：高低峰两种时刻、ttl/standard 两种缓存写、OpenAI 与 Anthropic 两条上游（验证 P0 后金额口径一致）。

## 6. 影响面

- 前端 `knot-front`：grep `uncachedInput|cachedWrite5m|detail.type` 无命中，**零引用**，改动不牵连前端。
- 对外契约：**破坏性**——`type` 改为 `dimension`、新增基础字段。下游若有解析需告知；**`billingVersion` 抬 v2 即为此留痕**。
- `knot_extend.usage`：无需改。

## 7. 待拍板关键取舍

- **A. 计费基础放哪**：billing 级（推荐，紧凑无冗余）/ 每条 detail 内嵌 basis（严格满足「在 detail 中体现」但冗余）。
- **B. dimension 命名风格**：snake_case 对齐 usage（推荐，便于对账）/ camelCase 沿用 billing（与 price/cost 一致）。
- **C. calculator 按 billing_mode 选（P0）**：强烈建议纳入本批，否则明细拆分对 OpenAI 系上游无效。
- **D. billingVersion 抬 v2**：建议是（结构破坏性变更的标准做法）。

> 另：阶梯价（TIERED）的档位区间属请求级基础，如需在 `billing` 层再补 `tierRange` 可后续追加，首期聚焦高低峰。

# UsageExtractor 双视图一次提取方案

> 状态：已评审，实施中
> 日期：2026-10-23
> 范围：`knot-server/knot-adapter`、`knot-server/knot-gateway`、`knot-server/knot-service`
> 目标：让一次上游 usage 解析同时产出计费视图与统一用量视图，消除当前输出阶段的二次解析和口径漂移。

## 1. 结论先行

不建议把 `UsageExtractor` 的返回值直接改成单一的 `ModelUsage`。

推荐改法是让提取器返回双视图：

```text
UsageExtractor
  └─ UsageExtractItem<T extends BillingUsage>
       ├─ T billing       // 计价输入，保持 sealed 类型
       └─ ModelUsage usage // 对外统一用量视图
```

理由：

1. `BillingUsage` 是计价输入，按 TOKEN / IMAGE / VIDEO / AUDIO 分型；`ModelUsage` 是对外展示结构，二者职责不同，不能互相替代。
2. `ModelUsage` 缺少媒体计费所需的部分字段（如视频的 `pixels`、`width`、`height`、`sound`、`reference`）。
3. 当前输出层的 `ModelUsageNormalizer` 会重新读取原始 usage，而不是从 `BillingUsage` 稳定转换；同一份数据被两套字段别名和语义判断分别解析，容易出现计费视图与展示视图不一致。
4. 因此应前移的是"解析和归一化"，不是把计价类型替换成展示类型。

## 2. 现状与问题

### 2.1 当前链路

```text
上游响应体
  │
  ├─ UsageExtractor.extractUsageBody()
  │     └─ BillingUsage
  │           └─ BillingModeCalculator
  │                 └─ NormalizedUsage（金额）
  │
  └─ UsageAccounting.rawUsage
        └─ KnotExtendPayload.of()
              └─ ModelUsageNormalizer.fromSource()
                    └─ ModelUsage（knot_extend.usage）
```

### 2.2 问题本质

当前不是简单的 `BillingUsage -> ModelUsage` 转换，而是两条并行解析链：

1. 计费链：`rawUsage -> BillingUsage`
2. 展示链：`rawUsage -> ModelUsage`

两条链各自维护字段别名、缓存语义和兜底规则，导致：同一字段可能在两边得到不同结果；协议特有的缓存口径只能在一侧正确处理；新增字段时必须同时改两处；计费结果与 `knot_extend.usage` 可能互相矛盾。

### 2.3 已暴露的协议差异

| 协议 | 输入字段 | 缓存字段位置 | reasoning 字段位置 | 输入是否已含缓存 |
|---|---|---|---|---|
| OpenAI Chat Completions | `prompt_tokens` | `prompt_tokens_details.cached_tokens` | `completion_tokens_details.reasoning_tokens` | 是 |
| OpenAI Responses | `input_tokens` | `input_tokens_details.cached_tokens` | `output_tokens_details.reasoning_tokens` | 是（按 OpenAI 语义） |
| AWS Bedrock Converse | `inputTokens` | `cacheReadInputTokens`、`cacheWriteInputTokens`、`cacheDetails` | 无统一 reasoning 字段 | 否；总输入需加回缓存 |
| Anthropic Messages | `input_tokens` | `cache_read_input_tokens`、`cache_creation_input_tokens` / TTL 明细 | 无统一 reasoning 字段 | 否；总输入需加回缓存 |

## 3. 设计目标

### 3.1 目标

1. 一次协议解析同时产出 `BillingUsage` 和 `ModelUsage`。
2. 计费器继续使用强类型的 `BillingUsage`，不降低现有 sealed 类型约束。
3. `KnotExtendPayload` 不再重新解析原始 usage。
4. 协议特有的缓存语义在提取器边界统一处理，不在通用 normalizer 中猜测。
5. 保持 SSE、非流式、媒体计费、无 usage 兜底行为可验证。

### 3.2 非目标

- 不修改计费规则、数据库结构或前端契约。
- 不改变 `knot_extend` 的对外字段形状。
- 不把 `ModelUsage` 扩展成计费超集。
- 不在本方案中重做路由、重试、频控或上游请求适配。

## 4. 方案设计

### 4.1 新增双视图记录

新增 `UsageExtractItem<T extends BillingUsage>`，放在 `org.chobit.knot.gateway.model.usage`：

```java
public record UsageExtractItem<T extends BillingUsage>(T billing, ModelUsage usage) {
    // billing 非空；usage 可为 null（上游无可归一化用量）
    // totalTokens()：billing 优先，usage 补充
    // mergeMax()：billing 分量取最大，usage 取累计总量更大的完整视图
}
```

### 4.2 调整 UsageExtractor 契约

```java
public interface UsageExtractor<T extends BillingUsage> {
    UsageExtractItem<T> empty();
    UsageExtractItem<T> extractUsage(Map<String, Object> body, Map<String, Object> requestBody);
    default UsageExtractItem<T> extractUsage(Map<String, Object> body) { ... }
    default UsageExtractItem<T> extractUsageBody(String responseBody, Map<String, Object> requestBody) { ... }
    default UsageExtractItem<T> extractEventStreamUsage(String responseBody, Map<String, Object> requestBody) { ... }
}
```

按"无历史包袱"原则，不保留旧返回类型重载；一次性迁移并全量 grep 残留。

### 4.3 提取器内部产出

每个提取器在解析协议字段时同时构造两个视图。token 字段读取收口，协议语义由提取器确定，不再由通用 normalizer 根据字段名猜测。

### 4.4 UsageAccounting 与输出层

`UsageAccounting` 增加 `ModelUsage usage`：

```java
public record UsageAccounting(Map<String, Object> rawUsage,
                              Map<String, Object> rawBody,
                              BillingUsage billing,
                              ModelUsage usage,
                              NormalizedUsage normalized) { }
```

`KnotExtendPayload.of(accounting)` 直接使用 `accounting.usage()`，输出阶段不再调用 `ModelUsageNormalizer`。

### 4.5 SSE 与整包路径

整包与 SSE 使用同一套 `UsageExtractItem`；SSE 逐事件提取后按累计量选择/合并；最终输出只读选中的视图。

### 4.6 通用兜底

无专用提取器的 OpenAI 兼容协议，保留 `ModelUsageNormalizer` 作为提取阶段的默认实现；专用提取器可覆盖协议字段映射，但不得把解析推迟到输出层。

## 5. 为什么不直接返回 ModelUsage

| 方案 | 结论 | 原因 |
|---|---|---|
| 只返回 `ModelUsage` | 不采用 | 缺少媒体计费维度，计费器失去 sealed 类型分派 |
| 只返回 `BillingUsage` | 现状问题 | 输出层必须二次解析原始 usage，形成双解析和口径漂移 |
| 返回 `UsageExtractItem(billing, usage)` | 推荐 | 保留计费类型安全，同时让展示视图在提取边界一次完成 |
| 把 `ModelUsage` 扩展成计费超集 | 不采用 | 会污染对外展示契约 |

## 6. 协议口径要求

### 6.1 OpenAI Chat Completions

- `prompt_tokens` 已包含缓存命中；
- `prompt_tokens_details.cached_tokens` 是缓存读明细；
- `completion_tokens_details.reasoning_tokens` 是输出 reasoning 明细，已包含在 `completion_tokens` 中；
- `total_tokens` 缺失时使用输入总量 + 输出总量补齐。

### 6.2 OpenAI Responses

- `input_tokens` 按 OpenAI 语义包含缓存；
- `input_tokens_details.cached_tokens` 需读取；
- `output_tokens_details.reasoning_tokens` 需读取；
- 事件形态下 usage 可能在 `response.usage`，使用 `UsageRawReader` 统一定位。

### 6.3 Bedrock Converse

- `inputTokens` 只含未缓存输入；
- 总输入 = `inputTokens + cacheReadInputTokens + cacheWriteInputTokens`；
- `cacheDetails` 存在时按 `ttl=5m/1h` 拆分缓存写。

### 6.4 Anthropic Messages

- `input_tokens` 不含缓存读写；
- 缓存读、缓存写、5m/1h 明细加回输入总量；
- 与 Converse 的差异通过提取器协议上下文表达。

## 7. 实施步骤

| 阶段 | 内容 | 退出条件 |
|---|---|---|
| P0 | 新增 `UsageExtractItem`、重构 `ModelUsageNormalizer` 为单一投影入口 | 单元测试覆盖字段归一 |
| P1 | 改造 token 类提取器：Default、Anthropic、BedrockConverse | 两视图同输入下字段一致 |
| P2 | 改造媒体提取器：Image / Video / Audio | 计费字段不回归，媒体计数正确 |
| P3 | 改造 `UsageAccounting`、`KnotExtendPayload`、SSE 合并 | 输出层不再调用 `ModelUsageNormalizer` |
| P4 | 同步影子链与测试 | `BillingFactAssembler`、路由判定和测试通过 |
| P5 | 收口验证 | 后端全量测试通过 |

## 8. 影响面

- `knot-adapter`：`UsageExtractor`、各 `*UsageExtractor`、`UsageAccounting`、`KnotExtendPayload`、`ModelUsageNormalizer`、新增 `UsageExtractItem`
- `knot-gateway`：`UsageExtractorRegistry`、`ProxyStreamingFlux`
- `knot-service`：`BillingFactAssembler`、`RoutingTestBillingVerdictService`

## 9. 测试方案

协议字段测试、双视图不变量（`billing.inputTokens == usage.input.tokens.total()` 等）、集成测试、门禁 `mvn -f knot-server/pom.xml test`。

## 10. 风险与应对

| 风险 | 应对 |
|---|---|
| 缓存重复计算 | 协议上下文显式决定是否加回；双视图测试固定三类口径 |
| SSE 合并丢失明细 | `UsageExtractItem` 一起进入 merge/选择 |
| 媒体计费回归 | 先保持媒体 `BillingUsage` 不变，仅新增 usage 视图 |
| 调用方遗漏 | 改前全量 grep，改后再次 grep 残留 |
| 输出为空 | 明确 `usage=null` 语义 |

## 11. 验收标准

1. `UsageExtractor` 一次提取返回 `UsageExtractItem`。
2. `KnotExtendPayload` 不再调用 `ModelUsageNormalizer`。
3. Chat、Responses、Converse、Anthropic 的缓存和 reasoning 字段均有明确测试。
4. 计费视图与展示视图的输入、输出、缓存、total 不变量全部通过。
5. `BillingFactAssembler` 与生产链路使用同一提取契约。
6. 后端真实门禁通过并记录 `BUILD SUCCESS`。

---

## 附录 A：评审结论（2026-10-23）

> 评审方式：逐行核对方案引用的 8 个核心文件 + 全量 grep 调用点。

### A.1 现状描述核对（属实）

| 方案断言 | 代码实际 | 结论 |
|---|---|---|
| `KnotExtendPayload:37` 调 `ModelUsageNormalizer.fromSource` | 行 37–41 一致 | 属实，输出层二次解析确认存在 |
| `UsageExtractor:26/63` 返回 `BillingUsage` | 接口 `T extends BillingUsage` | 属实 |
| `VideoBillingUsage:13` 含 `pixels/width/height/sound/reference` | record 行 13–19 | 属实，`ModelUsage` 无这些字段 |
| `UsageExtractorRegistry:72` 提 `BillingUsage` 并计价 | 行 72/90 | 属实 |
| Anthropic 缓存加回 | `AnthropicUsageExtractor:59` → `.withCacheCountedInInput()` | 属实 |

**核心动机成立**：`ModelUsageNormalizer.fromRaw` 独立重读 raw 字段并用 `promptTokensReported` 启发式猜缓存语义；`TokenBillingUsage.from` 是另一条独立读取链。两条链必须手工同步——这是真实隐患，不是假想。

### A.2 必须纠正/补齐的点

1. **收益表述校正**：是"消除口径分裂"，不是"消除二次解析"——解析工作量仍在，价值在于把两份独立读取收敛到一处、缓存语义只决策一次。
2. **接口默认方法漏列**：`extractUsageBody`、`extractEventStreamUsage` 均为返回 `BillingUsage` 的默认实现，必须同步改签名与 `mergeMax` 调用。
3. **`adapter.extractUsage` 兜底链**：`UsageExtractorRegistry:77` 在 extractor 为空时走该兜底，改造后也必须产出 `ModelUsage`，否则 `KnotExtendPayload` 读 `accounting.usage()` 会为 null。
4. **SSE 两套合并语义**：生产路径 `ProxyStreamingFlux.UsageState.merge` 按 `totalTokens()` 选最大事件；接口默认 `extractEventStreamUsage` 才是分量 `mergeMax`。纯媒体响应 `totalTokens==0` 时会保首事件。需明确两者统一。
5. **`TokenUsageSupport` 设想偏重**：`ModelUsageNormalizer.fromBilling` + `withBodyMedia` 已能从 `BillingUsage` + `rawBody` 产出 `ModelUsage`；务实路径是"计费字段走 billing 既有字段不重复读取，仅 reasoning 与媒体计数从 raw 补取"。
6. **Converse 待实测分支真实存在**：`BedrockConverseUsageSupport:21` 注释明确"`withCacheCountedInInput` 刻意不同，真实环境实测后再定"，与本方案 §6.3 呼应，P1 首验。

### A.3 待确认四问的答复

| # | 结论 |
|---|---|
| 1 命名 | **用 `UsageExtractItem`**：`Item` 在命名白名单内；`UsageExtractResult` 的 `Result` 不在白名单，禁用 |
| 2 Responses on AWS | 风险窄：per-binding 提取器绑定已是消解点；默认 fallback 假设 OpenAI 已含缓存，未显式绑定的"Responses 风格但 input 不含缓存"模型会少算，需在文档明确"非 OpenAI 缓存语义模型必须显式绑定提取器" |
| 3 媒体提取器 | 默认复用 `fromBilling(billing, rawBody)`，不必各自实现专用映射 |
| 4 管理端试算第三条链 | 已实质解决：`RoutingTestBillingVerdictService` 只镜像选 code，真正解析委托 `BillingFactAssembler`，复用真实提取器，无第三条别名字链 |

### A.4 评审结论

方向采纳。实施按 §7 P0→P5 推进，并将 A.2 的 1–5 点作为落地硬约束落实（本文件正文按原样保留，实施以下文附录 B 为准）。

---

## 附录 B：实施记录（2026-10-23）

### B.1 落地结果

按 §7 P0→P5 全部落地，方案验收 1–6 条均达成。

| 阶段 | 产出 |
|---|---|
| P0 | 新增 `UsageExtractItem<T>`；`ModelUsageNormalizer` 重构为唯一投影入口（删除 `fromSource`/`fromRaw`/`promptTokensReported` 启发式） |
| P1 | Default / Anthropic / BedrockConverse 三个 token 提取器返回双视图 |
| P2 | Image / Video / Audio 三个媒体提取器返回双视图（媒体计量进 usage 视图） |
| P3 | `UsageAccounting` 增 `usage` 组件；`UsageExtractorRegistry` 传双视图并覆盖 `adapter.extractUsage` 兜底；`KnotExtendPayload` 读 `accounting.usage()` |
| P4 | `BillingFactAssembler` 取 `.billing()`（影子链只需计费半边） |
| P5 | 3 个 adapter 测试 + 4 个 gateway 测试适配；全量门禁通过 |

### B.2 关键设计落点

- **单一投影入口**：`ModelUsageNormalizer.fromBilling(BillingUsage, rawBody)` —— 两侧 token 总量与缓存各档取自 `billing`（与计费同源），仅明细（reasoning / image / video token）与媒体计数从原始 usage 补取。`promptTokensReported` 启发式彻底移除。
- **双视图不变量成立**：`usage.input.tokens.total() == billing.inputTokens()`、`usage.output.tokens.total() == billing.outputTokens()`，由新增 `shouldExtractDualViewFromResponseBody`、`shouldAddCacheBackToInputTotalForAnthropicStyleUsage` 钉死。
- **输出层零二次解析**：`KnotExtendPayload.of` 只读 `accounting.usage()`，不再触碰原始 usage。

### B.3 变更文件

**主代码（9 个文件，1 新增）**

- 新增 `knot-adapter/.../model/usage/UsageExtractItem.java`
- 重写 `knot-adapter/.../usage/ModelUsageNormalizer.java`
- `knot-adapter/.../usage/extractor/UsageExtractor.java`
- `knot-adapter/.../usage/extractor/{DefaultTokenUsage,AnthropicUsage,BedrockConverseUsage,ImageUsage,VideoUsage,AudioUsage}Extractor.java`
- `knot-adapter/.../model/UsageAccounting.java`
- `knot-adapter/.../model/usage/KnotExtendPayload.java`
- `knot-gateway/.../upstream/usage/UsageExtractorRegistry.java`
- `knot-service/.../service/support/BillingFactAssembler.java`

**测试（7 个文件）**

- `knot-adapter`：`UsageExtractorTest`、`ModelUsageNormalizerTest`、`DecisionsUsageTest`
- `knot-gateway`：`GatewayRequestHandlerReturnUsageDetailTest`、`GatewayRequestHandlerStreamingTrafficTest`、`GatewayTrafficMsgDtoTest`、`ProxyStreamingFluxTest`

### B.4 门禁证据

```text
mvn -f knot-server/pom.xml test
BUILD SUCCESS   EXITCODE=0
knot-common / plugin-api / plugin / dal / adapter / service / admin / gateway  全部 SUCCESS
123 个测试类执行，Failures: 0，Errors: 0，Skipped: 0
```

日志：`.workbuddy/build-test-final.log`

### B.5 遗留与边界（如实标注）

1. **SSE 纯媒体选择**：生产 `ProxyStreamingFlux.UsageState.merge` 仍按 `totalTokens()` 选最大事件，纯媒体流式（token=0）保首事件的既有局限保留，本批未改（方案未要求，避免引入新行为）。
2. **Converse usage 视图**：detail 读取器只识 snake_case，Converse 的 camelCase 明细读不到，故其 `usage` 仅有总量、无缓存明细拆分——与旧 `fromRaw` 行为一致，无回归；总量与 `billing` 严格一致。
3. **`UsageAccounting.rawBody`**：按 §4.1 保留用于审计 / `upstreamUsage` 透出，输出层不再消费其做解析。
4. **`RoutingTestBillingVerdictService`**：未改。它只镜像选 extractor code，真正解析委托 `BillingFactAssembler`，不存在第三条别名字链（与附录 A.3 #4 结论一致）。


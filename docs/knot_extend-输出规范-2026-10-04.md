# knot_extend 输出规范（2026-10-04）

> 取代 `docs/model_usage-输出规范-2026-09-29.md`。
> 配套实现：`knot-adapter` 的 `model/usage/*`、`usage/*`；`knot-gateway` 的
> `runtime/GatewayRequestHandler`、`upstream/stream/ProxyStreamingBody`、`upstream/usage/UsageExtractorRegistry`。

## 1. 结论速览

| 项 | 决策 |
|---|---|
| 对外字段名 | **`knot_extend`**（取代 `model_usage`，再上取代 `knot_usage`；一次做干净不留兼容） |
| 输出内容 | **两视图**：`{usage, billing}`——归一化用量 + 网关计费结果 |
| **`raw` 已移除** | 上游原始 `usage` 不再透传：它已完整参与 `usage` 归一化，重复输出只会让调用方在两个口径间选错 |
| 生效条件 | 路由消费者 `return_usage_detail = true` |
| 无计费规则时 | `billing` 整个省略（区分「免费」与「未配置计费」），`usage` 照常输出 |
| 计费规则解析 | 模型显式绑定 → 模型族精确 → `model_family` 为空的默认规则（三级回退） |
| 流式注入 | 上游 `data: [DONE]` 之前插入；上游不发 `[DONE]` 时在流结束处兜底插入 |

## 2. 两视图定义（`KnotExtendPayload`）

```json
{
  "knot_extend": {
    "usage":   { "input": {"tokens": {"text": 700, "cache_read": 300}},
                 "output": {"tokens": {"text": 200}},
                 "total_tokens": 1200 },
    "billing": { "totalTokens": 1200, "totalCost": 0.00579, "currency": "USD",
                 "billingVersion": "v1",
                 "detail": [ {"type":"uncachedInput","tokens":700,"cost":0.0021,"price":3.0},
                             {"type":"cachedRead","tokens":300,"cost":0.00009,"price":0.3},
                             {"type":"cachedWrite5m","tokens":400,"cost":0.0004,"price":1.0},
                             {"type":"cachedWrite1h","tokens":100,"cost":0.0002,"price":2.0},
                             {"type":"output","tokens":200,"cost":0.003,"price":15.0} ] }
  }
}
```

| 视图 | 类型 | 用途 |
|---|---|---|
| `usage` | `ModelUsage` | 归一化统一用量，跨厂商字段一致，**业务侧以此为准** |
| `billing` | `NormalizedUsage` | 网关按计费规则算出的总价与明细；无规则可命中时省略 |

注入位置：
- 整包响应：作为响应体的 `knot_extend` 字段；
- SSE 响应：作为独立 `data:` 事件，插在上游 `data: [DONE]` 之前（无 `[DONE]` 时插在流末尾）。

> ⚠ 命名风格：`usage` 走 snake_case（`total_tokens` / `cache_read`），
> `billing` 沿用驼峰（`totalTokens` / `totalCost`）。这是既有契约，改动会打断调用方，暂不动。

## 3. `ModelUsage` 结构

```java
ModelUsage(Input input, Output output, long totalTokens)

// 输入侧
Input(InputTokens tokens, long imageCount, long audioSeconds, long videoSeconds)
InputTokens(long text, long image, long video,
            long cacheRead, long cacheWrite,
            long cacheWrite5m, long cacheWrite1h, long unclassified)

// 输出侧
Output(OutputTokens tokens, long imageCount, long audioSeconds, long videoSeconds)
OutputTokens(long text, long image, long video, long reasoning, long unclassified)
```

### 语义约束（由构造器/归一化器强制）

1. **0 值省略**：所有字段 `@JsonInclude(NON_DEFAULT)`，与 Go `omitempty` 一致。
2. **cache_write 互斥**：`cache_write_5m` / `cache_write_1h` 任一 > 0 时 `cache_write` 强制归 0（明细优先）。
3. **text 推算**：上游未显式给 `text` 时，`text = 该侧总量 − 其余明细`（下限 0）。
4. **unclassified 兜底**：明细配不平该侧总量的残留（厂商私有新维度）。
5. **total_tokens**：优先沿用上游上报值；未上报时 = 两侧明细之和。

## 4. 字段别名映射（`ModelUsageNormalizer`）

| 统一字段 | 上游别名（按序取第一个 > 0） |
|---|---|
| input 总量 | `input_tokens` / `prompt_tokens` |
| output 总量 | `output_tokens` / `completion_tokens` |
| cache_read | `cache_read_input_tokens` / `cache_read_tokens` / `cached_read_tokens` / `prompt_cache_hit_tokens` / `*_details.cached_tokens` |
| cache_write | `cache_creation_input_tokens` / `cache_write_input_tokens` / `cache_write_tokens` / `cached_write_tokens` / `*_details.cache_creation_input_tokens` |
| cache_write_5m | `cache_write_5m` / `cache_creation_5m_input_tokens` / `cache_creation.ephemeral_5m_input_tokens` / `cache_creation.cache_creation_input_tokens_5m` |
| cache_write_1h | 同上 `_1h` 系列 |
| reasoning | `reasoning_tokens` / `thinking_tokens` / `output_tokens_details.reasoning_tokens` / `completion_tokens_details.reasoning_tokens` |
| output image_count | `output_image_count` / 响应体 `data` 数组长度（兜底） |
| output video_seconds | `output_video_seconds` / `completion_video_seconds` / `duration_seconds` |

## 5. 计费规则解析：三级回退

```
模型显式绑定 kb_models.billing_rule_code
   ├─ 命中 → 用该规则的当前生效版本
   └─ 未绑定 / 未命中
        └─ kb_logical_models.model_family（经统一模型派生）
             ├─ 族精确匹配 kb_billing_rules.model_family = 族
             └─ 回退 model_family IS NULL 的默认规则（覆盖所有族）
        └─ 仍无 → billing 省略
```

**为什么必须有回退链**：`kb_models.billing_rule_code` 允许为空，而 `model_family` 为空的规则
在 schema 里就定义为「默认规则，覆盖所有族」。只认显式绑定会让未绑模型的计费整体消失。

**缓存**（`GatewayDataService`）：

| 结果 | TTL | 理由 |
|---|---|---|
| 命中规则 | 10 分钟 | 与其余缓存一致 |
| 未命中 | **30 秒** | 管理员刚绑定规则后，若把「查不到」也缓存 10 分钟，计费会整整 10 分钟不出结果（已实测复现） |

> ⚠ 已知相邻问题（本次未改）：`ModelEntity` 仍是 10 分钟缓存，
> 改模型的 `billing_rule_code` / `base_url` 后最长 10 分钟才生效（实测）。
> 运维侧如需立即生效，重启网关。

## 6. TOKEN 缓存写：standard 与 ttl 两种口径

`config_json.cacheWriteMode` 决定明细怎么切：

| cacheWriteMode | 明细项 | 触发条件 |
|---|---|---|
| `standard`（默认） | `uncachedInput` / `cachedRead` / `cachedWrite` / `output` | 上游给单字段缓存写 |
| `ttl` | `uncachedInput` / `cachedRead` / **`cachedWrite5m`** / **`cachedWrite1h`** / `output` | 上游 5m / 1h 明细任一 > 0 |

- 单价取自 `basePrices.cacheWrite5m` / `cacheWrite1h`（阶梯与高低峰同样生效），
  缺配置时按解析链回退 `defaultUnitPrice` → `input` 价 → 0。
- **`price` 字段恒为「每百万 token 单价」**（`unitPrice × 1e6 / unitSize`），与 `unit` 无关，
  便于调用方横向比价。
- 总价 = 全部明细 `cost` 之和。

> 修复前：`PriceSet` 定义了 `cacheWrite5m` / `cacheWrite1h`、`cacheWriteMode` 也进了校验，
> 但 `PriceKind` 只有 4 个种类，两个 TTL 单价**从未参与计算**——配了也不进总价。

## 7. 流式处理

`ProxyStreamingBody` 逐事件提取用量，取 token 总量最大的一份（与整包 `mergeMax` 同语义），
然后在以下**两个时机之一**写出 `knot_extend` 事件，且只写一次：

1. 遇到上游 `data: [DONE]` → 写在其之前（与整包缓冲模式位置一致）；
2. 上游发完但没有 `[DONE]`（Anthropic 以 `event: message_stop` 收尾）→ 在流结束处兜底写出。

逐事件挑选用的 `UsageAccounting.totalTokens()`：优先取计费结果，
无计费结果时退回内部计费输入的 token 总量——否则无规则场景下全部退化成 0，只能靠「取最后一个」碰运气。

## 8. 数据流

```
上游响应(整包/SSE)
  └─ UsageRawReader.readBody()        → rawBody（SSE 取信息量最大的事件）
       ├─ readUsage()                 → rawUsage（仅作归一化输入，不再对外透传）
       ├─ UsageExtractor.extractUsageBody() → BillingUsage（内部计费输入）
       │    └─ UsageNormalizationSupport → NormalizedUsage（billing 视图）
       └─ ModelUsageNormalizer.fromSource(rawUsage, rawBody, billing) → usage 视图
打包：UsageAccounting(rawUsage, rawBody, billing, normalized)
      → ProxyResult.usage
      → GatewayRequestHandler（整包）/ ProxyStreamingBody（流式）
      → 注入 knot_extend
```

## 9. 验证

| 门禁 | 命令 | 结果 |
|---|---|---|
| 编译打包 | `mvn -f knot-server/pom.xml package -DskipTests` | **BUILD SUCCESS，EXIT=0** |
| 单测 | `mvn -f knot-server/pom.xml -pl knot-common,knot-plugin,knot-dal,knot-adapter,knot-service,knot-admin test` | **BUILD SUCCESS，EXIT=0**（含 `ModelUsageNormalizerTest` 11 用例） |
| 前端 | 未改动（`knot-front` 无任何 `model_usage` / `knot_extend` 引用） | 无需跑门禁 |

> knot-gateway 模块另有 5 个用例失败（`GatewayTrafficGuardTest` ×4 / `CaffeineTrafficCounterStoreTest` ×1），
> 属并行会话正在做的频控 Redis 化改造 + JDK 25 下 Mockito inline mock 环境限制，与本次改动无关。

### 真实冒烟（jar 启动 + curl，`smoke-usage-billing.log`）

mock 上游 `usage_upstream.js`（19997）可切换 OpenAI / Anthropic-ttl 两种 usage 与「是否发 `[DONE]`」；
每个场景独立重启网关（`GatewayDataService` 对 `ModelEntity` 有 10 分钟缓存，不重启会串场景）。

| 场景 | 输入 | 结果 |
|---|---|---|
| A 未绑定计费规则 | OpenAI 格式 usage | `billing` 出现（回退到默认规则）→ **修复前整个 billing 消失** |
| B 绑定 `TOKEN_CLAUDE_S4` | 同上 | 单价 `3.0 / 0.3 / 3.75 / 15.0`，`totalCost=0.00609` → 显式绑定优先于默认规则 |
| C ttl 缓存写 | Anthropic 格式：`input_tokens=700` + `cache_read=300` + `cache_creation{5m:400,1h:100}` | `usage.total_tokens=1700` **=** `billing.totalTokens=1700`；明细出 `cachedWrite5m`/`cachedWrite1h`，`totalCost=0.00579` |
| D 流式无 `[DONE]` | SSE 不发 DONE | `knot_extend` 事件在流末尾注入 → **修复前永不注入** |

四场景均为整包 + 流式各打一次，两侧 `knot_extend` 内容一致。

## 10. Follow-up

- [ ] `MediaUsageExtractor`（音视频时长独立提取器）未拆分，当前秒数依赖 body 兜底。
- [ ] `ModelEntity` 10 分钟缓存：改模型配置后生效延迟，需评估是否引入失效广播。
- [ ] 管理端计费预览（`BillingService.preview`）尚未接 ttl 5m/1h 价，与网关链路口径可能漂移。
- [ ] `billing` 的驼峰字段与 `usage` 的 snake_case 风格不统一，下个破坏性版本再对齐。
- [ ] 用量流水表（kx_model_usage）暂缓；`ModelUsage` 结构已为落库预留。

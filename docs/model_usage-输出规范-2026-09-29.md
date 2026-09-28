# model_usage 输出规范（2026-09-29）

> 参考 Go `UnifiedUsage` 结构定义，knot 网关对调用方输出的统一用量契约。
> 配套实现：`knot-adapter` 的 `model/usage/*` 与 `usage/ModelUsageNormalizer`。

## 1. 结论速览

| 项 | 决策 |
|---|---|
| 结构 | `ModelUsage`（input / output / total_tokens，严格对齐 Go `UnifiedUsage`） |
| 分层 | **并存**：`BillingUsage`（内部计费输入，扁平）不动；`ModelUsage` 只做对外归一输出 |
| 落库 | 暂不落库，无用量流水表 |
| 对外字段名 | **`model_usage`**（取代历史 `knot_usage`，一次做干净不留兼容） |
| 输出内容 | `{usage, raw, billing}` 三视图，**归一化 usage 与上游原始 usage 同时输出** |
| 生效条件 | 路由消费者 `return_usage_detail = true` |

## 2. 三视图定义（`ModelUsagePayload`）

```json
{
  "model_usage": {
    "usage":   { "input": {...}, "output": {...}, "total_tokens": 1200 },
    "raw":     { "prompt_tokens": 1000, "prompt_tokens_details": {...} },
    "billing": { "totalTokens": 1200, "totalCost": 0.0042, "currency": "CNY",
                 "billingVersion": "...", "detail": [...] }
  }
}
```

| 视图 | 类型 | 用途 |
|---|---|---|
| `usage` | `ModelUsage` | 归一化统一用量，跨厂商字段一致，**业务侧以此为准** |
| `raw` | `Map` | 上游原始 `usage` 对象逐字透传，排障与对账用；上游未上报时省略 |
| `billing` | `NormalizedUsage` | 网关按计费规则算出的金额与明细（原 `knot_usage` 内容） |

注入位置：
- 整包响应：作为响应体的 `model_usage` 字段；
- SSE 响应：作为独立 `data:` 事件，插在上游 `data: [DONE]` 之前。

## 3. `ModelUsage` 结构

```java
ModelUsage(ModelUsageInput input, ModelUsageOutput output, long totalTokens)

// 输入侧
ModelUsageInput(ModelUsageInputTokens tokens,
                long imageCount,        // image_count
                long audioSeconds,      // audio_seconds
                long videoSeconds)      // video_seconds

ModelUsageInputTokens(long text, long image, long video,
                      long cacheRead,                    // cache_read
                      long cacheWrite,                   // cache_write，与 5m/1h 互斥
                      long cacheWrite5m,                 // cache_write_5m
                      long cacheWrite1h,                 // cache_write_1h
                      long unclassified)                 // unclassified

// 输出侧
ModelUsageOutput(ModelUsageOutputTokens tokens,
                 long imageCount, long audioSeconds, long videoSeconds)

ModelUsageOutputTokens(long text, long image, long video,
                       long reasoning,                   // reasoning，思考 token
                       long unclassified)
```

### 语义约束（由构造器/归一化器强制）

1. **0 值省略**：所有字段 `@JsonInclude(NON_DEFAULT)`，JSON 输出与 Go `omitempty` 一致。
2. **cache_write 互斥**：`cache_write_5m` / `cache_write_1h` 任一 > 0 时，`cache_write` 总量强制归 0（明细优先）。
3. **text 推算**：上游未显式给 `text` 时，`text = 该侧 token 总量 − 其余明细`（下限 0）。
4. **unclassified 兜底**：明细配不平该侧总量的残留，通常是厂商私有新维度。
   恒等式：`text + image + video + cache_read + cache_write(或 5m+1h) + unclassified = 该侧 token 总量`。
5. **total_tokens**：优先沿用上游上报值；未上报时 = 两侧明细之和。

## 4. 字段别名映射（ModelUsageNormalizer）

| 统一字段 | 上游别名（按序取第一个 > 0） |
|---|---|
| input 总量 | `input_tokens` / `prompt_tokens` |
| output 总量 | `output_tokens` / `completion_tokens` |
| cache_read | `cache_read_input_tokens` / `cache_read_tokens` / `cached_read_tokens` / `prompt_cache_hit_tokens` / `prompt_tokens_details.cached_tokens` / `input_tokens_details.cached_tokens` |
| cache_write | `cache_creation_input_tokens` / `cache_write_input_tokens` / `cache_write_tokens` / `cached_write_tokens` / `*_details.cache_creation_input_tokens` |
| cache_write_5m | `cache_write_5m` / `cache_creation_5m_input_tokens` / `cache_creation.ephemeral_5m_input_tokens` / `cache_creation.cache_creation_input_tokens_5m` |
| cache_write_1h | 同上 `_1h` 系列 |
| reasoning | `reasoning_tokens` / `thinking_tokens` / `output_tokens_details.reasoning_tokens` / `completion_tokens_details.reasoning_tokens` |
| output image_count | `output_image_count` / 响应体 `data` 数组长度（兜底） |
| output video_seconds | `output_video_seconds` / `completion_video_seconds` / `duration_seconds` |

> ⚠ 历史坑：`BillingUsage.amount` 曾把 `image_count / images / n / duration_seconds / audio_seconds / video_seconds`
> 全部塞进同一个字段（`BillingUsage.java:55`），靠计费模式猜语义。`ModelUsage` 落地后，
> **新代码禁止再向 `amount` 塞多模态值**，多模态维度一律走 `ModelUsage` 对应字段。

## 5. 数据流

```
上游响应(整包/SSE)
  └─ UsageRawReader.readBody()        → rawBody（SSE 取 token 最大的事件）
       ├─ readUsage()                 → rawUsage（model_usage.raw 透传）
       ├─ UsageExtractor.extractUsageBody() → BillingUsage（内部计费输入，不动）
       │    └─ UsageNormalizationSupport → NormalizedUsage（计费结果，model_usage.billing）
       └─ ModelUsageNormalizer.fromSource(rawUsage, rawBody, billing)
                                          → ModelUsage（model_usage.usage）
打包：UsageAccounting(rawUsage, rawBody, billing, normalized)
      → ProxyResult.usage
      → GatewayRequestHandler（整包）/ ProxyStreamingBody（流式）
      → 注入 model_usage
```

## 6. 验证

| 门禁 | 命令 | 结果 |
|---|---|---|
| 编译 | `mvn -pl knot-adapter,knot-gateway -am install -DskipTests` | **BUILD SUCCESS，EXIT=0**（`build-modelusage-compile3.log`） |
| 单测 | `ModelUsageNormalizerTest`（10 用例） | **10/10 全绿，EXIT=0**（JUnit Platform Launcher 直跑，runner 退出码 0） |
| JSON 契约 | 实测输出 | `{"input":{"tokens":{"text":100}},"output":{"tokens":{"text":50}},"total_tokens":150}` —— snake_case 对齐 Go，0 值省略 |

> ⚠ 环境注记：本机 surefire 3.2.5 fork VM 在 JDK 25 + Windows 下启动即失败
> （`The forked VM terminated without properly saying goodbye`，`forkCount=0` 亦不生效），
> 属环境级问题而非代码问题（此前项目一直以 `mvn package` 跳过 test）。单测改用
> JUnit Platform Launcher（junit-platform-launcher 1.10.5）直跑验证，已复核 exit code。

## 7. Follow-up

- [ ] `MediaUsageExtractor`（音频/视频时长独立提取器）尚未拆分，当前音视频秒数依赖 body 兜底，不够准。
- [ ] `kb_routing_consumers.return_usage_detail` 语义已从「返回计费结果」升级为「返回三视图」，前端文档若描述过 `knot_usage` 需同步。
- [ ] 用量流水表（kx_model_usage）暂缓；ModelUsage 结构已为落库预留（热字段拆列 + raw JSON 列）。
- [ ] `BillingUsage.amount` 滥用清理（等 ModelUsage 稳定后从 `from()` 中移除多模态别名，改为纯金额语义）。

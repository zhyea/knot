# knot-gateway 输出消息协议（Output Message Protocol）

> 2026-10-17 · 范围：网关对调用方的**全部输出形态**——成功响应、流式响应、错误响应、响应头。
> 依据：knot-server/knot-gateway 源码（controller / runtime / upstream / model）+ knot-adapter 用量模型 + knot-common 契约。
> 姊妹篇：《knot-gateway 请求/响应数据数仓落地设计》（`docs/gateway-warehouse-data-model-2026-10-17.md`）管数仓侧，本文管**对调用方的 wire 协议**。

## 1. 总则

网关输出分三种出口，全部由 `AbstractGatewayRequestTemplate.handleMap` 单模板驱动：

| 出口 | 形态 | 驱动代码 |
|---|---|---|
| 成功（非流式） | 上游响应体**原样透传** + 条件注入 `knot_extend` | `runtime/AbstractGatewayRequestTemplate.java:91-99` |
| 成功（流式） | `StreamingResponseBody` 逐行/逐字节转发 + SSE 注入 `knot_extend` 事件 | `runtime/GatewayRequestHandler.java:397-406` |
| 失败 | 统一错误信封 `{"error":{message,type,code,detail?}}` | `controller/GatewayExceptionHandler.java` |

三条不变量：

1. **上游响应体永不改写**（除追加 `knot_extend` 外）。`adapter.handleResponse` 只做协议适配，不回写业务字段。
2. **请求体的 `model` 会被路由目标覆盖**为 `kb_models.upstream_model`（`GatewayRequestHandler.java:272-275`）；调用方只能从响应头 `X-Knot-Model` 得知实际承接模型。
3. **异常路径无成功语义头**：`X-Knot-Model` / `X-Knot-First-Token-Time` 不设置，头缺失即无成功上游调用，**不是空值**。

## 2. 入口路由清单

| 路径 | 控制器 | 协议 code | 执行器 | 流式 |
|---|---|---|---|---|
| `POST /openai/v1/chat/completions` | GatewayTextController | CHAT_COMPLETIONS | TextGeneration | ✅ |
| `POST /openai/v1/responses` | GatewayTextController | RESPONSES | TextGeneration | ✅ |
| `POST /openai/v1/completions` | GatewayTextController | COMPLETIONS | TextGeneration | ✅ |
| `POST /anthropic/v1/messages` | GatewayAnthropicController | MESSAGES | TextGeneration | ✅ |
| `POST /openai/v1/embeddings` | GatewayEmbeddingController | EMBEDDINGS | Embedding | ❌ |
| `POST /openai/v1/images/generations` | GatewayImageController | IMAGE_GENERATIONS | Media | ❌ |
| `POST /openai/v1/images/edits`（multipart） | GatewayImageController | IMAGE_EDITS | Media | ❌ |
| `POST /openai/v1/images/variations` | GatewayImageController | IMAGE_VARIATIONS | Media | ❌ |
| `POST /openai/v1/audio/transcriptions` | GatewayAudioController | AUDIO_TRANSCRIPTIONS | Media | ❌ |
| `POST /openai/v1/audio/translations` | GatewayAudioController | AUDIO_TRANSLATIONS | Media | ❌ |
| `POST /openai/v1/audio/speech` | GatewayAudioController | AUDIO_SPEECH | Media | ✅（音频分片，非 SSE） |
| `POST /openai/v1/videos/generations` | GatewayVideoController | VIDEO_GENERATIONS | Media | ❌ |
| `POST /v1/rerank` | GatewayUtilityController | RERANK | Utility | ❌ |
| `POST /v1/openai/v1/moderations` | GatewayUtilityController | MODERATIONS | Utility | ❌ |
| `POST /v1/decisions` | GatewayDecisionsController | DECISIONS | Utility | ❌ |

流式判据是双闸：执行器 `supportsStreaming`（仅 TextGeneration 与 Media 的 AUDIO_SPEECH 打开）× 请求体 `stream=true`（`AbstractUpstreamProtocolExecutor.java:65,187-194`）。其余协议一律整包。

## 3. 响应头协议

| 头 | 设置时机 | 值 | 缺失语义 |
|---|---|---|---|
| `traceparent` | Filter 出口，**始终回写** | W3C `version-traceId-spanId-flags`（入参缺失则网关头生成） | — |
| `X-Knot-Model` | 候选调用成功后（流式在 commit 前写，仍生效） | 实际承接模型业务码 `kb_models.model_code` | 无成功上游调用 |
| `X-Knot-First-Token-Time` | 非流式：响应体就绪、返控制器前；流式：上游首行写出前 | TTFT 毫秒（端到端：鉴权+路由+插件+频控+重试/failover） | 无成功首 token |
| `Retry-After` | 429 拒绝 | 秒（窗口重置剩余，向上取整至少 1s） | — |
| `Content-Type` | 流式路径强制覆盖 | 上游 contentType；为空回落 `text/event-stream` | — |
| `Cache-Control: no-cache` + `X-Accel-Buffering: no` | 流式路径固定写 | 防 Nginx 缓冲 SSE | — |

代码位置：`constants/GatewayHeaders.java:20-27`；`runtime/GatewayTraceFilter.java:33`；`runtime/GatewayRequestHandler.java:257-267,446-460`；`runtime/FirstTokenHeaderWriter.java:71-82`；`controller/GatewayExceptionHandler.java:108-110`。

## 4. 成功响应协议

### 4.1 非流式 JSON（整包透传）

- 响应体 = 上游响应经 `adapter.handleResponse` 后的字符串，**原样返回**。
- `returnUsageDetail`（消费者级开关）为 true 时：解析成 JSON 对象 → 注入 `knot_extend` 字段 → 重新序列化。解析失败（非 JSON 对象，如音频二进制）→ **原样返回，不注入**。
- 特例：非流式但响应体内容含 `data:` 行（上游把 SSE 文本整包返回）→ 走 SSE 插入路径（`GatewayRequestHandler.java:378,470-486`）。

### 4.2 流式响应

| 上游形态 | 转发方式 | `knot_extend` 注入 |
|---|---|---|
| SSE（`text/event-stream`） | 逐行 readLine → write + flush | ✅ 独立 `data:` 事件，插在 `data: [DONE]` 之前；上游不发 [DONE]（Anthropic 以 `message_stop` 收尾）时兜底追加在流尾 |
| 非 SSE 二进制分片（audio/speech） | 原样字节透传（`pumpRaw`） | ❌ 二进制流不可插入事件 |

- SSE 路径逐事件累计用量，按 `totalTokens` **更大者胜出**（与整包 `mergeMax` 语义对齐，`ProxyStreamingBody.java:173-181`）。
- 首字节之前的失败（非 2xx / 连接失败）抛 `GatewayUpstreamException`，**failover 仍然有效**；一旦开始转发则无法切换目标（`AbstractUpstreamProtocolExecutor.java:107-124`）。
- 流式在途计数持续到流写完才释放（`onComplete`），客户端断连也走 finally 释放。

### 4.3 `knot_extend` 负载契约

注入位置：整包 = 响应体顶层字段；SSE = 独立事件的 `knot_extend` 键。结构 `KnotExtendPayload{usage, billing}`（`adapter/.../model/usage/KnotExtendPayload.java:24-25`）：

```json
{
  "knot_extend": {
    "usage": {
      "input":  { "tokens": { "text": 176, "image": 0, "video": 0, "cache_read": 1024,
                              "cache_write": 0, "cache_write_5m": 0, "cache_write_1h": 0, "unclassified": 0 },
                  "image_count": 0, "audio_seconds": 0, "video_seconds": 0 },
      "output": { "tokens": { "text": 380, "image": 0, "video": 0, "reasoning": 0, "unclassified": 0 },
                  "image_count": 0, "audio_seconds": 0, "video_seconds": 0 },
      "total_tokens": 1580
    },
    "billing": {
      "total_tokens": 1580,
      "total_cost": 0.00234,
      "currency": "CNY",
      "billing_version": "V-20260101",
      "detail": [
        { "type": "uncachedInput", "tokens": 176,  "cost": "0.00035000", "price": 2.0 },
        { "type": "cachedRead",    "tokens": 1024, "cost": "0.00050000", "price": 0.5 },
        { "type": "output",        "tokens": 380,  "cost": "0.00149000", "price": 4.0 }
      ]
    }
  },
  "usage": { "prompt_tokens": 1200, "completion_tokens": 380, "total_tokens": 1580 }
}
```

字段规则（`adapter/.../model/usage/ModelUsage.java`、`NormalizedUsage.java`、`NormalizedUsageDetail.java`）：

| 规则 | 说明 |
|---|---|
| `usage` 侧 0 值省略 | `NON_DEFAULT` 序列化，与 `omitempty` 同义；**下游判存在用字段有无，勿判 0** |
| `cache_write` 与 `cache_write_5m/1h` 互斥 | 明细任一 >0 时总量强制归 0（构造器强制） |
| 两侧恒等式 | `text+image+video+cache_read+cache_write(或5m+1h)+unclassified = 该侧总量`；`unclassified` 是厂商私有新维度残留 |
| `billing` 可缺 | 无计费规则可命中时整个 `billing` 省略——调用方可区分「免费」与「未配置计费」 |
| `detail[].cost` 是 **String** | 十进制普通字符串、不设小数位上限，绝不出科学计数法（`plainMoney`）；`price` 同为字符串（折算百万单价） |
| `detail[].type` 取值 | token 模式：`uncachedInput`/`cachedRead`/`cachedWrite`/`cachedWrite5m`/`cachedWrite1h`/`output`；简单模式：计费模式 code（TOKEN/REQUEST/IMAGE/AUDIO/VIDEO/EMBEDDING/FREE/CUSTOM）；视频按分辨率出明细 |
| 并列 `usage` 字段 | 上游原始 usage 原文透传（与 knot_extend 同级的 `usage` 键），仅供对照排障；上游未上报时省略 |
| SSE 注入形态 | `data: {"knot_extend":{...},"usage":{上游原文}}`，不改动上游原有 chunk |

## 5. 错误响应协议

统一信封 `GatewayErrorResponse{error:{message,type,code,detail?}}`（`model/GatewayErrorResponse.java:5-31`），`detail` 仅 429 携带：

| error.type | HTTP | 触发 | error.code | detail |
|---|---|---|---|---|
| `auth_error` | 401 | Authorization 无法提取 API key | — | — |
| `invalid_request_error` | 400 | Rule 头缺失 / 请求体不可解析 / 参数校验失败 / 管理面 BusinessException | 可选业务码 | — |
| `moderation_error` | 400 | 敏感词命中（插件抛 `ModerationRejectException`） | **分类 code**（唯一对外命中信息，不暴露词与词库） | — |
| `rate_limit_error` | 429 | RPM / TPM 超限 | `RATE_LIMIT_EXCEEDED` | TrafficRejectDetail |
| `quota_error` | 429 | token / 成本限额超限 | `QUOTA_EXCEEDED` | TrafficRejectDetail |
| `upstream_error` | 502 | 上游异常终态 | `UPSTREAM_ERROR` 等 8 值 | — |
| `storage_unavailable` | 503 | Redis 不可用（fail-hard 档） | — | — |
| （兜底）`upstream_error` | 500 | 未预期异常 | — | — |

`TrafficRejectDetail`（429 响应体 `error.detail`，`model/TrafficRejectDetail.java:24-32`）：

```json
{ "resource_type": "MODEL", "resource_code": "gpt-4o-mini", "reason": "RATE_LIMIT_TPM",
  "dimension": "TPM", "unit": "tokens/minute", "limit": 100000, "used": 100432,
  "reset_at": "2026-10-17T07:13:00Z", "reset_after_seconds": 27 }
```

- `reason` ∈ `RATE_LIMIT_RPM` / `RATE_LIMIT_TPM` / `QUOTA_TOKENS` / `QUOTA_COST`；成本维度 `unit` 取策略币种，数值已从 1e-12 缩放计数还原成金额。
- `resource_type` ∈ APP / MODEL / PROVIDER / ROUTING_RULE / ROUTING_CONSUMER；`resource_code` 为业务码（跨环境稳定，非主键 id）。
- `Retry-After` 头与 `reset_after_seconds` 同源。

## 6. 协议特有说明

1. **Decisions**：出入参都透传上游。出参是「问题名 → 答案」映射（noul/choice/score/box 四类），网关只做转发与用量提取，不消费问题语义（`vo/request/DecisionsRequest.java:9-20`）。无流式。
2. **媒体类**（图像/音频/视频）：上游常不上报 usage，网关从响应体推断张数/秒数（`data` 数组长度、`duration_seconds` 等）；token 维度为 0 时勿判「无用量」。
3. **上游非 2xx 的两条转换路径**：整包路径捕 `RestClientResponseException`（带状态码+响应体+Retry-After）；流式路径在 `toStreamResponse` 手工检查状态码。两者都产出 `GatewayUpstreamException`，重试/摘除口径一致。
4. **`model` 覆盖无响应体现**：请求体 model 被替换为 `upstream_model`，调用方应以 `X-Knot-Model` 头为准。

## 7. 已知缺口与 follow-up

| # | 缺口 | 现状 | 建议 |
|---|---|---|---|
| 1 | 异常不带阶段信息 | 错误信封无 `error.stage`，调用方无法区分「被网关拒」与「上游挂」的细分阶段 | 错误响应加 `stage` 字段（header_validate/routing_resolve/traffic_check/upstream_call） |
| 2 | MASK 动作未实现 | 敏感词 MASK 回落 FLAG，仅记事件不改写（`ModerationGuard.java:109-112`） | M2 与出站审核一起做 |
| 3 | audio/speech 流式无 `knot_extend` | 二进制分片不可插入事件，`returnUsageDetail` 开也拿不到用量 | 如需用量，改非流式或接受响应头旁路 |
| 4 | 非 JSON 整包响应不注入 | 音频/二进制原样返回 | 同 3 |
| 5 | 错误 message 为上游原文 | 502 的 message 来自上游异常文本，可能含内部信息 | 评估脱敏策略 |

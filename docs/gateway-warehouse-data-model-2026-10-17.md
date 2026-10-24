# knot-gateway 请求/响应数据数仓落地设计

> ⚠ **已被取代**：robin 2026-10-17 拍板「打平成请求/响应两层、msg 走 Kafka」，新方案见 `docs/gateway-warehouse-kafka-message-protocol-2026-10-17.md`。本文仅作设计演进留档，**不要再按本文的三事实表方案实施**；§7 口径清单已并入新文档。
>
> 2026-10-17 · 范围：knot-server/knot-gateway（含 knot-adapter 的用量/计费模型、knot-common 的枚举契约）
> 目的：把网关请求与响应全链路数据整理成可落数仓的多维统计模型。本文只做**数据契约整理与模型设计**，不改网关代码。

## 1. 结论

1. 网关链路已产生数仓所需的全部事实数据，分布在 **6 个阶段**（见 §2）；当前 schema **无任何请求级落库表**（`ks_operation_logs` 只管管理面操作），调用数据以四种形态存在：响应头、`knot_extend` 响应体注入、应用日志、Redis 计数器。落数仓需**新增事件出口**。
2. 事件按**两级粒度**设计：`request`（一次客户端请求，一行）+ `attempt`（一次上游调用，一次请求可产生 N 行，含 failover 与重试）。**上游异常归 attempt 级，网关异常归 request 级**——一次请求可能顺延多个候选、每个候选可能重投多次，只有 attempt 粒度能算清单个上游目标的失败率。
3. 核心表 3 张：`dwd_gateway_request_log`（请求事实）/ `dwd_gateway_attempt_log`（上游调用事实）/ `dwd_gateway_usage_detail`（计费明细事实）；维表 9 张（§3.5）。多维统计的切片能力完全由 `ResolvedRouting` + `GatewayRoutingInfo` + `RoutingRuleTargetDto` 三个结构支撑（应用/部门/用户/消费者/规则/模型/供应商/协议/池，全部为 **code 业务码**，符合项目「存 code 不存 id」红线）。
4. 最大口径风险：响应体里的 `knot_extend` 受消费者开关 `returnUsageDetail` 控制（`GatewayRequestHandler.java:367`），**关时响应无用量**。数仓取数必须走网关内部账目 `UsageAccounting`，不能解析响应体。

## 2. 链路数据全景（阶段 × 数据 × 代码位置）

| # | 阶段 | 产生的数据 | 代码位置 |
|---|---|---|---|
| 1 | 入口 Filter | `traceId` / `spanId` / `traceparent`（W3C，入参缺失则网关头生成）；MDC 注入；响应头回写 `traceparent` | `runtime/GatewayTraceFilter.java:28-33` |
| 2 | 请求校验 | `apiKey`（Authorization: Bearer 提取）、`ruleCode`（Rule 头）；失败即 `auth_error` / `invalid_request_error` 400/401 | `runtime/AbstractGatewayRequestTemplate.java:131-146` |
| 3 | 路由解析 | `ResolvedRouting`：`ruleId`/`ruleCode`/`consumerId`/`secretKey`/`returnUsageDetail`/`candidateModels`/`retryPolicy`/`ejectionPolicy`；`GatewayRoutingInfo`：rule(code/name/appScenario/username)、consumer(code/name)、app(appId/name/deptCode)、consumerUser(username/realName)、appOwner、department(deptCode/name) | `runtime/GatewayRequestHandler.java:109-111`；`model/ResolvedRouting.java:7-17`；`model/GatewayRoutingInfo.java:3-40` |
| 4 | 流量守卫 | 检查资源：APP / MODEL / PROVIDER / ROUTING_RULE / ROUTING_CONSUMER；拒绝原因 RPM / TPM / TOKENS / COST；`TrafficDecision`：resourceType/resourceCode/limit/used/resetAtMillis/currency | `traffic/GatewayTrafficGuard.java:59-70`；`traffic/TrafficDecision.java:12-19`；`traffic/TrafficRejectReason.java:13-24`；`constants/enums/TrafficResourceTypeEnum.java:14-18` |
| 5 | 候选调用（循环） | 每候选：摘除判定 skip/probing、目标频控判定、`enter/leave` 在途计数、重试（maxAttempts+退避）、`ProxyResult`（providerId/modelId/responseBody/usage/streamResponse）、`outageTracker.recordSuccess/Failure`、`X-Knot-Model` 响应头 | `runtime/GatewayRequestHandler.java:163-233`、`:289-309`、`:257-267` |
| 6 | 用量与计费 | `UsageAccounting`（rawUsage 上游原文 / rawBody / billing 计费输入 / normalized 计费结果）；`NormalizedUsage`（totalTokens/totalCost/currency/billingVersion/detail[]）；`knot_extend` 注入响应（仅 returnUsageDetail 开）；TTFT 写头 | `runtime/GatewayRequestHandler.java:355-388`；`adapter/.../model/UsageAccounting.java:21-24`；`adapter/.../model/NormalizedUsage.java:6-10` |

### 2.1 请求侧输入（数仓可作维度的原始字段）

| 数据 | 来源 | 备注 |
|---|---|---|
| `protocol` | URL 路由到控制器时定（15 种 `ModelApiProtocolEnum`） | 见 §3.5 dim_protocol |
| `stickyKey` | 请求头 `X-Knot-Sticky-Key`，可空 | `GatewayRequestContext.java:26-27` |
| `requestBody` | `Map<String,Object>`（model/stream/messages…） | 原始体**不入库**（体量 + 敏感），只存派生指标如 `stream` 布尔、`messageCount` |
| `contentType` | multipart（音频/图像类）或 JSON | `GatewayExchange.java:17-34` |

### 2.2 响应侧输出（成功路径）

| 数据 | 形态 | 代码位置 |
|---|---|---|
| `traceparent` | 响应头 | `GatewayTraceFilter.java:33` |
| `X-Knot-Model` | 响应头，实际承接模型 code（kb_models.model_code） | `GatewayRequestHandler.java:257-267` |
| `X-Knot-First-Token-Time` | 响应头，TTFT 毫秒（端到端，含鉴权/路由/插件/频控/重试） | `runtime/FirstTokenHeaderWriter.java:71-82` |
| `knot_extend.usage` | 响应体字段（`ModelUsage`：input/output 两侧 token 明细 + 图片张数/音视频秒数） | `adapter/.../model/usage/ModelUsage.java:30-32` |
| `knot_extend.billing` | 响应体字段（`NormalizedUsage`，无计费规则时省略） | `adapter/.../model/NormalizedUsage.java:6-10` |
| `upstreamUsage` | 与 knot_extend 并列的 `usage` 字段，上游原文透传 | `constants/AiPayloadFields.java`（RAW_USAGE） |
| SSE 场景 | `knot_extend` 作为独立 `data:` 事件插在 `[DONE]` 前；无 [DONE] 时追加流尾 | `runtime/GatewayRequestHandler.java:470-486`；`upstream/stream/ProxyStreamingBody.java:83-111` |

## 3. 数仓模型

### 3.1 分层

```
ODS  网关事件原文（JSON，Kafka/日志任选通道）
DWD  request_log / attempt_log / usage_detail 三事实表 + 缓慢变化维
DWS  日粒度汇总（按 app×model×provider 等常用组合预聚合）
ADS  看板：成本 TopN、成功率、P95 TTFT、限流命中率、缓存命中率、峰谷分布
```

### 3.2 `dwd_gateway_request_log`（一次客户端请求一行）

| 分组 | 列 | 类型 | 来源 |
|---|---|---|---|
| 键 | `trace_id` / `span_id` | string | GatewayTraceContext |
| 键 | `request_id` | string | 网关入口生成的 32 位小写 hex（2026-10-23 落地）：已以 `X-Request-Id` 透传给上游，是向供应商提工单的「我方编号」。⚠ 与 attempt 表的 `upstream_request_id`（对方编号）是两列，不可互相替代 |
| 时间 | `received_at` | datetime(3) | `GatewayRequestContext.receivedAtMillis`（本地时钟，见 §7） |
| 时间 | `finished_at` | datetime(3) | 事件产出时刻 |
| 度量 | `duration_ms` | bigint | finished - received |
| 度量 | `ttft_ms` | bigint **可空** | 响应头；**异常路径为 NULL**（无成功首 token） |
| 请求维度 | `rule_code` / `rule_name` / `app_scenario` | string | GatewayRoutingInfo.RuleInfo |
| 请求维度 | `consumer_code` / `consumer_name` | string | ConsumerInfo |
| 请求维度 | `app_code` / `app_name` / `dept_code` / `dept_name` | string | AppInfo / DepartmentInfo |
| 请求维度 | `consumer_username` / `app_owner_username` | string | UserInfo（**username 绑定，非 id**） |
| 模型维度 | `requested_protocol` | string | ModelApiProtocolEnum（15 值） |
| 模型维度 | `pool_or_model`（首个候选 targetType） | string | RouteTargetTypeEnum：MODEL / MODEL_POOL |
| 模型维度 | `candidate_count` | int | routing.candidateModels.size() |
| 模型维度 | `hit_model_code` / `hit_provider_code` | string **可空** | `X-Knot-Model` 头 + ProxyResult.providerId 反查；无成功上游调用为 NULL |
| 结果 | `status` | string | success / rate_limited / quota_exceeded / upstream_error / timeout / gateway_error |
| 结果 | `http_status` | int | 200/400/401/429/502/503/500 |
| 结果 | `error_type` | string **可空** | GatewayErrorTypeEnum（7 值，见 §4.2） |
| 结果 | `error_code` | string **可空** | ProxyErrorCodeEnum（8 值） |
| 结果 | `error_message` | string **可空** | 异常 message（**脱敏后**，见 §7） |
| 拒绝明细 | `reject_resource_type` / `reject_resource_code` / `reject_dimension` / `reject_limit` / `reject_used` / `reject_unit` / `reject_currency` / `reset_at` | string/bigint | TrafficRejectDetail（429 专有，见 §4.2） |
| 重试/摘除 | `retry_total` / `failover_total` / `ejected_skipped` / `fail_open` | int/bool | 汇总 attempt 级计数 |
| 用量 | `total_tokens` / `total_cost` / `currency` / `billing_version` | bigint/decimal/string | NormalizedUsage（**无计费规则时 cost 为 NULL**） |
| 用量 | `input_tokens` / `output_tokens` / `cached_read_tokens` / `cached_write_tokens` / `image_count` / `audio_seconds` / `video_seconds` | bigint | ModelUsage 归一化（内部账目，不受 returnUsageDetail 影响） |
| 开关 | `return_usage_detail` | bool | consumer 配置，解释响应有无 knot_extend |
| 节点 | `node_id` | string | 部署实例标识（多节点部署必需，见 §7） |

### 3.3 `dwd_gateway_attempt_log`（一次上游调用一行——上游异常主场）

| 列 | 类型 | 来源 |
|---|---|---|
| `trace_id` / `attempt_seq` | string/int | 事件序号（候选内重试与跨候选顺延统一编号） |
| `target_code` / `upstream_model_code` / `target_type` / `priority` / `is_primary` | string/int/bool | RoutingRuleTargetDto |
| `model_code` / `provider_code` | string | ProxyResult / ModelEntity（targetCode 即 kb_models.model_code） |
| `attempt_no` / `max_attempts` | int | invokeWithRetry（`GatewayRequestHandler.java:292-302`） |
| `skipped_by_ejection` / `probe_request` | bool | EjectionDecision（`OutageTracker.java:43-55`） |
| `traffic_rejected` / `reject_reason` | bool/string | 目标频控判定（RATE_LIMIT_RPM/TPM、QUOTA_TOKENS/COST） |
| `upstream_http_status` | int **可空** | GatewayUpstreamException.httpStatus() |
| `upstream_error_code` | string | ProxyErrorCodeEnum：UPSTREAM_ERROR / MODEL_NOT_FOUND / PROVIDER_NOT_FOUND / API_PROTOCOL_NOT_CONFIGURED / NO_ROUTING_TARGET … |
| `retry_after_ms` | bigint **可空** | 上游 Retry-After 换算 |
| `counted_for_ejection` | bool | OutageClassifier.countable（5xx/429 计；4xx 业务错与配置类错不计） |
| `outcome` | string | success / upstream_failure / skipped / traffic_rejected |
| `usage` 各列 | bigint/decimal | 成功时同 request 级口径 |

### 3.4 `dwd_gateway_usage_detail`（计费明细行，一次请求 N 行）

`NormalizedUsage.detail[]` 逐行落库，`type` 取值全集：

| type | 出现条件 | 含义 |
|---|---|---|
| `uncachedInput` | TOKEN 模式 | 未命中缓存的输入 token |
| `cachedRead` | TOKEN 模式 | 缓存读 token |
| `cachedWrite` | TOKEN 模式（单字段口径） | 缓存写 token 总量 |
| `cachedWrite5m` / `cachedWrite1h` | TOKEN 模式（ttl 分档口径，与上一行互斥） | 缓存写按 TTL 拆分 |
| `output` | TOKEN 模式 | 输出 token |
| `TOKEN`/`REQUEST`/`IMAGE`/`AUDIO`/`VIDEO`/`EMBEDDING`/`FREE`/`CUSTOM` | 简单模式（SimpleBillingModeCalculator） | 整单一种明细 |
| 分辨率串（如 720p） | 视频模式 | VideoBillingModeCalculator 按分辨率出明细 |

列：`trace_id` / `detail_type` / `tokens` / `cost`(decimal(20,8)，**入库时把 String 转 decimal**，禁科学计数法) / `price_per_million`(decimal(20,8)) / `billing_mode` / `unit` / `rule_code` / `rule_version` / `currency`。
来源：`NormalizedUsageDetail.java:14-17`、`TokenUsageDetailKind.java:13-22`、`BillingRuleEntity`（billingMode/unit/currency/versionCode/pricingPlan）。

### 3.5 维表

| 维表 | 主键 | 关键属性 | 来源表/枚举 |
|---|---|---|---|
| `dim_date` | date_key | 年月日小时、星期、是否周末、是否业务高峰 | 派生 |
| `dim_app` | app_code | app 名、部门 code、场景、owner username | kb_apps |
| `dim_dept` | dept_code | 部门名 | ks_departments |
| `dim_user` | username | realName | ks_users |
| `dim_consumer` | consumer_code | 名、returnUsageDetail | kb_routing_consumers |
| `dim_rule` | rule_code | 名、appScenario、username | kb_routing_rules |
| `dim_model` | model_code | 上游模型名、modelType(14 值)、modelFamily、logicalModelCode、providerAccountCode | kb_models / ModelTypeEnum |
| `dim_provider` | provider_code | 供应商名、账户 | kb_providers / kb_provider_accounts |
| `dim_protocol` | protocol_code | 路径、是否流式能力 | ModelApiProtocolEnum(15) |

> 维表一律按 **code 建关联**，与网关内部「跨表绑定存 code 不存 id」红线一致；id 只在维表内保留作参考列。

### 3.6 DWS/ADS 建议口径

- 成本汇总：`sum(total_cost)` 按 currency 分组（多币种不混加）；明细表可按 `detail_type` 拆缓存/非缓存成本占比。
- 成功率：`attempt_log` 按 `outcome` 聚合（区分上游失败 vs 网关拒绝）；`request_log` 按 `status` 聚合。
- 延迟：P50/P95/P99 `ttft_ms` + `duration_ms`（TTFT 空值不计入，勿填 0）。
- 缓存收益：`cached_read_tokens` / `total_tokens` 命中率趋势。
- 限流分析：`reject_dimension` × `reject_resource_type` 交叉，看谁在什么维度被限。

## 4. 异常数据专章（上游异常 + 网关异常）

### 4.1 上游异常（attempt 级）

**载体**：`GatewayUpstreamException`（`exception/GatewayUpstreamException.java:5-55`），字段 `httpStatus` / `responseBody` / `retryAfterMs` / `code`，`type` 恒为 `upstream_error`。

**业务码全集**（`constants/enums/ProxyErrorCodeEnum.java:4-11`）：`MISSING_MODEL` / `MODEL_NOT_FOUND` / `PROVIDER_NOT_FOUND` / `API_PROTOCOL_NOT_CONFIGURED` / `RATE_LIMIT_EXCEEDED` / `QUOTA_EXCEEDED` / `NO_ROUTING_TARGET` / `UPSTREAM_ERROR`。

**链路语义**（`GatewayRequestHandler.java:118-233`）：
- 同一候选内先重试（`invokeWithRetry`，默认 maxAttempts=2、只捕 `GatewayUpstreamException`，退避受 `respectRetryAfter` 控制取 Retry-After 与指数退避较大者），耗尽才抛给外层顺延下一个候选（failover）。
- 摘除过滤在频控检查**之前**：被摘除跳过的候选既不消耗频控预算，也不写入「首个拒绝原因」——**统计时 ejected_skipped 不是限流**。
- 候选被摘一空且策略 fail-open 时忽略摘除重跑一轮（`fail_open=true`），该轮产生的慢信号同样计数。
- 全失败终态：`lastUpstreamException`（502 upstream_error）或 `firstRejection`（429）。

**计入摘除判定的口径**（`outage/OutageClassifier.java:29-57`）：5xx 与上游 429 计；4xx 业务错不计（调用方的锅）；配置类错误（MODEL_NOT_FOUND/PROVIDER_NOT_FOUND/API_PROTOCOL_NOT_CONFIGURED）不计——「配置没配好」不能伪装成「上游挂了」。异常类型 `AnomalyKind`：FAILURE / SLOW（TTFT 超阈值）。数仓做目标健康度分析时**必须用 counted_for_ejection 过滤**，否则 4xx 会污染失败率。

### 4.2 网关异常（request 级）

`GatewayErrorTypeEnum` 8 值（`constants/enums/GatewayErrorTypeEnum.java:4-21`）与 HTTP 映射（`controller/GatewayExceptionHandler.java`）：

| error_type | HTTP | 触发 | 附加数据 |
|---|---|---|---|
| `auth_error` | 401 | API key 提取失败 | — |
| `invalid_request_error` | 400 | Rule 头缺失/请求体不可解析/参数校验失败/管理面 BusinessException | 可选业务 code |
| `rate_limit_error` | 429 | RPM/TPM 超限 | TrafficRejectDetail + Retry-After 头 |
| `quota_error` | 429 | token/成本限额超限 | TrafficRejectDetail + Retry-After 头 |
| `moderation_error` | 400 | 敏感词命中（GatewayBadRequestException，只暴露分类 code 不暴露词本身） | 分类 code |
| `storage_unavailable` | 503 | Redis 不可用（fail-hard 档） | `domain`/`operation`/`key`（ERROR 日志即报警通道） |
| `upstream_error` | 502 | 上游异常终态 | 同 §4.1 |
| `timeout_error` | 504 | 请求预算超时（B2 全局 deadline，`Mono.timeout` 抛 `TimeoutException`） | — |

**429 结构化明细**（`model/TrafficRejectDetail.java:24-32`，随响应体 `error.detail` 返回）：`resourceType` / `resourceCode` / `reason`（RATE_LIMIT_RPM/RATE_LIMIT_TPM/QUOTA_TOKENS/QUOTA_COST）/ `dimension` / `unit` / `limit` / `used` / `resetAt`(ISO UTC) / `resetAfterSeconds`。成本维度内部按 **1e-12 缩放计数**，输出时还原金额——数仓存还原后金额 + currency，勿存缩放计数。

**发生阶段标记**（request 级事件应有 `error_stage`）：header_validate / routing_resolve / routing_traffic_check / target_traffic_check / upstream_call / usage_accounting。由 §2 表可直接映射。

### 4.3 异常事件形态

- request 级：`error_type` + `error_code` + `error_stage` + `http_status` + `reject_*` 八列（429 专有） + `retry_total`/`failover_total`/`ejected_skipped`。
- attempt 级：每行 attempt 自带 `outcome` + `upstream_http_status` + `upstream_error_code` + `counted_for_ejection`——**上游失败率、按状态码分布、按目标分布全部走这张表**。
- 两者通过 `trace_id` 关联；attempt 无成功行时 request 级 `hit_model_code`/`ttft_ms` 为 NULL。

## 5. 事件 JSON 契约（建议）

```json
{
  "event": "gateway_request",
  "trace_id": "4bf92f3577b34da6a3ce929d0e0e4736",
  "span_id": "00f067aa0ba902b7",
  "received_at": "2026-10-17T07:12:33.412+08:00",
  "node_id": "gw-01",
  "protocol": "CHAT_COMPLETIONS",
  "stream": false,
  "sticky_key": null,
  "routing": {
    "rule_code": "R-xxx", "consumer_code": "C-xxx",
    "app_code": "APP-xxx", "dept_code": "D-xxx",
    "consumer_username": "alice", "app_owner_username": "bob",
    "app_scenario": "chat", "return_usage_detail": true,
    "candidate_count": 3, "target_type": "MODEL_POOL"
  },
  "result": {
    "status": "success", "http_status": 200,
    "hit_model_code": "gpt-4o-mini", "hit_provider_code": "PA-xxx",
    "ttft_ms": 812, "duration_ms": 5310,
    "retry_total": 1, "failover_total": 1, "ejected_skipped": 0, "fail_open": false
  },
  "usage": {
    "input_tokens": 1200, "output_tokens": 380, "total_tokens": 1580,
    "cached_read_tokens": 1024, "cached_write_tokens": 0,
    "total_cost": "0.00234000", "currency": "CNY",
    "billing_version": "V-20260101", "billing_mode": "TOKEN", "rule_code": "BR-xxx",
    "details": [
      {"type": "uncachedInput", "tokens": 176, "cost": "0.00035000", "price_per_million": "2.00000000"},
      {"type": "cachedRead", "tokens": 1024, "cost": "0.00050000", "price_per_million": "0.50000000"},
      {"type": "output", "tokens": 380, "cost": "0.00149000", "price_per_million": "4.00000000"}
    ]
  },
  "error": null
}
```

attempt 事件（同 trace_id，一条请求 N 条）：

```json
{
  "event": "gateway_attempt",
  "trace_id": "4bf92f3577b34da6a3ce929d0e0e4736",
  "attempt_seq": 2,
  "target_code": "gpt-4o-mini", "upstream_model_code": "gpt-4o-mini-2024-07-18",
  "target_type": "MODEL", "priority": 1, "is_primary": false,
  "attempt_no": 1, "max_attempts": 2,
  "outcome": "upstream_failure",
  "upstream_http_status": 503, "upstream_error_code": "UPSTREAM_ERROR",
  "retry_after_ms": null, "counted_for_ejection": true,
  "skipped_by_ejection": false, "probe_request": false,
  "traffic_rejected": false,
  "error_stage": "upstream_call"
}
```

网关异常（非上游）示例字段：`result.status` = `rate_limited`，`error` = `{"type":"rate_limit_error","code":"RATE_LIMIT_EXCEEDED","stage":"target_traffic_check","detail":{"resource_type":"MODEL","resource_code":"gpt-4o-mini","reason":"RATE_LIMIT_TPM","dimension":"TPM","unit":"tokens/minute","limit":100000,"used":100432,"reset_at":"2026-10-17T07:13:00Z","reset_after_seconds":27}}`。

## 6. 落地方式建议（不动热路径）

| 路线 | 做法 | 评价 |
|---|---|---|
| A. 日志采集 | 网关打结构化 JSON 日志，Filebeat/Fluentd 入 ODS | 零侵入；但失败事件（异常路径）已有 warn 日志、成功事件目前**无请求级日志**，需补 |
| B. 应用内事件总线 | `AbstractGatewayRequestTemplate.handleMap` 出口 + attempt 循环内发事件，内存队列 + 异步批量落库/Kafka | 数据最全（含 attempt 级与内部账目）；推荐 |
| C. 响应头回传 + 调用方上报 | 依赖 `X-Knot-Model`/`X-Knot-First-Token-Time`/`knot_extend` | 不可靠：头与体都受开关/异常路径影响，且调用方不一定上报 |

推荐 **B 为主、A 兜底**：事件源就用本文 §5 契约；Redis 计数器（频控/限额在途）不进数仓事实表，只作参考——它是滚动窗口快照，不是请求事实。

## 7. 口径与陷阱清单

1. **`returnUsageDetail` 关时响应无 `knot_extend`**——数仓取内部 `UsageAccounting`，不解析响应体（`GatewayRequestHandler.java:367-369`）。
2. **`cost` 是十进制普通字符串**，不设小数位上限、禁科学计数法（`NormalizedUsageDetail.java`）；⚠ 入库列需能容纳超 8 位小数——`decimal(20,8)` 会截断，建议 `decimal(26,12)` 或原样字符串。`TrafficRejectDetail` 已把 1e-12 缩放计数还原成金额，直接存。
3. **0 是合法值**：`total_tokens=0`、`status=0`、`ttft_ms=0` 都有业务含义；统计用 `IS NULL` 区分「无数据」与「零」，禁 truthy。
4. **TTFT / X-Knot-Model 缺失 = 无成功上游调用**，不是 0；慢信号摘除场景 TTFT 仍会写头（`FirstTokenHeaderWriter.java:71-82`）。
5. **流式用量口径**：逐 SSE 事件按 `totalTokens` 更大者胜出（`ProxyStreamingBody.java:173-181`），与整包 `mergeMax` 对齐；失败重试与 failover **不重复扣减**频控/额度（`GatewayRequestHandler.java:209-210`）。
6. **时钟双轨**：`receivedAtMillis` 用 `System.currentTimeMillis()`（本地时区），计费峰谷判定用 `Instant.now()`（UTC，`NormalizedUsageContext.occurredAt`）。事件统一带时区偏移，DWD 存 UTC + `dim_date` 按业务时区派生。
7. **多节点**：请求事件是节点本地的，必须带 `node_id`；Redis 计数才是全局口径。
8. **脱敏**：`secretKey` 禁入库；`error_message` 截断（≤512）；上游 `responseBody` 不入库（体量 + 可能含用户内容）。
9. **摘除/限流不要混算**：`ejected_skipped` 不是限流拒绝；`counted_for_ejection=false` 的 4xx/配置错误不计入上游失败率（§4.1）。
10. **媒体类请求**（图片/音频/视频）token 可能为 0 但张数/秒数非 0，统计成本勿只看 token。

## 8. Follow-up（待 robin 决策）

1. 事件出口选型：B 路线（应用内异步事件）需在 gateway 新增 publisher，涉及热路径改动——需批准后另开批次。
2. 数仓存储未定（MariaDB 宽表 / ClickHouse / Kafka+Flink），影响 §3 表是否原样建还是改列式。
3. `error_stage` 需要网关在异常抛出点补埋（当前异常对象不带阶段信息，可按 §2 表硬编码映射）。
4. 峰谷统计依赖 `pricingPlan` 规则列（FIXED/峰谷），DWS 需带 `pricing_plan` 维度。

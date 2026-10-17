# knot-gateway 数仓消息协议（Kafka 两层模型）

> 2026-10-17 · 本文取代 `docs/gateway-warehouse-data-model-2026-10-17.md` 的三事实表方案（request/attempt/usage_detail 三表 → **请求/响应两层消息**），口径清单沿用并扩充。
> 范围：网关 → Kafka 的消息契约（wire format）、topic 设计、埋点位置、口径。**不含实施代码**（Kafka 当前零依赖零配置，实施批次待 robin 批准）。

## 1. 模型总览

```
                 ┌──────────────────────── 网关进程 ────────────────────────┐
请求进入 ──► 鉴权/头校验 ──► 路由解析 ──► 流量守卫 ──► 候选循环(重试/failover) ──► 响应
                 │                              │                            │
                 ▼                              ▼                            ▼
        response-only                      REQUEST 消息                 RESPONSE 消息
        （入口即拒，无 routing）              （维度快照）              （结果+用量+错误+attempts+details）
                 └──────────── 两个 topic，key=trace_id 保序同分区 ─────────┘
```

| 层 | Topic | 发送时机 | 内容 |
|---|---|---|---|
| 请求层 | `knot.gateway.request.v1` | 路由解析成功后（`resolveRouting` 返回即发） | 入口输入 + 路由维度快照，**不含任何结果** |
| 响应层 | `knot.gateway.response.v1` | 请求终态（成功/失败/拒绝，含流式写完） | 结果、用量、计费明细、**统一错误信息**、attempts 上游调用明细 |

**关联键**：`trace_id`（W3C traceparent 派生）。请求层缺失（trace_id 在响应层有、请求层无）= 入口即拒（鉴权/头校验失败），数仓 left join 时该批流量只有响应层——**这是设计口径，不是丢消息**。

**与旧三表方案的差异（打平说明）**：

| 旧 | 新 | 理由 |
|---|---|---|
| `dwd_gateway_request_log` 表 | request topic 消息 | 消息即 ODS，消费时落 DWD |
| `dwd_gateway_attempt_log` 表 | `response.attempts[]` **内嵌数组** | attempt 有界（候选数 × maxAttempts，通常 ≤6），内嵌免三表 join |
| `dwd_gateway_usage_detail` 表 | `response.details[]` **内嵌数组** | 明细有界（≤6 行/请求），同上 |
| 9 维表 | 保留（数仓侧 dim，SCD2） | 消息内嵌 code 快照，dim 补历史属性 |

## 2. 请求层消息（RequestEnvelope）

```json
{
  "schema": "knot.gateway.request/1.0",
  "trace_id": "4bf92f3577b34da6a3ce929d0e0e4736",
  "span_id": "00f067aa0ba902b7",
  "node_id": "gw-01",
  "received_at": "2026-10-17T07:12:33.412+08:00",
  "protocol": "CHAT_COMPLETIONS",
  "stream": false,
  "sticky_key": null,
  "routing": {
    "rule_code": "R-xxx", "rule_name": "xxx",
    "consumer_code": "C-xxx", "consumer_name": "xxx",
    "app_code": "APP-xxx", "app_name": "xxx", "app_scenario": "chat",
    "dept_code": "D-xxx", "dept_name": "xxx",
    "consumer_username": "alice", "app_owner_username": "bob",
    "return_usage_detail": true,
    "target_type": "MODEL_POOL", "candidate_count": 3
  }
}
```

| 字段 | 类型 | 来源 |
|---|---|---|
| `trace_id` / `span_id` | string | `GatewayTraceContext` |
| `node_id` | string | 部署实例标识（多节点必填，见 §7） |
| `received_at` | ISO-8601 带偏移 | `GatewayRequestContext.receivedAtMillis` |
| `protocol` | string | `ModelApiProtocolEnum`（15 值） |
| `stream` | bool | 请求体 `stream` 字段 |
| `sticky_key` | string 可空 | `X-Knot-Sticky-Key` 头 |
| `routing.*` | object | `ResolvedRouting` + `GatewayRoutingInfo`；**全部为 code/username，无主键 id、无 secretKey** |

## 3. 响应层消息（ResponseEnvelope）

```json
{
  "schema": "knot.gateway.response/1.0",
  "trace_id": "4bf92f3577b34da6a3ce929d0e0e4736",
  "span_id": "00f067aa0ba902b7",
  "node_id": "gw-01",
  "received_at": "2026-10-17T07:12:33.412+08:00",
  "finished_at": "2026-10-17T07:12:38.722+08:00",
  "duration_ms": 5310,
  "status": "success",
  "http_status": 200,
  "ttft_ms": 812,
  "hit": { "model_code": "gpt-4o-mini", "provider_code": "PA-xxx", "upstream_model_code": "gpt-4o-mini-2024-07-18" },
  "attempts": [
    { "seq": 1, "target_code": "gpt-4o", "target_type": "MODEL", "priority": 1, "is_primary": true,
      "outcome": "upstream_failure", "skipped_by_ejection": false, "probe_request": false,
      "attempt_no": 1, "max_attempts": 2, "traffic_rejected": false, "reject_reason": null,
      "upstream": { "http_status": 503, "error_code": "UPSTREAM_ERROR", "retry_after_ms": null },
      "counted_for_ejection": true },
    { "seq": 2, "target_code": "gpt-4o-mini", "target_type": "MODEL", "priority": 2, "is_primary": false,
      "outcome": "success", "skipped_by_ejection": false, "probe_request": false,
      "attempt_no": 1, "max_attempts": 2, "traffic_rejected": false, "reject_reason": null,
      "upstream": null, "counted_for_ejection": false }
  ],
  "retry_total": 1, "failover_total": 1, "ejected_skipped": 0, "fail_open": false,
  "usage": {
    "input":  { "tokens": { "text": 176, "image": 0, "video": 0, "cache_read": 1024, "cache_write": 0, "cache_write_5m": 0, "cache_write_1h": 0, "unclassified": 0 }, "image_count": 0, "audio_seconds": 0, "video_seconds": 0 },
    "output": { "tokens": { "text": 380, "image": 0, "video": 0, "reasoning": 0, "unclassified": 0 }, "image_count": 0, "audio_seconds": 0, "video_seconds": 0 },
    "total_tokens": 1580
  },
  "billing": {
    "rule_code": "BR-xxx", "rule_version": "V-20260101", "billing_mode": "TOKEN", "pricing_plan": "FIXED",
    "total_tokens": 1580, "total_cost": "0.00234000", "currency": "CNY"
  },
  "details": [
    { "type": "uncachedInput", "tokens": 176,  "cost": "0.00035000", "price_per_million": "2.00000000" },
    { "type": "cachedRead",    "tokens": 1024, "cost": "0.00050000", "price_per_million": "0.50000000" },
    { "type": "output",        "tokens": 380,  "cost": "0.00149000", "price_per_million": "4.00000000" }
  ],
  "error": null
}
```

失败响应示例（限流 + 上游异常终态两种 error 形态见 §4）：

```json
{
  "schema": "knot.gateway.response/1.0",
  "trace_id": "ab12...", "span_id": "00f0...", "node_id": "gw-01",
  "received_at": "2026-10-17T07:12:33.412+08:00", "finished_at": "2026-10-17T07:12:33.455+08:00",
  "duration_ms": 43, "status": "rate_limited", "http_status": 429,
  "ttft_ms": null, "hit": null,
  "attempts": [
    { "seq": 1, "target_code": "gpt-4o-mini", "target_type": "MODEL", "priority": 1, "is_primary": true,
      "outcome": "traffic_rejected", "skipped_by_ejection": false, "probe_request": false,
      "attempt_no": 0, "max_attempts": 2, "traffic_rejected": true,
      "reject_reason": "RATE_LIMIT_TPM", "upstream": null, "counted_for_ejection": false }
  ],
  "retry_total": 0, "failover_total": 0, "ejected_skipped": 0, "fail_open": false,
  "usage": null, "billing": null, "details": [],
  "error": {
    "type": "rate_limit_error", "code": "RATE_LIMIT_EXCEEDED", "http_status": 429,
    "stage": "target_traffic_check",
    "message": "Rate limit exceeded for MODEL#gpt-4o-mini: 100432/100000 tokens/minute. Resets in 27s.",
    "detail": { "resource_type": "MODEL", "resource_code": "gpt-4o-mini", "reason": "RATE_LIMIT_TPM",
                "dimension": "TPM", "unit": "tokens/minute", "limit": 100000, "used": 100432,
                "reset_at": "2026-10-17T07:13:00Z", "reset_after_seconds": 27 }
  }
}
```

### 字段规则

| 字段 | 规则 |
|---|---|
| `status` | `success` / `rate_limited` / `quota_exceeded` / `upstream_error` / `gateway_error`（网关自身非流量拒绝：鉴权、参数、敏感词、存储不可用） |
| `ttft_ms` / `hit` | **可空**；异常路径为 null，禁填 0（0 是合法值，null 是无数据，见 §7） |
| `attempts[]` | 有界数组：候选循环每次评估产出一条；`outcome` ∈ `success` / `upstream_failure` / `skipped`（摘除跳过）/ `traffic_rejected`（目标频控拒）；`attempt_no=0` 表示未发起调用就被拒 |
| `upstream` | attempt 级上游异常三件套 `http_status` / `error_code`（ProxyErrorCodeEnum 8 值）/ `retry_after_ms`；成功为 null |
| `counted_for_ejection` | 该次失败是否计入摘除判定（5xx/429 计；4xx 业务错与配置类错不计）——**上游失败率统计必须过滤此列** |
| `usage` / `billing` / `details` | 成功路径才有；`billing` 命中计费规则才非 null；`details` 类型全集见输出协议文档 §4.3 |
| `error` | 统一错误结构，**成功为 null**；详见 §4 |

## 4. 错误信息（响应层核心）

统一 error 对象，上游异常与网关异常同形状，靠 `type` / `stage` / `upstream` 区分：

```json
{
  "type": "upstream_error",
  "code": "UPSTREAM_ERROR",
  "http_status": 502,
  "stage": "upstream_call",
  "message": "upstream responded with status 503",
  "detail": null,
  "upstream": { "http_status": 503, "error_code": "UPSTREAM_ERROR", "retry_after_ms": null }
}
```

| type | http_status | stage | 携带 |
|---|---|---|---|
| `auth_error` | 401 | `header_validate` | — |
| `invalid_request_error` | 400 | `header_validate`（Rule 头缺失）/ `request_parse`（体不可解析） | 可选业务 code |
| `moderation_error` | 400 | `plugin_moderation` | categoryCode（防词库探测，不暴露词） |
| `rate_limit_error` | 429 | `routing_traffic_check` / `target_traffic_check` | `detail`（TrafficRejectDetail 八列） |
| `quota_error` | 429 | 同上 | `detail` |
| `upstream_error` | 502 | `upstream_call` | `upstream` 三件套 |
| `storage_unavailable` | 503 | `storage` | — |

- `stage` 六值：`header_validate` / `request_parse` / `routing_resolve` / `routing_traffic_check` / `target_traffic_check` / `upstream_call` / `plugin_moderation` / `storage`（当前代码异常对象**不带阶段**，需埋点时按 §2 链路映射硬编码，见 §8）。
- 终态选取：候选全失败的终态异常是 `lastUpstreamException`（首个被频控拒的是 `firstRejection`，无可用候选时抛）——error 记录终态，**每次失败明细在 attempts[]**。
- `detail`（429 专有）：`resource_type` / `resource_code` / `reason`（RATE_LIMIT_RPM/RATE_LIMIT_TPM/QUOTA_TOKENS/QUOTA_COST）/ `dimension` / `unit` / `limit` / `used` / `reset_at` / `reset_after_seconds`；成本维度数值已从 1e-12 缩放计数还原成金额 + `unit` 为币种。

## 5. Kafka 输出设计

| 项 | 设计 |
|---|---|
| Broker 依赖 | **当前零依赖零配置**（已 grep 确认），实施时新增 spring-kafka（建议落 knot-gateway 模块，common 保持纯净） |
| Topic | `knot.gateway.request.v1` / `knot.gateway.response.v1`；版本号进 topic 名，契约演进不改名 |
| Key | `trace_id`（同请求两层消息同 key → 同分区保序，消费者可做窗口关联） |
| 序列化 | JSON（复用现有 `JsonKit`，cost 为十进制普通字符串、不设小数位上限） |
| 生产端 | 热路径**只入内存有界队列**（如 ArrayBlockingQueue 10k，满则弃旧 + WARN 计数），后台线程批量 send；同步 send 会拖慢 TTFT，不可 |
| 投递语义 | at-least-once（Kafka 默认）；重复消费由数仓侧按 `trace_id` 幂等（响应层是请求终态，可 upsert） |
| 失败兜底 | send 回调失败 / 队列满：本地死信文件（`logs/warehouse-dlq-*.jsonl`）+ 指标计数；**绝不让消息失败影响请求主链路** |
| 消息体积 | attempts≤~10 + details≤6 + 扁平字段，整体 < 8KB；上游 responseBody / 原始请求体**绝不入消息** |
| 乱序 | 两层消息可能乱序到达（响应先于请求到达，因请求层在路由解析后发、响应层在终态发，正常不会；跨节点重试场景以 `received_at` 为准修正） |

### 埋点位置（对应模板方法出口）

| 消息 | 埋点 | 代码位置 |
|---|---|---|
| request | `resolveRouting` 返回后、`dispatchGatewayPlugin(GATEWAY_REQUEST)` 前 | `AbstractGatewayRequestTemplate.java:87-89` |
| response（成功） | `applyUsageAccounting` 返回后、`GATEWAY_RESPONSE` 插件前 | `AbstractGatewayRequestTemplate.java:93-100` |
| response（失败） | catch 块内、`GATEWAY_ERROR` 插件派发前后均可（建议前，保证插件改写也被记录） | `AbstractGatewayRequestTemplate.java:102-105` |
| response（流式） | `ProxyStreamingBody.writeTo` 的 finally（流写完/断连）——usage 与 TTFT 此时才齐 | `ProxyStreamingBody.java:69-81` |
| response（入口即拒） | 仅响应层，`routing` 为 null，error.stage=`header_validate` | `validateRequestHeaders` 抛点 |

## 6. 数仓消费与维表（不变部分）

- Kafka 消息即 ODS；消费后展开：`dwd_gateway_request`（请求层）/ `dwd_gateway_response`（响应层，attempts/details 炸开行或保留 JSON 列视查询模式定）。
- 9 维表保留 SCD2：dim_date / dim_app / dim_dept / dim_user / dim_consumer / dim_rule / dim_model / dim_provider / dim_protocol，全部按 **code** 关联。
- DWS/ADS 口径同旧文档 §3.6：成本按 currency 分组、成功率走 attempts.outcome、延迟 P95 走 ttft_ms（null 不计）、缓存命中率走 cached_read/total、限流分析走 error.detail 交叉。

## 7. 口径清单（继承 + 新增）

1. **`returnUsageDetail` 关时响应无 `knot_extend`**——数仓取内部 `UsageAccounting`，不解析响应体。
2. **`cost` 是十进制普通字符串**，不设小数位上限、禁科学计数法；429 detail 的金额已从 1e-12 缩放计数还原，直接存。
3. **0 是合法值，null 是无数据**：`total_tokens=0`、`ttft_ms=0`、`attempt_no=0` 均有含义；统计用 `IS NULL` 区分，禁 truthy。
4. **TTFT / hit 缺失 = 无成功上游调用**，不是 0。
5. **流式用量**：逐 SSE 事件按 `totalTokens` 更大者胜出；failover 与重试**不重复记账**（频控/额度只在成功后记一次）。
6. **ejected_skipped ≠ 限流**；`counted_for_ejection=false` 的 4xx/配置错误不计入上游失败率。
7. **时钟双轨**：`received_at` 本地时钟带偏移，计费峰谷用 UTC；消息统一带偏移，DWD 转 UTC + `dim_date` 按业务时区派生。
8. **多节点**：消息必带 `node_id`；Redis 计数器才是全局口径。
9. **脱敏**：`secretKey` 禁入消息；`error.message` 截断 ≤512；上游响应体禁入。
10. **媒体类** token 可为 0 但张数/秒数非 0，勿只看 token。
11. **（Kafka 新增）重复**：at-least-once，按 `trace_id` 幂等 upsert。
12. **（Kafka 新增）丢失**：队列满弃旧 + DLQ 文件 + 计数指标；接受极小概率丢失，换热路径零阻塞。
13. **（Kafka 新增）乱序**：以 `received_at` 修正，勿假设到达序 = 发生序。

## 8. Follow-up（待 robin 批准后开实施批次）

1. **依赖**：spring-kafka 落 knot-gateway pom；配置 `knot.warehouse.kafka.{bootstrap-servers, topic-prefix, queue-capacity, dlq-path}`（起参走环境变量）。
2. **命名**（遵守 10-backend-java 命名纪律）：`WarehouseEventPublisher`（@Component，runtime 包或 service.support）+ 载荷 record 类型进 dto 包，禁泛化名。
3. **`error.stage` 硬编码映射**：当前异常家族不带阶段，需在埋点处按抛出位置映射（§4 表已给出）。
4. **指标**：发送成功/失败/丢弃计数（ Micrometer 或简单 AtomicLong + 日志），供运维对账。
5. 门禁：`mvn test` 全绿 + 真启冒烟验证消息实际到达 Kafka（无真 broker 时用内嵌测试 broker 或标注未验证）。

# knot-gateway 数仓消息协议（Kafka 两层模型）

> 2026-10-17 · 本文取代 `docs/gateway-warehouse-data-model-2026-10-17.md` 的三事实表方案（request/attempt/usage_detail 三表 → **请求/响应两层消息**），口径清单沿用并扩充。
> 范围：网关 → Kafka 的消息契约（wire format）、topic 设计、埋点位置、口径。实施代码已落地（§8）；命名与配置按 robin 2026-10-18 拍板调整：消息类带 Msg 标识（`RequestMsgDto` / `ResponseMsgDto` / …）、配置顶层 `kafka.*` 与 mybatis 平级、topic 写全名；投递形态按 robin 同日拍板定为 **Kafka 原生 async**（「是 kafka 的 async」——不建自建队列/投递线程，异步性由生产者客户端承担，启动期预热元数据）。

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
| 请求层 | `knot-gateway-request-event` | 路由解析成功后（`resolveRouting` 返回即发） | 入口输入 + 路由维度快照，**不含任何结果** |
| 响应层 | `knot-gateway-response-event` | 请求终态（成功/失败/拒绝，含流式写完） | 结果、用量、计费明细、**统一错误信息**、attempts 上游调用明细 |

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
| `node_id` | string | 部署实例标识（按主机名自动探测，无配置项） |
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
| Broker 依赖 | spring-kafka 已落 knot-gateway pom（§8）；knot-common 保持纯净，事件类不提权供插件用 |
| Topic | `knot-gateway-request-event` / `knot-gateway-response-event`（配置里写全名，不拼前缀）；契约版本在消息 `schema` 字段里演进，主题名保持稳定；不兼容变更才换主题名（显式改配置） |
| Key | `trace_id`（同请求两层消息同 key → 同分区保序，消费者可做窗口关联） |
| 序列化 | JSON（复用现有 `JsonKit`，cost 为十进制普通字符串、不设小数位上限） |
| 生产端 | **Kafka 原生 async**（robin 2026-10-18 拍板「是 kafka 的 async」）：调用线程 `kafkaTemplate.send()` 提交即返回，不等 ack（同步等 ack 会把 broker RTT 算进 TTFT，是请求链路红线）；投递异步性完全由生产者客户端承担（accumulator 缓冲 + sender 后台线程），**不建自建队列、不起投递线程**——队列化要么满即丢弃（丢数据）、要么无界堆积（OOM）、要么反压请求线程（拖慢 TTFT），三条路均不可接受；`KafkaPublisher.warmUp(...)` 在启动期预热两个主题的元数据，消化「首次发送时元数据未就绪」这个最常见的 `max.block.ms` 等待场景 |
| 投递语义 | at-least-once（Kafka 默认）；重复消费由数仓侧按 `trace_id` 幂等（响应层是请求终态，可 upsert） |
| 失败兜底 | 同步异常（`max.block.ms` 超时等，send 调用点抛出）与异步回调失败（broker 拒收/网络抖动，回调里才暴露）**两路统一落死信文件**（`kafka.dlq-path`，默认 `logs/kafka-dlq` **开启**——数据不丢是设计目标）；计数：publisher 的 sent/dropped + writer 的 written/lost；**绝不让消息失败影响请求主链路**。残留边界：Kafka 持续故障且 accumulator（默认 32MB）也满时调用线程最多阻塞 `max.block.ms=1000`ms（有界但真实存在；kafka-clients 3.7.2 已删 `block.on.buffer.full`，「缓冲满即失败」选项不存在，封顶等待时间是唯一杠杆） |
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
8. **多节点**：消息必带 `node_id`（按主机名自动探测，无配置项——admin 侧 `NodeIdentity` 同路线）；Redis 计数器才是全局口径。
9. **脱敏**：`secretKey` 禁入消息；`error.message` 截断 ≤512；上游响应体禁入。
10. **媒体类** token 可为 0 但张数/秒数非 0，勿只看 token。
11. **（Kafka 新增）重复**：at-least-once，按 `trace_id` 幂等 upsert。
12. **（Kafka 新增）丢失**：直推失败 → 死信文件（默认开启）+ written/lost 计数；**不丢数据是设计目标**，唯一丢失窗口是死信文件本身写不进去（磁盘满 / 权限），此时 ERROR 日志即报警（全仓无 webhook/Notifier）。
13. **（Kafka 新增）乱序**：以 `received_at` 修正，勿假设到达序 = 发生序。
14. **（Kafka 新增）直推阻塞边界**：`max.block.ms=1000` 封顶「拿元数据 / 等 accumulator 缓冲」的等待；Kafka 持续故障且 32MB 缓冲也满时请求/流式收尾线程最多阻塞 1s/事件——已向 robin 如实披露的有界残留风险；`warmUp()` 预热已把最常见触发场景（启动后首次发送）提前消化，运行期元数据过期刷新仍可能撞上。

## 8. 落地状态（2026-10-17 已实施；2026-10-18 按 robin 拍板完成命名与配置重构，代码未 commit）

| 件 | 位置 |
|---|---|
| 配置 | `kafka/KafkaProperties`（顶层 `kafka.*`，与 mybatis 平级，env 一律 `KAFKA_*`；`enabled` 默认 false；topic 写全名；`dlq-path` 默认 `logs/kafka-dlq` 开启）；`application.yml` 全套环境变量占位 |
| 装配 | `kafka/KafkaConfiguration`（`@ConditionalOnProperty(enabled=true)`，自建 `kafkaEventTemplate`（producer props = bootstrap-servers + 双 StringSerializer + `max.block.ms=1000`）；开了但没配 bootstrap-servers 直接启动失败；`kafkaPublisher` bean 创建时预热两个主题的元数据） |
| 死信 | `kafka/EventDeadLetterWriter`（synchronized 写 JSONL：`时间\ttopic\tkey\t失败原因\tpayload`；written/lost 计数；路径空白或写失败均不抛） |
| 协议 | `kafka/Msg`（消息超类：`traceId()` 分区键契约 + `nodeId()` 主机名自动探测无配置项）；`RequestMsgDto` / `ResponseMsgDto` `implements Msg`（record accessor 天然满足） |
| 投递 | `kafka/KafkaPublisher`（**纯管道**：唯一投递方法 `publish(String topic, Msg msg)`——topic 由调用方（埋点业务侧）维护传入，本类不持业务主题常量；key 取 `msg.traceId()`；序列化 + Kafka 原生 async + 双路死信 + sent/dropped；`warmUp(String...)` 启动预热。2026-10-18 演进：EventPublisher → EventSink（三层合一）→ KafkaPublisher（robin 拍板：Sink 业务含义太重，Publisher 是纯投递器；`Publisher` 后缀已补入命名白名单）） |
| 轨迹 | `kafka/CallTraceContext`（attempt 草稿 → 记录，存请求上下文属性 `callTrace`） |
| 消息 | `RequestMsgDto` / `ResponseMsgDto` / `AttemptMsgDto` / `ErrorMsgDto` / `TrafficRejectMsgDto` / `UpstreamErrorMsgDto` / `BillingViewMsgDto` / `AttemptOutcomeMsg`（基础协议：Msg 标识、无业务前缀） |
| 埋点 | 模板（request / 成功 / 失败，主题由模板持有）+ handler（候选轨迹 + 流式 onComplete）+ `ProxyStreamingBody.usageSink` |
| 测试 | `KafkaPublisherTest`(7，publish 两层主题/key 语义 + 同步失败死信与 dropped + 异步失败死信 + 未配路径 lost + 预热两主题 + 预热失败不抛 + Msg.nodeId 自动探测；KafkaTemplate 用子类假件，规避 JDK25 Mockito 不可 mock 具体类) + `ResponseMsgDtoTest`(6) |

**两处结构性调整**（为使埋点成立，非为兼容）：

1. `GatewayRequestContext.apiKey` 改为非 final + setter：上下文先建、apiKey 校验通过后写入。否则头校验失败时拿不到上下文，401/400 的拒绝量根本统计不到。
2. `GatewayRequestHandler.ATTR_HIT_TARGET` → `public HIT_TARGET_ATTRIBUTE`：响应层消息要从上下文取实际承接模型。

**门禁**：全量 `mvn -f knot-server/pom.xml test` → `BUILD SUCCESS`，8 模块全绿，gateway 189 例（含新增 10 例），EXIT=0。2026-10-18 六轮重构后分别复跑：① 包名 `gateway.warehouse` → `gateway.kafka`、配置 `knot.kafka.*`：BUILD SUCCESS，共 660 例（日志 `.workbuddy/audit/mvn-test-kafka-rename.log`）；② 消息类加 Msg 标识（`XxxMsgDto`）、配置提顶层 `kafka.*` 与 mybatis 平级：BUILD SUCCESS，8 模块全绿，共 660 例，gateway 189 例，日志 `.workbuddy/audit/mvn-test-kafka-msgrename.log`；③ 直推改造（删 `node-id` + 去内存队列/后台线程，fire-and-forget + `max.block.ms=1000` + DLQ 默认开启）：BUILD SUCCESS，共 **664 例**，gateway 193 例，日志 `.workbuddy/audit/mvn-test-kafka-directpush.log`；④ 「是 kafka 的 async」拍板 + 元数据预热：BUILD SUCCESS，共 **667 例**，gateway 196 例，日志 `.workbuddy/audit/mvn-test-kafka-async.log`；⑤ 三层合一 `EventSink`（robin 质疑 KafkaTransport 冗余）：BUILD SUCCESS，共 **663 例**，gateway 192 例，日志 `.workbuddy/audit/mvn-test-kafka-sinkmerge.log`；⑥ `KafkaPublisher` 收窄（robin 拍板「不要 EventSink，Sink 业务含义太重」：唯一 `publish(topic, Msg)`、topic 由业务维护、`Msg` 超类提供 traceId/nodeId）：BUILD SUCCESS，共 **663 例**，gateway 192 例（`KafkaPublisherTest` 7 + `ResponseMsgDtoTest` 6），日志 `.workbuddy/audit/mvn-test-kafka-publisher.log`。⚠ 后台任务 exit code 多次不可信——一律以 grep 日志 `BUILD SUCCESS` + `Failures/Errors: 0` 为准。

**未验证**：真实 Kafka broker 投递（本机无 broker，单测走内存 transport）；数仓侧消费未做。

## 9. Follow-up（待 robin 批准后开实施批次）

1. ~~**依赖**：spring-kafka 落 knot-gateway pom~~ —— 已落地（§8）。
2. ~~**命名**：`WarehouseEventPublisher` + 载荷 record 进 dto 包~~ —— robin 2026-10-18 拍板收敛：① 消息类是基础协议、无业务含义，**不加业务前缀**，但加 Msg 协议标识（`RequestMsgDto` / `ResponseMsgDto` / `AttemptMsgDto` / `ErrorMsgDto` / `TrafficRejectMsgDto` / `UpstreamErrorMsgDto` / `BillingViewMsgDto` / `AttemptOutcomeMsg`；`Msg` 非后缀白名单成员，故取 `Dto` 白名单后缀 + `Msg` 标识中缀）；② 配置**提到顶层 `kafka.*` 与 mybatis 平级**（env `KAFKA_*`，不走 `knot.*` 业务域）、topic 写全名、不写 Kafka 客户端默认项；类留 knot-gateway 不提升 common（不给插件用）；③ `Publisher` / `Transport` 均不在命名后缀白名单且 `KafkaTransport` 无第二实现（robin 质疑「是不是冗余」）→ EventPublisher / KafkaTransport / KafkaTemplateTransport 合并；④ robin 再拍板「不要 EventSink，Sink 业务含义太重，保留 KafkaPublisher」：投递器收敛为唯一方法 `publish(String topic, Msg msg)`（**topic 由调用方业务维护，publisher 不持主题常量**；`Msg` 超类提供 `traceId()` 分区键 + `nodeId()` 节点标识），`Publisher` 后缀已补入白名单（robin 拍板即修订）。
3. **`error.stage` 硬编码映射**：当前异常家族不带阶段，需在埋点处按抛出位置映射（§4 表已给出）。
4. **指标**：AtomicLong 计数已落地（publisher 的 sent/dropped、writer 的 written/lost，日志按 100 条节流）；Micrometer 接入未做。
5. 门禁：`mvn test` 全绿 + 真启冒烟验证消息实际到达 Kafka（无真 broker 时用内嵌测试 broker 或标注未验证）。
6. **直推残留风险**（已向 robin 披露）：Kafka 持续故障且缓冲已满时，请求/流式收尾线程最多阻塞 `max.block.ms=1000`ms/事件；kafka-clients 3.7.2 已删除 `block.on.buffer.full`，无法做到「缓冲满立即失败而不阻塞」。

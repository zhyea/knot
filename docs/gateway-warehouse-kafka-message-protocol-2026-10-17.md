# knot-gateway 数仓消息协议（Kafka 单条流量事件）

> 2026-10-17 起草，2026-10-18 多轮重构；**2026-10-18 当晚 robin 拍板「合并」**——原「请求层 + 响应层」两条 Kafka 消息收敛为**一条** `GatewayTrafficMsgDto`（schema `knot.gateway.traffic/1.0`），单 topic `knot-gateway-traffic-event`，终态一次发送。
> 合并理由（robin 拍板口径）：数仓是**离线批处理**，不需要请求层早期发出的「实时流量入口」信号（流式长连接期间入口维度延迟到终态才可见——可接受）；合并后**少一次发布、消除两层乱序、入口即拒不再需要 left join 识别**（一条记录即可表达：routing 为 null、hit 为 null、error.stage=header_validate）。
> 本文取代 `docs/gateway-warehouse-data-model-2026-10-17.md` 的三事实表方案（request/attempt/usage_detail 三表 → **单条流量事件**），口径清单沿用并扩充。
> 实施代码已落地（§7）；命名与配置按 robin 2026-10-18 拍板调整：消息类带 Msg 标识（`GatewayTrafficMsgDto` / `AttemptMsgDto` / …）、配置顶层 `kafka.*` 与 mybatis 平级、topic 写全名；投递形态按 robin 同日拍板定为 **Kafka 原生 async**（不建自建队列/投递线程，异步性由生产者客户端承担，启动期预热元数据）；真正请求的模型口径按 robin 同日拍板定为 **hit target**（候选循环落定后实际调用上游的那个，非候选链首位）。

## 1. 模型总览

```
                 ┌──────────────────────── 网关进程 ────────────────────────┐
请求进入 ──► 鉴权/头校验 ──► 路由解析 ──► 流量守卫 ──► 候选循环(重试/failover) ──► 响应
                 │                              │                    │
                 └──────────── 一条消息，终态一次发出（key=trace_id） ─────────┘
                                  ▼
                        TRAFFIC 事件（knot-gateway-traffic-event）
                        （入口维度快照 + 真正请求的模型 + 处置结果：
                         status/ttft/usage/billing/error/attempts）
```

| 消息 | Topic | 发送时机 | 内容 |
|---|---|---|---|
| 流量事件（单条） | `knot-gateway-traffic-event` | **请求终态**：非流式在用量记账后发；流式在响应体写完的 finally 发；异常路径（含入口即拒）在 catch 发 | 入口维度快照（routing）+ **真正请求的模型**（hit）+ 处置结果（status/ttft/usage/billing/error/attempts），一条记录表达一次请求的完整事实 |

**自关联键**：`trace_id`（W3C traceparent 派生）。消息内 `received_at` / `finished_at` / `duration_ms` 由网关自己填，数仓按 `trace_id` 幂等 upsert（at-least-once）。**入口即拒（鉴权 / Rule 头校验失败）同样发一条**：`routing` 为 null、`hit` 为 null、`error.stage`=header_validate——这是设计口径，单条记录即可表达，**无需请求层/响应层 left join**。

**与旧三表方案的差异（打平说明）**：

| 旧 | 新 | 理由 |
|---|---|---|
| `dwd_gateway_request_log` 表 | traffic 消息（单条；入口维度在终态一次带出，不再单独发请求层） | 消息即 ODS，消费时落 DWD |
| `dwd_gateway_attempt_log` 表 | `traffic.attempts[]` **内嵌数组** | attempt 有界（候选数 × maxAttempts，通常 ≤6），内嵌免三表 join |
| `dwd_gateway_usage_detail` 表 | `traffic.billing.detail[]` **内嵌数组** | 明细有界（≤6 行/请求），同上 |
| 9 维表 | 保留（数仓侧 dim，SCD2） | 消息内嵌 code 快照，dim 补历史属性 |

## 2. 流量事件消息（GatewayTrafficMsgDto）

```json
{
  "schema": "knot.gateway.traffic/1.0",
  "trace_id": "4bf92f3577b34da6a3ce929d0e0e4736",
  "span_id": "00f067aa0ba902b7",
  "node_id": "gw-01",
  "received_at": "2026-10-17T07:12:33.412+08:00",
  "finished_at": "2026-10-17T07:12:38.722+08:00",
  "duration_ms": 5310,
  "protocol": "CHAT_COMPLETIONS",
  "stream": false,
  "sticky_key": null,
  "routing": {
    "rule_code": "R-xxx", "rule_name": "xxx", "app_scenario": "chat",
    "consumer_code": "C-xxx", "consumer_name": "xxx",
    "app_code": "APP-xxx", "app_name": "xxx",
    "dept_code": "D-xxx", "dept_name": "xxx",
    "consumer_username": "alice", "app_owner_username": "bob",
    "return_usage_detail": true,
    "target_type": "MODEL_POOL", "candidate_count": 3
  },
  "status": "success",
  "http_status": 200,
  "ttft_ms": 812,
  "hit": { "model_code": "gpt-4o-mini", "upstream_model_code": "gpt-4o-mini-2024-07-18", "provider_account_code": "PA-xxx", "pool_code": "POOL-xxx" },
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
    "total_tokens": 1580, "total_cost": "0.00234000", "currency": "CNY",
    "detail": [
      { "type": "uncachedInput", "tokens": 176,  "cost": "0.00035000", "price": "2.00000000" },
      { "type": "cachedRead",    "tokens": 1024, "cost": "0.00050000", "price": "0.50000000" },
      { "type": "output",        "tokens": 380,  "cost": "0.00149000", "price": "4.00000000" }
    ]
  },
  "error": null
}
```

失败响应示例（限流终态；上游异常终态见 §3）：

```json
{
  "schema": "knot.gateway.traffic/1.0",
  "trace_id": "ab12...", "span_id": "00f0...", "node_id": "gw-01",
  "received_at": "2026-10-17T07:12:33.412+08:00", "finished_at": "2026-10-17T07:12:33.455+08:00",
  "duration_ms": 43, "protocol": "CHAT_COMPLETIONS", "stream": false, "sticky_key": null,
  "routing": {
    "rule_code": "R-xxx", "rule_name": "xxx", "app_scenario": "chat",
    "consumer_code": "C-xxx", "consumer_name": "xxx",
    "app_code": "APP-xxx", "app_name": "xxx",
    "dept_code": "D-xxx", "dept_name": "xxx",
    "consumer_username": "alice", "app_owner_username": "bob",
    "return_usage_detail": true,
    "target_type": "MODEL", "candidate_count": 1
  },
  "status": "rate_limited", "http_status": 429,
  "ttft_ms": null, "hit": null,
  "attempts": [
    { "seq": 1, "target_code": "gpt-4o-mini", "target_type": "MODEL", "priority": 1, "is_primary": true,
      "outcome": "traffic_rejected", "skipped_by_ejection": false, "probe_request": false,
      "attempt_no": 0, "max_attempts": 2, "traffic_rejected": true,
      "reject_reason": "RATE_LIMIT_TPM", "upstream": null, "counted_for_ejection": false }
  ],
  "retry_total": 0, "failover_total": 0, "ejected_skipped": 0, "fail_open": false,
  "usage": null, "billing": null,
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

**入口即拒**（鉴权 / Rule 头校验失败）：`routing`=null、`hit`=null、`usage`=null、`billing`=null、`error.stage`=`header_validate`、`http_status`=401/400。

### 字段规则

| 字段 | 类型 | 来源 |
|---|---|---|
| `trace_id` / `span_id` | string | `GatewayTraceContext` |
| `node_id` | string | 部署实例标识（按主机名自动探测，无配置项） |
| `received_at` / `finished_at` | ISO-8601 带偏移 | `GatewayRequestContext.receivedAtMillis` / `System.currentTimeMillis()` |
| `duration_ms` | long | `finished_at - received_at`（≥0） |
| `protocol` | string | `ModelApiProtocolEnum`（15 值） |
| `stream` | bool 可空 | 请求体 `stream` 字段 |
| `sticky_key` | string 可空 | `X-Knot-Sticky-Key` 头 |
| `routing` | object 可空 | `ResolvedRouting` + `GatewayRoutingInfo`；**全部为 code/username，无主键 id、无 secretKey**；入口即拒为 null |
| `status` | string | `success` / `rate_limited` / `quota_exceeded` / `upstream_error` / `gateway_error` |
| `http_status` | int | 成功 200；失败取 error.httpStatus |
| `ttft_ms` / `hit` | 可空 | 异常路径为 null，禁填 0（0 是合法值，null 是无数据） |
| `attempts[]` / `retry_total` / `failover_total` / `ejected_skipped` / `fail_open` | — | 候选循环轨迹（`CallTraceContext`）；详见 §3 attempts 规则 |
| `usage` / `billing` | 可空 | 成功路径才有；`billing` 命中计费规则才非 null |
| `error` | 可空 | 统一错误结构，**成功为 null**；详见 §3 |

**`routing`（RoutingSnapshot，14 字段）**：rule_code / rule_name / app_scenario / consumer_code / consumer_name / app_code / app_name / dept_code / dept_name / consumer_username / app_owner_username / return_usage_detail / target_type / candidate_count。`target_type` 按首个候选是否带来源池 code 还原 MODEL / MODEL_POOL（池展开后候选 targetType 被路由统一改写为 MODEL，规则维度的真实目标类型靠首个候选 poolCode 还原）。**全部为业务码 / username，无主键 id**。

**`hit`（HitSnapshot，4 字段）= 真正请求的模型**（hit target，候选循环落定后实际调用上游的那个候选，非候选链首位）：

| 字段 | 含义 |
|---|---|
| `model_code` | KB 侧模型业务码（也是响应头 `X-Knot-Model` 的值） |
| `upstream_model_code` | 实际写入上游请求体的模型标识（可能与前者不同） |
| `provider_account_code` | 供应商账户业务码 |
| `pool_code` | 来源模型池业务码；规则直挂模型为 null（池内命中时归因到池） |

拒绝 / 全失败路径无 hit，`hit` 为 null（尝试过哪些模型看 `attempts[]`）。

**`usage`（ModelUsage）**：input / output 两组 token 拆解 + `total_tokens`；媒体类 `image_count` / `audio_seconds` / `video_seconds` 独立口径，token 可为 0 但张数/秒数非 0。

**`billing`（BillingViewMsgDto）**：rule_code / rule_version / billing_mode / pricing_plan / total_tokens / total_cost / currency + `detail[]`（逐类用量金额行：type / tokens / cost / price，金额十进制普通字符串、不设小数位上限）；「免费」与「没配置计费」靠 billing 是否为 null 区分（两件事）。

## 3. 错误信息（error 对象）

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

- `stage` 八值：`header_validate` / `request_parse` / `routing_resolve` / `routing_traffic_check` / `target_traffic_check` / `upstream_call` / `plugin_moderation` / `storage`（当前代码异常对象**不带阶段**，需埋点时按 §1 链路映射硬编码，见 §4）。
- 终态选取：候选全失败的终态异常是 `lastUpstreamException`（首个被频控拒的是 `firstRejection`，无可用候选时抛）——error 记录终态，**每次失败明细在 attempts[]**。
- `detail`（429 专有）：`resource_type` / `resource_code` / `reason`（RATE_LIMIT_RPM/RATE_LIMIT_TPM/QUOTA_TOKENS/QUOTA_COST）/ `dimension` / `unit` / `limit` / `used` / `reset_at` / `reset_after_seconds`；成本维度数值已从 1e-12 缩放计数还原成金额 + `unit` 为币种。

### attempts 字段规则

| 字段 | 规则 |
|---|---|
| `attempts[]` | 有界数组：候选循环每次评估产出一条；`outcome` ∈ `success` / `upstream_failure` / `skipped`（摘除跳过）/ `traffic_rejected`（目标频控拒）；`attempt_no=0` 表示未发起调用就被拒 |
| `upstream` | attempt 级上游异常三件套 `http_status` / `error_code`（ProxyErrorCodeEnum 8 值）/ `retry_after_ms`；成功为 null |
| `counted_for_ejection` | 该次失败是否计入摘除判定（5xx/429 计；4xx 业务错与配置类错不计）——**上游失败率统计必须过滤此列** |

## 4. Kafka 输出设计（含埋点位置）

| 项 | 设计 |
|---|---|
| Broker 依赖 | spring-kafka 已落 knot-gateway pom（§7）；knot-common 保持纯净，事件类不提权供插件用 |
| Topic | `knot-gateway-traffic-event`（配置里写全名，不拼前缀）；契约版本在消息 `schema` 字段里演进，主题名保持稳定；不兼容变更才换主题名（显式改配置） |
| Key | `trace_id`（同请求同 key → 同分区保序，消费者可按 `trace_id` 幂等 upsert） |
| 序列化 | JSON（复用现有 `JsonKit`，cost 为十进制普通字符串、不设小数位上限） |
| 生产端 | **Kafka 原生 async**（robin 2026-10-18 拍板「是 kafka 的 async」）：调用线程 `kafkaTemplate.send()` 提交即返回，不等 ack（同步等 ack 会把 broker RTT 算进 TTFT，是请求链路红线）；投递异步性完全由生产者客户端承担（accumulator 缓冲 + sender 后台线程），**不建自建队列、不起投递线程**；`KafkaPublisher.warmUp(...)` 在启动期预热主题的元数据，消化「首次发送时元数据未就绪」这个最常见的 `max.block.ms` 等待场景 |
| 投递语义 | at-least-once（Kafka 默认）；重复消费由数仓侧按 `trace_id` 幂等（单条消息即请求终态，upsert 直接） |
| 失败兜底 | 同步异常（`max.block.ms` 超时等，send 调用点抛出）与异步回调失败（broker 拒收/网络抖动，回调里才暴露）**两路统一落死信文件**（`kafka.dlq-path`，默认 `logs/kafka-dlq` **开启**——数据不丢是设计目标）；计数：publisher 的 sent/dropped + writer 的 written/lost；**绝不让消息失败影响请求主链路** |
| 消息体积 | attempts≤~10 + billing.detail≤6 + 扁平字段，整体 < 8KB；上游 responseBody / 原始请求体**绝不入消息** |
| 乱序 | 单条消息，无两层乱序；多节点到达序以 `received_at` 为准修正即可（不再需要 left join / 窗口关联） |

### 埋点位置（终态三处发布）

| 消息 | 埋点 | 代码位置 |
|---|---|---|
| traffic（非流式成功） | `applyUsageAccounting` 返回后、`GATEWAY_RESPONSE` 插件前 | `AbstractGatewayRequestTemplate.java`（`trafficTopic` 由模板持有） |
| traffic（失败 / 入口即拒） | catch 块内、`GATEWAY_ERROR` 插件派发前后均可（建议前，保证插件改写也被记录）；入口即拒照发（去掉旧的 `if (routing != null)` 判定） | `AbstractGatewayRequestTemplate.java` |
| traffic（流式） | `ProxyStreamingBody.writeTo` 的 finally（流写完/断连）——usage 与 TTFT 此时才齐 | `ProxyStreamingBody.java` |

> 合并后发布点由原先 4 处（proxy 后发请求层 + 成功发响应层 + catch 发响应层 + 流式 finally 发响应层）收敛为 **3 处终态**（均发同一条 `GatewayTrafficMsgDto`）：非流式成功、异常、流式完成。入口即拒路径同样发一条（routing/hit 为 null），不再依赖请求层独立消息。

## 5. 数仓消费与维表（不变部分）

- Kafka 消息即 ODS；消费后展开：`dwd_gateway_traffic`（单条流量事实，attempts/billing.detail 炸开行或保留 JSON 列视查询模式定）。
- 9 维表保留 SCD2：dim_date / dim_app / dim_dept / dim_user / dim_consumer / dim_rule / dim_model / dim_provider / dim_protocol，全部按 **code** 关联。
- DWS/ADS 口径同旧文档 §3.6：成本按 currency 分组、成功率走 attempts.outcome、延迟 P95 走 ttft_ms（null 不计）、缓存命中率走 cached_read/total、限流分析走 error.detail 交叉。

## 6. 口径清单（继承 + 新增）

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
11. **（Kafka 新增）重复**：at-least-once，按 `trace_id` 幂等 upsert（单条消息即终态，upsert 直接、无需 left join）。
12. **（Kafka 新增）丢失**：直推失败 → 死信文件（默认开启）+ written/lost 计数；**不丢数据是设计目标**，唯一丢失窗口是死信文件本身写不进去（磁盘满 / 权限），此时 ERROR 日志即报警（全仓无 webhook/Notifier）。
13. **（Kafka 新增）乱序**：单条消息模型已消除「两层消息到达序 ≠ 发生序」问题；多节点到达仍以 `received_at` 修正，勿假设到达序 = 发生序（但不再需要窗口关联 / left join）。
14. **（Kafka 新增）直推阻塞边界**：`max.block.ms=1000` 封顶「拿元数据 / 等 accumulator 缓冲」的等待；Kafka 持续故障且 32MB 缓冲也满时请求/流式收尾线程最多阻塞 1s/事件——已向 robin 如实披露的有界残留风险；`warmUp()` 预热已把最常见触发场景（启动后首次发送）提前消化，运行期元数据过期刷新仍可能撞上。

## 7. 落地状态（2026-10-17 起草；2026-10-18 多轮重构 + 当晚「合并」拍板，代码未 commit）

| 件 | 位置 |
|---|---|
| 配置 | `kafka/KafkaProperties`（顶层 `kafka.*`，与 mybatis 平级，env 一律 `KAFKA_*`；`enabled` 默认 false；topic 写全名；`dlq-path` 默认 `logs/kafka-dlq` 开启）；`application.yml` 全套环境变量占位 |
| 装配 | `kafka/KafkaConfiguration`（`@ConditionalOnProperty(enabled=true)`，自建 `kafkaEventTemplate`（producer props = bootstrap-servers + 双 StringSerializer + `max.block.ms=1000`）；开了但没配 bootstrap-servers 直接启动失败；`kafkaPublisher` bean 创建时预热主题的元数据） |
| 死信 | `kafka/EventDeadLetterWriter`（synchronized 写 JSONL：`时间\ttopic\tkey\t失败原因\tpayload`；written/lost 计数；路径空白或写失败均不抛） |
| 协议 | `kafka/msg/Msg`（消息超类：`traceId()` 分区键契约 + `nodeId()` 主机名自动探测无配置项）；`GatewayTrafficMsgDto` `implements Msg`（**单条合并消息**，schema `knot.gateway.traffic/1.0`）；`hit` 取**真正请求的模型**（hit target，4 字段含 providerAccountCode + poolCode） |
| 投递 | `kafka/KafkaPublisher`（**纯管道**：唯一投递方法 `publish(String topic, Msg msg)`——topic 由调用方维护传入，本类不持业务主题常量；key 取 `msg.traceId()`；序列化 + Kafka 原生 async + 双路死信 + sent/dropped；`warmUp(String...)` 启动预热。robin 拍板：Sink 业务含义太重，Publisher 是纯投递器；`Publisher` 后缀已补入命名白名单） |
| 轨迹 | `kafka/CallTraceContext`（attempt 草稿 → 记录，存请求上下文属性 `callTrace`） |
| 消息 | `GatewayTrafficMsgDto` / `AttemptMsgDto` / `ErrorMsgDto` / `TrafficRejectMsgDto` / `UpstreamErrorMsgDto` / `BillingViewMsgDto` / `BillingDetailMsgDto` / `AttemptOutcomeMsg` / `RoutingSnapshot` / `HitSnapshot`（基础协议：Msg 标识、无业务前缀） |
| 埋点 | 模板（非流式成功 / 失败 catch / 流式 onComplete 三处终态发布，主题由模板持有 `trafficTopic`）+ handler（候选轨迹 + 流式 onComplete）+ `ProxyStreamingBody.usageSink` |
| 测试 | `KafkaPublisherTest`(7，publish 单主题/key 语义 + 同步失败死信与 dropped + 异步失败死信 + 未配路径 lost + 预热 + 预热失败不抛 + Msg.nodeId 自动探测；KafkaTemplate 用子类假件，规避 JDK25 Mockito 不可 mock 具体类) + `GatewayTrafficMsgDtoTest`(12，recordsActuallyRequestedModelFromHitTarget / hitFromPoolCarriesPoolCode / failoverHitIsNotFirstCandidate / noHitYieldsNullHit / nullRoutingYieldsNull / nodeIdComesFromMsgConstant / successCarriesUsageAndHitModel / upstreamFailureIsCarriedAsStructuredError / rateLimitRejectionCarriesRejectDetail / storageUnavailableIsNotUpstreamError / traceIsFlattenedIntoMessage / serializedJsonKeepsCostAsString) |

**两处结构性调整**（为使埋点成立，非为兼容）：

1. `GatewayRequestContext.apiKey` 改为非 final + setter：上下文先建、apiKey 校验通过后写入。否则头校验失败时拿不到上下文，401/400 的拒绝量根本统计不到。
2. `GatewayRequestHandler.ATTR_HIT_TARGET` → `public HIT_TARGET_ATTRIBUTE`：消息从上下文取实际承接模型（真正请求的模型）。

**门禁**：全量 `mvn -f knot-server/pom.xml test` → `BUILD SUCCESS`，8 模块全绿，gateway 198 例（含新增 19 例），EXIT=0（grep 日志 `BUILD SUCCESS` + `Failures/Errors: 0` 为准，后台 exit code 不可信）。2026-10-18 重构与合并轮次：
① 包名 `gateway.warehouse` → `gateway.kafka`、配置 `knot.kafka.*`：BUILD SUCCESS，共 660 例（日志 `.workbuddy/audit/mvn-test-kafka-rename.log`）；
② 消息类加 Msg 标识、配置提顶层 `kafka.*`：BUILD SUCCESS，8 模块全绿，共 660 例，gateway 189 例（日志 `.workbuddy/audit/mvn-test-kafka-msgrename.log`）；
③ 直推改造（删 node-id + 去内存队列/后台线程，fire-and-forget + max.block.ms=1000 + DLQ 默认开启）：BUILD SUCCESS，共 664 例，gateway 193 例（日志 `.workbuddy/audit/mvn-test-kafka-directpush.log`）；
④ 「是 kafka 的 async」+ 元数据预热：BUILD SUCCESS，共 667 例，gateway 196 例（日志 `.workbuddy/audit/mvn-test-kafka-async.log`）；
⑤ 三层合一 `EventSink`：BUILD SUCCESS，共 663 例，gateway 192 例（日志 `.workbuddy/audit/mvn-test-kafka-sinkmerge.log`）；
⑥ `KafkaPublisher` 收窄（删 EventSink）：BUILD SUCCESS，共 663 例，gateway 192 例（日志 `.workbuddy/audit/mvn-test-kafka-publisher.log`）；
⑦ 请求层补候选模型三 code（池展开候选带来源池）：BUILD SUCCESS，共 668 例，gateway 197 例（日志 `.workbuddy/audit/mvn-test-kafka-poolcode.log`）；
⑧ 请求层收窄为 hit target 三 code + `publishRequest` 后移：BUILD SUCCESS，reactor 全量 669 例，gateway 198 例（日志 `.workbuddy/audit/mvn-test-kafka-hittarget.log`）；
⑨ **合并为单条消息**（robin 拍板：离线批处理 + 接受延迟可见）：删 `RequestMsgDto` / `ResponseMsgDto` 及对应两测试；新建 `GatewayTrafficMsgDto`（schema `knot.gateway.traffic/1.0`）+ `GatewayTrafficMsgDtoTest`(12)；单 topic `knot-gateway-traffic-event`；发布点 4→3（终态三处）；配置/yml 收敛为单 `topic`；**BUILD SUCCESS，reactor 全量 669 例，gateway 198 例**（`KafkaPublisherTest` 7 + `GatewayTrafficMsgDtoTest` 12），日志 `.workbuddy/audit/mvn-test-merge.log`。⚠ 后台任务 exit code 多次不可信——一律以 grep 日志 `BUILD SUCCESS` + `Failures/Errors: 0` 为准。

**未验证**：真实 Kafka broker 投递（本机无 broker，单测走内存 transport）；数仓侧消费未做。

## 8. Follow-up（待 robin 批准后开实施批次）

1. ~~**依赖**：spring-kafka 落 knot-gateway pom~~ —— 已落地（§7）。
2. ~~**命名/结构**：`WarehouseEventPublisher` + 载荷 record 进 dto 包 → 收敛为 `KafkaPublisher`（`publish(topic, Msg)`）+ `GatewayTrafficMsgDto` 单条消息（robin 2026-10-18 拍板：① 消息类是基础协议、无业务含义，加 Msg 协议标识；② 配置提到顶层 `kafka.*` 与 mybatis 平级，env `KAFKA_*`，topic 写全名；③ 投递器收敛为唯一方法 `publish(String topic, Msg msg)`，topic 由业务维护，`Msg` 超类提供 `traceId()` / `nodeId()`；④ 当晚拍板「请求层+响应层合并为单条」，删 `RequestMsgDto` / `ResponseMsgDto` 及两测试）。`Publisher` 后缀已补入白名单~~ —— 已落地（§7）。
3. **`error.stage` 硬编码映射**：当前异常家族不带阶段，需在埋点处按抛出位置映射（§3 表已给出）。
4. **指标**：AtomicLong 计数已落地（publisher 的 sent/dropped、writer 的 written/lost，日志按 100 条节流）；Micrometer 接入未做。
5. 门禁：`mvn test` 全绿 + 真启冒烟验证消息实际到达 Kafka（无真 broker 时用内嵌测试 broker 或标注未验证）。
6. **直推残留风险**（已向 robin 披露）：Kafka 持续故障且缓冲已满时，请求/流式收尾线程最多阻塞 `max.block.ms=1000`ms/事件；kafka-clients 3.7.2 已删除 `block.on.buffer.full`，无法做到「缓冲满立即失败而不阻塞」。
7. **（合并衍生）数仓侧单表落地**：消费端由「两 topic 双表 + left join」改为「单 topic 单表 `dwd_gateway_traffic` + 内嵌 attempts/billing.detail」，upsert 按 `trace_id`；本仓仅产出消息，消费侧实现不在本仓。

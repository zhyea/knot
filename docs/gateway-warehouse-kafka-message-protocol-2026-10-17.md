# knot-gateway 数仓消息协议（Kafka 单条流量事件）

> 2026-10-17 起草，2026-10-18 多轮重构；**2026-10-18 当晚 robin 拍板「合并」**——原「请求层 + 响应层」两条 Kafka 消息收敛为**一条** `GatewayTrafficMsgDto`（schema `knot.gateway.traffic/1.0`），单 topic `knot-gateway-traffic-event`，终态一次发送。
> **2026-10-18 深夜追加 attempt 级 `upstream_request_id`（2026-10-19 晚定为 request-id 优先、log-id 兜底、单字段）**：把上游供应商响应头白名单归一后的 request-id 随流量事件一并入库，用于双向对账（提工单时供应商只认他们回的 id）。契约与 `docs/上游trace打通-双向对账设计-2026-10-17.md` §4「方案 B」对齐；**埋点取数依赖方案 B 的上游响应头捕获实现（网关出站链路三处抓取）——已于 2026-10-19 落地（全量 703 例 BUILD SUCCESS，log-id 兜底口径后重跑），实现与验证细节见该文档 §11**。
> 合并理由（robin 拍板口径）：数仓是**离线批处理**，不需要请求层早期发出的「实时流量入口」信号（流式长连接期间入口维度延迟到终态才可见——可接受）；合并后**少一次发布、消除两层乱序、入口即拒不再需要 left join 识别**（一条记录即可表达：routing 为 null、hit 为 null、error.stage=header_validate）。
> 本文取代 `docs/gateway-warehouse-data-model-2026-10-17.md` 的三事实表方案（request/attempt/usage_detail 三表 → **单条流量事件**），口径清单沿用并扩充。
> 实施代码已落地（§7）；命名与配置按 robin 2026-10-18 拍板调整：消息类带 Msg 标识（`GatewayTrafficMsgDto` / `AttemptMsgDto` / …）、配置顶层 `kafka.*` 与 mybatis 平级、topic 写全名；投递形态按 robin 同日拍板定为 **Kafka 原生 async**（不建自建队列/投递线程，异步性由生产者客户端承担，启动期预热元数据）；真正请求的模型口径按 robin 同日拍板定为 **hit target**（候选循环落定后实际调用上游的那个，非候选链首位）。

> **2026-10-27 追加用量三视图**（robin 拍板）：顶层新增 `upstream_usage`（上游 `usage` 原文，与 `usage` **同级**）；`usage`（`ModelUsage`）扩容为「归一化用量 + `attributes` 计费属性」，输出侧新增 `video_count`。动机：下游要能**只凭消息自行复算价格**（转卖 token / 成本分摊 / 毛利核算），而网关**不下发计费规则内容**，故量与分档键必须齐备，原文用于回溯口径。schema 仍为 `knot.gateway.traffic/1.0`（加字段向后兼容）。设计与实施记录见 `docs/统一视频用量模型-2026-10-27.md` §五之六。

> **2026-10-28 异步任务改为 1 + N 条，schema 升 1.1**（robin 拍板：改路 B）。此前异步提交时**抑制**创建事件，只由轮询器发一条终态——等于把「1 次提交 + N 次查询」压成「1 条事件」，提交那次上游调用与中间每一次查询在数仓里全部消失，上游成本、产物延迟分布、「重试 5 次才成功」全部算不出来。
> 现在**不抑制**，改为给每条事件打身份：顶层新增 `task_code` / `task_phase` / `task_terminal` / `task_query_seq` 四个字段（同步请求不带，`NON_NULL` 整段省略）。
> **升版本的理由**：字段层面纯追加，但消息基数从「1 请求 1 条」变成「1 + N 条」——按 `count(*)` 当请求数写的消费逻辑会静默放大，这属于破坏性变更，不能用「加字段向后兼容」搪塞。**数仓聚合必须按下表加口径**（§2.1）。

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

> **2026-10-28 破例：异步任务是 1 + N 条，不是 1 条**（robin 拍板改路 B）。异步请求在上游的真实开销是
> **1 次 create + N 次 query**（N 由产物耗时与轮询间隔决定，视频任务可达数百），所以这类请求**发 1 + N 条事件**，
> 靠 `task_code` 归到同一笔任务，用 `task_phase`（`submit` / `query`）与 `task_terminal` 区分每条的身份。
> schema 升 `knot.gateway.traffic/1.1`：字段层面纯追加，但**消息基数变了**，
> 「一条消息 = 一次请求」的假设不再成立。同步请求仍然严格 1 条，且不带任何 `task_*` 字段。

| 消息 | Topic | 发送时机 | 内容 |
|---|---|---|---|
| 流量事件（同步 1 条） | `knot-gateway-traffic-event` | **请求终态**：非流式在用量记账后发；流式在响应体写完的 finally 发；异常路径（含入口即拒）在 catch 发 | 入口维度快照（routing）+ **真正请求的模型**（hit）+ 处置结果（status/ttft/usage/billing/error/attempts），一条记录表达一次请求的完整事实 |
| 流量事件（异步 1 + N 条） | 同上 | 提交时 1 条（`task_phase=submit`）；**每次查询上游任务状态发 1 条**（`task_phase=query`），其中拿到终态那次 `task_terminal=true` 并带用量与计费 | 同上；中间查询那条 `usage` / `billing` 为 null，只带 `task_query_seq` 与（若失败）`error` |

**自关联键**：`trace_id`（W3C traceparent 派生）。消息内 `received_at` / `finished_at` / `duration_ms` 由网关自己填，数仓按 `trace_id` 幂等 upsert（at-least-once）。**入口即拒（鉴权 / Rule 头校验失败）同样发一条**：`routing` 为 null、`hit` 为 null、`error.stage`=header_validate——这是设计口径，单条记录即可表达，**无需请求层/响应层 left join**。

⚠ 异步任务**不能**按 `trace_id` 去重：1 + N 条共用同一个 trace **与同一个 span**（同一次客户端请求），
去重键必须是 `(task_code, task_phase, task_query_seq)` 三元组；同步记录该三元组全 null，退回 `trace_id` 幂等。

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
  "schema": "knot.gateway.traffic/1.1",
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
      "upstream_request_id": "req_abc123",
      "counted_for_ejection": true },
    { "seq": 2, "target_code": "gpt-4o-mini", "target_type": "MODEL", "priority": 2, "is_primary": false,
      "outcome": "success", "skipped_by_ejection": false, "probe_request": false,
      "attempt_no": 1, "max_attempts": 2, "traffic_rejected": false, "reject_reason": null,
      "upstream": null, "upstream_request_id": "req_def456",
      "counted_for_ejection": false }
  ],
  "retry_total": 1, "failover_total": 1, "ejected_skipped": 0, "fail_open": false,
  "upstream_usage": {
    "prompt_tokens": 1200, "completion_tokens": 380, "total_tokens": 1580,
    "prompt_tokens_details": { "cached_tokens": 1024 }
  },
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
  "schema": "knot.gateway.traffic/1.1",
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
      "reject_reason": "RATE_LIMIT_TPM", "upstream": null,
      "upstream_request_id": null, "counted_for_ejection": false }
  ],
  "retry_total": 0, "failover_total": 0, "ejected_skipped": 0, "fail_open": false,
  "upstream_usage": null, "usage": null, "billing": null,
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

**入口即拒**（鉴权 / Rule 头校验失败）：`routing`=null、`hit`=null、`upstream_usage`=null、`usage`=null、`billing`=null、`error.stage`=`header_validate`、`http_status`=401/400。

### 字段规则

| 字段 | 类型 | 来源 |
|---|---|---|
| `trace_id` / `span_id` | string | `GatewayTraceContext`。⚠ 异步的 1 + N 条**两者都相同**（`replay` 保住提交那次的 span），区分查询靠 `task_query_seq` |
| `request_id` | string | `GatewayTraceContext.requestId()`：网关入口生成的 32 位小写 hex，已以 `X-Request-Id` 透传给上游（2026-10-23 新增） |
| `node_id` | string | 部署实例标识（按主机名自动探测，无配置项） |
| `received_at` / `finished_at` | ISO-8601 带偏移 | `GatewayRequestContext.receivedAtMillis` / `System.currentTimeMillis()` |
| `duration_ms` | long | `finished_at - received_at`（≥0） |
| `protocol` | string | `ModelApiProtocolEnum`（15 值） |
| `stream` | bool 可空 | 请求体 `stream` 字段 |
| `sticky_key` | string 可空 | `X-Knot-Sticky-Key` 头 |
| `routing` | object 可空 | `ResolvedRouting` + `GatewayRoutingInfo`；**全部为 code/username，无主键 id、无 secretKey**；入口即拒为 null |
| `status` | string | `success` / `rate_limited` / `quota_exceeded` / `upstream_error` / `timeout` / `gateway_error` |
| `http_status` | int | 成功 200；失败取 error.httpStatus |
| `ttft_ms` / `hit` | 可空 | 异常路径为 null，禁填 0（0 是合法值，null 是无数据） |
| `attempts[]` / `retry_total` / `failover_total` / `ejected_skipped` / `fail_open` | — | 候选循环轨迹（`CallTraceContext`）；详见 §3 attempts 规则（含 attempt 级上游 `upstream_request_id`） |
| `upstream_usage` / `usage` / `billing` | 可空 | 成功路径才有；`billing` 命中计费规则才非 null。**用量三视图**，见下方专段（2026-10-27 新增 `upstream_usage`） |
| `task_code` | string 可空 | 异步任务句柄；**同步请求为 null**。同一笔任务的 1 + N 条事件共用它（2026-10-28 新增） |
| `task_phase` | string 可空 | `submit`（create 调用）/ `query`（查询上游任务状态）；同步为 null |
| `task_terminal` | bool 可空 | 该阶段是否已落终态。`submit` 恒 false；`query` 中拿到产物/失败/超时那次为 true。**计费与失败率只看这一个字段** |
| `task_query_seq` | int 可空 | 第几次查询，从 1 开始；`submit` 为 null（不是 0——0 会被误读成「查了 0 次」）。异步任务的延迟分布只能靠它 |
| `error` | 可空 | 统一错误结构，**成功为 null**；详见 §3。⚠ 异步任务的中间查询失败也会带 `error`（`code=UPSTREAM_QUERY_ERROR`），统计失败率必须过滤 `task_terminal`，见 §2.1 |

**`routing`（RoutingSnapshot，14 字段）**：rule_code / rule_name / app_scenario / consumer_code / consumer_name / app_code / app_name / dept_code / dept_name / consumer_username / app_owner_username / return_usage_detail / target_type / candidate_count。`target_type` 按首个候选是否带来源池 code 还原 MODEL / MODEL_POOL（池展开后候选 targetType 被路由统一改写为 MODEL，规则维度的真实目标类型靠首个候选 poolCode 还原）。**全部为业务码 / username，无主键 id**。

**三个 id 的语义边界**（2026-10-23 明确，混用即失去对账能力）：

| 字段 | 谁生成 | 用途 | 检索方向 |
|---|---|---|---|
| `trace_id` | 可能来自调用方 traceparent | W3C 链路追踪，日志/MDC 关联 | 我方内部 |
| `request_id` | **网关入口生成**（32 位 hex） | 提工单时的「我方编号」，已随 `X-Request-Id` 发上游 | **拿它去供应商日志里查** |
| `attempts[].upstream_request_id` | **上游返回** | 供应商系统里的这次调用编号 | **拿它回我方数仓查** |

> 上游若把我们的 `X-Request-Id` 原样回显，网关会**排除该值**，不会把它写进 `upstream_request_id`（否则两列变成同一个 id）。

**`hit`（HitSnapshot，4 字段）= 真正请求的模型**（hit target，候选循环落定后实际调用上游的那个候选，非候选链首位）：

| 字段 | 含义 |
|---|---|
| `model_code` | KB 侧模型业务码（也是响应头 `X-Knot-Model` 的值） |
| `upstream_model_code` | 实际写入上游请求体的模型标识（可能与前者不同） |
| `provider_account_code` | 供应商账户业务码 |
| `pool_code` | 来源模型池业务码；规则直挂模型为 null（池内命中时归因到池） |

拒绝 / 全失败路径无 hit，`hit` 为 null（尝试过哪些模型看 `attempts[]`）。

**用量三视图（2026-10-27 定稿）**：单向派生，三者并列下发。

```
upstream_usage（上游原文，只透传不加工）
      ↓ 归一化 + 补本次请求的计费属性
usage（ModelUsage：input / output / attributes / total_tokens）
      ↓ 按本网关规则计价（规则内容不下发）
billing（NormalizedUsage：金额与明细）
```

| 字段 | 内容 | 为什么下发 |
|---|---|---|
| `upstream_usage` | 上游响应里那个 `usage` 对象原样（`UsageAccounting.rawUsage()`），**保留厂商键名**（`prompt_tokens` / `input_tokens` / `prompt_tokens_details.cached_tokens`…）；上游没报或报空对象时**整段缺席** | 归一化口径若有争议可回溯；数仓不必再自行解析响应体 |
| `usage` | 归一化用量 + `attributes`（计费属性） | 下游要能**只凭这一份自己算钱**（转卖 token / 成本分摊 / 毛利核算） |
| `billing` | 本网关按规则算出的金额与明细 | 对账基准；**不含规则内容**（无 `config_json`），下游复算靠量 + 分档键 |

**`usage`（ModelUsage）**：

- `input` / `output` 两组 token 拆解 + `total_tokens`；非 token 计量与 token 明细**平级**：
  `image_count` / `audio_seconds` / `video_seconds`，输出侧另有 `video_count`（按条计价，如 Veo）。
- 输入侧同样有 `image_count` / `audio_seconds` / `video_seconds`：图生视频的首帧图、
  参考视频时长走这里（MiniMax「输出秒 + 输入秒 + 输入图张」多分量计价要靠它复算）。
- `attributes`：**本次请求声明的**计费属性（不是上游 usage 报的），9 项 ——
  `resolution` / `aspect_ratio` / `mode` / `width` / `height` / `pixels` / `sound` /
  `voice_specified` / `reference_video`。值一律**原样保留厂商写法**（`768P` / `16:9` / `wan-pro` /
  `MOTION_CONTROL`），键空间归一是下游自己的事。
- 全 0 值与空串按 `NON_DEFAULT` **省略**；`attributes` 全空时整段缺席。
  **缺席即默认**：读不到 `attributes.resolution` 等价于「未按分辨率分档」，与网关取价回落默认档同口径。

> ⚠ 视频响应的 `data` 数组装的是视频，**不会**被回填进 `output.image_count`；
> 视频条数只出现在 `output.video_count`（2026-10-27 修）。


**`billing`（BillingViewMsgDto）**：rule_code / rule_version / billing_mode / pricing_plan / total_tokens / total_cost / currency + `detail[]`（逐类用量金额行：type / tokens / cost / price，金额十进制普通字符串、不设小数位上限）；「免费」与「没配置计费」靠 billing 是否为 null 区分（两件事）。

### 2.1 异步任务：1 + N 条事件与聚合口径（2026-10-28）

一笔异步任务（`VIDEO_GENERATIONS` 这类「提交即返回 task_id」的协议）产生 **1 + N 条事件**：

```json
// ① 提交（客户端拿 202）：task_phase=submit，没有用量
{ "schema": "knot.gateway.traffic/1.1", "trace_id": "4bf92f3577b34da6a3ce929d0e0e4736", "span_id": "00f067aa0ba902b7", "request_id": "9f2a...",
  "protocol": "VIDEO_GENERATIONS", "status": "success", "http_status": 202,
  "task_code": "a3f1c9e0b7d24a5f8c6e1b0d9a7f3e2c", "task_phase": "submit",
  "task_terminal": false, "usage": null, "billing": null, "error": null }

// ② 中间查询第 5 次：还在跑，没有用量
{ "schema": "knot.gateway.traffic/1.1", "trace_id": "4bf92f3577b34da6a3ce929d0e0e4736", "span_id": "00f067aa0ba902b7",
  "protocol": "VIDEO_GENERATIONS", "status": "success", "http_status": 200,
  "task_code": "a3f1c9e0b7d24a5f8c6e1b0d9a7f3e2c", "task_phase": "query",
  "task_terminal": false, "task_query_seq": 5, "usage": null, "billing": null }

// ③ 中间查询第 9 次：上游 5xx，要重试 —— 带 error，但仍是中间态
{ "schema": "knot.gateway.traffic/1.1", "trace_id": "4bf92f3577b34da6a3ce929d0e0e4736", "span_id": "00f067aa0ba902b7",
  "protocol": "VIDEO_GENERATIONS", "status": "upstream_error", "http_status": 502,
  "task_code": "a3f1c9e0b7d24a5f8c6e1b0d9a7f3e2c", "task_phase": "query",
  "task_terminal": false, "task_query_seq": 9, "usage": null, "billing": null,
  "error": { "type": "upstream_error", "code": "UPSTREAM_QUERY_ERROR", "http_status": 502, "stage": "upstream" } }

// ④ 第 14 次查询拿到产物：终态，带用量与计费
{ "schema": "knot.gateway.traffic/1.1", "trace_id": "4bf92f3577b34da6a3ce929d0e0e4736", "span_id": "00f067aa0ba902b7",
  "protocol": "VIDEO_GENERATIONS", "status": "success", "http_status": 200,
  "task_code": "a3f1c9e0b7d24a5f8c6e1b0d9a7f3e2c", "task_phase": "query",
  "task_terminal": true, "task_query_seq": 14,
  "usage": { ... }, "billing": { ... }, "error": null }
```

**数仓聚合口径（必须带，否则每条都会算错）**：

```sql
-- 客户端请求数：同步全算，异步只算提交那条
where task_phase is null or task_phase = 'submit'

-- 上游调用数：不加条件。同步 1 条 = 1 次调用；异步 1 + N 条 = 1 + N 次真实调用
-- （这正是本次改路 B 保住的唯一事实——旧口径把 N-1 次查询全抹掉了）

-- 计费金额：同步全算，异步只算终态
where task_phase is null or task_terminal = true

-- 失败率：⚠ 必须同样过滤，否则「重试 5 次后成功」会被算成 5 次失败
where task_phase is null or task_terminal = true

-- 产物延迟分布（异步独有）：提交时间 → 终态的 finished_at - 提交的 finished_at
--   task_query_seq 就是「等了几次轮询」，两者一起才解释得了延迟
```

**幂等键**：同步记录按 `trace_id`；异步必须按 `(task_code, task_phase, task_query_seq)`——
1 + N 条共用一个 trace（同一次客户端请求），只按 trace 去重会把 14 条压成 1 条。

**追踪坐标**：同一笔任务的 1 + N 条消息**共用同一个 `trace_id` 和同一个 `span_id`**（2026-10-28 定）。
N 次上游查询实际发出的 `traceparent` 头也是提交那一个，于是供应商侧的链路视图里
「一次提交 + N 次查询」是同一父 span 下的 N 个子调用，与我们的数仓口径对得上。
要区分第几次查询看 `task_query_seq`，**不要指望 span_id 区分**——它被刻意做成不变量。

> 实现上的一个坑（已修）：`GatewayTraceContext.fromTraceparent` 的语义是
> 「解析入站 trace + 为**本次**请求生成新 span」，用它重建已发生过的那次请求，
> trace_id 对得上但 span_id 每次都变 → 数仓按 `(trace_id, span_id)` 聚不到一组。
> 轮询侧必须走 `GatewayTraceContext.replay(traceparent, requestId)`：沿用第 3 段作为本次 span，
> 并复用提交时的 `request_id`。守门测试 `AsyncTaskCompletionTraceTest`——
> 它跑的是真实 `publish()`，把 `replay` 换回 `fromTraceparent` 必备（且只它会变红，
> 单测 `GatewayTraceContextTest` 全绿，因为它碰不到 `publish`）。

**已知边界**：中间查询的 `usage` / `billing` 恒为 null。若某家上游**对查询接口本身计费**，
这个成本当前不计入（提取器口径是产物口径）——需要时另开事件类型，不在本条消息里硬塞。

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
| `upstream_request_id` | attempt 级**可空** string；上游供应商响应头白名单归一值（request-id 类优先序：`x-request-id` → `request-id` → `apim-request-id` → `x-acs-request-id` → `x-tc-request-id` → `msh-request-id`，见 `docs/上游trace打通-双向对账设计-2026-10-17.md` §4.1）；**成功失败均带**（排障工单 90% 在错误场景，失败 attempt 的 id 最关键）；`attempt_no=0`（未发起调用即被频控/摘除跳过）或上游两类头均无匹配时为 null。**2026-10-19 晚口径：request-id 类全未命中时兜底取 log-id 类（`X-Tt-Logid` / `cf-ray`）并视为 request_id——单字段取其一，不并存两个字段** |
| `counted_for_ejection` | 该次失败是否计入摘除判定（5xx/429 计；4xx 业务错与配置类错不计）——**上游失败率统计必须过滤此列** |

## 4. Kafka 输出设计（含埋点位置）

| 项 | 设计 |
|---|---|
| Broker 依赖 | spring-kafka 已落 knot-gateway pom（§7）；knot-common 保持纯净，事件类不提权供插件用 |
| Topic | `knot-gateway-traffic-event`（配置里写全名，不拼前缀）；契约版本在消息 `schema` 字段里演进，主题名保持稳定；不兼容变更才换主题名（显式改配置） |
| Key | `trace_id`（同请求同 key → 同分区保序，消费者可按 `trace_id` 幂等 upsert）。⚠ 异步任务的 1 + N 条同 key 同分区 → **天然保序**，提交在前、查询在后，数仓不会先看到终态再看到提交 |
| 序列化 | JSON（复用现有 `JsonKit`，cost 为十进制普通字符串、不设小数位上限） |
| 生产端 | **Kafka 原生 async**（robin 2026-10-18 拍板「是 kafka 的 async」）：调用线程 `kafkaTemplate.send()` 提交即返回，不等 ack（同步等 ack 会把 broker RTT 算进 TTFT，是请求链路红线）；投递异步性完全由生产者客户端承担（accumulator 缓冲 + sender 后台线程），**不建自建队列、不起投递线程**；`KafkaPublisher.warmUp(...)` 在启动期预热主题的元数据，消化「首次发送时元数据未就绪」这个最常见的 `max.block.ms` 等待场景 |
| 投递语义 | at-least-once（Kafka 默认）；重复消费由数仓侧幂等：同步按 `trace_id`（单条即终态，upsert 直接），异步按 `(task_code, task_phase, task_query_seq)`（1 + N 条共用一个 trace，只按 trace 去重会压成 1 条） |
| 失败兜底 | 同步异常（`max.block.ms` 超时等，send 调用点抛出）与异步回调失败（broker 拒收/网络抖动，回调里才暴露）**两路统一落死信文件**（`kafka.dlq-path`，默认 `logs/kafka-dlq` **开启**——数据不丢是设计目标）；计数：publisher 的 sent/dropped + writer 的 written/lost；**绝不让消息失败影响请求主链路** |
| 消息体积 | attempts≤~10 + billing.detail≤6 + 扁平字段，整体 < 8KB；上游 responseBody / 原始请求体**绝不入消息** |
| 乱序 | 同步单条，无两层乱序；异步 1 + N 条同 key 同分区，天然保序（提交必在查询之前）。多节点到达序以 `received_at` 为准修正即可（不再需要 left join / 窗口关联） |

### 埋点位置（同步 3 处 + 异步 2 处）

| 消息 | 埋点 | 代码位置 |
|---|---|---|
| traffic（非流式成功） | `applyUsageAccounting` 返回后、`GATEWAY_RESPONSE` 插件前 | `AbstractGatewayRequestTemplate.java`（`trafficTopic` 由模板持有） |
| traffic（失败 / 入口即拒） | catch 块内、`GATEWAY_ERROR` 插件派发前后均可（建议前，保证插件改写也被记录）；入口即拒照发（去掉旧的 `if (routing != null)` 判定） | `AbstractGatewayRequestTemplate.java` |
| traffic（流式） | `ProxyStreamingBody.writeTo` 的 finally（流写完/断连）——usage 与 TTFT 此时才齐 | `ProxyStreamingBody.java` |
| traffic（异步提交，`task_phase=submit`） | 走上面第 1 处（**不再抑制**）；`GatewayRequestHandler.applyUsageAccounting` 在任务行落库后往 context 写 `TaskTrace.submit(taskCode)`，事件构造时读取 | `GatewayRequestHandler.java` → `GatewayTrafficMsgDto.of` |
| traffic（异步每次查询，`task_phase=query`） | 轮询器 `apply()` 的 `RESCHEDULE` 分支发中间事件、`finish()` 发终态事件；两者共用 `AsyncTaskCompletion.publish(...)` 一处装配 | `AsyncTaskPoller.java` → `AsyncTaskCompletion.java` |

> 合并后发布点由原先 4 处（proxy 后发请求层 + 成功发响应层 + catch 发响应层 + 流式 finally 发响应层）收敛为 **3 处终态**（均发同一条 `GatewayTrafficMsgDto`）：非流式成功、异常、流式完成。入口即拒路径同样发一条（routing/hit 为 null），不再依赖请求层独立消息。
>
> **2026-10-28 补：异步任务在第 1 处之外另有 1 处（轮询器），合计 2 个"物理发布点"**。这三处仍然是 `publishTraffic` 的**唯一出口**（`protected final`），轮询器侧不是走它而是自己的 `AsyncTaskCompletion`——因为它拿不到请求线程的 context，只能按任务快照重建。两处装配必须用同一份 `GatewayTrafficMsgDto.of`，否则字段口径会漂。⚠ 曾经为了让异步"只发一条"而在 `publishTraffic` 里挂抑制钩子，**已整体废除**：抑制掉的是真实发生过的上游调用。

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
11. **（Kafka 新增）重复**：at-least-once，按 `trace_id` 幂等 upsert（同步单条即终态，upsert 直接、无需 left join）；异步任务改按 `(task_code, task_phase, task_query_seq)` 去重（2026-10-28）。
12. **（Kafka 新增）丢失**：直推失败 → 死信文件（默认开启）+ written/lost 计数；**不丢数据是设计目标**，唯一丢失窗口是死信文件本身写不进去（磁盘满 / 权限），此时 ERROR 日志即报警（全仓无 webhook/Notifier）。
13. **（Kafka 新增）乱序**：单条消息模型已消除「两层消息到达序 ≠ 发生序」问题；多节点到达仍以 `received_at` 修正，勿假设到达序 = 发生序（但不再需要窗口关联 / left join）。异步 1 + N 条同 key 同分区保序，前提是**不要给异步事件换 key**。
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
| 测试 | `KafkaPublisherTest`(7，publish 单主题/key 语义 + 同步失败死信与 dropped + 异步失败死信 + 未配路径 lost + 预热 + 预热失败不抛 + Msg.nodeId 自动探测；KafkaTemplate 用子类假件，规避 JDK25 Mockito 不可 mock 具体类) + `GatewayTrafficMsgDtoTest`(16，recordsActuallyRequestedModelFromHitTarget / hitFromPoolCarriesPoolCode / failoverHitIsNotFirstCandidate / noHitYieldsNullHit / nullRoutingYieldsNull / nodeIdComesFromMsgConstant / successCarriesUsageAndHitModel / upstreamFailureIsCarriedAsStructuredError / rateLimitRejectionCarriesRejectDetail / storageUnavailableIsNotUpstreamError / traceIsFlattenedIntoMessage / failoverAttemptsCarryTheirOwnUpstreamIds / attemptWithoutUpstreamCallHasNullIds / serializedJsonCarriesUpstreamRequestId / serializedJsonOmitsNullUpstreamRequestId / serializedJsonKeepsCostAsString) |
| 上游 id 捕获（2026-10-19 追加，2026-10-19 晚定为 request-id 优先 + log-id 兜底单字段） | `upstream/support/UpstreamRequestIdResolver`（双段白名单 + snapshot/requestId 归一，禁全量抓取）；`AbstractUpstreamProtocolExecutor.readBuffered`（非流式 `retrieve()`→`exchange()`，错误分支同构抛 `GatewayUpstreamException`）+ 流式快照 + catch 归一；`GatewayUpstreamException` 6 参构造带单 id；`UpstreamStreamResponse` 构造时快照（与连接生命周期解耦）；测试 `UpstreamRequestIdResolverTest`(18) + `AbstractUpstreamProtocolExecutorBufferedTest`(6) + `UpstreamStreamResponseTest`(4)；插件日志经 `UpstreamPluginContext.upstreamRequestId` 输出（三个 payload + 三个插件，`PluginLogPayloadSerializationTest` 加断言）。全量 703 例 BUILD SUCCESS + 必错探针复验，详见 `docs/上游trace打通-双向对账设计-2026-10-17.md` §11 |

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

8. ~~**`attempts[].upstream_request_id` 入消息（协议已定，代码待方案 B）**~~ —— **已落地（2026-10-19，2026-10-19 晚定为 request-id 优先 + log-id 兜底、单字段 `upstream_request_id`）**：`AttemptMsgDto` 含单字段 `upstream_request_id`，埋点经 `CallTraceContext.AttemptDraft`（成功取 `ProxyResult`、失败取 `GatewayUpstreamException`）填充；捕获侧（executor 三处 + 白名单归一 `UpstreamRequestIdResolver`）属设计文档「方案 B」批次，同批落地。**向后兼容追加，schema 仍为 `knot.gateway.traffic/1.0`，不触发主题名变更**。该字段恒可空（未发起调用的 `attempt_no=0` 必为 null），下游消费按 `IS NULL` 区分，禁 truthy。实现与验证细节见 `docs/上游trace打通-双向对账设计-2026-10-17.md` §11。

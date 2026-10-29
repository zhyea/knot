# GatewayTrafficMsgDto 字段协议（消息字段级参考）

> 范围：单条网关流量事件 `GatewayTrafficMsgDto` 的全部字段（含所有嵌套结构），作为数仓消费 / 跨团队对接的字段字典。
> 配套总览文档：`docs/gateway-warehouse-kafka-message-protocol-2026-10-17.md`（模型总览、Kafka 输出设计、埋点位置、聚合口径）。
> 类位置：`knot-gateway/src/main/java/org/chobit/knot/gateway/kafka/msg/GatewayTrafficMsgDto.java`。
> 契约版本：`schema = "knot.gateway.traffic/1.1"`（2026-10-28 由 1.0 升级，异步任务口径改为 1 + N 条事件，详见 §0）。

## 0. 序列化约定（先读这条，否则字段名会对不上）

| 约定 | 取值 | 影响 |
|---|---|---|
| 命名策略 | `@JsonNaming(SnakeCaseStrategy)` | Java 字段 `traceId` → JSON `trace_id`、`receivedAt` → `received_at`、`ttftMs` → `ttft_ms`、`retryTotal` → `retry_total`、`taskQuerySeq` → `task_query_seq` … |
| 空值策略 | `@JsonInclude(NON_NULL)` | 字段为 `null` 时**整段缺席**，不是 `null` 字面量。同步请求不带任何 `task_*` 字段（四个全省略）；`routing`/`hit`/`error`/`usage`/`billing`/`upstream_usage` 在对应路径为空时同样省略 |
| 常量根 | `implements Msg` | 分区键 `trace_id` 来自 `Msg.traceId()`；`node_id` 来自 `Msg.NODE_ID`（按主机名自动探测，无配置项，解析失败退化为 `"unknown"`） |
| 金额形态 | `String` 十进制普通串 | `billing.total_cost` / `billing.detail[].cost` / `price` 一律字符串，**不设小数位上限、禁科学计数法**（Jackson 对 BigDecimal 默认写 `1E-8`，此处钉死） |

⚠ **`0` 是合法值、`null` 是无数据**：`total_tokens=0`、`ttft_ms=0`、`attempt_no=0` 各有含义；统计用 `IS NULL` 区分，前端/数仓禁用 truthy 判断。这一条贯穿所有用例。

---

## 1. 顶层字段（30 个）

| # | JSON 字段 | Java 类型 | 可空 | 取值 / 枚举 | 来源与语义 |
|---|---|---|---|---|---|
| 1 | `schema` | string | 否 | `knot.gateway.traffic/1.1` | 常量 `SCHEMA`；契约版本，**破坏性变更（如消息基数变化）才升版本** |
| 2 | `trace_id` | string | 否 | W3C traceparent 派生 | `GatewayTraceContext`。可能**继承自调用方** traceparent，不保证我方生成；是 Kafka 分区键与数仓关联键 |
| 3 | `span_id` | string | 否 | W3C span | 同上。⚠ 异步 1 + N 条**故意共用同一 span**（replay 保住提交那次），区分查询看 `task_query_seq`，别指望 span 区分 |
| 4 | `request_id` | string | 否 | 32 位小写 hex | 网关**入口生成**的请求编号（`GatewayTraceContext.requestId()`），已以 `X-Request-Id` 透传上游；对账时的「我方 id」 |
| 5 | `node_id` | string | 否 | 主机名 / `unknown` | `Msg.NODE_ID` 按主机名自动探测，无配置项 |
| 6 | `received_at` | string(ISO-8601) | 否 | 本地时钟带偏移 | `GatewayRequestContext.receivedAtMillis()` |
| 7 | `finished_at` | string(ISO-8601) | 否 | 本地时钟带偏移 | `System.currentTimeMillis()`（终态时刻） |
| 8 | `duration_ms` | long | 否 | ≥0 | `finished_at - received_at`（`Math.max(0,...)`） |
| 9 | `protocol` | string | 可空 | `ModelApiProtocolEnum`（CHAT_COMPLETIONS / VIDEO_GENERATIONS …） | 请求协议类型枚举 code |
| 10 | `stream` | boolean | 可空 | true/false | 取请求体 `stream` 字段；取不到为 `null`（注意 `NON_NULL` 下 `false` 仍会写出，`null` 才省略） |
| 11 | `sticky_key` | string | 可空 | — | `X-Knot-Sticky-Key` 头；无则 `null` 省略 |
| 12 | `routing` | object | 可空 | `RoutingSnapshot`（§2） | `ResolvedRouting` + `GatewayRoutingInfo`。**全部业务码 / username，无主键 id、无 secretKey**；入口即拒 / 路由解析失败为 `null` |
| 13 | `status` | string | 否 | 见 §5 | 终态语义：success / rate_limited / quota_exceeded / upstream_error / timeout / gateway_error |
| 14 | `http_status` | int | 否 | 200 / 4xx / 5xx | 成功 200；失败取 `error.http_status` |
| 15 | `ttft_ms` | long | 可空 | ≥0 / 无则 null | 首 token 耗时（毫秒）；无成功首 token 为 `null`（**禁填 0**，0 是合法值） |
| 16 | `hit` | object | 可空 | `HitSnapshot`（§3） | **真正请求的模型**（hit target，候选循环落定后实际调用上游的那个候选）；拒绝 / 全失败路径为 `null` |
| 17 | `attempts` | array | 否 | `AttemptMsgDto[]`（§4） | 候选循环轨迹；有界（通常 ≤6）。同步失败路径为 `List.of()`（空数组仍写出） |
| 18 | `retry_total` | int | 否 | ≥0 | 重试总次数（每个候选内部额外试投之和，首次不计），来自 `CallTraceContext.retryTotal()` |
| 19 | `failover_total` | int | 否 | ≥0 | 顺延次数（成功调用之前失败过的候选数） |
| 20 | `ejected_skipped` | int | 否 | ≥0 | 因**异常摘除**被跳过的候选数（≠ 限流拒绝，统计勿混） |
| 21 | `fail_open` | boolean | 否 | true/false | 候选被摘除一空后是否走了 fail-open 重跑 |
| 22 | `upstream_usage` | object(Map) | 可空 | 上游 `usage` 原文 | `UsageAccounting.rawUsage()` 原样透传（保留厂商键名 `prompt_tokens` / `input_tokens` / `prompt_tokens_details.cached_tokens` …）；上游没报或报空对象时**整段缺席**（用于归一化口径回溯） |
| 23 | `usage` | object | 可空 | `ModelUsage`（§6） | 归一化用量 + `attributes` 计费属性；失败路径为 `null`。下游可凭它自行复算价格 |
| 24 | `billing` | object | 可空 | `BillingViewMsgDto`（§7） | 本网关按规则算出的金额与明细；**命中计费规则才非 null**（免费与没配置计费靠 billing 是否为 null 区分，是两件事） |
| 25 | `task_code` | string | 可空 | 异步任务句柄 | 异步任务专属；**同步请求为 null（整段省略）**。同一笔任务的 1 + N 条事件共用它 |
| 26 | `task_phase` | string | 可空 | `submit` / `query` | 异步阶段；同步为 null。`submit`=create 调用，`query`=查询上游任务状态 |
| 27 | `task_terminal` | boolean | 可空 | true/false | 该阶段是否落终态。`submit` 恒 false；`query` 中拿到产物 / 失败 / 超时那次为 true。**计费金额与失败率统计只看这一个字段** |
| 28 | `task_query_seq` | integer | 可空 | ≥1 / submit 为 null | 第几次查询，从 1 开始；`submit` 为 **null（不是 0——0 会被误读为「查了 0 次」）**。异步任务延迟分布唯一来源 |
| 29 | `error` | object | 可空 | `ErrorMsgDto`（§8） | 统一错误结构；**成功为 null**。上游异常与网关异常同形状，靠 `type`/`stage`/`upstream` 区分 |

> 字段 25–28（`task_*`）由内部 `TaskTrace`（context 属性 `asyncTaskTrace`）展平而来，同步请求四个全为 null → `NON_NULL` 整段省略。**升 1.1 的根因**：字段层面纯追加，但消息基数从「1 请求 1 条」变「1 + N 条」，按 `count(*)` 当请求数写的消费逻辑会静默放大。

---

## 2. `routing`（RoutingSnapshot，14 字段）

路由维度快照，供多维统计直接切片。**全部为业务码 / username，无主键 id**。入口即拒时为 `null`。

| JSON 字段 | 类型 | 含义 |
|---|---|---|
| `rule_code` | string | 命中路由规则业务码 |
| `rule_name` | string | 规则名 |
| `app_scenario` | string | 应用场景（如 `chat`） |
| `consumer_code` | string | 消费方（调用方应用）业务码 |
| `consumer_name` | string | 消费方名 |
| `app_code` | string | 应用业务码 |
| `app_name` | string | 应用名 |
| `dept_code` | string | 部门业务码 |
| `dept_name` | string | 部门名 |
| `consumer_username` | string | 消费方实际使用者 username |
| `app_owner_username` | string | 应用归属人 username |
| `return_usage_detail` | boolean | 该消费方是否开启用量明细回传 |
| `target_type` | string | 规则维度主目标类型：`MODEL` / `MODEL_POOL`（按首个候选是否带来源池 code 还原；池展开后候选 targetType 被路由统一改写为 MODEL） |
| `candidate_count` | int | 候选数量；>1 才有 failover / 负载均衡统计意义 |

---

## 3. `hit`（HitSnapshot，5 字段）

真正请求的模型（hit target）。拒绝 / 全失败路径无 hit → `null`（尝试过哪些模型看 `attempts[]`）。

| JSON 字段 | 类型 | 含义 |
|---|---|---|
| `model_code` | string | KB 侧模型业务码（也是响应头 `X-Knot-Model` 的值） |
| `upstream_model_code` | string | 实际写入上游请求体的模型标识（可能与前者不同） |
| `provider_account_code` | string | 供应商账户业务码 |
| `pool_code` | string | 来源模型池业务码；规则直挂模型为 `null`（池内命中时归因到池） |
| `upstream_url` | string | 本次上游调用的完整请求地址（scheme+host+path）；排障看打到哪个端点；未发起调用（频控拒绝）为 `null` |

---

## 4. `attempts[]`（AttemptMsgDto，17 字段 + outcome 枚举 + upstream 内嵌）

一次上游调用尝试。序列按候选循环登记顺序，`seq` 从 1 递增。

| JSON 字段 | 类型 | 含义 |
|---|---|---|
| `seq` | int | 序号，从 1 开始 |
| `target_code` | string | 该候选目标业务码 |
| `target_type` | string | 目标类型（`MODEL` / `MODEL_POOL`，来自 `RoutingRuleTargetDto.targetType()`） |
| `upstream_model_code` | string | 实际发往上游的模型标识（网关覆盖请求体 `model` 后的值） |
| `priority` | int | 候选优先级 |
| `primary` | boolean | 是否主目标（`RoutingRuleTargetDto.primary()`） |
| `outcome` | string | 结局枚举（见下） |
| `skipped_by_ejection` | boolean | 是否因异常摘除被跳过（压根没发请求） |
| `probe_request` | boolean | 是否为探测请求 |
| `attempts` | int | 本候选实际试投次数（含首次） |
| `max_attempts` | int | 本候选最大试投次数（≥1） |
| `traffic_rejected` | boolean | 该目标自身是否被频控 / 额度拒绝 |
| `reject_reason` | string | 拒绝原因码（如 `RATE_LIMIT_TPM`）；未拒绝为 null |
| `upstream` | object | attempt 级上游异常三件套（`UpstreamErrorMsgDto`，§4.1）；成功为 `null` |
| `upstream_request_id` | string | 供应商侧请求 id（响应头白名单归一）；**成功失败均带**（排障关键）；`attempt_no=0`（未发起调用即被拒）或上游无匹配头为 `null`。上游若回显我们的 `X-Request-Id` 会被排除，不会写进这里 |
| `counted_for_ejection` | boolean | 该次失败是否计入摘除判定（5xx/429 计；4xx 业务错与配置类错误不计——**上游失败率统计必须过滤此列**） |
| `upstream_url` | string | 本次上游调用完整地址；未发起调用为 `null` |

**`outcome` 枚举（`AttemptOutcomeMsg`）**：

| code | 含义 |
|---|---|
| `success` | 调用成功 |
| `upstream_failure` | 以上游异常告终（重试耗尽或不可重试） |
| `skipped` | 被异常摘除跳过，没发请求 |
| `traffic_rejected` | 目标自身被频控 / 额度拒绝 |

`attempt_no=0`（即 `attempts=0` 或 `max_attempts` 未被触发）表示未发起调用即被拒；其 `upstream_request_id` / `upstream_url` 为 `null`。

### 4.1 `attempts[].upstream`（UpstreamErrorMsgDto，3 字段）

| JSON 字段 | 类型 | 含义 |
|---|---|---|
| `http_status` | integer | 上游响应状态码；未发出请求或拿不到为 `null` |
| `error_code` | string | 网关侧业务码（`ProxyErrorCodeEnum` 8 值） |
| `retry_after_ms` | long | 上游 `Retry-After` 换算的毫秒数；无则 `null` |

---

## 5. `status` 终态枚举（由 error.type 映射）

| status | 触发 error.type |
|---|---|
| `success` | 无 error |
| `rate_limited` | `rate_limit_error` |
| `quota_exceeded` | `quota_error` |
| `upstream_error` | `upstream_error` |
| `timeout` | `timeout_error` |
| `gateway_error` | 其他（默认） |

---

## 6. `usage`（ModelUsage，归一化用量 + 计费属性）

单向派生链中间层：`upstream_usage`（原文）→ `usage`（归一化 + 本次请求计费属性）→ `billing`（价格）。**不含价格**，价格只在 `billing`。

| JSON 字段 | 类型 | 含义 |
|---|---|---|
| `input` | object | 输入侧用量（§6.1） |
| `output` | object | 输出侧用量（§6.2） |
| `attributes` | object | 计费属性 / 分档键（§6.3）；**全部为空时整段省略** |
| `total_tokens` | long | 总 token（优先沿用上游上报值，否则两侧相加补齐）；`NON_DEFAULT` 下 0 省略 |

### 6.1 `usage.input`（Input）

| JSON 字段 | 类型 | 含义 |
|---|---|---|
| `tokens` | object | 输入 token 明细（§6.1.1） |
| `image_count` | long | 输入图片张数（图生视频首帧图 / 参考图） |
| `audio_seconds` | long | 输入音频秒数 |
| `video_seconds` | long | 输入视频秒数（参考视频 / 视频编辑输入时长） |

**6.1.1 `usage.input.tokens`（InputTokens，8 字段）**：`text` / `image` / `video` / `cache_read` / `cache_write` / `cache_write_5m` / `cache_write_1h` / `unclassified`。
约束：`cache_write` 与 `cache_write_5m`/`cache_write_1h` 互斥（任一明细 >0 时 `cache_write` 强制归 0）；`text + image + video + cache_read + cache_write(或5m+1h) + unclassified = 该侧 token 总量`。

### 6.2 `usage.output`（Output）

| JSON 字段 | 类型 | 含义 |
|---|---|---|
| `tokens` | object | 输出 token 明细（§6.2.1） |
| `image_count` | long | 输出图片张数 |
| `audio_seconds` | long | 输出音频秒数 |
| `video_seconds` | long | 输出视频秒数 |
| `video_count` | long | 输出视频条数（按条计价，如 Veo） |

**6.2.1 `usage.output.tokens`（OutputTokens，5 字段）**：`text` / `image` / `video` / `reasoning`（思考 token）/ `unclassified`。

> ⚠ 视频响应的 `data` 数组装的是视频，**不会**回填进 `output.image_count`；视频条数只出现在 `output.video_count`。视频类 token 可为 0 但张数 / 秒数非 0，统计勿只看 token。

### 6.3 `usage.attributes`（Attributes，9 字段，计费分档键）

值一律**原样保留厂商写法**（`768P` / `16:9` / `wan-pro` / `MOTION_CONTROL`），键空间归一是下游自己的事；空串归一成 `null`。**来源是请求体而非上游 usage**（上游从不回传分辨率）。

| JSON 字段 | 类型 | 含义 |
|---|---|---|
| `resolution` | string | 分辨率档位名（`720P`/`1080P`/`4K`…） |
| `aspect_ratio` | string | 画幅（`16:9`/`9:16`/`1:1`…） |
| `mode` | string | 模式 / 功能档（`wan-std`/`MOTION_CONTROL`…） |
| `width` | long | 图片宽（像素） |
| `height` | long | 图片高（像素） |
| `pixels` | long | 图片总像素（宽 × 高） |
| `sound` | boolean | 是否有声输出 |
| `voice_specified` | boolean | 是否指定音色 |
| `reference_video` | boolean | 是否有参考 / 输入视频 |

**缺席即默认**：读不到 `attributes.resolution` 等价于「未按分辨率分档」，与网关取价回落默认档同口径。

---

## 7. `billing`（BillingViewMsgDto，8 字段 + detail 内嵌）

本网关按规则算出的金额与明细（对账基准）。**不含规则内容**（无 `config_json`），下游复算靠 `usage` 的量 + `attributes` 分档键。

| JSON 字段 | 类型 | 含义 |
|---|---|---|
| `rule_code` | string | 命中计费规则业务码（归因用） |
| `rule_version` | string | 规则版本（与 `kb_billing_rules.version_code` 关联；规则内容不下发） |
| `billing_mode` | string | 计费模式：`TOKEN`（命中细化 token 明细时）/ 其余场景 `null` |
| `pricing_plan` | string | 定价方案 |
| `total_tokens` | long | 计费总 token |
| `total_cost` | string | 总价（十进制普通字符串，8 位小定标） |
| `currency` | string | 币种（如 `CNY`） |
| `detail` | array | 逐类用量金额行（`BillingDetailMsgDto`，§7.1）；免费 / 未命中规则时整段为 `null` |

### 7.1 `billing.detail[]`（BillingDetailMsgDto，4 字段）

| JSON 字段 | 类型 | 含义 |
|---|---|---|
| `type` | string | 计费量类型（如 `uncachedInput` / `cachedRead` / `output` …） |
| `tokens` | long | 该类 token 数 |
| `cost` | string | 该类金额（十进制普通字符串） |
| `price` | string | 折算到百万单位的单价（字符串） |

---

## 8. `error`（ErrorMsgDto，7 字段 + detail / upstream 内嵌）

统一错误结构，上游异常与网关异常同形状，靠 `type` / `stage` / `upstream` 区分。成功路径为 `null`。

| JSON 字段 | 类型 | 含义 |
|---|---|---|
| `type` | string | 错误类型（`auth_error` / `invalid_request_error` / `moderation_error` / `rate_limit_error` / `quota_error` / `upstream_error` / `storage_unavailable` …） |
| `code` | string | 业务码（如 `RATE_LIMIT_EXCEEDED` / `UPSTREAM_ERROR`） |
| `http_status` | int | 终态 HTTP 状态码 |
| `stage` | string | 发生阶段：`header_validate` / `request_parse` / `routing_resolve` / `routing_traffic_check` / `target_traffic_check` / `upstream_call` / `plugin_moderation` / `storage` |
| `message` | string | 错误描述，**截断 ≤512 字符** |
| `detail` | object | 仅 429（限流 / 限额）携带（`TrafficRejectMsgDto`，§8.1）；其余为 `null` |
| `upstream` | object | 仅 502（上游失败）携带（`UpstreamErrorMsgDto`，§4.1）；其余为 `null` |

### 8.1 `error.detail`（TrafficRejectMsgDto，9 字段，429 专有）

告诉数仓「谁被限了、限的哪个维度、上限与已用多少、何时恢复」。成本维度数值已从纳元缩放还原成金额 + `unit` 为币种。

| JSON 字段 | 类型 | 含义 |
|---|---|---|
| `resource_type` | string | 受限资源类型（`MODEL` / `CONSUMER` …） |
| `resource_code` | string | 受限资源业务码 |
| `reason` | string | 原因（`RATE_LIMIT_RPM` / `RATE_LIMIT_TPM` / `QUOTA_TOKENS` / `QUOTA_COST` …） |
| `dimension` | string | 维度（`TPM` / `RPM` / `TOKENS` / `COST` …） |
| `unit` | string | 单位（`tokens/minute` / 币种 …） |
| `limit` | decimal | 上限值 |
| `used` | decimal | 已用值 |
| `reset_at` | string | 恢复时间（ISO） |
| `reset_after_seconds` | long | 距恢复秒数 |

---

## 9. 数仓聚合口径速查（异步 1 + N 条必带，否则每条都算错）

| 指标 | 过滤条件 |
|---|---|
| 客户端请求数 | `task_phase is null or task_phase = 'submit'` |
| 上游调用数 | 不加条件（同步 1 条 = 1 次；异步 1 + N 条 = 1 + N 次真实调用） |
| 计费金额 | `task_phase is null or task_terminal = true`（同步不带 `task_phase`，天然全计） |
| 失败率 | **必须** `task_phase is null or task_terminal = true`（否则「重试 5 次后成功」算成 5 次失败） |
| 幂等键 | 同步按 `trace_id`；异步按 `(task_code, task_phase, task_query_seq)`（1 + N 条共用 trace，只按 trace 去重会压成 1 条） |

---

## 10. 入口即拒 / 失败表达速查

- **入口即拒**（鉴权 / Rule 头校验失败）：`routing`=null、`hit`=null、`upstream_usage`=null、`usage`=null、`billing`=null、`error.stage`=`header_validate`、`http_status`=401/400。
- **全失败路径**：`hit`=null、`usage`=null、`billing`=null，但 `attempts[]` 保留每次尝试轨迹、`error` 取终态异常。
- **异步中间查询失败**：`task_phase=query`、`task_terminal=false`、带 `error`、`usage`/`billing` 仍为 null（聚合失败率必须过滤 `task_terminal`）。

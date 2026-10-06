# 路由规则 · 模型失败重试方案设计

> 状态：**已实现并验证**（2026-10-05 设计 → 2026-10-06 落地 → 2026-10-06 调整双模式与新默认）
> 关联代码：`knot-server/knot-gateway/.../runtime/GatewayRequestHandler.java`（`proxyWithFailover` / `invokeWithRetry`）
> 设计日期：2026-10-05
>
> **调整记录**（2026-10-06）
> - 默认值收紧：maxAttempts `3 → 2`、multiplier `2.0 → 1.0`、jitter `true → false`。
> - multiplier 由整数改为**支持 1 位小数浮点**（归一化四舍五入）。
> - 新增**配置模式**：`simple`（默认，仅暴露尝试次数+判定方式+状态码，其余走内置默认）/ `professional`（全字段可调）。后端 `RetryPolicy` 增 `mode` 字段 + `normalizeConfigMode`；前端 `RetryPolicySection` 加模式切换并显隐控件。
> - 清理死字段 `fallback_rule_id`（设计误标为已实现，实际无消费者），含 DROP 迁移。
> - `retryOn` 支持**通配符 `Nxx`**（`4xx`/`5xx`），默认集合改为 `["4xx","5xx"]`。
>   **⚠ 契约变更：4xx 由「默认不重试」翻转为「默认重试」**（robin 拍板按字面 `4xx+5xx`），
>   故 401/403/404 等也会原地重投；如需收紧把 `retryOn` 显式配成 `["5xx","429"]` 即可。
>   后端 `matchesStatus` 改为「精确码 OR 通配符」双判定、`normalizeRetryOn` 保留通配符 token；
>   前端 `retryPolicy.ts` 归一化放行通配符、`RetryPolicySection` 预设加入 `4xx`/`5xx`。
>
> **验证结论**（真实 exit code）
> | 门禁 | 结果 |
> |---|---|
> | `mvn -f knot-server/pom.xml package` | BUILD SUCCESS，exit 0，205 项测试 0 失败 |
> | 新增单测 | RetryPolicy 9 + RetryableClassifier 7 + BackoffCalculator 7 |
> | `npm run type-check` | exit 0 |
> | `npm run build` | exit 0（构建后已还原 `components.d.ts`） |
> | 真启 admin jar 冒烟 | exit 0：list 命中 → PUT 写入 → 回读持久化 → 复原；DB 已还原为 NULL |
>
> **实现偏差（相对设计稿）**：可观测性只落地了 WARN 日志，§10 的响应头
> `X-Knot-Retry-Attempts` 未实现（需把尝试次数透出到响应层，收益低于复杂度，暂缓）。

## 1. 背景与现状

现有 `GatewayRequestHandler.proxyWithFailover` 已实现**跨候选 failover**：遍历 `routing.candidateModels()`，某候选抛 `GatewayUpstreamException` 即跳到下一候选。

**缺口**：每个候选在 failover 之前**只试一次**，缺少「同一目标连续失败后在原地重投」的能力。结果是上游偶发 5xx/超时会被立即 failover，既浪费候选、又可能在所有候选都抖动的瞬间整体失败。

本次设计为 failover 之前补一层**同目标失败重试（retry）**。

## 2. 设计目标与原则

- 重试与 failover 正交：重试在最内层（同一 `upstream_model`），耗尽才 failover。
- 默认开启、参数保守，规则可显式关闭或调参。
- 不重复扣减频控、不重复记账用量。
- 不引入冗余列：规则级策略用单 `retry_policy` JSON 列承载（类比 `kb_billing_rules.config_json`）。
- 流式请求仅在「首个字节前」可重试，不破坏已建立的数据流。

## 3. 降级模型（由内到外：retry → failover）

| 层 | 行为 | 现状 |
|---|---|---|
| **Retry** | 同一目标失败后原地重投 N 次 + 退避 | ❌ 本次新增 |
| **Failover** | Retry 耗尽仍失败 → 切规则内下一候选 | ✅ 已有 |

## 4. 重试执行流程

在 `proxyWithFailover` 的「单候选」内层包重试闭包。**频控/额度 `checkTarget` 对每个候选只做一次**（避免 RPM 预扣随重试次数累加）；重试发生在该次检查通过后、到 `proxyClient.proxy(...)` 返回的闭环内。

```
for (candidate : routing.candidateModels()) {
    decision = trafficGuard.checkTarget(candidate, ctx)   // 每候选仅一次
    if (!decision.allowed()) { recordFirstRejection(decision); continue; }
    hasAllowedTarget = true

    ProxyResult result = retryPolicy.execute(candidate, () -> {
        requestBody.put(MODEL, candidate.upstreamModelCode())
        return proxyClient.proxy(requestBody, contentType, candidate, protocol, traceparent)
    })
    // RetryPolicy.execute 内部：
    //   首次调用失败且「不可重试」→ 直接抛出
    //   失败且「可重试」且未达 maxAttempts → 退避后重投
    //   全部失败 → 抛出最后一个 GatewayUpstreamException

    trafficGuard.record(routing, candidate, usageOf(result), ctx)
    return result
}
```

`RetryPolicy.execute` 只捕获 `GatewayUpstreamException`；其他 `RuntimeException`（代码缺陷）直接上抛，不重试。

## 5. 可重试判定口径（`RetryableClassifier`）

按上游 HTTP 状态码 + 错误码分类：

- ✅ 默认可重试：**全部 4xx 与 5xx**（默认集合即用通配符 `["4xx","5xx"]`）、网络层异常（连接超时、读超时、连接重置、EOF）。
- ❌ 默认不可重试：1xx/2xx/3xx（如重定向 301/302/304）、业务错误（内容审核等）、配置类（`MODEL_NOT_FOUND` / `PROVIDER_NOT_FOUND` / `API_PROTOCOL_NOT_CONFIGURED`）。
- 可配置：`retryOn` 支持**通配符 `Nxx`**（如 `4xx`/`5xx` 匹配某一类全部状态码）与精确码（`500`/`429`）混用；allowlist（命中才重试）/ denylist（命中才不重试，`retryOnMode=denylist`）覆盖默认。
- 无 HTTP 状态时按错误码兜底：网络/超时类可重试，配置类不可重试。

判定优先级：非 `GatewayUpstreamException` → 不重试；`httpStatus` 为空 → 按错误码分；`httpStatus` 有值 → 按 `retryOn` 集合判定。

## 6. 退避策略（`BackoffCalculator`）

- 默认**指数退避 + 抖动**：`delay = min(backoffMaxMs, backoffBaseMs * multiplier^(attempt-1))`，开启 jitter 时 `delay = random(0, delay)`。
- 若 `respectRetryAfter=true` 且上游 `429/503` 返回 `Retry-After`，退避取 `max(delay, retryAfterMs)`。
- 同步 `Thread.sleep`（网关请求线程），**设总预算上限**（默认 cap 5s）避免超出客户端耐心。
- 流式场景重试只发生在首字节前，线程仍为请求线程，阻塞可接受。

## 7. 流式边界（硬约束）

- 流式请求仅在「首个字节前」失败（连接/读超时、非 2xx）才走重试/failover；一旦 `ProxyResult` 带 `streamResponse` 返回即向客户端提交，无法再切换或重试。
- **流式中途断流不可重试**（客户端已收部分数据），记为已知限制，由 `ProxyStreamingBody` 自然结束错误流。

## 8. 与频控/计费交互

- **RPM 预扣**：`checkTarget` 每候选一次，不随重试累加 → 重试不打爆限流。
- **用量记账**：仅成功那次 `trafficGuard.record`，失败重试不写用量（与现有 failover 一致）。

## 9. 配置 Schema（规则级 `retry_policy` JSON 列）

`kb_routing_rules` 新增列 `retry_policy`（`TEXT/JSON`）：

```json
{
  "enabled": true,
  "mode": "simple",
  "maxAttempts": 2,
  "retryOnMode": "allowlist",
  "retryOn": ["4xx", "5xx"],
  "backoffBaseMs": 200,
  "backoffMaxMs": 5000,
  "multiplier": 1.0,
  "jitter": false,
  "respectRetryAfter": true
}
```

> **双模式**：`mode=simple`（默认）只暴露「尝试次数 + 判定方式 + 状态码」三项，其余字段在持久化与解析时退回内置默认（`backoffBaseMs=200`、`backoffMaxMs=5000`、`multiplier=1.0`、`jitter=false`、`respectRetryAfter=true`）；`mode=professional` 放开全部字段。前端 `RetryPolicySection` 按此显隐控件，提交恒送归一化结果。

| 字段 | 含义 | 默认 | 模式 |
|---|---|---|---|
| `enabled` | 是否启用重试 | `true` | 全 |
| `mode` | `simple` / `professional` | `simple` | 全 |
| `maxAttempts` | 总尝试次数（含首次） | `2` | 全（simple 仅此一项可调） |
| `retryOnMode` | `allowlist` / `denylist` | `allowlist` | 全（simple 仅此一项可调） |
| `retryOn` | 状态集合（支持 `Nxx` 通配符） | `["4xx","5xx"]` | 全（simple 仅此一项可调） |
| `backoffBaseMs` | 首次退避基数 | `200` | 仅 professional |
| `backoffMaxMs` | 单次退避上限 / 总预算上限 | `5000` | 仅 professional |
| `multiplier` | 指数基数（**1 位小数浮点**） | `1.0` | 仅 professional |
| `jitter` | 随机抖动 | `false` | 仅 professional |
| `respectRetryAfter` | 服从上游 Retry-After | `true` | 仅 professional |

约定：`maxAttempts<=1` 视为关闭重试；`retry_policy` 为空/解析失败 → 走默认启用策略（simple + 上述默认）。

> **`retryOn` 通配符**：每项可以是精确状态码（`500`、`429`）或通配符 `Nxx`（`4xx` 匹配 400-499、`5xx` 匹配 500-599）。
> 归一化时精确码限定 100-599、通配符前缀限定 1-5，非法项直接丢弃，全空退回默认 `["4xx","5xx"]`。
> 两种写法可混用（如 `["5xx","429"]` = 全部服务端错误 + 上游限流）。

## 10. 可观测性

- `GatewayTraceContext` 记录：最终命中目标 `targetCode`、各候选尝试次数 `attempts`、末次失败原因。
- 可选透出响应头 `X-Knot-Retry-Attempts`（不进对外 body，避免污染 `knot_extend`）。

## 11. 默认值

默认开启 + 简单模式：`maxAttempts=2`、base 200ms、cap 5s、恒定退避（倍数 `1.0`，即不放大）、不抖动、重试 4xx 与 5xx（通配符）、服从 Retry-After。规则可显式 `enabled=false` 关闭；切到 professional 模式可微调 base/cap/倍数(1 位小数)/抖动/Retry-After。

## 12. 落地影响面（文件级）

**DB**
- `knot-admin/src/main/resources/db/schema.sql`：`kb_routing_rules` 加 `retry_policy` 列。
- `docs/database/migration/2026-10-05-routing-retry.sql`：幂等 `ADD COLUMN IF NOT EXISTS` + 默认 `DEFAULT`。

**后端（knot-server）**
- `knot-dal/.../entity/RoutingRuleEntity.java`：加 `retryPolicy`（String/JSON）。
- `knot-service/.../dto/routing/RoutingRuleDto.java` + `vo/routing/RoutingRule.java`：加 `retryPolicy` 字段。
- `knot-service/.../converter/RoutingRuleConverter.java`：字段映射（JSON ↔ 对象）。
- `knot-common` 或 `knot-gateway` 新增三个小类：
  - `RetryPolicy`（解析 `retry_policy` JSON、默认值、`parse` 容错）。
  - `RetryableClassifier`（§5 判定）。
  - `BackoffCalculator`（§6 退避）。
- `knot-gateway/.../model/ResolvedRouting.java`：record 增加 `RetryPolicy retryPolicy()`。
- `knot-gateway/.../routing/RoutingResolver.java:74`：构造 `ResolvedRouting` 时塞入 `RetryPolicy.parse(rule.getRetryPolicy())`。
- `knot-gateway/.../runtime/GatewayRequestHandler.java:74`：将候选调用包进 `retryPolicy.execute(...)`。
- `knot-gateway/.../runtime/GatewayTraceContext.java`：补重试计数/命中字段。

**前端（knot-front）**
- 路由规则编辑抽屉：新增「失败重试」卡片（开关 + 次数 + 退避基数/上限/倍数 + 重试状态集）。
- `utils/` 校验：次数≥1、退避参数非负、状态集格式。
- 类型定义：同步 `RetryPolicy` 接口。

**测试**
- 单测：`RetryableClassifier`、`BackoffCalculator`、`RetryPolicy.parse` 容错。
- 网关集成测试：某候选连续 5xx → 原地重试 N 次 → 仍失败才 failover 到下一候选。
- 4xx 不重试直接 failover；流式首字节前失败可重试、中途断流不可重试。

## 13. 验证门禁

1. 后端：`mvn -f knot-server/pom.xml package`（含测试）。
2. 前端：`npm run type-check` + `npm run build`（build 后 `git checkout -- components.d.ts`）。
3. 真启 jar 冒烟：本地 node 回声上游制造连续 5xx，观察「同目标重试 N 次 → failover」日志与 `X-Knot-Retry-Attempts` 头；冒烟后清理。
4. 全量 grep 残留确认零残留；未经显式批准不 `git commit`。

## 14. 风险与限制

- 重试为**同步阻塞**请求线程，受 `backoffMaxMs` 预算约束；超大退避需求另议异步化。
- 流式中途断流不可重试（固有限制）。
- 重试不重新走 RPM 预扣，极端高频抖动下可能「多实际请求、少限流计数」——视为可接受取舍。
- `ResolvedRouting` 构造点（含 `RoutingRuleStreamControllerTest` 等测试）需同步补 `retryPolicy` 参数。

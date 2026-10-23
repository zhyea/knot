# 网关 request_id 生成与上游透传

> 日期：2026-10-23 ｜ 状态：**已落地**（gateway 294 例全绿）｜ 关联：`docs/上游trace打通-双向对账设计-2026-10-17.md`

## 1. 目标

请求进入网关时**生成 request_id**，随请求头透传给上游，使供应商侧日志能检索到我方编号——
提工单时我方给出 id、对方系统能查到，构成双向对账的另一半。

与既有能力的差别：既有只做了**回站方向**（采集上游返回的 id）；本次补上**出站方向**。

## 2. ⭐ 三个 id 的语义边界（不可互相替代）

| id | 包络 | 谁生成 | 检索方向 |
|---|---|---|---|
| `trace_id` | traceparent（W3C） | **可能来自调用方**（入站 traceparent） | 我方内部日志/数仓关联 |
| `request_id` | 请求头 `X-Request-Id` + Kafka `request_id` | **网关生成**，32 位小写 hex | 拿它去**供应商**日志查 |
| `upstream_request_id` | Kafka `attempts[].upstream_request_id` | **上游返回** | 拿它回**我方数仓**查 |

- `request_id` 不是 W3C 字段，不进 traceparent；也**不复用 traceId**（入站 traceId 是别人的链路 id）。
- 客户端传入的任何 request-id 类头**一律忽略**：网关是唯一权威，防伪造串号。

## 3. 实现落点

| 位置 | 行为 |
|---|---|
| `knot-common/.../constants/GatewayHeaders.java` | 新增 `X_REQUEST_ID = "X-Request-Id"`（含与 upstream id 的区分说明） |
| `knot-gateway/.../runtime/GatewayTraceContext.java` | 新增 `requestId` 字段（构造即生成，`newRequestId()` = 128 位随机 → 32 位 hex） |
| `GatewayTraceFilter` | 未改代码：request_id 生成点即 `GatewayTraceContext` 构造点，随同一份 Reactor Context 下发 |
| `runtime/GatewayRequestContext.java` | 新增 `requestId()` 委托 traceContext |
| `knot-adapter/.../upstream/UpstreamRequestContext.java` | record 末尾新增 `requestId` 组件（便利构造器保留，缺省 null） |
| `upstream/UpstreamProxyClient.java` | `proxy(...)` / `proxyModel(...)` 增加 `requestId` 入参并写入上下文 |
| `runtime/GatewayRequestHandler.java` | 候选循环内取 `context.requestId()`，**重试与 failover 复用同一个值**（一次客户端请求 = 一个我方编号） |
| `upstream/protocol/AbstractUpstreamProtocolExecutor.java` | `requestSpec(...)` 内非空即加 `X-Request-Id`（multipart / 签名路径同样生效；SigV4 只签 host/x-amz-date/token，不受影响） |
| `kafka/msg/GatewayTrafficMsgDto.java` | 顶层新增 `requestId` → JSON `request_id`（`@JsonNaming` 自动 snake_case） |

## 4. ⭐ 回显污染防护（本次最容易踩的坑）

部分上游 / 反代（Azure APIM、各类网关中间件）会把 `X-Request-Id` **原样回显**到响应头。
若不处理，`UpstreamRequestIdResolver` 会把我方 id 当成 `upstream_request_id`：
- 数仓里两列变成同一个值，双向对账失效；
- 提工单时给对方的是**我们自己的 id**，对方查不到。

做法（`upstream/support/UpstreamRequestIdResolver.java`，两个入口 `requestId` / `snapshot` 均加 `ownRequestId` 参数）：
- 命中值等于 `ownRequestId`（忽略大小写与首尾空白，上游可能规范化大写）→ **视为未命中**；
- **跳过而非直接返回 null**：继续按优先序看下一个头，不连带丢掉真实存在的上游 id
  （如同时回了 `x-request-id`（回显）与 `x-tc-request-id`（真实））。

## 5. 门禁证据

命令（隔离验证：adapter 的**测试**被并发会话改坏，先 install 产物再单跑 gateway，不带 `-am`）：

```bash
# 1) 装依赖产物（跳过测试编译）
mvn -f knot-server/pom.xml -pl knot-gateway -am install -Dmaven.test.skip=true   # BUILD SUCCESS
# 2) 单跑网关模块
mvn -f knot-server/pom.xml -pl knot-gateway test                                 # exit=0
```

结果：**Tests run: 294, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS**（`audit/reqid-gateway-test.log`）

本次新增/扩展用例：

| 测试类 | 结果 | 覆盖 |
|---|---|---|
| `GatewayTraceContextTest` | 10 例 | request_id 为 32 位小写 hex / 不复用入站 traceId / 每次不同 / 不进 traceparent |
| `UpstreamRequestIdResolverTest` | 23 例 | 回显值被排除、排除后继续找下一个头、大小写不敏感、snapshot 同步排除、own=null 保持旧行为 |
| `AbstractUpstreamProtocolExecutorBufferedTest` | 7 例 | 非流式路径回显值不进 `upstream_request_id` |
| `UpstreamWebClientEndToEndTest` | 6 例 | **真实 reactor-netty 上游**收到 `X-Request-Id`（逐字符比对）+ `/echo` 回显场景不进 upstream id |
| `GatewayTrafficMsgDtoTest` | 17 例 | 顶层 `request_id` 与 attempt 级 `upstream_request_id` 同时序列化且不相等 |

## 6. 未验证 / 风险

- ⚠ **未打真实供应商**：头是否被各家接受/记录未实测。无标准的厂商（如只认自家头）可能忽略本头，
  不影响成功率（未知请求头按 HTTP 语义应被忽略），但检索价值归零。
- ⚠ **knot-adapter 模块测试未跑**：该模块测试目录当前被并发会话改到不可编译
  （`UpstreamAuthApplierDefaultTest:91` 调 `HttpHeaders.isReadOnly()`，该方法不存在）。
  本次对 adapter 的改动仅为 record 增字段（9 参便利构造器保留），main 编译通过；但模块测试结论缺失。
  **并发会话收口后需补跑全量确认。**
- ⚠ 多会话抢改同一热点文件（`AbstractUpstreamProtocolExecutor` / E2E 测试）。本次结论基于
  2026-10-23 15:08 的快照；**合并前必须重跑**。

## 7. follow-up

1. 并发会话的 adapter 测试修好后，重跑 `mvn -f knot-server/pom.xml test` 全量。
2. 真启冒烟时抓一次上游请求头，确认 `X-Request-Id` 实际发出（含 multipart 与 AWS 签名路径）。
3. 数仓侧建 `request_id` 列后，做一次「我方 request_id → 供应商查」的实操验证（选一家支持的厂商，如 OpenAI 兼容系）。
4. 若后续接分布式追踪，把 `request_id` 也写进日志 MDC（当前只在消息与上游头；日志靠 traceId 关联）。

# P6 网关并发 / IO 改造方案（清单）

> 关联评审：`docs/性能与安全评审-2026-10-19.md` P6 / P7 / P8。
> 配套批次状态：P10 / P4 / P2 本批次已落地并全量门禁通过（706 例 0 失败）；P8 用户排除；P5 / P7 不改。

## 1. 背景与根因

网关是**纯同步 servlet** 栈：

- 控制器同步返回 `Object`（`GatewayControllerSupport.handleRequest`）→ 请求线程 == Tomcat worker 线程，**全程占住**。
- 失败重试在 `GatewayRequestHandler.invokeWithRetry` 内通过 `sleepQuietly` **同步 `Thread.sleep`** 退避（`:352` 调 `:363-373`），退避期间 worker 被纯睡眠占用。
- 上游客户端（`UpstreamRestClientConfiguration`）只设了 connect 5s / socket(read) 120s / pool-wait 5s，**没有总响应超时** → 慢速滴流上游（1B/100s）可无限续命，把 worker 一直占着。
- 乘积无兜底：同步阻塞 × 睡眠退避 × 无总超时 → Tomcat 默认 200 个 worker 可被少量坏上游吃干（P6 展开分析已实证：`RetryPolicy` 单候选最坏 9×60s=540s 纯睡眠）。

**核心矛盾**：在纯同步栈里，"退避期间真正释放 worker 线程" 只有一条路——把代理链路整体改 async-servlet。这是大改动（回归流式 + 流量事件时序）；而默认 `maxAttempts=2` 下退避纯睡眠的实测收益边际。所以按"数据不丢失 > 性能"与"重大调整前先对齐"，把 P6 拆成两组。

## 2. 风险分级清单

### A 组 —— 低风险，推荐先落（不触动流式 / 流量事件时序）

- [x] **A1 启用 HC5 总响应超时（setResponseTimeout）**
  给 `UpstreamRestClientConfiguration` 的 Apache HttpClient 5 `RequestConfig` 加**总响应超时**（`knot.gateway.upstream.response-timeout`，默认 120s）。
  作用：直接堵住"无总超时"这个最坏场景——无论上游如何滴流，到点即断、worker 释放。
  流式安全已确认：`executeStreaming` 仅在 `exchange(...)` 内读响应头，响应体由 `ProxyStreamingBody` 在独立 `StreamingResponseBody` 任务里惰性读取（受 readTimeout 静默约束），总响应超时只卡到响应头到达，不会误杀长流。新增 `GatewayUpstreamClientPropertiesTest` 断言默认值。

- [x] **A2 退避 / 超时 / 并发相关配置收敛**
  上游超时/连接池/缓冲区等 9 项可调旋钮已集中到 `GatewayUpstreamClientProperties`（Java 默认值 + 注释），并在 `application.yml` 补 env 覆盖示例注释块（`knot.gateway.upstream.*`，与现有 `${ENV:default}` 风格一致）。重试/退避上限由域层 `RetryPolicy` record 紧凑构造器 `clampInt/clampDouble` 硬收敛（maxAttempts≤10 / backoff≤60000 / multiplier≤10），经构造器与 Jackson 反序列化两入口，无绕过——无需在 API/管理端 VO 重复约束。

- [x] **A3 A2 涉及的数值约束落地**
  在 `GatewayUpstreamClientProperties` 加 `@Validated` + 连接池/缓冲区整数字段 `@Min(1)`；网关模块补 `spring-boot-starter-validation` 依赖使校验真正生效（否则 `@Validated` 静默忽略）。误配 0 启动即 fail-fast，防自我 DoS。`GatewayUpstreamClientPropertiesTest` 覆盖默认值与 0 值拒绝。

### B 组 —— 高收益、高风险，需单独专项 + 真启冒烟验证

> **落地下沉决策（2026-10-19 实施）**：原计划要求把 `handleMap` 整体改 `CompletableFuture` + `delayedExecutor`。
> 经 Spring 源码实证（见 `.workbuddy/tmp/` 抽取的 `StreamingResponseBodyReturnValueHandler` / `HandlerMethodReturnValueHandlerComposite`
> / `WebAsyncManager`），B 组的「worker 释放」目标有更稳的落点：控制器返回 `DeferredResult<Object>`、把**同步的**
> `handle(...)` 整体搬到专用网关线程池执行，Tomcat worker 立即释放（含退避 `Thread.sleep` 窗口）；`handle` /
> `proxy` / `invokeWithRetry` 逻辑**一字未改**，规避了「把退避改 `delayedExecutor` 需把 `RequestContextHolder`
> 穿过每一层 CF 组合」的高风险改动。流式与异常链经源码论证均安全（详见下方「已落地」）。更深的
> `delayedExecutor`（连退避窗口都不占专用线程）作为后续可选项单列。

- [x] **B1 退避真正异步化（async-servlet，落地版）**
  控制器（`GatewayControllerSupport` + 8 个网关控制器）返回 `DeferredResult<Object>`；`GatewayControllerSupport.dispatch`
  把同步的 `requestHandler.handle / handleMultipart` 经 `CompletableFuture.supplyAsync(..., gatewayAsyncExecutor)`
  搬到专用线程池执行，**Tomcat worker 立即释放**。退避 `Thread.sleep` 改在 `gatewayAsyncExecutor` 线程上发生，
  不再占用 Web 容器线程。配套：新增 `GatewayAsyncConfiguration` 提供 `gatewayAsyncExecutor` 线程池
  （`asyncExecutorThreads` 默认 200、@Min(1)，CallerRunsPolicy 背压）；`dispatch` 内随异步任务传递
  `RequestContextHolder`（保证流式响应头 / 首 token 响应头在正确线程写出）；异常解包 `CompletionException`
  后以原异常 `setErrorResult`，`GatewayExceptionHandler` 既有 502/429/401/503 分支继续按原类型命中。
  回归面：`handle` 签名未变 → **现有直接调用 `handle(...)` 的单元测试零改动**；`GatewayRequestHandler`
  及 `AbstractGatewayRequestTemplate` 逻辑零改动。

- [x] **B2 全局请求预算 deadline（B1 配套，落地版）**
  以 `DeferredResult` 超时（`requestBudget`，默认 600s）作为**唯一真总超时**，覆盖「非流式零兜底 / 流式仅 600s」
  的历史不一致；`GatewayExceptionHandler` 新增 `AsyncRequestTimeoutException` → 504（`timeout_error`），
  `GatewayErrorTypeEnum` 增 `TIMEOUT_ERROR`。默认 600s 与既有流式 `spring.mvc.async.request-timeout` 对齐，
  长流式响应不被误伤。`application.yml` 在 `knot.gateway.upstream.*` 注释块补 `request-budget` / `async-executor-threads`。

## 3. 已落地（本批次，全量门禁通过）

- [x] **P10** 入站请求体大小上限（`MaxRequestBodySizeFilter`，仅非 multipart 按 `Content-Length` 拒超大，默认 10MB 可配，豁免多模态上传 / 分块 / OPTIONS）。
- [x] **P4** 鉴权防爆破（`BruteForceGuard` 固定窗口计数器 + `TooManyRequestsException` 429；登录按用户名、消费者鉴权按 IP）。
- [x] **P2** AES 密钥升级（PBKDF2WithHmacSHA256 + 随机盐，新写入 `ENC:v2:`，保留 `ENC:` v1 存量兼容解密）。
- [x] **A1** 网关上游总响应超时（HC5 `setResponseTimeout`，默认 120s；流式安全已论证）。
- [x] **A2** 上游超时/连接池/缓冲区可调项收敛进 `GatewayUpstreamClientProperties` + `application.yml` 注释块。
- [x] **A3** `GatewayUpstreamClientProperties` 加 `@Validated` + `@Min(1)` 边界，网关补 `spring-boot-starter-validation` 使校验生效。
- [x] **B1** 网关异步化：控制器返回 `DeferredResult<Object>`，同步 `handle` 经 `gatewayAsyncExecutor` 专用线程池执行，Tomcat worker 立即释放（含退避窗口）；`RequestContextHolder` 随任务传递；`GatewayUpstreamClientProperties` 增 `asyncExecutorThreads`（默认 200、@Min(1)）。`handle` / `proxy` / `invokeWithRetry` 逻辑零改动。
- [x] **B2** 全局请求预算 `requestBudget`（默认 600s）→ `DeferredResult` 超时，作为唯一真总超时；`GatewayExceptionHandler` 增 `AsyncRequestTimeoutException` → 504（`TIMEOUT_ERROR`）。

## 4. 建议执行顺序

A 组先落（低风险、堵最坏场景、门禁风险小）→ 真启网关冒烟（admin 18092 + 网关 19091）跑通 → B 组异步化专项（已落地，见上）。

## 5. 门禁与验证

- **A 组**：reactor `clean test` 全绿 + 真启网关冒烟 8/8 通过；A1 专项实测流式不受影响。
- **B 组**：reactor `clean test` 全绿 + 真启网关冒烟（重点回归流式 `ProxyStreamingBodyTest` / `GatewayRequestHandlerStreamingTrafficTest` / `GatewayRequestHandlerReturnUsageDetailTest`，以及异步化后 `GatewayUpstreamErrorResponseTest` 异常链 502/429/401/503 仍按原类型命中）。

## 6. 提交策略

延续批准制：全部改动等你确认 / 批准后 commit（当前 P10 / P4 / P2 均未 commit）。

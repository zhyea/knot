# 首 token 响应时间响应头（X-Knot-First-Token-Time）

- 日期：2026-10-13
- 状态：已落地，gateway 模块测试全绿；全量门禁被并行会话既存失败阻挡（见「遗留事项」）

## 1. 需求与口径

网关向调用方暴露**首 token 响应时间（TTFT）**，响应头 `X-Knot-First-Token-Time`，单位毫秒。

拍板口径（robin，2026-10-13）：

| 决策点 | 选择 |
|---|---|
| 测量起点 | **端到端**：请求进入网关模板处理时刻（`GatewayRequestContext` 构造时固定 `System.currentTimeMillis()`），含鉴权、路由解析、插件、频控与重试/failover 耗时 |
| 适用范围 | **流式 + 非流式都设** |
| 头名与单位 | `X-Knot-First-Token-Time`，整数值毫秒 |

语义定义：

- **流式**：上游首行数据到达时刻（SSE 每行 / 裸流首个字节就绪）− 请求进入网关时刻。
- **非流式**：响应体就绪时刻（`applyUsageAccounting` 产出最终响应体）− 同上。
- **异常路径不设头**：鉴权失败、429 限流/限额、上游失败等场景无成功首 token，**头缺失即本次无成功首 token**。

## 2. 实现

| 文件 | 改动 |
|---|---|
| `knot-common/.../constants/GatewayHeaders.java` | 新增常量 `X_KNOT_FIRST_TOKEN_TIME = "X-Knot-First-Token-Time"` |
| `knot-gateway/.../runtime/GatewayRequestContext.java` | 新增 final `receivedAtMillis`，显式构造器内固定当前时间（`@RequiredArgsConstructor` 改显式构造，调用处零改动） |
| `knot-gateway/.../runtime/FirstTokenHeaderWriter.java`（新增） | 单一语义组件：`of(context)` 工厂自动从 `RequestContextHolder` 取 response；`mark()` 幂等，首次调用 `setHeader`；`noop()` 降级实例（response 缺失静默跳过） |
| `knot-gateway/.../upstream/stream/ProxyStreamingBody.java` | 构造器尾参加 `FirstTokenHeaderWriter`（null 归一为 noop）；`pumpEventStream` 每行、`pumpRaw` 首次 read 后调 `mark()`——均在首次 write/flush 之前 |
| `knot-gateway/.../runtime/GatewayRequestHandler.java` | `streamResponse()` 传 `FirstTokenHeaderWriter.of(context)` |
| `knot-gateway/.../runtime/AbstractGatewayRequestTemplate.java` | `handleMap` 非流式分支：response 非 null 且非 `StreamingResponseBody` 时 `mark()` |
| `knot-server/knot-gateway/src/test/.../ProxyStreamingBodyTest.java` | 7 处构造调用补参；新增 2 个用例（慢速 SSE 流 150ms / 裸流 100ms，断言头值 ≥ 延迟） |
| `knot-server/knot-gateway/src/test/.../FirstTokenHeaderWriterTest.java`（新增） | 口径/幂等/无 response 降级/无请求属性降级/of(context) 绑定当前 response |

### 为什么流式可以在首行之后写头

Tomcat 在响应体**首次 flush 之前**不会 commit 响应头；`StreamingResponseBodyReturnValueHandler` 只是交出 OutputStream，不会预先提交头。因此 `ProxyStreamingBody` 在上游首行到达后、第一次 `write/flush` 之前调用 `setHeader` 依然生效——首行数据到达的时刻就是 header 的最后写入窗口，与 TTFT 的终点语义天然重合。

### 与 knot_extend / raw usage 透传的关系

正交：TTFT 走响应头，`knot_extend` 与并列 `usage`（raw usage）走响应体/SSE 事件，两条线不交叉。并行会话 2026-10-13 对 `ProxyStreamingBody` 用量注入的轻量调整已核对，无冲突。

## 3. 验证

### 单元测试

- `ProxyStreamingBodyTest`：新增 `writesFirstTokenTimeHeaderWhenFirstChunkArrives`（SSE，上游首字节延迟 150ms，断言头值 ≥150）与 `writesFirstTokenTimeHeaderOnRawStream`（裸流，100ms）。
- `FirstTokenHeaderWriterTest`：6 个用例覆盖口径（receivedAt 之前耗时计入）、幂等（重复 mark 不改写）、null response / 无请求属性静默降级、`of(context)` 绑定当前 response。
- gateway 模块全量：见 build-ttft-gateway-test.log（`Tests run: N, Failures: 0, Errors: 0`）。

### jar 冒烟

待补（见遗留事项）：真启 admin + gateway，既有 smoke 场景零回归；TTFT 头实证需本地假 SSE 上游 + 冒烟库临时路由规则。

## 4. 遗留事项

1. **全量门禁被并行会话既存失败阻挡**：`knot-adapter` 模块 `UsageExtractorBillingModesTest.catalogDefinitionsCarryBillingModes`（`UsageExtractorBillingModesTest.java:55`）按字面量 `"DEFAULT"` 取 definitions，而 `DefaultTokenUsageExtractor.CODE` 已是 `"DEFAULT-TOKEN"`（更早提交 `ff09840` 改名，今天 10:19 并行会话 `db8249b` 调整该测试时漏同步）。与本次改动无关（git status 仅本次 8 个文件）。修复方式一行：测试 5 处 `"DEFAULT"` → `"DEFAULT-TOKEN"`，待 robin 裁定由谁修。
2. **TTFT 头真机实证**（jar 冒烟 + 假上游）待 robin 批准后执行。

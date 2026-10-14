# 首 token 响应时间响应头（X-Knot-First-Token-Time）

- 日期：2026-10-13
- 状态：已落地，全 reactor 门禁 BUILD SUCCESS（唯一失败为并行会话既存项，见「遗留事项」）
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

### 单元测试（build-ttft-gateway-test2.log）

- gateway 模块全量：**Tests run: 90, Failures: 0, Errors: 0, BUILD SUCCESS**。
- 新增 `ProxyStreamingBodyTest.writesFirstTokenTimeHeaderWhenFirstChunkArrives`（SSE，上游首字节延迟 150ms，断言头值 ≥150）与 `writesFirstTokenTimeHeaderOnRawStream`（裸流，100ms）——均通过。
- 新增 `FirstTokenHeaderWriterTest` 6 个用例（口径 / 幂等 / null response / 无请求属性降级 / of(context) 绑定 / 无属性不写头）——均通过。

### jar 冒烟（真机，build-ttft-package.log + gateway-ttft-run.log）

冒烟库 `knot_smoke_options`（ProbeBuildSmokeDb 从 schema.sql+data.sql 构建，授权 admin/admin），gateway 以 jar 真启（SERVER_PORT=19091、KNOT_SQL_INIT_MODE=NEVER）。链路：Rule `gpt4o-default` → `chat-premium-pool` → `gpt-4o`（base_url 经 ProbeTtftRewrite 改指向本地假上游，池内 claude 权重清零保证选模确定性）。

假上游为本地 python SSE 服务（`.workbuddy/audit/ttft_fake_upstream.py`）：流式首 chunk 前延迟 **600ms**，chunk 间隔 200ms；非流式立即返回。探针 `.workbuddy/audit/ttft_probe.py` 实测：

| 场景 | 结果 |
|---|---|
| 流式（stream:true） | status=200，**X-Knot-First-Token-Time=607ms**（≥600ms 上游延迟 + 7ms 网关处理），SSE 完整转发含 `[DONE]`，总耗时 1.221s |
| 非流式（stream:false） | status=200，**X-Knot-First-Token-Time=10ms** |

探针退出码 0（`TTFT SMOKE PASS`）。假上游侧日志确认收到 `stream:true` 走 SSE 分支。冒烟后 DROP 库（ProbeTtftClean），gateway/假上游进程已停，零残留。

### 全 reactor 最终门禁（build-ttft-final2.log）

`mvn -f knot-server/pom.xml test "-Dmaven.test.failure.ignore=true"`（历史同口径）：**BUILD SUCCESS，EXIT=0，8 模块全 SUCCESS**。各模块用例：common 84 / plugin 10 / adapter 84（1 失败=上述既有项）/ service 65 / admin 103 / **gateway 90 零失败**（较今日 least-load 收尾时 79 + 本次净增 11）。前端零改动，未跑前端门禁。

> 排障记录（冒烟基建，非产品缺陷）：网关经 Apache HttpClient 5 发上游请求用 `Transfer-Encoding: chunked`（真实上游均支持），python 假上游首版不认 chunked 导致读到空 body——已给假上游补 chunked 读取后全链路通过。

## 4. 遗留事项

1. ~~并行会话既存失败~~ **已修复（robin 指令「修一下」）**：`UsageExtractorBillingModesTest.catalogDefinitionsCarryBillingModes` 原按字面量 `"DEFAULT"` 取 definitions，而 `DefaultTokenUsageExtractor.CODE` 早已是 `"DEFAULT-TOKEN"`（`ff09840` 改名，`db8249b` 调整该测试时漏同步）。已改为一律引用各提取器的 `CODE` 常量（防再次改名漂移）。修复后全 reactor `mvn test`（不带 ignore）**BUILD SUCCESS、EXIT=0、8 模块全 SUCCESS、全仓零失败**（adapter 84 / gateway 90 含本次新增 11 例），证据 `build-ttft-adapterfix.log`。
2. ~~更深层不一致~~ **已全链路收口（robin 指令，见「附录：默认 Usage 提取器编码收口」）**：`ff09840` 把 CODE 改名 'DEFAULT-TOKEN' 只做了一半——`schema.sql` 列默认、`data.sql` 种子行、`ModelService` 落库兜底、前端默认值与「默认」标签仍写 'DEFAULT'，运行时解析落空静默走兜底路径。已一次做干净，并补齐迁移脚本与实证。

## 附录：默认 Usage 提取器编码收口（DEFAULT → DEFAULT-TOKEN）

背景见遗留事项 2。收口清单：

| 层 | 文件 | 改动 |
|---|---|---|
| 后端主代码 | `ModelService.java:690` | 兜底值改引用 `DefaultTokenUsageExtractor.CODE` |
| 后端测试 | `ModelApiBindingSyncTest.java` | 4 处字面量改引用 `DefaultTokenUsageExtractor.CODE` |
| 后端测试 | `UsageExtractorBillingModesTest.java`（同批） | 5 处改引用各提取器 `CODE` 常量 |
| DB 权威 | `schema.sql:452` | 列默认 `'DEFAULT'` → `'DEFAULT-TOKEN'` |
| DB 权威 | `data.sql` | 7 处种子行 `'DEFAULT'` → `'DEFAULT-TOKEN'` |
| 前端 | `src/api/models.ts`（新增常量） | `DEFAULT_USAGE_EXTRACTOR_CODE = "DEFAULT-TOKEN"`（单一来源，紧邻 `listUsageExtractors`） |
| 前端 | `ModelFormDrawer.vue` | 表单兜底值 / 提交兜底值 / 下拉置顶排序 3 处共用常量 |
| 前端 | `UsageExtractorView.vue` | 「默认」标签判定用常量 |
| 迁移 | `docs/database/migration/2026-10-13-usage-extractor-default-code.sql` | information_schema 守卫 + UPDATE 存量行 + ALTER 列默认，幂等 |

验证：
- 迁移实证（`ProbeApplyExtractorDefaultMigration`）：模拟既有库（8 行 'DEFAULT'、列默认 'DEFAULT'）→ 跑迁移 → **残留 0 行、列默认 'DEFAULT-TOKEN'** → 再跑一遍结果不变（幂等）。
- 后端全量 `mvn test`：**BUILD SUCCESS、EXIT=0、8 模块全 SUCCESS**，gateway 116 例零失败（含 `ModelApiBindingSyncTest` 4 / `UsageExtractorBillingModesTest` 2）。证据 `build-extractor-migrate-test2.log`。
- 前端：`npx vite build` ✓ built（10.66s，dist 已重建）；`vue-tsc --noEmit` **EXIT=0** 零类型错误。
- jar 冒烟：新 schema/data 下 gateway 启动正常（19091），链路请求 200（流式 ttft=751ms / 非流式 46ms）。收尾 DROP 库 + 停进程，零残留。

⚠ 排障：首轮后端门禁挂 `moderation/ModerationGuard.java` 找不到 `ModerationScopeEnum`——该文件是并行会话**当刻刚落盘的未跟踪新文件**，编译时竞态；重跑即过（非代码缺陷）。

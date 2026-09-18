# 技术选型评估：knot-gateway 是否应改用 Spring WebFlux

- **评估日期**：2026-09-18
- **评估对象**：`knot-server/knot-gateway`（AI 网关转发运行时，9090 端口，60 文件 / 3150 LOC）
- **评估方法**：逐文件静态走读热路径 + 全局模式扫描 + 本地 Maven 仓库 jar 级 API 验证
- **本次范围**：仅决策评估，**未改动任何源代码**
- **前置文档**：`docs/性能瓶颈与潜在Bug审查-2026-09-18.md`（本文引用的 P0#1~#3 / P1#4~#6 / #7~#9 均出自该文）

---

## 一、结论摘要

> **不引入 Spring WebFlux。** 在 500~5000 并发流式连接的目标量级下，WebFlux 的收益可以被
> **虚拟线程**以一项配置项的代价等价获得；而引入 WebFlux 的必付成本是**重写级**的
> （模块拆分 + 持久层 + 链路上下文 + 异常体系），其中「换 r2dbc 重写 DAL」一项因
> **热路径零 DB 访问**而收益恰好为 0。

| 维度 | 判断 |
|---|---|
| WebFlux 在长连接场景的收益 | **真实存在**（连接数与线程数解耦），不可否定 |
| 引入 WebFlux 的改造量 | **重写级**（约 25+ 文件，跨 3 个共享模块） |
| 本项目的等效替代 | **虚拟线程**（配置级，代码零改动） |
| 最终建议 | **虚拟线程 + 连接池/超时 + 流式改造**，WebFlux 出局 |
| 重新评估门槛 | 峰值并发流式连接 **> 10000** 且要求极致内存/吞吐 |

---

## 二、WebFlux 的收益：成立，但必须量化

### 2.1 收益的真实来源

AI 网关的典型负载是大批**长时间的 SSE 流式连接**。LLM 上游是「断续吐字」——
平均每 20~100ms 吐一个 token，其余时间全是静默等待。因此：

| 模型 | 线程占用率 | 说明 |
|---|---|---|
| 阻塞式转发 | ≈ **连接数** | pump 线程整段连接生命周期被占（生成 30s 就占 30s，其中 99% 在空等） |
| 非阻塞转发 | ≈ **活跃传输的瞬时并发** | 仅在实际搬运字节的瞬间占用 |

两者相差 **10~100 倍**。这就是 WebFlux 在 AI 网关场景的真实价值来源，**该收益不因本项目特点而消失**。

### 2.2 收益的边界

WebFlux 的收益集中在**「上游长等待期间的线程占用」**这一个维度。它**不能**改善：

- 上游配额限制（那是供应商侧约束）
- 路由解析与限流判定（`knot-gateway` 热路径为纯内存，见 §4.1）
- 单个凭证（Key）被打爆的问题（那是分流机制问题，见 §8.1）

---

## 三、引入成本：重写级

### 3.1 阻断性事实

| # | 阻断点 | 证据 |
|---|---|---|
| 1 | **依赖链无法干净切换** | `knot-gateway/pom.xml:24` 依赖 `knot-service`，而 `knot-service/pom.xml:57` 依赖 `spring-boot-starter-web`（servlet 栈）。gateway 自身 `pom.xml:37` 也显式带 starter-web |
| 2 | **两个可执行应用共享服务层** | `knot-admin`（8080，`pom.xml:24,32` 带 starter-web + `springdoc-openapi-starter-webmvc-ui`，约 30 个 `@RestController`）与 gateway 共享 knot-service/dal/plugin/adapter。要让 gateway 上 WebFlux，**必须先拆 `knot-service`（core / web）**，牵动两个应用 |
| 3 | **Boot 的 WebApplicationType 推断会优先 SERVLET** | classpath 同时存在 `DispatcherServlet` 与 `DispatcherHandler` 时判定为 SERVLET。**「加个 webflux 依赖就自动响应式」是错误认知**，容易白做 |
| 4 | **servlet API 被直接引用** | `knot-service/.../aspect/OperationLogAspect.java:3`、`knot-service/.../auth/CurrentAuth.java:3`（`jakarta.servlet.http.HttpServletRequest`）；`knot-gateway/.../runtime/GatewayTraceFilter.java:12` `extends OncePerRequestFilter` |
| 5 | **ThreadLocal 贯穿全链路** | `GatewayTraceContext.java:16` `ThreadLocal<GatewayTraceContext> CURRENT` ← `GatewayTraceFilter.java:29` 写入 → `UpstreamProxyClient.currentTraceId()` 读取；日志 `application.yml:22` 的 `%X{traceId}` 依赖 MDC 线程绑定。reactive 下须整体改 Reactor Context 传播 |
| 6 | **异常处理是 MVC 专属** | `GatewayExceptionHandler` 使用 `@ControllerAdvice(assignableTypes = ...)` + `MissingRequestHeaderException` / `MethodArgumentNotValidException`（MVC 特有异常），7 个 handler 方法需全量重写 |
| 7 | **插件分发是同步接口** | `PluginDispatcher.dispatch(...)` 返回 `void`，4 个 stage（GATEWAY_REQUEST/RESPONSE/ERROR、UPSTREAM_REQUEST/RESPONSE/ERROR），转 reactive 属**接口级破坏性变更** |
| 8 | **持久层是同步 JDBC** | knot-dal 使用 `mybatis-spring-boot-starter` + `mysql-connector-j`，32 个 Mapper；knot-service 有 **82 处 `@Transactional`**。真非阻塞须换 r2dbc → **等于重写 DAL** |

### 3.2 存量阻塞调用清单（reactive 下须逐个处理）

| 组件 | 位置 | 性质 |
|---|---|---|
| 14 个 Caffeine `LoadingCache` | `GatewayDataService.java:49-62` | 同步 loader；`get` 在 miss / expire 时阻塞 |
| AES-GCM 凭证解密 | `ProviderCredentialSupport.decryptField`（`UpstreamProxyClient` 每请求必经） | CPU 密集，须 `boundedElastic` 包装 |
| 限流计数 | `GatewayTrafficGuard.java:26`（`ConcurrentHashMap`）、`:110`（`synchronized`） | 进程内状态，WebFlux 无改善 |
| 上游转发 | `AbstractUpstreamProtocolExecutor.java:57-59` | 完全阻塞 + 全量缓冲 |

### 3.3 反向风险（常被忽略）

- WebFlux 下 Netty worker 线程默认 **≈ CPU 核数（4~8）**。若把上述任一阻塞调用留在原地，会**直接阻塞 event loop**，比现在 200 个 Tomcat 线程更易崩溃。
- 若 wrap 到 `boundedElastic`（默认 `10 × CPU`），则又回到「阻塞调用占一个线程」的模型，额外付出调度 + 队列 + 上下文切换开销，**无结构性收益**。
- 结论：**WebFlux 在纯 I/O 等待场景赢不到东西，在混合场景反而先输。**

---

## 四、两条决定成本的关键事实

### 4.1 热路径零 DB 访问 —— 「重写 DAL」的收益为 0

- `GatewayDataService`（244 行）是 **gateway 模块里唯一接触 Mapper 的类**
  （全模块 grep `"Mapper "` 仅命中该文件）。
- `RoutingResolver`（223 行）、`UpstreamProxyClient`、`GatewayTrafficGuard`、`GatewayRequestHandler`
  **零直连 mapper**，全部经由 14 个 `LoadingCache` 读取。
- 缓存配置：`expireAfterWrite = 10min` + `refreshAfterWrite = 3min`（`GatewayDataService.java:46-47, 233-237`）。
- **稳态下每请求 DB 访问次数 = 0**，热路径 = 纯内存 + 上游网络 I/O。

> **推论**：WebFlux 改造中最昂贵的一项（换 r2dbc 重写 32 个 Mapper 与 82 处事务），
> 其收益恰好为 0。这一条单独就足以否掉「为了性能改 WebFlux」的动机。

DB 真正会进入路径的 4 个场景：① 冷启动首访；② `refreshAfterWrite` 时机（异步、非请求线程阻塞）；
③ key 不在缓存；④ 热 key 长时间无访问后 expire。其中 ③ 存在真实 DoS 面（见 §8.4）。

### 4.2 本地无阻塞 —— WebFlux 的卖点找不到标的物

WebFlux 的核心主张是「避免阻塞」与「连接/线程解耦」：

- 前者在本项目的**本地热路径上没有标的物**（§4.1）
- 后者可以**被虚拟线程等价拿下**（§5）

---

## 五、方案空间：五条路径对比

| # | 方案 | 连接/线程解耦 | 5000 并发可行性 | 改动量 | 关键代价 |
|---|---|---|---|---|---|
| A | 现状（200 Tomcat 线程 + 全量缓冲） | ❌ | ❌ | — | 200 线程上限；无连接池/超时 |
| B | 仅止血：连接池 + connect/read 超时 | ❌ | ❌ | 小 | 只解决握手开销与雪崩，不解决长连接 |
| C | 阻塞式流式（`SseEmitter` + `InputStream` pump） | ❌ **平移** | ❌ | 中 | 问题只是从 Tomcat 池搬到自建池，早晚返工 |
| D | **虚拟线程**（`spring.threads.virtual.enabled=true`） | ✅ | ✅ | **极小（一项配置）** | 需 JDK 21+ |
| E | MVC + 引入 `spring-webflux`（作库）+ 返回 `Flux` + `WebClient` | ✅ | ✅ | 中 | 需重写转发与 usage 注入；不改模块/DAL/异常体系 |
| F | 全量 WebFlux（`starter-webflux` 替换 servlet） | ✅ | ✅ | **大** | §3 全部成本，含拆模块与 DAL |

### 5.1 容量核算（按 5000 并发流式连接）

| 项 | 数值 | 判断 |
|---|---|---|
| 虚拟线程内存 | 5000 × KB 级栈 ≈ 数十 MB | 富余（同量级平台线程需 GB 级栈空间） |
| 载体线程 | 默认 = CPU 核数；阻塞 I/O 时自动 unmount | 富余（峰值并行度**需压测确认**） |
| Tomcat 连接层 | `maxConnections` 默认 8192，与 `maxThreads=200` 是两个独立上限 | 够用 |
| 上游并发连接 | 受账户 / Key 配额约束 | 取决于分流机制与 Key 级选取（见 §8.1） |

### 5.2 对本项目而言，B 与 D 的差异

- **B（连接池 + 超时）**：不解决长连接，但**与后续任何方案都不冲突**，是独立的止血项。
- **D（虚拟线程）**：保持阻塞式写法，代码零改动；Tomcat `Http11NioProtocol` 连接层本就事件驱动，
  叠加虚拟线程后，连接等待期间的载体线程被释放 —— 这正是 WebFlux 想给的收益。

---

## 六、决策判据

以**峰值并发活跃流式连接数**为决策变量：

| 量级 | 建议 |
|---|---|
| < 500 | 连接池 + 超时 + 虚拟线程即足够，不必考虑架构改造 |
| **500 ~ 5000** | **虚拟线程优先**；内存敏感时再考虑路径 E |
| 5000 ~ 20000 | 先以虚拟线程实测验证，不达标再评估路径 F |
| > 20000 / 公网 SaaS | 路径 F 值得付重写成本，或将网关独立成进程以剥离 knot-service 依赖 |

---

## 七、本项目适用结论

已确认目标量级为 **500 ~ 5000 并发流式连接** → 落在「虚拟线程优先」区间。

**建议组合（4 处改动）：**

1. **JDK 17 → 21**（`knot-server/pom.xml:25` `<java.version>17</java.version>`）
   + `spring.threads.virtual.enabled: true`
2. **`GatewayApplication.java:25-28` 的 `RestClient` 换用 `JdkClientHttpRequestFactory`**
   —— Spring Framework 6.1+ 内置（本项目为 6.1.13），底层是 JDK `HttpClient`：
   **自带连接池 + `connectTimeout` / `readTimeout`**，一次性修掉 P0#2 / P0#3
3. **流式路径**取 `InputStream`（`exchange(...)` 或 JDK `HttpClient.send` + `BodyHandlers.ofInputStream()`），
   Controller 返回 `ResponseBodyEmitter`
4. **`GatewayRequestHandler.java:93-135` 的 usage 注入改为流尾 SSE 事件**
   —— 该处目前对整个响应 String 做 `knot_usage` 注入（`appendUsageEvent` 依赖 `lastIndexOf("data: [DONE]")`），
   流式改造后若不改，**usage 明细会静默丢失**。这是最容易漏的一点

---

## 八、附带发现：比本议题更紧急的独立缺陷

> 以下问题不属于「是否改 WebFlux」的范畴，但在 500~5000 量级下**优先级高于线程模型选型**。

### 8.1 同一账户下的多 Key 选取缺失：约 85% 的 Key 闲置（🔴 建议最优先）

**语义前提（关键）**：`providers` 表**在设计上是供应商品牌级** —— 表结构仅含
`code`(UNIQUE) / `name` / `provider_type` / `status` / `contact_name` / `contact_phone`
（`schema.sql:254-265`），前端表单字段为「编码 / 名称 / 类型 / 启用 / 认证凭证」，
**UI 上无任何「账户」字样**。但**实际使用语义是「模型供应商账户」**：每条 provider 记录代表一个账户
（同一品牌的多账户靠 `code` / `name` 区分）。以下分析按**实际使用语义**展开。
这一「设计语义（品牌）vs 使用语义（账户）」的漂移应在文档层面显式化，否则后续极易误判容量。

已确认生产建模为 **「30 个供应商账户（provider 记录），每个账户挂多条 Key（合计 200+）」**。而代码是：

```sql
-- ProviderCredentialMapper.xml:9-14
select ... from provider_credentials
where provider_id = #{providerId} and status = 'ACTIVE'
order by id desc limit 1        -- 每账户只取一条
```

- `ProviderCredentialSupport.saveAuthConfig:75-108` 是**覆盖式单凭证**语义（查 active → 有则 update / 无则 insert）。
  这在「一个账户一个凭证」的原始设计下自洽，但**无法表达一个账户下的多个 Key** ——
  从 admin 界面新增第二个 Key 会**覆盖**第一个
- `ProviderCredentialEntity` **无任何标识字段**（无 name / label / alias）→ 即使写入多个 Key，
  UI 也无法区分「这是该账户下的第几个 Key」
- 全项目**无凭证级 CRUD / list 端点**（`listActiveAll` 仅被 `ProviderCredentialEncryptionMigrator:42` 用于加密迁移）
- **后果**：每个账户只用 `id` 最大的那一条 → **200+ Key 中约 170 个（85%）完全闲置**

**摊薄能力量化（本议题的关键数字）**：

| 场景 | 生效凭证数 | 5000 并发下单 Key 承载 |
|---|---|---|
| 当前实现 | 30 | **≈ 167 并发/Key** |
| Key 级轮询生效 | 200+ | **≈ 25 并发/Key** |

→ 实现 Key 级选取可把单凭证并发压力降低 **约 6.7 倍**，这是把 5000 并发真正摊开的核心杠杆。

**建模选择**：

- ✅ 保持「provider = 账户」语义，在**账户内部**实现 Key 级轮询（改动集中在 dal / gateway / admin，
  不动 routing target 的 `providerId` 语义与计费维度）
- ❌ 把每个 Key 展开成独立 provider 记录 —— 会让 `provider_type` / `contact_*` 等字段彻底失去意义，语义更混乱
- ⚠ 若要严格建模（品牌 → 账户 → Key 三层），需新增 `provider_accounts` 表并改动 routing / 计费 / 限流维度，
  成本显著上升，建议作为长期方向而非本次改造

**附带**：因 `providers` 本身即账户粒度，`TrafficResourceTypeEnum.PROVIDER`
（`GatewayTrafficGuard:52-58` 的 `checkProvider(target.providerId(), ...)`）**已经是账户级限流**，
缺的只是 `checkQuota` 的实现（§8.4）以及 Key 级（`CREDENTIAL`）维度。

### 8.2 路由是「顺序 failover」，不是负载均衡

```java
// GatewayRequestHandler.java:68-84
for (RoutingRuleTargetDto candidate : routing.candidateModels()) {
    if (!trafficGuard.checkTarget(candidate, trafficContext)) continue;
    try {
        return proxyClient.proxy(...);   // 成功即返回，后续候选永不使用
    } catch (GatewayUpstreamException e) { lastUpstreamException = e; }
}
```

候选顺序由 `RoutingResolver.orderTargets:125-138` **固定排序**（primary 第一，其余按
priority desc → targetId → targetType）。**当前唯一的「分散」机制是限流/失败触发的副作用式切换。**

### 8.3 `selection_strategy` 是「配置了但未实现」

| 层 | 状态 |
|---|---|
| schema | `selection_strategy VARCHAR(32) DEFAULT 'WEIGHTED'`（`schema.sql:323`） |
| admin | 完整 CRUD + 必填校验（`ModelPoolService.java:201`） |
| gateway | **从未读取该字段**（全仓库 grep 仅命中 entity / DTO / admin CRUD） |
| 池内选择 | `RoutingResolver.resolvePoolCandidates:200-202` 只用 weight **排序**，非**加权随机** |

→ UI 上选择的「加权随机」，运行时实际等价于「固定顺序 failover」。

### 8.4 缓存与限流相关

- **缓存穿透 DoS 面**：`RoutingResolver.java:41` 的 `isRoutingSecretKey` **只校验格式**，
  攻击者用大量「格式合法但不存在」的 secretKey 打进来，每个都是新 miss → **每请求一次 DB 查询**。
  修法：`maximumSize` + 负缓存短 TTL（即前置文档 #6）
- **配额检查是桩**：`GatewayTrafficGuard.java:78-84` `checkQuota` 直接 `return true` → 配额配置完全不生效
- **限流是单 JVM 内存计数**（`:26`）+ **固定窗口 2× 突发**（`:106-121`）

---

## 九、建议的实施顺序

```
①  Key 级选取（账户内多 Key 轮询）+ 池内选择策略  → 让 200+ 个 Key 真正参与分摊（§8.1 + §8.2 + §8.3）
②  限流与配额收口              → 它同时是 failover 的唯一触发条件（§8.4）
③  连接池 + 超时 + 虚拟线程     → 止血 + 支撑目标连接量（§7-1 / §7-2）
④  流式转发改造                → 与 ③ 的线程模型同批决策（§7-3 / §7-4）
```

**顺序理由**：① 的收益（把并发摊到 200+ 个 Key，单 Key 承载从 ≈167 降到 ≈25）与 ④ 的收益（不占线程）量级相当，但
**① 的改动完全独立于线程模型**，且风险更低、可用 DB 侧计数直接验证。先做 ① 可以避免
在错误的前置条件下投入 ④。

---

## 附录 A：已验证的技术前提

以下均通过读取本地 Maven 仓库 jar 逐一验证（非回忆）：

| 结论 | 验证对象 | 结果 |
|---|---|---|
| `JdkClientHttpRequestFactory` 可用 | `spring-web-6.1.13.jar` | ✅ 存在 `org/springframework/http/client/JdkClientHttpRequestFactory.class` |
| `spring.threads.virtual.enabled` 支持 | `spring-boot-autoconfigure-3.3.4.jar` 配置元数据 | ✅ `spring.threads.virtual.enabled (java.lang.Boolean, 默认 false)` |
| Tomcat 虚拟线程支持 | `spring-boot-autoconfigure-3.3.4.jar` | ✅ `TomcatVirtualThreadsWebServerFactoryCustomizer` |
| MVC 可返回 `Flux`（路径 E） | `spring-webmvc-6.1.13.jar` | ✅ `ReactiveTypeHandler.class` 存在 |
| Reactor 类型适配 | `spring-core-6.1.13.jar` | ✅ `ReactiveAdapterRegistry$ReactorAdapter` |
| 异步流式输出能力 | `spring-webmvc-6.1.13.jar` | ✅ `SseEmitter` / `ResponseBodyEmitter` / `StreamingResponseBody` |

> 即：**路径 D / E / 首次三项均无需新增依赖或仅需一个客户端库**，本项目现有版本（Boot 3.3.4 / Spring 6.1.13）即可支撑。

## 附录 B：结论演进（含被推翻的中间结论）

本评估经过三轮修正，保留过程以防后续复用错误结论：

| 轮次 | 结论 | 状态 |
|---|---|---|
| 初版 | 「不引入 WebFlux，用 `SseEmitter` 即可替代」 | ❌ **论证不完整**：`SseEmitter` 只解决 worker 线程被占，阻塞读上游仍需独占线程，属**平移**而非解决 |
| 二次 | 「长连接场景 WebFlux 收益真实，虚拟线程是更优解」 | ✅ 成立 |
| 三次 | 「上游配额是更早瓶颈」 | ❌ **表述作废**：忽略了多供应商/多账户摊薄；但**发现多 Key 实际未生效**（§8.1），问题转入分流机制 |
| 四次 | 澄清 `providers` **设计语义是品牌级、实际使用语义是账户级** | ✅ 并入 §8.1「语义前提」；摊薄粒度据此从「账户数」修正为「Key 数」（30 → 200+） |
| 定稿 | 「虚拟线程优先，WebFlux 出局；第一优先级是分流机制」 | ✅ 本文 |

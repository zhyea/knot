# 40 · 插件架构规范

> 适用：`knot-server/knot-plugin*/**`、涉及插件/敏感词的代码。完整索引见 `docs/standards/PROJECT-STANDARDS.md`。

## 1. 执行模型

- ⭐⭐ **插件只有 SYNC 一种类型**（`PluginTypeEnum` 现仅 SYNC）。原 ASYNC 异步链（`PluginEventSink` / `PluginEvent` / `CompositePluginEventSink` / `LoggingPluginEventSink` / `KafkaPlaceholderPluginEventSink` / `PluginSinkProperties`）已于 2026-10-14 **整条删除**。
- ⭐⭐ **同步插件能拦截**：SYNC handler 在请求线程内联执行，`DefaultPluginDispatcher.dispatch` **无 try/catch** —— handler 抛异常直接冒泡打断链路。需要拦截/改写一律走同步插件。
- 实例行由 `PluginReconcileRunner`（`ApplicationRunner`）**以 `entrypoint` 为键幂等 upsert** 生成，`data.sql` 不再预置插件种子。执行顺序 `order_no` 随 `PluginDescriptor` 声明：敏感词 `200` > 审计日志 `100`，保证被拦的请求先落审计。
- 同步日志插件统一出口 `PluginLogWriter`（Slf4j 结构化日志，含 stage / instance / capability / traceId / payload）。

## 2. 单表与状态

- 单表收口 `kb_plugin_instances`，**scope 维度已移除**（2026-10-13）、**插件包维度也已移除**（2026-10-25）。剩余两层是「能力族 `capability_code` → 插件实例 `instance_code`」。
- ⭐⭐ **表的定位是「`PluginDescriptor` 的物化 + 运维开关」，权威边界必须分清**：
  - **代码权威**（descriptor 投影）：`instance_code` / `instance_name` / `capability_code` / `extension_point` / `stage_code` / `order_no` / `timeout_ms` / `version` / `plugin_type` —— reconcile 每次启动**全覆盖写**，代码改了重启即跟随。
  - **运维权威**：`status` / `is_deleted` —— reconcile **永不覆盖**（用户停用过的插件不能被重启重新打开）。
  - `orphaned` 是派生标记，每次启动重算。
- ⭐⭐ **插件元数据只能写在 `PluginDescriptor` 里**：`code` / `name` / `capabilityCode` / `extensionPoint` / `stageCode` / `orderNo`。**禁止**在 `data.sql` 写插件种子、**禁止**在 reconcile 里从能力码反推展示名（判例：`deriveInstanceCode` 拼 `capability-extension-stage`、`firstSegment` 取能力码首段拼 `plugin_code`，两条启发式产出的值与手写种子不等价 → 同一列两套口径，且最长一条 65 字符超过当时列宽 64，启动期 `DataTruncation` 直接打挂上下文，2026-10-25 全删）。
- 启动期写库前校验三项：`code` 非空、全局唯一、不超过 `instance_code` 列宽。这三类错误原先只能在插库时由 MySQL 报出来，现在提前到启动第二秒指名报错（报错信息带 entrypoint）。
- Runner 按 IOC 中的 `PluginHandler` 校正：代码有则 upsert + 清 orphaned，代码移除则标 `orphaned=1`（前端浅红展示 + 逻辑删除按钮）。
- 管理面只做：开关、逻辑删除、孤儿标记展示。


## 3. 模块归属红线

1. **knot-plugin 的类禁止外部模块 import（含测试）**，依赖一律 `runtime`。
2. 需 DB 的插件能力：网关直接依赖 `knot-service` 的类（先例 `ModerationWordService.loadEnabledTerms()`），不再另设词源接口。
3. `PluginHandler` 实现放 knot-plugin，靠 service/gateway 的 runtime 依赖带进两个 app 的 IOC。
4. 要抛网关 400 但不想下沉整套网关异常家族：在 common 建 400 父异常 `GatewayBadRequestException(message, errorType, code)`，插件异常继承它，`GatewayExceptionHandler` 用 `@ExceptionHandler(GatewayBadRequestException.class)` 统一转 400（先例 `ModerationRejectException`）。
5. **契约模块不放运行时**：`knot-plugin-api` 只留接口、DTO 与枚举，不放 `@Component`。分发器实现 `DefaultPluginDispatcher` 归 `knot-plugin`（2026-10-24 迁出，见 3.12）；调用方只依赖接口 `PluginDispatcher`，因此迁移对 gateway 零改动。

## 4. 敏感词审核拓扑（2026-10-14 收官）

- **网关缓存词库资源列表**（`ModerationWordResource`，Caffeine 单键，expire 10min / refresh 30s，fail-open：异常不缓存、逐次重试、空列表 + warn）。
- **plugin 缓存编译自动机**（`ModerationWordCache.snapshotFor(words)`，按 `ModerationTerm.versionOf(words)` 去重编译，`@Scheduled` 防御性重建）。
- 网关每请求经 `ModerationWordResource.currentWords()` 取词，随 `GatewayPluginContext.words` 下发——**这是网关唯一的审核职责**。
- 词库**不进 `GatewayEntityCacheService`**（那是路由热路径数据层，16 个缓存全按实体键；词库是单例式全量资源）。
- `ModerationGuard` 不持缓存/配置源、不碰持久层；空配置或空词表即放行（fail-open）。
- 归属：`TextNormalizer` / `ModerationTerm`（record + 静态 `versionOf`）→ **common**；`AhoCorasickMatcher` / `ModerationSnapshot` → **knot-plugin**（引擎，外部禁 import）。
- 对外只暴露 `categoryCode`，防词库探测。

## 5. 归属与删除台账

已删（不要再复活）：`ModerationRuntimeResolver`、`ModerationCacheConfiguration`、`ModerationCacheConstants`、`ScheduledRefreshManager`（`@Scheduled` 取代自建线程池）、`PluginEventType`、`PluginScopeType`、`ModerationSnapshotCache`、`ModerationProperties`、`PluginAutoConfiguration`、`ModerationContext`、`PluginConfig`。

全局配置键已迁 `knot.plugin.moderation.{action,scope,refresh-interval}`（默认 FLAG / BOTH / 10s）。

# Kafka 异步插件实现方案

> 整理依据：knot 插件单表收口后的真实代码（2026-10-14 核查）。
> 范围：异步插件（ASYNC）现状梳理 + Kafka 异步插件落地方案，含**配置落点**与**逻辑切入**。

---

## 一、异步插件现状（基于代码实证）

### 1.1 契约与运行时

| 层 | 类 / 文件 | 职责 |
|---|---|---|
| 标记接口 | `PluginContract` (`knot-plugin/.../plugin/PluginContract.java`) | `pluginType()` 在接口层固定 SYNC/ASYNC |
| 同步契约 | `PluginHandler` (default `pluginType()=SYNC`) | 内联嵌入请求链路，可改写/拦截 |
| 异步契约 | `PluginEventSink` (default `pluginType()=ASYNC`) | 离链消费 `PluginEvent`，不进关键路径 |
| 分发器 | `DefaultPluginDispatcher` (`runtime/DefaultPluginDispatcher.java`) | **只分发 SYNC**（`handler.handle()`），且**无 try/catch** |
| 扇出器 | `CompositePluginEventSink` (`runtime/CompositePluginEventSink.java`) | 收集全部 `PluginEventSink` bean（排除自身）做 fan-out |
| 配置 | `PluginSinkProperties` (`config/PluginSinkProperties.java`) | `knot.plugin.sink.kafka.*` 前缀 |
| 启用 | `PluginAutoConfiguration` (`config/PluginAutoConfiguration.java`) | `@EnableConfigurationProperties(PluginSinkProperties.class)` |

### 1.2 当前 `PluginEventSink` 实现（仅 2 个 bean）

1. `LoggingPluginEventSink` (`runtime/LoggingPluginEventSink.java`)：把 `PluginEvent` 打 SLF4J。
2. `KafkaPlaceholderPluginEventSink` (`runtime/KafkaPlaceholderPluginEventSink.java`)：占位实现，仅 `log.info` 打印参数，**未真正发 Kafka**；由 `knot.plugin.sink.kafka.enabled` 门控（默认 false）。

### 1.3 ⚠ 关键发现：异步链路目前是休眠态

全仓 grep `CompositePluginEventSink.emit()` 的调用方，**生产代码里没有任何 SYNC 插件调用它**（仅 `CompositePluginEventSink` 自身与单测调用）。

- 链路日志：6 个 `*LoggingPlugin` 已改为**直接经 `PluginLogWriter` 打 SLF4J**（见 `log/PluginLogWriter.java:11-14` 注释），**不**转交 `PluginEventSink` —— 这是 10-13 决策「同步插件应在请求链路内联完成」的结果。
- 审核事件：`ModerationGuard` (`moderation/ModerationGuard.java:91`) 调的是**独立的** `ModerationEventSink`（不实现 `PluginEventSink` 接口），仅打 SLF4J。

**结论**：`PluginEventSink` / `CompositePluginEventSink` 这一套异步基础设施是「保留作参考」，今天并未在主干生效；真正在跑的离链出口只有 `ModerationEventSink`。

### 1.4 数据层

- `kb_plugin_instances.plugin_type` 列已存在（`schema.sql:605`，`SYNC`/`ASYNC`）。
- `data.sql` 种子（776-813 行）**全部为 `SYNC`**，无 ASYNC 行。
- `PluginReconcileRunner` (`knot-service/.../PluginReconcileRunner.java:31-32,60-62`)：只给 SYNC 自动建行；ASYNC sink 仅纳入 entrypoint 集合**防误标孤儿，不建实例行**（dispatcher 只按 SYNC 分发）。

---

## 二、配置在哪里

| 项目 | 落点 |
|---|---|
| 配置类 | `knot-plugin/.../plugin/config/PluginSinkProperties.java`（`@ConfigurationProperties(prefix = "knot.plugin.sink")`） |
| 字段 | `kafka.enabled` / `kafka.topicPrefix`（默认 `knot.plugin`）/ `kafka.bootstrapServers` |
| 启用开关 | `PluginAutoConfiguration`（`@EnableConfigurationProperties`） |
| **实际配置值** | **`knot-gateway/src/main/resources/application.yml` 当前未配置 `knot.plugin.sink` 任何项** → `enabled` 默认 false，Kafka 占位 bean 为空转 |
| 依赖 | **pom 全无 kafka 依赖**（grep `spring-kafka` 无命中）→ 真做需给 `knot-plugin/pom.xml` 加 `spring-kafka` |

模块边界红线（10-14）：Kafka 异步实现放 **`knot-plugin`**（gateway 已依赖 `knot-plugin`，见 `knot-gateway/pom.xml:20`）；admin 不依赖 `knot-plugin`/`knot-gateway`，故 Kafka bean 只在网关进程生效，符合红线。

---

## 三、逻辑如何切入（实现方案）

异步插件 = 「SYNC 插件 emit 事件 → `CompositePluginEventSink` 扇出 → 各 `PluginEventSink` 消费」。要让 Kafka 真正收到数据，**必须有一个 emit 源**。当前缺口即此。

### 3.1 推荐：ModerationEventSink 接 Kafka（最低风险）

`ModerationEventSink` 自身注释已写明意图（`moderation/ModerationEventSink.java:8-13`）：

> 已确认：不必等 Kafka 就绪 —— 本期结构化日志先行……Kafka 配齐后在本类内切到 `${prefix}.moderation.hits` topic，主链路无感。

落地步骤：

1. **加依赖**：`knot-plugin/pom.xml` 加 `spring-kafka`（或原生 `kafka-clients`）。
2. **实现发送**：在 `ModerationEventSink` 内注入 `KafkaSinkPublisher`（由 `PluginSinkProperties.kafka` 构建 `KafkaTemplate`/`Producer`），`emit()` 中当 `enabled=true` 时 `send("${topicPrefix}.moderation.hits", event)`；SLF4J 日志保留作兜底。
3. **失败策略 fail-open**：Kafka 发送异常 catch 后仅 `log.warn`，**不**抛回主链路（审核事件丢失不得阻断请求/响应）。
4. **配置**：`knot-gateway/application.yml` 补：
   ```yaml
   knot:
     plugin:
       sink:
         kafka:
           enabled: ${KNOT_PLUGIN_KAFKA_ENABLED:false}
           topic-prefix: ${KNOT_PLUGIN_KAFKA_TOPIC_PREFIX:knot.plugin}
           bootstrap-servers: ${KNOT_PLUGIN_KAFKA_BOOTSTRAP:}
   ```

此方案**不触碰 10-13 红线**（只动审核业务事件，不动链路日志直出 SLF4J 的决策），改动面最小。

### 3.2 可选（后续）：链路日志进 Kafka

若要把 6 类链路日志也发 Kafka，需让 `*LoggingPlugin` 经 `PluginLogWriter` 同时 emit `PluginEvent` 到 `CompositePluginEventSink`，由 `KafkaPluginEventSink`（把占位类升级为真实实现，topic = `${prefix}.${eventType.code()}`）消费。

⚠ **这会推翻 10-13 决策**（「同步日志直出 SLF4J，不转交 sink」）。须先与 robin 对齐再动，本文档不默认执行。

### 3.3 管理面可见性（可选）

`kb_plugin_instances` 若要让 Kafka 异步插件出现在管理台，需在 `data.sql` 补 ASYNC 种子行：
```sql
INSERT IGNORE INTO kb_plugin_instances (..., plugin_type, ...) VALUES
(..., 'knot-plugin/.../KafkaPluginEventSink', ..., 'ASYNC', 0, 0);
```
（`PluginReconcileRunner` 不会为 ASYNC 自动建行，须手动种子；ASYNC 行无 capability/extension/stage 语义，相关列填空或约定值。）

---

## 四、决策点（待确认）

| # | 问题 | 建议 |
|---|---|---|
| D1 | Kafka 异步插件首批接**哪类事件**？ | 仅审核命中（3.1，低风险）；链路日志（3.2，需推翻 10-13） |
| D2 | 占位类 `KafkaPlaceholderPluginEventSink` 是否**就地升级**为真实实现，还是另起 `KafkaModerationEventSink`？ | 审核事件走 `ModerationEventSink` 内嵌发送（更契合其注释）；占位类可保留为链路日志的将来实现 |
| D3 | 是否需要在 `kb_plugin_instances` 落 ASYNC 种子行（管理面可见/可开关）？ | 暂不需要；Kafka 开关由 `application.yml` 控制即可 |

> 未获确认前**不写实现代码**，仅本方案文档。确认后按 D1/D2/D3 落地并跑门禁（gateway `mvn test` + 真启 jar 冒烟）。

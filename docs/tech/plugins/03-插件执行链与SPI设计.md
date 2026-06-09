# 插件执行链与 SPI 设计

## 1. 设计原则

不要定义一个万能 `Plugin` 接口。  
应该按能力边界拆分 SPI，每个 SPI 只处理一类扩展问题。

## 2. 第一批推荐 SPI

### 2.1 GatewayFilterPlugin

用于请求生命周期扩展。

适用场景：

- 内容审查
- 请求头补充
- 请求体标准化
- 响应脱敏
- 审计埋点

建议阶段：

- `gateway.pre-auth`
- `gateway.post-auth`
- `gateway.pre-route`
- `gateway.pre-upstream`
- `gateway.post-upstream`
- `gateway.pre-response`

### 2.2 RoutePolicyPlugin

用于路由决策增强。

适用场景：

- 自定义候选目标打分
- 按成本优先排序
- 故障切换策略
- 区域就近调度

### 2.3 UpstreamProviderPlugin

用于供应商接入扩展。

可替代或收编当前：

- `UpstreamProviderAdapter`
- `UpstreamProtocolExecutor`
- 部分上游响应归一化逻辑

### 2.4 UsageExtractorPlugin

用于 Usage 与计费相关提取。

当前 `UsageExtractor` 已很接近这个形态。

### 2.5 ScheduledTaskPlugin

用于异步任务。

当前 `ScheduledTaskHandler` 已经是一个很好的雏形。

### 2.6 NotificationChannelPlugin

用于通知渠道扩展。

例如：

- Email
- Webhook
- 企业微信
- 钉钉
- Slack

## 3. 执行上下文设计

每类插件都应拿到强类型上下文，而不是裸 `Map`。

例如：

```java
public interface GatewayFilterPlugin {
    String pluginId();
    String stage();
    FilterResult apply(GatewayFilterContext context);
}
```

`GatewayFilterContext` 建议至少包含：

- traceId
- app
- consumer
- routingRule
- requestHeaders
- requestBody
- protocol
- selectedTarget
- upstreamResponse

## 4. 结果模型设计

结果不能只返回 `boolean`。

建议统一返回：

- `CONTINUE`
- `SKIP`
- `BLOCK`
- `REWRITE_REQUEST`
- `REWRITE_RESPONSE`

例如：

```java
public record FilterResult(
        FilterAction action,
        Map<String, Object> requestPatch,
        Object responseOverride,
        String errorCode,
        String message
) {}
```

## 5. 推荐扩展点清单

### 5.1 网关请求链路

- `gateway.pre-auth`
- `gateway.post-auth`
- `gateway.pre-route`
- `gateway.post-route`
- `gateway.pre-upstream`
- `gateway.post-upstream`
- `gateway.pre-billing`
- `gateway.post-billing`
- `gateway.pre-response`

### 5.2 上游接入

- `provider.request-adapter`
- `provider.response-adapter`
- `provider.usage-extractor`
- `provider.error-mapper`

### 5.3 策略层

- `route.target-filter`
- `route.target-score`
- `route.failover-policy`
- `traffic.limit-check`

### 5.4 系统任务

- `task.handler`
- `catalog.sync`
- `reconciliation.handler`

### 5.5 通知与观测

- `notify.channel`
- `audit.sink`
- `metrics.exporter`

## 6. 与现有代码的映射建议

| 现有接口 | 建议归属 |
|---|---|
| `UpstreamProviderAdapter` | `UpstreamProviderPlugin` |
| `UpstreamProtocolExecutor` | `ProtocolBridgePlugin` |
| `UsageExtractor` | `UsageExtractorPlugin` |
| `ScheduledTaskHandler` | `ScheduledTaskPlugin` |

这样可以平滑重构，而不是全部推翻。

---
description: knot 业务域规则：计费、频控额度、重试、模型路由、流式与 knot_extend
alwaysApply: false
---

> 本文件由 `docs/standards/generate-tool-rules.py` 自动生成，**勿手工编辑**。权威源：`docs/standards/rules/rules/60-business-rules.md`。

# 60 · 业务域关键规则

> 适用：计费 / 频控额度 / 重试 / 流式 / 模型路由 相关改动。完整索引见 `docs/standards/PROJECT-STANDARDS.md`。

## 1. 计费

- **三级回退**：模型 `billing_rule_code` → 模型族精确匹配 → `model_family IS NULL` 默认规则。
- TOKEN 计算器：`NormalizedUsageContext.pricingPlan()` 派生访问器（空/未知 → FIXED）；`TokenBillingModeCalculator` switch fixed / tiered / peakOffPeak + 共用 assemble；**单价唯一来源 `BillingConfig.PricingPlan`**。
- 缓存变长：命中 10min / 未命中 30s，key `(ruleCode, modelFamily)`。
- 厂商语义差异：OpenAI `prompt_tokens` **含**缓存 token，Anthropic **不含**；`AnthropicUsageExtractor` 加回 cache 才是总量语义。（口径归属与双视图纪律见 §8）
- billingMode(8) × pricingPlan(3) **正交**；前端 `utils/billingTier.ts` / `utils/billingPrice.ts`，`UNIT_PRICE_PRECISION = 8`。
- ⭐ `NormalizedUsageDetail.cost` 是 **String**（8 位小数 `toPlainString()`）——Jackson 默认序列化对 <1e-6 会输出科学计数法（`1E-8`）；唯一构造点 `AbstractBillingModeCalculator.detail()`。
- ⚠ config_json 是 `{pricing:{...}}` **嵌套结构**，扁平写会被 ignoreUnknown 静默成 `pricing=null`。
- ABSOLUTE 高低峰：`PhaseDecision` 四元组，取价走 `matchedRule().unitPrices()` **不乘倍率**；MULTIPLIER 模式不变。
- BillingUsage 按模式拆分：sealed 接口 + Token / Image / Video / Audio 四实现；媒体内嵌 `TokenBillingUsage`，`tokenUsage()` 默认方法。`UsageExtractor<T>`：`empty()` / `extractUsage(body, requestBody)` 返回 **`UsageExtractItem<T>` 双视图**（`billing` 计费输入 + `usage` 统一用量视图），详见 §8。

## 2. 频控与额度

- 分层（`TrafficResourceTypeEnum.supportsRateLimit/supportsQuota`）：模型 + 路由规则 = **只限流**；应用 + 供应商账户 + 消费者 = **只限额**。
- RPM 事前 +1 判 `used > limit`；TPM 事后累加、额度判 `used >= limit`。
- 成本计数用 Long；换算只能用 `movePointRight(COST_SCALE).setScale(0, HALF_UP)`，`COST_SCALE` 恒为 12（1e-12）。超出 long 可表示范围（约 922 万）由 `toScaledAmount` 明确抛 `BusinessException`，不静默溢出。
- ⭐ 金额对外契约（`knot_extend.billing`）：`detail[].cost` / `detail[].price` / `totalCost` **一律十进制普通字符串**，不设小数位上限（单价常见 6 位小数，除百万量级需 12 位以上才不归零）。
- ⭐ 所有计费单位基数都是 10 的幂（1 / 1e3 / 1e6；`PER_MINUTE=60` 已于 2026-10-17 退役）——「用量 × 单价 ÷ 基数」**恒定除尽**，不终止除法在这套单位体系里不可能出现；`DECIMAL128` 仅为未来非 10 的幂基数兜底（`BillingUnitEnumTest.everyUnitSizeIsPowerOfTen` 钉住该不变式）。新增单位若引入非 10 的幂基数，必须先在配置期把单价折算成基础单位价，不允许大基数进金额公式。
- ⭐ 拒绝契约：限流与限额**同为 429**（`GatewayTrafficRejectException` 单一 handler，靠 `type` + `code` 区分）；`error.detail` 只暴露 `resourceCode`；带 `Retry-After` 头。
- ⚠ `expireAtMillis()` 是**存储键 TTL**，`windowEndMillis()` 才是**重置时刻**，不要用错。

## 3. 重试

- 两层：retry → failover。`RetryPolicy` record 10 字段；默认 `retryOn = ["4xx","5xx"]`；simple / professional 双模式。

## 4. 模型 / 路由

- `kb_models`：`model_code` 是**内部业务码**；`upstream_model` NOT NULL，发往上游用。网关只靠 header（`Authorization` + `Rule: <code>`）。⚠ 目标解析**禁止依赖派生 `targetId`**。
- 模型池：池内必须是同一统一模型；改绑要求池内为空；`model_type` 列已删（join `kb_logical_models` 派生）。
- 模型 ↔ 统一模型关联走 `kb_provider_model_mappings`（uk on `model_code`，一模型唯一统一模型）。
- ⭐ options 两个统一模型参数语义分离：`logicalModelCode` = **硬过滤**（模型池编辑）；`preferredLogicalModelCode` = **纯排序**（路由规则目标选择器），SQL `order by (x.logical_model_code = #{p}) desc, <原次序>`，MariaDB DESC 下 NULL 尾随。
- 路由规则 `logicalModelCode` 非必填；选中后目标选择器**优先置前但不过滤**；派生 `logicalModelName`（join `kb_logical_models`，`display_name` 优先）。
- 统一模型选择器 `RemoteEntitySelect` 用 `value-key="modelCode"`；模型族编码 `^[a-z0-9][a-z0-9_-]*$`，引用数 > 0 禁删。

## 5. 流式 / knot_extend

- 上游流式（2026-10-20 批2 起，Netty 栈）：取流**只能** `retrieve().onStatus(...).toEntityFlux(DataBuffer.class)`。`exchangeToMono` 会在 handler 返回后调 `releaseBody()` 把未消费的响应体整个吞掉（`UpstreamWebClientStreamingTest` 钉住）。⚠ 旧规则「取原始响应用 `exchange((req,res)->res,false)`、不要再链 `.retrieve()`」作废——那是 Servlet 栈时期针对 RestClient 的写法，与现行 WebClient 实现矛盾，以代码为准。
- 流式测试（2026-10-20 起）：**MockMvc 已不可用**（网关是 Netty 栈，无 Servlet）。流式契约靠 ① 直接调方法 + 反射注解契约检查（`WebTestClient.bindToController` 的 mock codec 管线跑不通 multipart，别走）；② 真启冒烟 s9（首字节 ≪ 总耗时、`knot_extend` 恰好 1 次且在 `[DONE]` 前）。前端 SSE 仍走 fetch（30s 超时）。
- `knot_extend` = `{usage, billing}`，raw usage 不透传；整包作响应体字段，SSE 作独立 `data:` 事件（`[DONE]` 前插入）；**usage 用 snake_case，billing 用驼峰**。

## 6. 操作日志

- AOP + 拦截器双通道，强制记录增删改前后快照；`OperationLogService` 异步落库（`@Async`），查询同在此类（2026-10-15 由 `SystemService` 并入）。
- 忽略快照字段：`updatedAt` / `updated_at` / `updateTime` / `update_time`。

## 7. 运行时存储与 Redis 统一层（2026-10-15）

跨节点共享的运行时存储——频控计数 `TrafficCounterStore`、在途负载 `ModelLoadStore`——**两档选型 + 统一故障口径**。

| 档 | 语义 | 不切的后果 |
|---|---|---|
| `caffeine`（默认） | 进程内视图，单节点 | 频控实际限额 = 配置值 × 节点数；`LEAST_LOAD` 选路只看本节点在途 |
| `redis` | 全局视图，**故障 fail-hard** | Redis 抖动即 503 + 报警 |

**摘除状态 `TargetOutageStore` 不在这套词表内**：纯进程内 Caffeine，无 store 开关。摘除表达的是「本节点自己观察到的异常」，本就不需要跨节点共享。

### 7.1 故障口径：fail-hard，绝不静默放行（红线）

⭐ **Redis 不可用时一律抛 `RedisUnavailableException` → 网关 503 + ERROR 日志，没有例外。**

依据（2026-10-15 决策，robin 拍板）：这三个存储承载的是**跨节点共享的全局口径**，静默按放行值处理会产生**看不见的损失**：

| 存储 | 若按放行值静默处理 | 实际损失 |
|---|---|---|
| 频控计数 | 计 0（等于未产生用量） | 多节点实际限额悄悄涨到「配置值 × 节点数」，**直接超发且无感知** |
| 在途负载 | 记 0（等于不拥塞） | 所有模型被看成不拥塞，`LEAST_LOAD` 退化成权重序，**已过载模型继续被加压** |

结论：**宁可全拒 + 报警，人来决策；不可静默劣化。** 已下线的机制：`fail-open` 开关、`fallback` 降级档、`RedisFallbackSupport` 状态机、`RedisHealthResolver` 探针。

**报警通道**：ERROR 级日志（`RedisCache` 分钟级节流，按 `domain`/`operation`/`key` 写进消息文本供采集侧配规则）。全仓**无** webhook / Notifier / 邮件设施，不要自己发明。

### 7.2 结构约束

- ⭐ **Redis 访问只能走门面 `RedisCache`**（`gateway/redis/`）：统一异常分类（`RedisUnavailableException`）+ ERROR 日志节流。禁止在存储实现里直接注入 `StringRedisTemplate`。
- ⭐ **`RedisCache` 是实现细节、非对外 API**：门面**不提供任何 fail-open 入口**（无 `*Or` 方法）。业务层只依赖各域存储接口，`*StoreConfiguration` 按配置装配。改动时勿让业务层直接依赖门面。
- ⭐ **存量 `fallback` 配置必须启动失败**：`StoreModeResolver` 只认 `caffeine` / `redis`，配了 `fallback` 抛 `IllegalStateException`——宁可启动失败也不静默忽略。
- 配置契约：**业务域直接持有**，`knot.traffic.store` / `knot.load.store`（无 `cache` 中间层——cache 只是工具，traffic/load/outage 才是业务，新域直接 `knot.<域>.store` 接入）；⚠ 各域 Properties 的 `store` 字段**必须保持 `null` 默认值**——null 才代表「未显式设置」，归一器据此回落 `caffeine`。无旧键兼容层（本项目不做历史包袱）。
- 模式值无法识别 → **启动失败**，不静默按单节点跑。
- 命名：后缀限白名单（**没有 `Adapter` 后缀）——门面 `RedisCache`、模式枚举 `StoreMode`、归一器 `StoreModeResolver`（`gateway/store/`，各域共用的选型词表，不构成独立 cache 层）。
- ⚠ `RedisCache` / `Redis*Store` **无自动化单测**（本机无 testcontainers），只能靠真启冒烟；配置归一与 fail-hard 契约**有**单测（`StoreModeResolverTest` / `RedisCacheFailHardTest`）。

## 8. 用量提取：双视图一次提取与口径单一来源（2026-10-23）

上游用量有两类消费者：**计费**（按 sealed `BillingUsage` 分派计算器）与**对外展示**（`knot_extend.usage` 的 `ModelUsage`）。二者字段集不同（媒体计费的 `pixels`/`sound`/`reference` 不在 `ModelUsage`），**不可互相替代**，故一次提取同时产出双视图。

### 8.1 契约

- `UsageExtractor<T>` 返回 `UsageExtractItem<T>(T billing, ModelUsage usage)`；`empty()` / `extractUsage(...)` / `extractUsageBody(...)` / `extractEventStreamUsage(...)` 一律返回双视图。
- `billing` 恒非空（无用量时用对应 `empty()`）；`usage` 可为 `null`（无可归一化用量）。
- **不放原始 JSON**：原始 usage 仍由 `UsageAccounting.rawUsage` / `rawBody` 承载，供审计与 `upstreamUsage` 透出。

### 8.2 口径归属（核心红线）

⭐ **两侧 token 总量与缓存读写各档一律取自 `billing`**；只有「明细拆分」从原始 usage 补取（输入 image/video token、输出 reasoning token）与媒体计数兜底。唯一投影入口 `ModelUsageNormalizer.fromBilling(BillingUsage, rawBody)`。

⭐ **禁止启发式猜协议语义**：不得按「字段名是否存在」推断缓存是否已含在输入总量里。历史教训——`ModelUsageNormalizer` 曾以「有 `prompt_tokens` 就认为输入已含缓存」判语义，与计费链分属两条解析链、别名表与语义判断各自维护，必然漂移。协议语义由**提取器在自己的边界内一次性决定**（Anthropic 走 `withCacheCountedInInput()`，Converse 走 `BedrockConverseUsageSupport`）。

> 收益表述纪律：这套改造的价值是**消除口径分裂**（单一来源、只决策一次），**不是减少解析次数**——解析工作量仍在，别按性能收益来验收。

### 8.3 协议口径表

| 协议 | 输入总量是否已含缓存 | 处理 |
|---|---|---|
| OpenAI Chat Completions | 是 | 直接用上报的 `prompt_tokens` |
| OpenAI Responses | 是（按 OpenAI 语义） | 直接用 `input_tokens`；usage 可能在 `response.usage`，用 `UsageRawReader` 定位 |
| Anthropic Messages | 否 | `input_tokens + cacheRead + cacheWrite`（5m/1h 明细优先） |
| AWS Bedrock Converse | 否 | `inputTokens + cacheReadInputTokens + cacheWriteInputTokens` |

⭐ 协议差异的**消解点是 per-binding 提取器绑定**（`kb_model_api_bindings.usage_extractor` / `stream_usage_extractor`）。默认兜底 `DEFAULT-TOKEN` 按 OpenAI「已含缓存」语义。
⚠ 任何**非 OpenAI 缓存语义**的模型必须**显式绑定**对应提取器，不能靠默认兜底——否则输入侧会少算缓存。

### 8.4 输出层纪律与失败口径（以代码为准）

- ⭐ 输出层（`KnotExtendPayload`）**只读 `accounting.usage()`**，禁止二次解析原始 usage，禁止再走 `fromSource` 一类入口。
- ⭐ **没有兜底提取器**：`requireExtractor()` 预检到「显式配置了未知 extractor code」即在**响应头写出之前**抛 `IllegalStateException`；未配置而走默认提取器属**合法路径**，不抛。
- ⭐ **口径错配 fail-fast**：上游回了非零用量、提取器却提取成空 → `log.error` + 抛 `IllegalStateException`。判据 `hasPositiveNumber(rawUsage)`（递归查非零数值）用来区分「提取器读不出来」与「上游合法报零」。
  **绝不静默记零账**——零账会把问题推迟到对账才暴露，与 §7.1 的 fail-hard 同一哲学。
- 上游未上报 / 全零 usage → `log.warn`，**合法零用量，不失败**。
- 响应体非法 JSON → 抛 `IllegalStateException`，不静默降级成 `null`（否则「解析失败」与「上游没回 usage」无法区分）。

> 历史沿革：本域曾有一条 `adapter.extractUsage` 兜底链（提取器缺失时回落适配器实现），**现已移除**——配置错即失败，不再静默回落。

### 8.5 媒体计量

- 媒体计量（张数 / 秒数）进 usage 视图，取自 `billing`。
- ⭐ 双报模型（如 gpt-image-1 同时上报 token 与图片）**内嵌 token 必须保留**在 usage 视图，不得只出媒体计数（否则 `total_tokens` 从「有」变「无」）。
- 媒体 `BillingUsage` 的 `pixels` / `width` / `height` / `sound` / `reference` 只服务计费，**不因 usage 视图而改**。

### 8.6 SSE 合并

- 双视图**一起**参与 merge / 选择，禁止后续重建。接口默认 `extractEventStreamUsage` 用 `UsageExtractItem.mergeMax`：billing 按分量取最大，usage 取累计 token 总量更大的完整视图。
- ⚠ 生产 `ProxyStreamingFlux.UsageState.merge` 按 `totalTokens()` 选最大事件：**纯媒体响应 token=0 时会保首事件**（既有局限，已知，本批未改）。

### 8.7 不变量与测试

```
billing.inputTokens  == usage.input.tokens.total()
billing.outputTokens == usage.output.tokens.total()
billing.totalTokens  >= billing.inputTokens + billing.outputTokens
usage.totalTokens    >= usage.input.tokens.total() + usage.output.tokens.total()
```

`usage.input.tokens.total()` 含缓存读写与 unclassified；`usage.output.tokens.total()` 含 reasoning（reasoning 是 output 的组成部分，计费上计入 output 总量，展示才拆出）。

新增协议 / 字段必须补三类测试：协议字段归一、双视图不变量、全链路（registry → accounting → payload）。

### 8.8 契约变更纪律

- ⭐ 一次性迁移，**不留旧返回类型的兼容重载**（项目不做历史包袱）。
- 改前全量 grep 调用方，改后再 grep 残留。调用方清单必须含：**接口默认方法**、`UsageExtractorRegistry`（含 adapter 兜底）、影子链 `BillingFactAssembler`、路由判定、**所有提取器测试**与用到 `UsageAccounting.of(...)` 的测试构造点。
- ⚠ 影子链只消费 `billing` 半边，不必强制其产出 usage；路由判定（`RoutingTestBillingVerdictService`）只镜像选 extractor code，真正解析委托影子链——**不存在第三条别名字链**。

---
trigger: model_decision
description: knot 业务域规则：计费、频控额度、重试、模型路由、流式与 knot_extend
---

> 本文件由 `docs/standards/generate-tool-rules.py` 自动生成，**勿手工编辑**。权威源：`docs/standards/rules/60-business-rules.md`。

# 60 · 业务域关键规则

> 适用：计费 / 频控额度 / 重试 / 流式 / 模型路由 相关改动。完整索引见 `docs/standards/PROJECT-STANDARDS.md`。

## 1. 计费

- **三级回退**：模型 `billing_rule_code` → 模型族精确匹配 → `model_family IS NULL` 默认规则。
- TOKEN 计算器：`NormalizedUsageContext.pricingPlan()` 派生访问器（空/未知 → FIXED）；`TokenBillingModeCalculator` switch fixed / tiered / peakOffPeak + 共用 assemble；**单价唯一来源 `BillingConfig.PricingPlan`**。
- 缓存变长：命中 10min / 未命中 30s，key `(ruleCode, modelFamily)`。
- 厂商语义差异：OpenAI `prompt_tokens` **含**缓存 token，Anthropic **不含**；`AnthropicUsageExtractor` 加回 cache 才是总量语义。（口径归属与双视图纪律见 §8）
- billingMode(8) × pricingPlan(3) **正交**；前端 `utils/billingTier.ts` / `utils/billingPrice.ts`，`UNIT_PRICE_PRECISION = 8`。
- ⭐ `NormalizedUsageDetail.cost` / `.price` / `totalCost` 是 **String**——Jackson 默认序列化对 <1e-6 会输出科学计数法（`1E-8`）。**不设小数位上限、不做 8 位定标**（8 位会把「6 位小数单价 × 1 token ÷ 1e6」抹成 0）：唯一文本化出口 `NormalizedBillingAmount.plainMoney`，唯一构造点 `AbstractBillingModeCalculator.detail()`。
- ⭐⭐ **每一行明细都必须满足 `cost = tokens × price ÷ 1e6`**（2026-10-27）：`price` 恒为**每百万单位单价**（由 `pricePerMillion` 折算），不是金额本身——这是下游「只凭消息复算」的唯一依据。附加费这类「与用量无关」的常数项也必须走 `detail()`：`tokens=1`、单位基数 `1`，price 自然折成 `surcharge × 1e6`。**禁止**自己 `new NormalizedUsageDetail(...)` 拼行。判例：`VIDEO_SURCHARGE` 曾把总额直接塞进 price，`type` 与 `cost` 全对、静态检查与肉眼均无异常，只有复算式能抓（差 1e6 倍）。守门：`VideoBillingModeCalculatorTest#everyDetailRowShouldSatisfyAmountTimesPriceFormula` 全行遍历，新增明细行自动纳入。
- ⚠ **计费视图空值整项省略**（`NormalizedUsage` 类上 `@JsonInclude(NON_NULL)`，2026-10-27）：`billingVersion`（版本号经 `trimToNull`）与 `currency` 均可空，不声明的默认行为会写出 `"billingVersion": null`；而同一 `knot_extend` 里的 `usage` 走 `ModelUsage` 的 `NON_DEFAULT`（空值省略）——一段响应两套空值口径，下游「字段缺席」判空在 billing 段失效。选 `NON_NULL` 而非 `NON_DEFAULT`/`NON_EMPTY`：视频按秒计费时 `total_tokens` 合法就是 `0`，被「看起来为空」吞掉会让数仓误判成上游未上报。守门：`NormalizedUsageSerializationTest`。
- ⭐⭐ **计费单位归一化唯一出口 `BillingModeEnum.normalizeUnit(code)`**（2026-10-25，4.6 审查所修）：空白 / 非法编码 / 该模式不支持 → 一律取 `defaultUnit()`。**禁止**在计算器、Service、DB 默认值里各自写「兜底成 `1K_TOKENS`」——单位基数是金额公式的**分母**，`1K_TOKENS`(1000) 与 `PER_SECOND`(1) 差 1000 倍，而正确默认值随 `billing_mode` 变（VIDEO→PER_SECOND、TOKEN→1M_TOKENS），不存在万能默认值。判例：曾有「计算器热路径 / 管理端落库 / schema DEFAULT」三处都兜底成 `1K_TOKENS`，绕过表单写入即静默 1000 倍错价且全门禁绿灯。
- ⚠ **折扣（`kb_provider_discount_policies.discount_value`）仅供展示，不参与任何计费逻辑**（robin 2026-10-25 明确）：不要把折扣接进金额计算，也不要为「将来可能接入」去改折扣链路（精度/校验/DTO 类型都不必为此铺路）。折扣值用 `BigDecimal`（对齐 `DECIMAL(10,4)`）仅为与库列同型与健壮性。
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
- `knot_extend` = `{usage, billing}`；整包作响应体字段（`body.knot_extend`），SSE 作独立 `data:` 事件（`[DONE]` 前插入）；**usage 用 snake_case，billing 用驼峰**。
- 上游 usage **原文**在 SSE 事件里以并列的 `usage` 字段透出（`{knot_extend, usage}`，与 `knot_extend` 同级；`return_usage_detail` 关闭或无原文时不出现）。⚠ 整包（非流式）路径目前**只回 `knot_extend`**，不含原文——两条路径的这一差异是现状，改前先确认下游是否依赖。
- ⭐ **错误前置**：流一开始转发，响应头即已 commit，此后任何错误都无法再用 HTTP 状态反馈，只能沉进日志。因此**能预检的错误必须在建立上游连接 / 写出响应头之前暴露**（先例：`AbstractUpstreamProtocolExecutor.executeStreaming` 前的 `requireExtractor(true, context)`）。
  ⚠ 预检必须与正式提取**共用同一套选择逻辑**。若做不到（如整包路径预检时尚无响应体、`isEventStream` 判不了），**宁可不预检，也不要传一个猜出来的标志位**——改在该路径如实标注「失败发生在提取阶段」。

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

上游用量有两类消费者：**计费**（按 sealed `BillingUsage` 分派计算器）与**对外**（`knot_extend.usage` / Kafka `usage` 的 `ModelUsage`）。两者**形状不同但事实同源**：
`ModelUsage` 是 `BillingUsage` 的**无损投影**（2026-10-27 扩容后，媒体维度经 `attributes` 全量透出），
判据见 §8.12。它们仍然**不可互相替代**——`BillingUsage` 按模式分型、承载计费语义，`ModelUsage` 是对外稳定契约，
故一次提取同时产出双视图。

### 8.1 契约

- `UsageExtractor<T>` 返回 `UsageExtractItem<T>(T billing, ModelUsage usage)`；`empty()` / `extractUsage(...)` / `extractUsageBody(...)` / `extractEventStreamUsage(...)` 一律返回双视图。
- `billing` 恒非空（无用量时用对应 `empty()`）；`usage` 可为 `null`（无可归一化用量）。
- **不放原始 JSON**：原始 usage 仍由 `UsageAccounting.rawUsage` / `rawBody` 承载，供审计与 `upstreamUsage` 透出。

### 8.2 口径归属（核心红线）

⭐ **两侧 token 总量与缓存读写各档一律取自 `billing`**；只有「明细拆分」从原始 usage 补取（输入 image/video token、输出 reasoning token）与媒体计数兜底。唯一投影入口 `ModelUsageNormalizer.fromBilling(BillingUsage, rawBody)`。

⭐ **禁止启发式猜协议语义**：不得按「字段名是否存在」推断缓存是否已含在输入总量里。历史教训——`ModelUsageNormalizer` 曾以「有 `prompt_tokens` 就认为输入已含缓存」判语义，与计费链分属两条解析链、别名表与语义判断各自维护，必然漂移。协议语义由**一个明确的所有者一次性决定**：Anthropic 由 `ANTHROPIC` 提取器走 `withCacheCountedInInput()`；AWS Bedrock Converse 由 Bedrock 适配器族的响应转换（`AwsBedrockConverseSupport.handleConverseResponse` 的 `toOpenAiUsage`，经 `AwsBedrockChatCompletionsToConverseUpstreamAdapter` 等四个适配器分发）一次决定——整包路径上用量提取吃的是 `handleResponse` 的产物，转换后客户端响应与计费读取**同形**，无需第二套解析（2026-10-25 删 `BedrockConverseUsageExtractor` / `BedrockConverseUsageSupport` 的依据）。

> 收益表述纪律：这套改造的价值是**消除口径分裂**（单一来源、只决策一次），**不是减少解析次数**——解析工作量仍在，别按性能收益来验收。

### 8.3 协议口径表

| 协议 | 输入总量是否已含缓存 | 处理 |
|---|---|---|
| OpenAI Chat Completions | 是 | 直接用上报的 `prompt_tokens` |
| OpenAI Responses | 是（按 OpenAI 语义） | 直接用 `input_tokens`；usage 可能在 `response.usage`，用 `UsageRawReader` 定位 |
| Anthropic Messages | 否 | `input_tokens + cacheRead + cacheWrite`（5m/1h 明细优先） |
| AWS Bedrock Converse | 是（转换后按 OpenAI 语义） | Bedrock 适配器族 `toOpenAiUsage`（`AwsBedrockConverseSupport`）把 camelCase usage 转成 OpenAI 形状（原样上报，不加回输入侧）；计费侧读转换产物，走 `DEFAULT-TOKEN`。**待实测（R2）**：`inputTokens` 是否已含缓存读写，当前按「不含」处理 |

⭐ 协议差异的**消解点是 per-binding 提取器绑定**（`kb_model_api_bindings.usage_extractor` / `stream_usage_extractor`）。默认兜底 `DEFAULT-TOKEN` 按 OpenAI「已含缓存」语义。
⚠ 任何**非 OpenAI 缓存语义**的模型必须**显式绑定**对应提取器，不能靠默认兜底——否则输入侧会少算缓存。

### 8.4 输出层纪律与失败口径（以代码为准）

- ⭐ 输出层（`KnotExtendPayload`）**只读 `accounting.usage()`**，禁止二次解析原始 usage，禁止再走 `fromSource` 一类入口。
- ⭐ **没有兜底提取器**：`requireExtractor()` 预检到「显式配置了未知 extractor code」即在**响应头写出之前**抛 `IllegalStateException`；未配置而走默认提取器属**合法路径**，不抛。
- ⭐ **口径错配 fail-fast**：上游回了非零用量、提取器却提取成空 → `log.error` + 抛 `IllegalStateException`。判据 `hasPositiveNumber(rawUsage)`（递归查非零数值）只用来区分「提取器读不出来」与「上游合法报零」；**能否为空本身由提取器声明**，见 §8.9。
  「静默记零账」是红线——零账会把问题推迟到对账才暴露，与 §7.1 的 fail-hard 同一哲学。
  ⚠ 2026-10-24 补充：三处提取失败**收敛到唯一处置点**，是否中断由 `knot.gateway.usage.degrade-on-failure` 决定（默认 `false` = 中断），见 §8.11。
- 上游未上报 / 全零 usage → `log.warn`，**合法零用量，不失败**。
- 响应体非法 JSON → 抛 `IllegalStateException`，不静默降级成 `null`（否则「解析失败」与「上游没回 usage」无法区分）。

> 历史沿革：本域曾有一条 `adapter.extractUsage` 兜底链（提取器缺失时回落适配器实现），**现已移除**——配置错即失败，不再静默回落。

### 8.5 媒体计量

- 媒体计量（张数 / 秒数 / 条数）进 usage 视图，取自 `billing`：输出侧 `output.image_count` / `audio_seconds` / `video_seconds` / `video_count`，**输入侧同样有** `input.image_count` / `audio_seconds` / `video_seconds`。
- ⭐ 双报模型（如 gpt-image-1 同时上报 token 与图片）**内嵌 token 必须保留**在 usage 视图，不得只出媒体计数（否则 `total_tokens` 从「有」变「无」）。
- ⭐ 媒体 `BillingUsage` 的计费属性（2026-10-27 起：`resolution` / `aspectRatio` / `mode` / `width` / `height` / `pixels` / `sound` / `voiceSpecified` / `referenceVideo`）经 **`ModelUsage.attributes`** 透出——它们是价目表的分档键，下游自算价必须拿到。值**原样保留厂商写法**，不做键空间归一。
- ⚠ 视频响应的 `data` 数组装的是**视频**，禁止回填进 `output.image_count`（会把条数错标成张数）；条数只走 `output.video_count`。`withBodyMedia` 对 `VIDEO` 一律跳过。

### 8.6 SSE 合并

- 双视图**一起**参与 merge / 选择，禁止后续重建。`UsageExtractItem.mergeMax` 的两侧**都逐字段合并**（2026-10-27 起）：token 明细取总量更大的**一整份**（明细必须自洽，不能跨事件按字段拼），媒体计量取 max，`attributes` 取先出现的非空值 / 布尔取或。规则与 `BillingUsage#mergeMax` 一致——两视图必须由同一套合并语义推出，否则「计费按合并后的量、展示按某一个事件的量」。
- ⚠ 生产 `ProxyStreamingFlux.UsageState.merge` 仍在**账目层整份选择**（`totalTokens` 更大者胜，相等后者覆盖），不逐字段合并：纯媒体事件 `totalTokens` 恒 0，会永久输给任一 token>0 的事件。这是**已知遗留**，与 `UsageExtractItem` 已修的口径不同层；修它要连 `UsageAccounting` 的合并语义一起定，未在 2026-10-27 批次内。

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

### 8.9 空用量语义的声明纪律（2026-10-24）

⭐ **「能不能为空」由提取器声明，注册表不做全局启发式。**（§8.2「禁止启发式猜协议语义」在本域的具体化）

- `UsageExtractor.allowsEmptyUsage()` 默认 `false`：上游 usage 存在正数却提取为空 → 口径错配，失败。
- 少数供应商的 usage 带本提取器不消费的正数字段（如 `reasoning_tokens`、媒体时长），
  导致 token 分项被读成全零——这属**合法**，由该提取器覆写为 `true` 声明放行。
- 门控形式：`!extractor.allowsEmptyUsage() && hasPositiveNumber(rawUsage)`。
  `hasPositiveNumber` 只负责**排除「上游合法报全零」**，不负责判断协议语义——两者职责不许合并。

⚠ **禁止用实例类型特判放行**（如 `instanceof 某个提取器`）。新增容忍能力一律走
「接口 default 声明 + 具体实现覆写」：语义与实现同处一处，不随类继承关系漂移，
且管理端下拉能看到它、可按模型绑定。先例 `EmptyAllowedTokenUsageExtractor`（code `TOKEN_ALLOW_EMPTY`），
取量口径与 `DEFAULT-TOKEN` 同一出口 `TokenBillingUsage.fromResponse`，唯一差别是声明允许全零。

⚠⭐ **`DEFAULT-TOKEN` 不得打开 `allowsEmptyUsage()`**：它是通用的错配探针，
一旦放行，「配错提取器」（如 camelCase 用默认提取器）就不再失败，§8.4 的 fail-fast 直接失守。

**用法与边界**：供应商合法返回全零 token 时绑定 `TOKEN_ALLOW_EMPTY`；它是**显式配置而非兜底**，
**不能**替代 §8.3 要求的专用提取器绑定（非 OpenAI 缓存语义、camelCase 仍须绑对应提取器）。

**测试必检三项**：声明允许 → 有正数也放行；未声明 → 有正数即失败；新提取器与同类提取器取量口径必须完全相等。

> 设计评审纪律：判定「某类错误能否提前暴露」时，先算清 fail-fast 的**真实触发面**。
> 本域 `resolveExtractorCode` 末行恒回落 `DEFAULT-TOKEN`，因此预检只在「显式配了错误 code」时触发，
> 未配置属合法路径——不要把「覆盖范围未配置」误写成「覆盖全部」。

### 8.10 影子链（离线复算）的失败语义（2026-10-24）

⭐ **影子链不是生产闸门，不抛异常——但回落必须可见。**

- `BillingFactAssembler` 是管理端**离线复算**，语义是「尽力算出来给你对照」。
  让它对配置错误（extractor 编码未知 / 缺失）抛异常，会让路由测试抽屉直接报错、用户看不到任何对照结果。
- 正确做法：**保留回落**，在 `BillingFact` 上**显式标记**（`extractorFallback` / `usageAbsent`），
  交给裁判层 / UI 判断是否告警。这同样满足「错误配置可见」，且不炸链路。
- 对照：生产链路（§8.4）对同类错误是 **fail-fast 抛异常**。二者语义不同，**不许互相套用**。

⚠ 判据：**这个错误会不会阻断用户看到结果？**
会 → 用标记（影子链 / 离线复算）；不会、或本就该阻断错误请求 → 抛（生产热路径）。

### 8.11 提取失败的中断开关（2026-10-24）

⭐ **一处判定失败原因，一处处置。** 三处「提取不出用量」的失败（响应体非法 JSON / 提取器没产出结果 / 口径错配）
在 `UsageExtractorRegistry` 里收敛成 `ExtractFailure` 枚举，由 `applyFailurePolicy` 这**唯一**入口决定抛出还是降级。

| 项 | 值 |
|---|---|
| 配置 | `knot.gateway.usage.degrade-on-failure`（env `KNOT_GATEWAY_USAGE_DEGRADE_ON_FAILURE`） |
| 取值 | 二元：`false`（默认，抛异常中断响应）/ `true`（降级放行） |
| 归属 | `UsageExtractionProperties`（`knot-gateway` 的 `config` 包，`@EnableConfigurationProperties` 由 `UsageExtractionConfiguration` 开启） |

- ⭐⭐ **默认必须不降级（`false`）**：静默零账 = 这笔上游消耗永远收不回，且只在对账时才暴露。这是 §7.1 fail-hard 口径在提取域的落地。
- ⭐⭐ **开关的方向必须是「降级 = true」，不能写成「中断 = true」。**
  布尔未配置时天然是 `false`，即安全档位，不依赖字段初始化那行代码；反过来写成 `interrupt=true`
  就得靠显式 `= true` 兜底，漏一行、绑错一处就把红线放松了。**宽松行为一律让调用方显式打开**，
  与 §7.1「不提供 fail-open 开关、不静默降级」的取向一致。
- ⭐ 降级的语义是**放行 + 零账 + 保留上游原文**（`UsageAccounting.degraded(rawUsage, rawBody)`）：
  `billing` 置空即不计费，但 `rawUsage` / `rawBody` **必须留**——原文是事后重算（影子链 / 对账）的唯一依据，
  丢了就变成「无从追查的零账」。
- 降级产出的账目形状**与「上游未上报 usage」完全一致**（`isEmpty()` 为 true、`knot_extend` 不输出 billing 块），
  因此不引入任何下游新管道；区别只在日志级别（ERROR）与 `degrade=true` 标记。
- ⚠⚠ **未知 extractor code 不受本开关影响，恒 fail-fast**：那是配置错误而非提取失败，
  降级它等于让该绑定下的所有请求永久零账且无自愈路径。
  判据：**这个错误会不会每次请求都重现、且不会自己好？** 会 → 不能降级。
- 不论抛还是降，日志统一 `log.error`，固定带 `reason` / `extractor` / `protocol` / `rawUsageKeys` / `degrade` 五项——
  降级不是正常路径，必须保留告警面（`degrade=true` 是给告警规则区分档位用的）。
- **测试纪律**：处置只有一处，故**把开关两种取值各验一次**即可（`false` 一次、`true` 一次），
  不必按失败原因逐个验；另需单独钉死「开启降级后未知 code 仍然抛」。默认值本身也要有一条断言钉住
  （`assertFalse(new UsageExtractionProperties().isDegradeOnFailure())`），否则改默认值不会有测试报警。

⚠ 反模式：在三处失败点各写一遍 `if (degrade) ... else throw ...`。
新增失败情形就会漏改其中一处，且「抛还是降」的口径会随代码位置漂移。

### 8.12 对外用量视图是计费事实的无损投影（2026-10-27）

⭐⭐ **`ModelUsage` 必须能从 `BillingUsage` 无损重建**：计费事实每加一个字段，
`ModelUsage` 就要有对应出口，否则下游「只凭消息自己算钱」的能力静默丢失。

- 判据（加字段时逐条自查）：**这个字段影响价格吗？** 影响 → 必须在 `ModelUsage` 有出口
  （新增 `input` / `output` 计量，或进 `attributes`）；不影响 → 不进。
- 投影必须用 **sealed `switch` 穷尽分派**（`ModelUsageNormalizer.projectFacts`），不得用 `instanceof` 链：
  新增一种 `BillingUsage` 时编译器会强制回来补投影。
- `attributes` 只装**本次请求声明的计费属性**（分辨率 / 画幅 / 模式 / 宽高像素 / 有声 / 音色 / 参考视频），
  来源是**请求体**而非上游 usage。值**原样保留厂商写法**，键空间归一是 Calculator 的内部动作。
- 「缺席即默认」：`@JsonInclude(NON_DEFAULT)` + 空串归一成 `null`（紧凑构造器做），
  读不到某一维度 ≡ 未按该维度分档，与取价回落默认档同口径。
- **用量三视图，单向派生**：`upstream_usage`（上游原文，只透传）→ `usage`（`ModelUsage`）
  → `billing`（金额，规则内容不下发）。Kafka 消息三者并列同级；`upstream_usage` 空对象归 `null` 整段省略。
- 守门测试：`ModelUsageNormalizerTest` 的「字段对齐」用例逐字段断言 `VideoBillingUsage` 的 11 个事实都有出口。
  **加计费字段必须同步扩这条用例**，否则「有损失败」不会被任何断言发现。
- ⚠ 反面判例（2026-10-27 修复）：`videoCount` 无出口、输入侧三字段是死字段、视频 `data` 被误标成
  `output.image_count`、SSE 下两视图用不同合并语义（计费 5 秒 / 展示 0 秒）——四个缺陷都是「投影有损」
  的不同形态，且当时**没有任何测试报警**。

### 8.13 媒体档位事实的取数入口：一个类、一次解析（2026-10-28）

⭐⭐ **媒体用量的「档位事实」由一个记录承载、一次解析取齐**：
`AbstractVideoUsageExtractor#facts` → `VideoUsageFacts`（10 个分量：输出时长/条数/分辨率/画幅/模式、
输入时长/图张数、有声/音色/参考视频）。**禁止再按档位加 `protected` 钩子**。

判据（为什么不能一档一个方法）：

- **同一份输入被反复扫**：十个钩子 = 请求体键表扫十遍、`usage` 子对象查十遍；
- **维度清单散在签名里**：加一维要动基类 + 每个子类 + 组装点，**漏任一处的表现是少一路钱且不报错**
  （只在月结对账时显形）。聚成记录后加一维 = 记录加一分量 + `of()` 加一行 + 投影加一行。

厂商差异的唯一写法：覆写 `facts()`，用 `VideoUsageFacts.of(body, requestBody).toBuilder()` 只改自己那几项。

- ⚠⚠ **禁止在子类里重新 `new VideoUsageFacts(...)` / `builder()` 从零构造**：那会把通用解析的结论全部丢弃。
  判例（2026-10-28 探针）：Kling 改为从零构造后，分辨率、输入图张数、输出条数三处同时变红——
  若只在生产上发生，就是「档位回落默认价 + 少收分量」。
- ⚠ **解析层不做兜底**：未声明给空（字符串 `null`、数值 0、布尔 false），归一与兜底归模型/计费
  （`VideoBillingUsage` 的紧凑构造器与 `billedVideoCount()`）。兜底是计费语义，不是解析语义。
- ⚠ **不要拿 `VideoBillingUsage` 当解析载体**：它是计费模型，受「明细行 = 量 × 价」与
  `ModelUsage` 无损投影约束；事实里允许存在**只记录不进计费**的观察值，那种字段不该进模型。
  边界：**事实类认识键表，模型只认识自己**。
- 守门测试：`VideoUsageFactsTest`（① 10 项一次取齐；② `toBuilder` 只改指定项；③ `usage` 优先于顶层；
  ④ `present`/`flag` 两种语义不被合并）。新增一维必须扩第一例，否则漏解析不会被任何断言发现。

### 8.14 异步任务档案的表达式：语义进配置，失败口径分两类（2026-10-28）

⭐⭐ `kb_async_task_profiles` 里 **三个 `*_path`（`task_id_path` / `status_path` / `result_path`）是
MVEL 取值表达式，三个 `status_*` 是 MVEL 布尔表达式**（变量 `status` = `status_path` 取到的文本、
`body` = 整个查询响应）。求值的**唯一出口**是 `AsyncTaskExpressionSupport`（`text` / `test`），
禁止各处自行 `MVEL.eval`。
**规则形态的判据**：旧的自研 JSON 路径语法只能表达「取某个节点」，而终态判定本质不是「取值相等」，
于是状态语义被迫一半在配置（逗号列表）、一半在 Java（字符串比较）；每加一种写法都要改代码发版。
**路径语法的适用边界是「知道结构」，表达式的适用边界是「知道意图」**——档案要的是后者。

⭐⭐ **两类求值的失败口径刻意不同，不许统一**：

- **取值失败 → `null`**：上游还没出字段、数组越界、上层为 null 都是常态，调用方解释成「还没出结果」。
  判 null 比判死任务安全。
- **判定失败 → 必须抛**（`IllegalStateException`，并指路迁移脚本）：旧值 `succeeded,failed` 在 MVEL 下
  是变量名，**能编译、求值期才抛**。若宽容成 `false`，任务会永远 RUNNING 直到超时结单——
  **配置错被伪装成上游慢，是最难查的一类事故**。
- ⚠ 编译与求值必须分开：`compiled()` 抛的也是 `RuntimeException`，若放在取值的 `catch` 里，
  「档案写错」会被当成「取不到」静静吞掉（判例：本条款落地时实测发现该缺陷，`text()` 已把编译提到 try 外）。

⚠ **MVEL 语法坑（实测，不是文档抄的）**：

- **没有 `in (...)` 元组语法**（`CompileException`），多值写 **`['a','b'] contains status`**；
- 裸标识符（`succeed`）编译通过、求值抛 `unresolvable property or identifier`；
- 缺失键 / 越界抛 `PropertyAccessException` / `StringIndexOutOfBoundsException`，**不返回 null**。

⚠ **表达式区分大小写**：旧实现是 Java 侧 `equalsIgnoreCase`，改表达式后按**所列字形**精确匹配；
需要宽匹配就在档案里写 `lower(status) == 'succeeded'`。**不要再把比较逻辑搬回 Java**。

⚠ **判定必须返回布尔**：返字符串/数字/null 一律抛。`status_running` 为空 = 除成功/失败外一律非终态；
配了却谁都不匹配 → 回 `RUNNING`（不判死）但 `log.warn`，否则上游新增状态会被当成正常轮询直到超时。

- 安全边界：表达式只由管理端/运维脚本写入（与 SQL 同权限级），求值时**只绑定数据**，
  不绑定 bean、不绑类加载器。
- 编译缓存 `ConcurrentHashMap<表达式原文, Serializable>`：改档案自然过期，无失效逻辑。
- 守门测试：`AsyncTaskExpressionSupportTest`（10 例，含「旧逗号列表必须抛且消息指路迁移脚本」探针）。
  双向探针实测：取值改上抛 → 3 例红；判定改宽容 `false` → 逗号列表那例红。

## 9. 上游适配器职责边界（2026-10-28）

### 9.1 视频适配器只做透传（红线）

⭐⭐ **视频 `UpstreamAdapter` 的 `buildRequestBody` 只做两件事：原样透传 + 剥掉客户端回推地址。
除此之外一律不做**——不转换请求形状、不按白名单校验、不代厂商补默认值、不注入任何
`external_task_id` 之类自己发明的字段。

判据一句话：**请求错误由调用方自己承担**。请求体该长什么样由客户端按上游文档自己写，
写错了让上游 400 原路返回。网关是代理，不是转译层。

为什么这条是红线（两个后果都已在真机上出过事）：

- **形状转换必然与上游文档漂移**：每家厂商的字段语义要在网关里重新实现一遍
  （prompt 放顶层还是 `contents`、参数进 `settings` 还是平铺、大小写归一……），
  上游一改文档网关就是一份过期副本，且错误在出站时才显形。
- **「顺手做兜底」会把错参静默吞掉**：判例——分辨率白名单之外的取值被折成裸串后
  **静默回落默认价**，一次错价要等月末对账才发现；`n>1` 被「纠正」成 1 等于收一次钱交一份货。
  拦（抛 400）与纠正（改成合法值）都不行：前者让网关承担了本属上游的校验口径，
  后者更糟，直接改写了客户端的意图。

✅ 正确形态（`KlingVideoUpstreamAdapter#buildRequestBody` 现在就是这个）：
`return VideoRequestSupport.passthroughBody(context);`

### 9.2 唯一必须做的事：剥 callback_url / webhook_url

⭐⭐ **客户端的回推地址绝不能漏给供应商**。所有视频适配器（含 `PassthroughUpstreamAdapter`
落到视频协议时的兜底路径）都必须剥，且必须**递归剥**。

- 判据不是「整洁」，是**计费完整性**：漏了它，供应商直接把终态推给客户端 → 客户端拿到产物
  却没经过 knot 的轮询与终态结算 → 那笔用量既查不到也收不回。
- 它同时是一个**可被客户端指定目标的出站地址**，安全面不该由客户端决定。
- **必须递归**：Kling 放 `options.callback_url`，Luma / MiniMax 放顶层。只扫一层等于只挡住一半厂商，
  **漏一家的表现是完全静默的**（没有任何运行期信号）。
- 字段名归一化后比较（忽略大小写与 `_` / `-`），名单唯一出口 `VideoRequestSupport.CALLBACK_FIELDS`；
  新增一家用了别的名字（如 `notify_url`）只在那里加一项。
- **不能原地改** `UpstreamRequestContext.requestBody()`：异步任务要拿它落 `request_json`
  供终态计费复现。故「无命中返回同一实例（零拷贝），有命中返回浅拷贝」。
- 兜底路径同样要守：**越是「没配适配器」的绑定越容易漏**。

### 9.3 归属：通用原语 vs 供应商私有

- 与供应商无关的（本节的剥 callback）住 `adapter/support/VideoRequestSupport`；
  供应商特有的（如 Bedrock 的 SigV4、Converse 的字段改写）住 `adapter/support/<供应商>/`。
- ⚠ 别把厂商特有的「特殊情况」往 `VideoRequestSupport` 里加：那是 9.1 明令禁止的形状转换，
  只是换了个文件放。真要转换，说明该厂商确实特殊，写进它自己的 Support 并在此处留判例理由。
- 流式与否**不在适配器上声明**：协议枚举（`ModelApiProtocolEnum.streamSupported`）+
  执行器（`MediaProtocolExecutor` 只放行 `AUDIO_SPEECH`）两道已挡住视频。适配器再覆写一遍
  只会让人以为「是这个适配器关掉了流式」，改协议时找不到真正的地儿。
- 守门测试：`KlingVideoUpstreamAdapterTest`（透传/不校验/不注入/剥 callback 三路径）、
  `VideoRequestSupportTest`（递归、List、归一化、近似字段不误伤、零拷贝）、
  `PassthroughUpstreamAdapterTest#shouldStripCallbackFieldsForVideoProtocol`。

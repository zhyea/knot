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
- 厂商语义差异：OpenAI `prompt_tokens` **含**缓存 token，Anthropic **不含**；`AnthropicUsageExtractor` 加回 cache 才是总量语义。
- billingMode(8) × pricingPlan(3) **正交**；前端 `utils/billingTier.ts` / `utils/billingPrice.ts`，`UNIT_PRICE_PRECISION = 8`。
- ⭐ `NormalizedUsageDetail.cost` 是 **String**（8 位小数 `toPlainString()`）——Jackson 默认序列化对 <1e-6 会输出科学计数法（`1E-8`）；唯一构造点 `AbstractBillingModeCalculator.detail()`。
- ⚠ config_json 是 `{pricing:{...}}` **嵌套结构**，扁平写会被 ignoreUnknown 静默成 `pricing=null`。
- ABSOLUTE 高低峰：`PhaseDecision` 四元组，取价走 `matchedRule().unitPrices()` **不乘倍率**；MULTIPLIER 模式不变。
- BillingUsage 按模式拆分：sealed 接口 + Token / Image / Video / Audio 四实现；媒体内嵌 `TokenBillingUsage`，`tokenUsage()` 默认方法。`UsageExtractor<T>`：`empty()` + `extractUsage(body, requestBody)`。

## 2. 频控与额度

- 分层（`TrafficResourceTypeEnum.supportsRateLimit/supportsQuota`）：模型 + 路由规则 = **只限流**；应用 + 供应商账户 + 消费者 = **只限额**。
- RPM 事前 +1 判 `used > limit`；TPM 事后累加、额度判 `used >= limit`。
- 成本纳元（1e-8）用 Long；换算只能用 `movePointRight(8).setScale(0, HALF_UP)`，`COST_SCALE` 恒为 8。
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

- Spring 6.2：`exchange(Class, boolean)` / `onStatus` 已移除；取原始响应用 `exchange((req, res) -> res, false)`，**不要**再链 `.retrieve()`。
- MockMvc 测 `StreamingResponseBody` 必须 `asyncStarted()` + `getAsyncResult()`。前端 SSE 走 fetch（30s 超时）。
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

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

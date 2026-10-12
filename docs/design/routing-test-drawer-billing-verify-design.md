# 路由规则测试抽屉 · usage 解析 + 计费校验 技术方案

> 文档状态：**设计稿，源码零改动，待评审**。
> 最后更新：2026-10-12。

---

## 1. 核心目的

**用 jev 模型作探针，在路由规则测试抽屉里快速校验 usage 解析与计费运算是否准确。**

- 现状痛点：路由规则测试抽屉（`knot-front/src/components/routing/RoutingRuleTestDrawer.vue`）直连网关（9090）拿原始响应，只展示 Body / 流式 / Header；usage 与计费完全是黑盒，只有真实流量出账后才看得到。
- 本方案把抽屉升级为**可校验探针**：测试拿到响应体后，复用生产同款链路做一次离线 dry-run，把「原始 usage → 解析结果 → 计费算式」三段透明摊开，让人当场核对准不准。
- 选 jev 作探针的依据（其特点恰好适配「快速校验」）：
  - **input-only 计费，输出免费** → 计费退化为单一项 `input tokens × input 单价`，无 output/cache 干扰项，肉眼/一笔即可核对，是最干净的探针。
  - **不出文本、结构化、端到端 70–500ms** → 请求极快极便宜，可高频换请求体 / 换规则组合反复试。
  - **usage 经 OpenRouter / Vercel 等网关归一为 OpenAI 形态** → 抽屉验证的是真实生产解析链路，不是另写一套，结论可信。

---

## 2. jev 模型画像（设计约束，来自百度百科 2026-09）

| 项 | 真实情况 | 对设计的影响 |
|---|---|---|
| 本质 | TypeSafe AI 的「System One」决策模型，不生成文本，跳过自回归解码、用单 token logits 直接产出**结构化决策**（Choice 选选项 / Score 打分 / Noul 出 0~1 概率），每输出带选项概率分布 + 置信度 | 响应体是 JSON 决策，无常规 completion 文本；抽屉渲染层无需改，照常展示原始响应 |
| 计费 | **按输入 Token 计费，输出端免费** | jev 计费规则必须配成 input-only 定价（output 单价 = 0）；抽屉预览会如实显示 output 成本 = 0，正好用于核对规则没被错配输出价 |
| 接入形态 | 经 OpenRouter / Vercel AI Gateway 等归一为 chat-completions 形态 | 协议仍走 chat-completions 路线，usage 字段仍是 `prompt_tokens` / `completion_tokens` / `total_tokens`（OpenRouter 归一）；`JEV` 提取器委托 `TokenBillingUsage.fromResponse` 成立 |
| 可达性 | 仅经 TypeSafe API，不直对中国大陆开放 | 抽屉真启 jar 冒烟时，上游必须可达（走 OpenRouter 或代理）——这是**冒烟前置条件**，非设计阻塞 |
| 缓存 | 内部 KV-Cache 广播机制 | usage 大概率**不报** `cache_creation` / `cache_read` 字段；提取器如实给 0，不影响方案 |

---

## 3. 设计原则

1. **复用生产同款算法**：dry-run 复用 `knot-adapter` 的 `UsageExtractor` + `UsageNormalizationSupport` + `BillingModeCalculator`，与线上口径一致，杜绝「预览对、线上错」。
2. **抽屉不写解析逻辑**：抽屉只捕获响应体、转发后端 dry-run、渲染结果；jev 与非 jev 走同一通道，差异仅在 API 绑定指向哪个提取器。
3. **不加抽屉特例**：jev 通过 API 绑定 `usage_extractor='JEV'` 自动接入，抽屉无需为 jev 写分支。
4. **规则解析三级回退与生产一致**：显式 `ruleCode` → 模型族 → 默认规则。
5. **计费校验 = 仅预览**：把 raw → parsed → computed 派生链透明展示，便于人工逐项核对；不做自动断言 / 标红（保留可核对而非自动判定的定位）。

---

## 4. 架构与数据流

```
路由规则测试抽屉（捕获 responseBody）
   │  POST /api/routing-rules/{id}/test/billing
   │  { targetCode, protocol, responseBody, requestBody, stream, occurredAt? }
   ▼
RoutingRuleBillingDryRunService (knot-service)
   ├─ 1. 取 API 绑定 (modelCode + protocol) → usage_extractor / stream_usage_extractor
   ├─ 2. UsageExtractorCatalog.resolve(code) → JevUsageExtractor (jev) / DEFAULT (兜底)
   ├─ 3. extractor.extractUsageBody(responseBody, requestBody) → BillingUsage
   ├─ 4. 三级回退取规则:
   │        BillingRuleMapper.getActiveByRuleCode(model.billingRuleCode)
   │          └─ miss → getActiveByModelFamily(model.modelFamilyCode)
   └─ 5. UsageNormalizationSupport.normalize(billing, rule, requestBody, occurredAt, calculator)
            → NormalizedUsage
   ▼
返回 { rawUsage, billing, billingRule(含 matchedBy), normalized, trace }
```

| 步骤 | 复用组件 | 说明 |
|---|---|---|
| 2 | `UsageExtractorCatalog`（knot-adapter） | 与生产同一注册表；DEFAULT `@Order(0)` 兜底 |
| 3 | `UsageExtractor`（含 `JevUsageExtractor`） | `extractUsageBody` 已处理整包 / SSE 合并 |
| 4 | `BillingRuleMapper` 直查（knot-dal） | 不引 `GatewayDataService` 的 Caffeine 缓存，dry-run 直查即可 |
| 5 | `UsageNormalizationSupport` + `BillingModeCalculator`（knot-adapter） | 与生产归一化、计算同一套 |

---

## 5. 后端改动清单（文件级，落地时执行）

| 模块 | 文件 | 改动 |
|---|---|---|
| knot-adapter | `usage/extractor/JevUsageExtractor.java` | 新 `@Component`：`code="JEV"`，`billingModes={TOKEN}`，`calculator=TokenBillingModeCalculator`，`extractUsage` 委托 `TokenBillingUsage.fromResponse(body)` |
| knot-service | `service/RoutingRuleBillingDryRunService.java` | 新服务：拼上下文 + 取提取器 + 三级回退 + 调 `UsageNormalizationSupport` |
| knot-admin | `controller/RoutingRuleController.java` | 新端点 `POST /api/routing-rules/{id}/test/billing` + 入参 / 出参 VO |
| knot-dal | `mapper/BillingRuleMapper.xml` | 复用既有 `getActiveByRuleCode` / `getActiveByModelFamily`（已存在于 `GatewayDataService` 同类） |
| 数据库 | `kb_model_api_bindings` | jev 模型行 `usage_extractor='JEV'`（流式同形则 `stream_usage_extractor` 留空复用） |

**`JevUsageExtractor` 为何独立存在**：与 OpenAI 同形时，`extractUsage` 直接 `return TokenBillingUsage.fromResponse(body)`；独立命名只为 (a) 在 API 绑定里显式挂 `JEV`（而非隐式 DEFAULT），(b) 给 jev 后续任何字段分歧留单点修改入口，不污染 DEFAULT。

---

## 6. 接口契约

**入参**
```json
{
  "targetCode": "jev-xxx",
  "protocol": "CHAT_COMPLETIONS",
  "responseBody": "{...上游整包或 SSE 文本...}",
  "requestBody": {},
  "stream": false
}
```

**出参**
```json
{
  "rawUsage": { "prompt_tokens": 120, "completion_tokens": 1, "total_tokens": 121 },
  "billing": {
    "type": "TOKEN",
    "inputTokens": 120, "outputTokens": 1, "totalTokens": 121,
    "cacheReadTokens": 0, "cacheWriteTokens": 0,
    "cacheWrite5mTokens": 0, "cacheWrite1hTokens": 0
  },
  "billingRule": {
    "id": 42, "code": "br-jev-input", "matchedBy": "EXPLICIT|FAMILY|DEFAULT",
    "billingMode": "TOKEN", "currency": "USD", "versionCode": "v1"
  },
  "normalized": {
    "totalTokens": 121, "totalCost": 0.00018, "currency": "USD",
    "billingVersion": "v1",
    "details": [
      { "dimension": "uncachedInput", "amount": 120, "unitPrice": 0.0000015, "cost": 0.00018 },
      { "dimension": "output", "amount": 1, "unitPrice": 0, "cost": 0 }
    ]
  },
  "trace": "JEV 提取器命中；规则命中来源=EXPLICIT"
}
```

无 usage / 未绑规则时 `normalized=null` + `trace` 注明原因（便于识别「这次不会计费」）。

---

## 7. 前端改动（`RoutingRuleTestDrawer.vue`）

- **触发时机**：`runBufferedTest` 成功、`runStreamingTest` 走到 `finishStream("SUCCESS")` 后，用已捕获的 `testResult.responseBody`（流式 = `streamBody`）+ `activeTarget.targetCode` + `activeProtocol` 调新端点。
- **新增两个 tab，升级为「raw → parsed → computed 透明派生链」**（非纯展示，便于核对）：
  - **Usage 解析**：左列上游原始 `usage`（`prompt_tokens` / `completion_tokens` / `total_tokens`），右列解析后 `billing` 视图（token 各维度）；逐项对齐，解析错没错一眼可见。
  - **计费校验**：摊开算式「规则 input 单价 × 解析出 inputTokens = 计算费用」；**output 成本必须恒为 0**（非 0 即 jev 规则被错配输出价，立即暴露）；`matchedBy` 标 `EXPLICIT / FAMILY / DEFAULT` 确认路由到的规则是预期那条。
- **空态**：`normalized` 为空时 `el-empty`「未匹配到计费规则 / 响应无 usage」。

---

## 8. 验证门禁（落地时执行）

| 门 | 命令 | 判据 |
|---|---|---|
| 后端 | `mvn -f knot-server/pom.xml test` | BUILD SUCCESS（新增 `JevUsageExtractorTest` + `RoutingRuleBillingDryRunTest`） |
| 前端类型 | `vue-tsc --noEmit` | EXIT=0 |
| 前端构建 | `vite build` | EXIT=0 |
| 冒烟 | 真启 admin + 经可达上游（OpenRouter / 代理）用 jev 发请求 → 看两新 tab | 命中 JEV 提取器、规则 `matchedBy` 正确、input 费用与规则单价吻合、output 成本 = 0 |
| 清理 | 杀进程 / DROP 一次性库 | 零残留 |

---

## 9. 实施批次建议（按风险排序）

1. **批次 1（后端核心）**：`JevUsageExtractor` + `RoutingRuleBillingDryRunService` + 端点 + 单测。门禁：`mvn test`。
2. **批次 2（前端）**：抽屉两新 tab + 触发接线（升级为透明派生链）。门禁：`vue-tsc` + `vite build`。
3. **批次 3（冒烟）**：真启 jar，jev 模型端到端验证（上游可达性前置），清理零残留。

---

## 10. 待确认 / 风险

- **上游可达性**：jev 不直对中国大陆开放，冒烟需经 OpenRouter 或代理；属批次 3 前置，非设计阻塞。
- **jev 实际 usage 字段**：以接入后实测为准；若流式 usage 位置/形态不同，需单独配 `stream_usage_extractor`。
- **cache_* 字段**：以实测为准，大概率不报；不影响方案。
- **计费校验深度**：当前按「仅预览」落地；若后续要自动断言 / 标红，作为独立增量，不在本方案范围。

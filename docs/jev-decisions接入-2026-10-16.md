# jev 模型接入（OpenRouter AlphaDecisions）交付报告

> 日期：2026-10-16 · 依据 `https://openrouter.ai/docs/api/api-reference/alphadecisions/submit-a-decisions-request`

## 1. 结论

jev（`typesafe/jev-1.13-20260917`）走 OpenRouter 独有的 AlphaDecisions 协议，**不是** OpenAI 兼容形态。knot 以新增 `DECISIONS` 协议 + `DECISION` 模型类型的方式完成全链路接入：网关 `POST /v1/decisions` → 协议过滤 → 透传上游 `/api/alpha/decisions` → usage 提取 → 计费。

三门（后端单测 / 前端类型 / 前端构建）+ 真启 jar 冒烟**全部通过**，端到端 200 且业务语义正确。

## 2. 上游契约（文档事实）

| 项 | 内容 |
|---|---|
| 端点 | `POST https://openrouter.ai/api/alpha/decisions` |
| 认证 | `Authorization: Bearer <management key>`（普通 key 403） |
| 请求 | `model`(必填) / `state`(必填, string\|object\|array) / `questions`(必填, 值四类变体 `noul`/`choice`/`score`/`box`) / `provider` / `session_id`(≤256) / `trace` / `user` |
| 响应 | `answers`(必填，与 questions 一一对应) / `model` / `usage{cost?, input_tokens, output_tokens}` / `id` / `provider` |
| 错误码 | 400/401/402/403/404/413/429/500/502/503/524/529 |
| 流式 | 无 |

## 3. 改动清单

| 类型 | 文件 | 说明 |
|---|---|---|
| 主 | `knot-common/.../ModelApiProtocolEnum.java` | +`DECISIONS("DECISIONS","Decisions","/v1/decisions",false)` |
| 主 | `knot-common/.../ModelTypeEnum.java` | +`DECISION("DECISION","决策",14,DECISIONS)`（`ModelService:680` 保存 binding 时强制 `supportsProtocol` 校验，不新增类型则协议绑不进去） |
| 新增 | `knot-gateway/vo/request/DecisionsRequest.java` | 请求 VO，显式声明 questions/state/provider/session_id/trace/user |
| 新增 | `knot-gateway/controller/GatewayDecisionsController.java` | `POST /v1/decisions` 入口 |
| 主 | `knot-gateway/.../protocol/UtilityProtocolExecutor.java` | `PROTOCOLS` +`DECISIONS`（非流式 buffered） |
| 数据 | `db/data.sql` | +预设用例 `builtin-decisions-basic`（DECISIONS） |
| 迁移 | `docs/database/migration/2026-10-16-decisions-test-preset.sql` | 既有库幂等补录同一条预设 |
| 测试 | `DecisionsRequestTest`（新，5 例）、`DecisionsUsageTest`（新，2 例）、`UtilityProtocolExecutorTest`（新，2 例） | 契约与执行器归属 |
| 测试 | `ModelTypeEnumTest` / `ModelServiceTest` / `EnumOptionRegistryTest` | 三处「13 个模型类型」硬断言同步为 14（踩坑点） |

**零改动**：前端（协议与模型类型下拉均由 `EnumOptionRegistry` + `/api/models/types` 动态提供，无硬编码）、DB schema（`kb_model_api_bindings.protocol` 为 `VARCHAR(64)` 无约束，路径走 `api_path` 配置）。

**未新增**（有意为之）：请求适配器复用 `PASSTHROUGH`（@Order(0) 已覆盖全协议，`GatewayRequestHandler.withTargetModel` 自动把 model 覆盖为 `kb_models.upstream_model`）；用量提取器复用 `DEFAULT-TOKEN`（`TokenBillingUsage.from` 已认 `input_tokens`/`output_tokens` 别名）；上游 `cost` 不参与计费（knot 按自有计费规则算，仅随原始 usage 透传）。

## 4. jev 配置口径

- provider account：`base_url=https://openrouter.ai`，auth=Bearer
- model：`model_type=DECISION`，`upstream_model=typesafe/jev-1.13-20260917`，计费规则走 TOKEN 模式
- binding：`protocol=DECISIONS`，`api_path=/api/alpha/decisions`，`request_adapter` 留空，`usage_extractor=DEFAULT-TOKEN`

## 5. 门禁结果（真实 exit code）

| 门 | 命令 | 结果 |
|---|---|---|
| 后端全量 | `mvn -f knot-server/pom.xml test` | **BUILD SUCCESS，EXIT=0**，7 模块 582 例 0 失败 |
| 前端类型 | `npx vue-tsc --noEmit` | **EXIT=0**（无输出） |
| 前端构建 | `npx vite build` | **EXIT=0**（built in 8.89s） |
| 真启冒烟 | `.workbuddy/audit/smoke_decisions.sh` | **200 通过**，见下 |

日志：`build-decisions-full.log` / `build-decisions-typecheck.log` / `build-decisions-vite.log` / `smoke-decisions.log`。

## 6. 冒烟证据（一次性库 + 假上游）

| 判据 | 实测 |
|---|---|
| 状态码 | `200`，`X-Knot-Model: jev-decision`（协议过滤只留 DECISIONS 绑定候选） |
| 请求体 | 上游收到 `model=typesafe/jev-1.13-20260917`（客户端传值被覆盖）、`questions=[is_bug,team,urgency]`、`state` 原样，路径 `/api/alpha/decisions` |
| 响应体 | `answers` 三类齐全（noul 0.96 / choice payments / score 1.99）+ `usage{476,70}` |
| 计费 | `knot_extend.usage` input 476 / output 70 / total 546；`billing.totalCost=0.00343 USD`（TOKEN_GPT4O：0.005×476/1000 + 0.015×70/1000 ✓） |
| 预设用例 | 新建库含 `builtin-decisions-basic / DECISIONS / status=1` |

## 7. 未验证项与风险

1. **真实 OpenRouter 端点未验证**：该端点要求 management key（普通 key 403），沙箱无凭据。冒烟走本地假上游（返回体取自官方文档示例）。配真 key 后需复核一次真链路。
2. **请求体携带 `stream` 的上游宽容度未验证**：VO 走 `@JsonInclude(NON_NULL)`，客户端不传则不输出，实际调用不受影响。
3. 上游 `box` 类型问题未做端到端验证（假上游只回 noul/choice/score 三类）。

## 8. Follow-up

1. 配真实 OpenRouter management key 后跑一次真链路冒烟（重点看 403/401 与真实 usage 口径）。
2. 如需把 jev 挂到模型池做 failover，按 DECISIONS 绑定到池内每个模型（协议过滤按绑定声明生效）。

# XFusionAI 外部模型源接入 — 2026-11-01

## 结论

XFusionAI（`llm.azopenai.com`，New-API 部署）已作为**第二个外部模型源**接入，与 OpenRouter 并列。
本次**只同步模型目录（186 个模型），不落定价**——robin 已拍板（2026-11-01 AskUserQuestion 确认：仅目录同步 + 前端改多源）。

## 改动清单

| 文件 | 类型 | 说明 |
|---|---|---|
| `knot-service/.../external/XFusionAIModelSyncProvider.java` | 新增 | 源码 `XFUSIONAI`，拉 `/api/pricing` 解析 186 条 |
| `knot-service/.../external/ExternalModelJsonSupport.java` | 新增 | 目录 JSON 取值工具（integer/epochSeconds/shortHash 等），XFusionAI 消费 |
| `knot-service/src/test/.../XFusionAIModelSyncProviderTest.java` | 新增 | 11 例，fixture 为实抓响应裁剪 |
| `knot-service/src/test/resources/external/xfusionai-pricing.json` | 新增 | 测试夹具（真实响应 6 条裁剪） |
| `knot-admin/.../schedule/XFusionAIModelSyncHandler.java` | 新增 | `XFUSIONAI_MODEL_SYNC`，cron `0 30 4 * * ?`，**默认停用**（与 OpenRouter 一致） |
| `knot-admin/src/main/resources/db/data.sql` | 修改 | 补 `kx_model_sources` id=2 与 `ks_scheduled_tasks` id=4 种子（仅空库生效） |
| `docs/database/migration/2026-11-01-xfusionai-model-source.sql` | 新增 | 存量库补两行种子（不写死 id，靠唯一键幂等）+ 自检 |
| `knot-front/src/views/model/ExternalModelManageView.vue` | 修改 | 同步按钮从写死 OPENROUTER 改为跟随「来源」筛选当前选中源 |

## 关键决策

1. **定价不结构化**：`model_ratio / completion_ratio / cache_ratio / model_price` 全留 `rawJson`。
   实测 ratio 是 New-API 相对倍率（`gemini-3.8-flash`=0.375 vs `gemini-3.1-pro-preview`=37.5，差 100 倍且不落官价量级），quota→金额换算基准不在响应里，解析成价格等于把猜测写进计费。等口径定了再做定价字段。
2. **上下文长度刻意留空**：源没有 `context_length`，只把「上下文 1M」写在中文 description/tags 里。不解析——文案格式不固定，正则抠数会静默变成假数据。`XFusionAIModelSyncProviderTest.leavesContextAndCompletionEmpty` 锁住该决定。
3. **`created_time=0` 视为没有**（源里 7 条如此），不落 1970-01-01；`ExternalModelJsonSupport.epochSeconds` 统一处理。
4. **不硬编码 `acw_tc` cookie**（CDN 会话凭据，写死必过期，实测非必需）；带 `New-API-User: -1` / `xfusionai-user: -1` 匿名访客头。
5. **模型链接指向 `/pricing` 本身**：该站无模型详情页，拼 `/pricing/<name>` 必 404。

## 字段映射

`model_name`→modelId/canonicalSlug；`vendor_id`→vendors 表映射成厂商名（缺失兜底 `XFusionAI`）；`model_type` text/image/video/audio/embedding→CHAT/IMAGE/VIDEO/AUDIO/EMBEDDING（未命中落 CHAT）；`tags` 逗号切分 + 厂商/类型/`region:*`/`endpoint:*`，上限 12。

## 门禁结果（2026-11-01 实跑）

| 门 | 结果 |
|---|---|
| 后端全量 `clean test`（8 模块） | **1194 例 / 1 失败**，失败为 `GatewayConfigConverterTest.voMirrorsEntityFieldSet[11]`：`BillingRuleConfigVo` 字段集 ≠ `BillingRuleEntity`——工作区里**他人未提交改动**（`BillingRuleEntity.java` +4 字段、VO 未同步），与本批改动零交集，按「多会话抢改不代修」原则未处理 |
| `XFusionAIModelSyncProviderTest` | 11 例全绿（定向与全量两次均验证） |
| `vue-tsc --noEmit` | exit=0 |
| `vite build --emptyOutDir false` | exit=0 |

## Follow-up

- [ ] 他人半成品收口后重跑全量（`BillingRuleEntity` 与 `BillingRuleConfigVo` 棘轮不一致）。
- [ ] `OpenRouterModelSyncProvider` 里有一份同款 JSON 取值 private 副本，等它补测试后收口进 `ExternalModelJsonSupport`。
- [ ] quota→金额换算口径确认后，做定价结构化（届时需 `kx_model_items` 加列 + 迁移）。
- [ ] 定时任务 `xfusionai-model-sync` 种子为停用态，要用请在「定时任务」界面启用。

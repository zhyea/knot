# Kling 3.0 Turbo 对接方案

> 日期：2026-10-27　范围：`knot-server`（gateway 链路 / adapter 适配器与提取器）+ 计费规则配置
> 状态：**异步链路（P0）与 Kling 组件（P1 / P2）已于 2026-10-28 落地**，P3（落库）待真实 API Key；
> 代码结论均为读源码实测（附 `文件:行号`），未经运行时验证处已标注「推断」。落地记录见 §9。
> 依赖文档：`docs/视频异步任务轮询拉取技术方案-2026-10-26.md`、`docs/统一视频用量模型-2026-10-27.md`、
> `docs/视频计费规则不足盘点-2026-10-27.md`、`docs/维度分档价方案-2026-10-26.md`

---

## 0. 结论先行

| # | 结论 |
|---|---|
| 1 | **计费侧零代码改动**。3.0 Turbo 的「有声 · 按输出秒 · 720P ¥0.8/秒 · 1080P ¥1.0/秒」正好落在通用路径上：`VIDEO` + `PER_SECOND` + `quantityMode=OUTPUT` + `resolutionPrices{720P:0.8, 1080P:1.0}`。`VENDOR_KLING` 已于 2026-10-27 整体删除，**不要再走厂商定制方案**。 |
| 2 | **链路侧缺一大块，且是资损**：Kling 是「提交任务 + 轮询」异步模型，而异步轮询方案（`docs/视频异步任务轮询拉取技术方案-2026-10-26.md`）**尚未实施**——全仓 grep `async_task` / `task_query_path` 零命中（实测）。不实施就接 Kling = 提交回执被「至少一秒」兜成 1 秒（盘点 A2，`VideoBillingModeCalculatorTest` 已钉住该行为）。**Kling 应作为该方案第一个落地场景同批做。** |
| 3 | **只需新增两个组件**，不碰任何通用类：`KlingVideoUpstreamAdapter`（纯透传 + 剥 callback）+ `KlingVideoUsageExtractor`（提取器子类，覆写 `facts()` 一处、`toBuilder` 改 4 项）。 |
| 4 | **前端零改动**：`DIMENSION_TIERED` 的档位表（`ResolutionTierTable`）只填「档位/单价」两列即可表达本价表。 |
| 5 | **三个待拍板**：① `defaultUnitPrice` 取多少；② 非标分辨率是 400 拒绝还是静默回落；③ 是否做官方 `billing.amount` 影子对账。见 §7。 |

---

## 1. 官方事实（协议 + 价表）

### 1.1 端点

| 能力 | 方法 | 路径 | 说明 |
|---|---|---|---|
| 文生视频 | POST | `/text-to-video/kling-3.0-turbo` | `prompt` 顶层字符串 |
| 图生视频 | POST | `/image-to-video/kling-3.0-turbo` | `contents[]`（`prompt` + `first_frame`），**仅首帧**，不支持尾帧 |
| 查询任务 | GET | `/tasks?task_ids=<id>` | 与 `external_task_ids` 二选一，支持逗号批量 |
| 游标批量查询 | POST | `/tasks` | 运维/对账用，**不用于计费主链路**（理由 §3.1） |

鉴权：`Authorization: Bearer <API_KEY>` → 现有 `BearerAuthApplier`（默认策略，`UpstreamAuthApplierCatalog:36`）直接可用。

### 1.2 请求体（对计费有意义的字段）

| 字段 | 类型 | 默认 | 计费相关性 |
|---|---|---|---|
| `settings.resolution` | string | `720p` | ⭐ **分档键**（`720p` / `1080p`） |
| `settings.duration` | int | `5` | 声明时长（3–15）。**不是计费量**，计费量是产物实际时长 |
| `settings.aspect_ratio` | string | `16:9`（仅文生） | 本价表不分画幅 → 不进键 |
| `options.external_task_id` | string | - | **不使用**（2026-10-28 拍板废除，见 §3.1） |
| `options.callback_url` | string | - | **必须剥掉**（不论客户端怎么填，见 §3.2） |
| `options.watermark_info.enabled` | bool | false | 与计费无关，不下发 |

### 1.3 响应体

**create**（`data` 是**对象**）：
```json
{ "code":0, "data":{ "id":"8936...", "status":"submitted", "create_time":..., "external_id":"..." } }
```
⚠ create 响应**没有任何计费字段** —— 这是异步方案的根因。

**query**（`data` 是**数组**）：
```json
{ "code":0, "data":[ {
    "id":"...", "status":"succeeded|failed|processing|submitted", "message":"...",
    "outputs":[ { "type":"video", "id":"...", "url":"...", "watermark_url":"...",
                  "duration":"10" } ],          // ⭐ duration 是【字符串】秒
    "billing":[ { "charge_type":"cash|unit", "cash_type":"balance|test_balance",
                  "amount":"8.00", "currency":"CNY", "list_price":"..." } ]
} ] }
```

状态枚举：`submitted` / `processing` → 非终态；`succeeded` → 终态计费；`failed` → 终态不计费。

### 1.4 价表（用户提供）

| 模型 | 功能 | 计费方式 | 720P | 1080P | 4K |
|---|---|---|---|---|---|
| Kling 3.0 Turbo | 有声 | 按秒（**总时长 = 输出时长**） | ¥0.8/秒 | ¥1.0/秒 | — |

**两条推论（决定配置形态）**：
1. 「总时长 = 输出时长」→ `video.quantityMode = OUTPUT`；**不要** `OUTPUT_PLUS_MAX`（那是 Kling 4.0 的口径，`BillingConfig.java:232`）。
2. 只有「有声」一档 → **不需要** `:AUDIO` 分档键，配裸档位名即可（详见 §5.2 的取价链验算）。

---

## 2. 现状核对（实测，决定了要改哪些钩子）

### 2.1 提取器拿到的是哪份请求体 —— 本方案最关键的一条

```java
// UsageExtractorRegistry.java:102,116
Map<String, Object> requestBody = requestBody(context);      // = context.requestBody()
UsageExtractItem<?> item = extractor.extractUsageBody(body, requestBody);
```
`adapter.buildRequestBody(context)` 的转换结果**只用于发往上游**（`AbstractUpstreamProtocolExecutor.java:373`），不回灌给提取器。
→ **提取器看到的是客户端原始 `VideoGenerationRequest`（`{prompt, image, duration, size, quality, n, user}`）**，不是 Kling 的 `{contents, settings, options}`。
→ 因此**键表必须按 knot 对外契约写，不能按 Kling 上游体写**（写成 `settings.resolution` 会一个都命不中）。

### 2.2 通用解析命中矩阵（`VideoUsageFacts.of` / `MediaUsageReaders`）

> **2026-10-28 更新**：本节原按「每个档位一个钩子」列矩阵。`AbstractVideoUsageExtractor` 已改为
> **一个取数入口 `facts()` + 一次解析的 `VideoUsageFacts`**（见 `60-business-rules.md` §8.13），
> 下表从「覆写哪个钩子」改成「哪一项与通用解析不符」，处置列统一落到
> `VideoUsageFacts.of(...).toBuilder()...build()`。

| 事实（`VideoUsageFacts` 分量） | 通用解析 | 对 3.0 Turbo 是否命中 | 处置 |
|---|---|---|---|
| `outputVideoMillis` | 读 `usage.*` / 顶层 `duration_seconds|video_seconds|seconds|duration`（`MediaUsageReaders:45,60`） | ❌ 产物时长在 `data[0].outputs[0].duration`，且是字符串 | **必须改** |
| `outputResolution` | 读顶层 `resolution`/`size`/`dimensions`（`RESOLUTION_KEYS:228`） | ✅ `size` 在键表内；`"720p"→720P`、`"1280x720"→720P`（`normalizeResolution:267-276`） | 不改；**约定 `size` 语义 = 档位名**（见 §3.2） |
| `outputAspectRatio` | 读顶层 `aspect_ratio` | ❌ 客户端体无此字段 | 不接（价不分画幅） |
| `outputVideoCount` | `data`/`videos` 数组长度，Map 计 1（`:315`） | ✅ query 体 `data` 是长度 1 的数组 → 1 | 不改（**不要批量查询**，否则数成 N） |
| `inputVideoMillis` | 读 `input_duration_seconds` 等 | ❌（3.0 Turbo 无输入视频）恒 0 | 不改 |
| `inputImageCount` | 读 `images`/`input_images`/… 数组或计数字段（`:111`） | ❌ 客户端是单值 `image` | 改（记 1 张首帧图，仅作事实展示，不计费） |
| `sound` | `flag` 布尔开关键表（`:34`） | ❌ 请求体无音频开关 | **改恒 `true`**（模型事实，见 §5.2） |
| `referenceVideo` | `present` 存在性键表（`:45`） | ❌（客户端无 `video`/`video_url`） | **改恒 `false`**（声明该模型无参考视频能力） |
| `outputMode` | 读 `video_mode`/`output_mode`/`mode` | ❌ | 不接 |

**净结论：新增 `KlingVideoUsageExtractor`，覆写 `facts()` 一处，在其中用 `toBuilder()` 改 4 项
（`outputVideoMillis` / `inputImageCount` / `sound` / `referenceVideo`）。**

### 2.3 三类已有红线，本方案必须落在其上

- 档位名**由厂商/客户端声明**，不由宽高反推（`VideoBillingUsage` javadoc）；
- 「有输入视频」唯一判据 `hasInputVideo = inputVideoMillis>0 || referenceVideo`；**参考图走图片张数口径，不算输入视频**；
- 明细行恒满足 `cost = tokens × price ÷ 1e6`（`AbstractBillingModeCalculator.detail:66-77`），附加费类常数项也必须走 `detail()`。

---

## 3. 设计

### 3.1 异步链路（前置，且必须先于本方案落地）

> **2026-10-27 更新**：异步任务的**存储与调度**已由 `docs/异步任务表与通用轮询方案-2026-10-27.md` 承接
> （落 `kb_async_tasks` + `kb_async_task_profiles` 两表，DB 为唯一真相源，Spring `@Scheduled` 轮询）。
> 本节的流程与判定仍然有效，只是「异步配置」改挂在 **档案表的一行**（`KLING_TASK`）上，
> 不再是往 `kb_model_api_bindings` 铺一堆 `task_*` 列。

本方案只补充 Kling 的三个差异点：

| 项 | MiniMax（原方案样例） | **Kling 3.0 Turbo** | 处置 |
|---|---|---|---|
| 查询 URL 形态 | 路径占位 `/v2/query/video_generation/{task_id}` | **query 参数** `/tasks?task_ids={task_id}` | `task_query_path` 允许 `{task_id}` 出现在 query 段；拼装仍走 `UpstreamUrlSupport`（唯一 URL 工具，禁 URI 编解码）。task_id 为纯数字，无编码风险 |
| 状态取值 | 各厂商自定义 | `submitted`/`processing`/`succeeded`/`failed` | `task_status_json_path=data[0].status`；终态 `succeeded`；失败 `failed` |
| 产物 / 用量位置 | `content.url` + `usage` | `data[0].outputs[0].url` + `data[0].outputs[0].duration`（**字符串秒**） | `task_result_url_json_path=data[0].outputs[0].url`；时长由提取器解析（§3.3） |

三条本方案新增的约定：

1. ~~**`external_task_id` 填 knot 任务句柄**~~ —— **2026-10-28 作废**。当初的理由是「重启后可用 `GET /tasks?external_task_ids=` 反查上游」，但反查能力换不来什么：任务行本身在 `kb_async_tasks`，DB 就是唯一真相源，丢失场景只剩「DB 也丢了」，那时反查也没有可回填的行。为上这条发明了一个上游不要求、网关也不消费的字段，属于自己给自己加转换逻辑（违反 §9.1「视频适配器只做透传」）。`external_task_field` / `external_task_id` 整条管线（档案列、任务列、spec 字段、`{external_task_id}` 占位）已全部删除。
2. **不做批量查询**：`?task_ids=a,b,c` 会让 `outputVideoCount` 把任务数当成视频条数（§2.2），且部分成功/部分失败语义复杂。一 knot 任务 = 一上游任务，单查。
3. **`callback_url` / `webhook_url` 一律剥掉**：不论客户端怎么填都不下发（§3.2）。

计费时机：`create`（202）**不发**流量事件；poller 在 `succeeded`/`failed`/`expired` 终态发**一条**（原方案 §5.7）。
`succeeded` 但 `outputs` 为空/无 `type=video` 产物 → **按 failed 处理，不进计费**（分层原则：「该不该出账」由异步层判定，不塞进用量模型 —— 与 robin 2026-10-27「阶段不进计费模型」的判定一致）。

### 3.2 `KlingVideoUpstreamAdapter`（新增，`adapter/protocol`）

对外契约不变（`POST /openai/v1/videos/generations`），**实现只有一行**：

```java
@Override
public Object buildRequestBody(UpstreamRequestContext context) {
    return VideoRequestSupport.passthroughBody(context);
}
```

**⭐ 2026-10-28 修正：本节原写的「字段映射表 + 分辨率白名单 + `n>1` 拒绝 + `external_task_id` 注入」全部删除。**
网关是代理不是转译层——请求体该长什么样由客户端按上游文档自己写，写错了让上游 400 原路返回。
规范条款见 `60-business-rules.md` §9.1「视频适配器只做透传」，两个后果都已在真机上出过事：

| 曾经的「转换」 | 为什么删 |
|---|---|
| `prompt` → `contents[].{type:prompt}`（图生拆分） | 形状在网关里重实现一遍，上游一改文档这里就是过期副本；且客户端本可以自己按文档写对 |
| `size` → `settings.resolution`（含大小写归一） | 同上。真正的读取点是**计费 Calculator**，它读请求体里的原始档位名，不需要适配器搬家 |
| 分辨率白名单 → 400 | 网关替上游承担校验口径；上游本来就会拒。**注意**：原担心「白名单外静默回落默认价」仍然成立，但根治点在计费侧的维度取价告警，不在适配器拦请求 |
| `n>1` → 400 | 同属替上游校验。`n` 的语义由上游定义，网关拦住反而掩盖了客户端的真实意图 |
| 注入 `options.external_task_id` | 上游不要求、网关不消费，纯自造字段（§3.1 第 1 条已废除） |

**唯一保留且必须做的**：剥掉客户端的 `callback_url` / `webhook_url`（含 `options.callback_url` 这种嵌套位置），
实现见 `adapter/support/VideoRequestSupport`。

- 判据不是「整洁」，是**计费完整性**：漏给供应商，供应商直接把终态推给客户端 → 客户端拿到产物却没经过
  knot 的轮询与终态结算 → 那笔用量既查不到也收不回。
- **必须递归**：Kling 放 `options.callback_url`，Luma / MiniMax 放顶层，只扫一层等于只挡住一半厂商。
- 字段名归一化比较（忽略大小写与 `_` / `-`），名单唯一出口 `VideoRequestSupport.CALLBACK_FIELDS`。
- **不能原地改** `requestBody()`：异步任务要拿它落 `request_json` 供终态复现计费，故「无命中返回同一实例、
  有命中返回浅拷贝」。
- 兜底路径同守：`PassthroughUpstreamAdapter` 落到 `VIDEO_GENERATIONS` 时也走 `passthroughBody`
  （**越是没配适配器的绑定越容易漏**）。
- 流式不必覆写 `streamSupported`：协议枚举已是 `streamSupported=false`，`MediaProtocolExecutor` 又只放行
  `AUDIO_SPEECH`，适配器再声明一遍只会误导后来人。

`handleResponse`：异步路径下 create 响应不再直接回客户端，由网关改发 202 信封（原方案 §5.2）。适配器不覆写它。

### 3.3 `KlingVideoUsageExtractor`（新增，`adapter/usage/extractor`，与 `AbstractVideoUsageExtractor` **同包**）

```java
@Component
public class KlingVideoUsageExtractor extends AbstractVideoUsageExtractor {
    public static final String CODE = "KLING_VIDEO";
    // 构造器注入 VideoBillingModeCalculator（同 VideoUsageExtractor）

    @Override
    protected VideoUsageFacts facts(Map<String, Object> body, Map<String, Object> requestBody) {
        return VideoUsageFacts.of(body, requestBody).toBuilder()   // 通用解析打底，只改 4 项
                .outputVideoMillis(productMillis(body))
                .inputImageCount(firstFrameCount(requestBody))
                .sound(true)
                .referenceVideo(false)
                .build();
    }
}
```

| 改的项 | 实现 | 为什么 |
|---|---|---|
| `outputVideoMillis` | 取 `data[0].outputs[]` 中 `type=video` 的 `duration`（**字符串秒**）→ 经 `MediaUsageReaders`-级秒→毫秒换算 | 通用键表只扫 `usage` 与顶层，Kling 的产物时长在二层数组里，且是字符串。⚠ 不要把它塞进通用 `DURATION_KEYS` |
| `sound` | 恒 `true` | 官方价表只有「有声」一档，请求体无音频开关。这是**模型事实**，不是请求声明；写在这里比让调用方传更可靠 |
| `referenceVideo` | 恒 `false` | 3.0 Turbo 无参考视频能力；图生视频的首帧是**图片**不是视频（红线：图走张数口径）。显式关掉，避免日后通用键表扩容时被 `image`/`url` 类键误命中 |
| `inputImageCount` | `image` 非空 → 1 | 记录事实（`ModelUsage.input.imageCount`），**规则侧不配 `inputImagePerImage` 就不会产生分量** |

⚠ **用 `toBuilder()` 而不是重新 `new VideoUsageFacts(...)`**：后者会把通用解析的分辨率 / 条数 /
输入时长全部丢成默认值（2026-10-28 探针实测：分辨率、张数、条数三处同时变红），
且日后基类新增维度本类不会跟上。规范条款见 `60-business-rules.md` §8.13。

身份声明：`code=KLING_VIDEO`、label/description、`calculator()=VideoBillingModeCalculator`、`billingModes()=EnumSet.of(VIDEO)`。
`allowsEmptyUsage()` 维持默认。

### 3.4 计费规则配置（可直接落库）

| 规则字段 | 值 | 理由 |
|---|---|---|
| `billing_mode` | `VIDEO` | — |
| `unit` | `PER_SECOND` | `BillingModeEnum.VIDEO` 默认单位；官方按秒 |
| `pricing_plan` | `DIMENSION_TIERED` | ⭐ 唯一增量是**保存期强校验**：键拼错/空档位名直接拒绝，杜绝 `FIXED` 下「配置看着生效、那档价从未命中」（盘点 D1） |
| `default_unit_price` | 待拍板（建议 `1.0`） | 档位未命中时的兜底，取最贵档（1080P）方向不漏收 |
| `config_json` | 见下 | — |

```json
{
  "defaultUnitPrice": 1.0,
  "resolutionPrices": {
    "720P": 0.8,
    "1080P": 1.0
  },
  "video": {
    "quantityMode": "OUTPUT"
  }
}
```

**必须留空的字段（写了就是资损）**：

| 字段 | 为什么不能配 |
|---|---|
| `video.inputVideoPerSecond` | 3.0 Turbo 不按输入秒计费；配了会多开一条 `VIDEO_INPUT_VIDEO` 分量 |
| `video.inputImagePerImage` / `freeInputImages` | 首帧图不计费；配了会多开 `VIDEO_INPUT_IMAGE` 分量 |
| `surcharges` | 无附加费；官方已把「有声」折进档位价 |
| `progressive` | 无阶梯 |
| `720P:AUDIO` / `1080P:AUDIO` | 不必配：`sound=true` 时候选键先试 `720P:AUDIO`，未配则**逐级降级**到 `720P`（`VideoBillingModeCalculator.dimensionKeys:307-323`），正好命中。日后同模型出现无声档，再补 `:AUDIO` 列即可 |

**验算（10 秒 / 1080P / 有声）**：
```
候选键：1080P:AUDIO(未配) → 1080P(命中 1.0)
计费量 = outputSeconds = 10     (quantityMode=OUTPUT，unitSize=1)
金额   = 10 × 1.0 ÷ 1 = 10.00
明细行  price = 1.0 × 1e6 ÷ 1 = 1000000 ，自检 10 × 1000000 ÷ 1e6 = 10 ✅
```

### 3.5 数据落库清单

| 层 | 动作 |
|---|---|
| 供应商 / 账户 | 新增供应商 + 账户，凭据类型 `API_KEY`，鉴权策略 `BEARER`（默认，无需新增 applier） |
| 模型 | 新增两个 model_code：`kling-3.0-turbo-t2v` / `kling-3.0-turbo-i2v`（同族，可共用族默认规则） |
| 异步档案（`kb_async_task_profiles`） | 新增一行 `KLING_TASK`（文生/图生两条绑定共用，因为查询端点都是 `/tasks`）：`protocol=VIDEO_GENERATIONS`、`query_path=/tasks?task_ids={task_id}`、`task_id_path=data.id`、`status_path=data[0].status`、`status_succeeded=['succeed','SUCCEEDED'] contains status`、`status_failed=['failed','FAILED'] contains status`、`status_running=['submitted','processing'] contains status`、`result_path=data[0].outputs[0].url`、`result_expires_days=30`（⚠ 三个 `*_path` 与三个 `status_*` 都是 **MVEL 表达式**，2026-10-28 起；完整 INSERT 见通用方案 §6）。⚠ `external_task_field` 列已删（§3.1） |
| 绑定（`kb_model_api_bindings`） | `protocol=VIDEO_GENERATIONS`；`api_path=/text-to-video/kling-3.0-turbo` 与 `/image-to-video/kling-3.0-turbo`；`request_adapter=KLING_VIDEO`；`usage_extractor=KLING_VIDEO`；`async_profile_code=KLING_TASK` |
| 计费规则 | 见 §3.4（文生/图生同价，可共用一条规则 + 版本） |
| 权限 | 新增查询端点须登记权限绑定（红线：漏 = 运行期 403） |
| schema | 异步字段沿用既有方案 P0 的 `schema.sql` + `docs/database/migration/` 迁移脚本，本方案不另加列 |

### 3.6 前端

**零改动**。`DIMENSION_TIERED` 的档位表（`ResolutionTierTable.vue`）四列（档位 / 有声 / 含参考 / 含参考+有声），本价表只需填第一列；`defaultUnitPrice` 是既有必填项。

---

## 4. 验证计划

| 层 | 动作 | 判据 |
|---|---|---|
| 试算 | 管理端 `previewBillingRule` + `PricingPreviewDrawer` 过 4 组样本（720P×5s / 720P×10s / 1080P×10s / 未传 size） | 金额 = 秒 × 档位价；未传 size 落到 `defaultUnitPrice` 且**可见告警** |
| 单测（新增） | `KlingVideoUsageExtractorTest`：① `data[0].outputs[0].duration="10"` → 10000ms；② duration 为字符串小数（推断）→ 向上取整秒；③ `outputs` 为空 → 0ms（不兜 1 秒）；④ `size=1280x720` → `720P`；⑤ sound 恒 true / reference 恒 false | 全绿 |
| 单测（新增） | `KlingVideoUpstreamAdapterTest`：① 请求体逐字透传（不拼 `contents`、不造 `settings`、不改大小写）；② 错参（`size=2k` / `duration="ten"` / `n=2`）原样放行；③ 不注入 `options`；④ 顶层与嵌套 `callback_url`、`webhook_url` 全部剥掉；⑤ 快照不被改写、无命中零拷贝 | 全绿（10 例，2026-10-28 重写） |
| 单测（新增） | `VideoRequestSupportTest`：递归（Map / List / 混嵌）、字段名归一化、近似字段不误伤、零拷贝 | 全绿（9 例） |
| 单测（复用） | 用真实 query 体跑一遍 `VideoBillingModeCalculator`，断言单分量、金额、明细行自检式 | 明细行数 = 1，`cost = tokens × price ÷ 1e6` |
| 门禁 | `mvn -f knot-server/pom.xml test` / `vue-tsc --noEmit` / `vite build` | 真跑，grep `BUILD SUCCESS` |
| 真启冒烟 | 本机无 Redis → `store=caffeine` 档跑通 create→poll→终态计费 | Kafka 终态事件携带真实用量与金额 |

> 「一次就绿必打双向探针」：上述 ①② 类用例须先验证改错实现会变红（例如把 `duration` 读成顶层 → 断言 0ms 的用例失败）。

---

## 5. 风险与应对

| 风险 | 应对 |
|---|---|
| 异步方案未落地就接 Kling → 提交回执按 1 秒计费（A2） | **门禁项**：`async_task` 未生效时该绑定不允许启用（P1 退出的硬条件） |
| 分辨率键不识别 → 静默回落默认价（D2） | ⚠ 原方案写「适配器白名单 400」，**2026-10-28 撤回**（网关不替上游校验，§3.2）。根治点改在**计费侧**：`DIMENSION_TIERED` 的键校验 + 未命中分档要**可见告警**。今天仍存在的缺口是「回落默认价无日志」，见 §7 ② |
| 产物时长异常（如 1000 秒）照单计费（D3） | 建议加「单笔最大可计费秒数」上限（可配，默认 15s + 容差），超限告警**并按上限计**（是否按上限计待拍板） |
| 产物 URL 30 天后清理 | 透传 + 文档提示；转存对象存储列未来增强 |
| 官方 `billing.amount` 与 knot 计算值不一致 | 见 §7 ③ |
| 鉴权是否真的静态 API Key | 文档写 `Bearer <API_KEY>`；若实际部署要求 JWT（部分站点 ak/sk 换 token），`BearerAuthApplier` 不够，需新增动态 token applier + 缓存。**上线前必须实测一次真实调用确认**（见 §7 ④） |
| `occurredAt` 语义（D4） | 建议异步任务下取**任务完成时刻**；本方案用 `DIMENSION_TIERED`（无相位倍率）暂不受影响，但需与后续 `PEAK_OFF_PEAK` 规则一致 |

---

## 6. 分批实施

| 阶段 | 内容 | 状态 |
|---|---|---|
| P0 | 异步链路（已改由 `docs/异步任务表与通用轮询方案-2026-10-27.md` 承接） | ✅ 2026-10-28 落地，见该文档 §7 |
| P1 | `KlingVideoUpstreamAdapter`（透传 + 剥 `callback_url`/`webhook_url`） | ✅ 2026-10-28 落地；同日改成纯透传，单测 10 例（原 11 例的转换断言全作废） |
| P2 | `KlingVideoUsageExtractor`（覆写 `facts()`，`toBuilder` 改 4 项） | ✅ 2026-10-28 落地，单测 13 例 |
| P3 | 供应商/账户/模型/绑定/计费规则落库 + 试算 4 组样本 | ⏳ 待真实 API Key（凭据要加密落库，不能凭空造） |
| P4 | 全量门禁 + 真启冒烟 | ✅ 门禁全绿 + 上下文真启成功；**真实调用仍待 P3** |

### 6.1 P3 落库脚本（待真实 key 后执行，幂等）

```sql
-- 1) 异步档案（文生/图生共用：查询端点都是 /tasks）
INSERT IGNORE INTO kb_async_task_profiles
  (profile_code, protocol, query_method, query_path, task_id_path,
   status_path, status_succeeded, status_failed, status_running, result_path,
   result_expires_days, poll_interval_ms, poll_backoff_base_ms, poll_backoff_max_ms,
   max_attempts, timeout_ms, status, remark)
VALUES
  ('KLING_TASK', 'VIDEO_GENERATIONS', 'GET', '/tasks?task_ids={task_id}', 'data.id',
   'data[0].status',
   '[''succeed'',''SUCCEEDED''] contains status',
   '[''failed'',''FAILED''] contains status',
   '[''submitted'',''processing''] contains status',
   'data[0].outputs[0].url', 30, 5000, 1000, 30000, 360, 1800000, 1, '可灵 3.0 Turbo 异步任务档案');

-- 2) 绑定（两条：文生 / 图生，各自 api_path 不同）
--    ⚠ 现有 seed 的 INSERT 列清单不含 async_profile_code（默认 NULL = 同步），
--       故这里显式写出列名，别复用不带该列的写法。
INSERT IGNORE INTO kb_model_api_bindings
  (model_code, protocol, api_path, request_adapter, usage_extractor, async_profile_code, status, remark)
VALUES
  ('kling-3.0-turbo-t2v', 'VIDEO_GENERATIONS', '/text-to-video/kling-3.0-turbo',
   'KLING_VIDEO', 'KLING_VIDEO', 'KLING_TASK', 1, '可灵 3.0 Turbo 文生视频（异步）'),
  ('kling-3.0-turbo-i2v', 'VIDEO_GENERATIONS', '/image-to-video/kling-3.0-turbo',
   'KLING_VIDEO', 'KLING_VIDEO', 'KLING_TASK', 1, '可灵 3.0 Turbo 图生视频（异步）');
```

> 供应商 / 账户 / 凭据 / 模型 / 计费规则需要真实 API Key（凭据加密落库），
> 拿到后按 §3.4 的价表配一条 `VIDEO + PER_SECOND + DIMENSION_TIERED` 规则即可，
> `defaultUnitPrice` 建议 1.0（§7 ①）。

---

## 7. 待 robin 拍板

| # | 决策点 | 选项 | 建议 |
|---|---|---|---|
| 1 | `defaultUnitPrice` | `1.0`（1080P 价） / `0.8`（720P 价） | **1.0**：档位未命中时不漏收；漏收是资损且事后追不回 |
| 2 | 非标分辨率（`2k` 等） | ~~400 拒绝~~ → 已拍板**不在适配器拦**（2026-10-28：请求错误由调用方承担），改由计费侧未命中分档告警兜 | 适配器侧已撤回；剩下要做的是给「回落 `defaultUnitPrice`」加日志/告警，否则 D2 仍然静默 |
| 3 | 官方 `billing.amount` 对账 | 影子记录 + 偏差告警 / 不接 | **影子记录**：不作为计费输入（口径不同：含折扣、资源包、币种），但存进任务 result 供账实核对 |
| 4 | 鉴权实测 | 文档 Bearer API Key / 实际需 JWT | 上线前用真实 key 打一次 create+query 确认；若需 JWT，P1 追加动态 token applier |
| 5 | 产物时长上限熔断 | 加 / 不加 | 加（默认 15s + 容差），超限告警并按上限计 |

---

## 8. 落地记录（2026-10-28）

### 8.1 新增文件

| 文件 | 说明 |
|---|---|
| `knot-adapter/.../protocol/KlingVideoUpstreamAdapter.java` | 首个 `VIDEO_GENERATIONS` 适配器；`@Order(120)`，`CODE=KLING_VIDEO`。`buildRequestBody` 只有一行：`VideoRequestSupport.passthroughBody(context)` |
| `knot-adapter/.../support/VideoRequestSupport.java` | **与供应商无关**的透传原语：递归剥掉 `callback_url` / `webhook_url`，无命中零拷贝 |
| `knot-adapter/.../usage/extractor/KlingVideoUsageExtractor.java` | 只覆写 `facts()`，用 `toBuilder()` 改 `outputVideoMillis` / `inputImageCount` / `sound` / `referenceVideo`；与 `AbstractVideoUsageExtractor` **同包**（依赖包私有 `MediaUsageReaders`） |
| `knot-adapter/.../usage/extractor/VideoUsageFacts.java` | **2026-10-28 新增**：十个档位事实一次解析的载体（`of` + `toBuilder`），取代原来的十个 `protected` 钩子 |
| ~~`support/kling/KlingVideoSupport.java`~~ | **已删**（2026-10-28）。它做的形状转换 / 白名单 / `n>1` / `external_task_id` 全属越界，见 §3.2 |
| 三个测试共 32 例 | `KlingVideoUpstreamAdapterTest` 10 / `VideoRequestSupportTest` 9 / `KlingVideoUsageExtractorTest` 13 |

### 8.2 与方案的偏差（两处，必须知晓）

**① `external_task_id` 整条管线已删除（2026-10-28 拍板）。**

方案 §3.1 原本要「恒填 knot 任务句柄」；实施时发现顺序不成立（句柄要等上游返回 `task_id` 之后才生成，
而 `buildRequestBody` 在出站之前执行），当时退而填 requestId。**现在连 requestId 也不填了**：
反查能力换不来什么——任务行本来就在 `kb_async_tasks`，唯一丢失场景是「DB 也丢了」，那时反查也没有可回填的行。
为它发明的上游不要求、网关也不消费的字段，本身就是 §9.1 禁止的转换。

已清除：`kb_async_task_profiles.external_task_field`、`kb_async_tasks.external_task_id`、
`AsyncTaskSpec.externalTaskField`、`{external_task_id}` 占位、`AsyncTaskSupport.externalTaskIdOf`。
⚠ 档案里再写 `{external_task_id}` 会在拼路径时**直接抛**（不是静默查不到），守门用例
`AsyncTaskSpecTest#externalTaskIdPlaceholderIsGone`。

**② 从「转换 + 校验」改成「纯透传 + 剥 callback」（2026-10-28，robin 指令）。**

原 P1 记录里的「分辨率白名单 400 / `n>1` 拒绝 / 字段映射」全部撤回，理由与后果逐条见 §3.2。
判据：网关是代理，不是转译层；请求错误由调用方承担。

### 8.3 门禁实测

| 项 | 结果 |
|---|---|
| `mvn -f knot-server/pom.xml test` | **BUILD SUCCESS**。2026-10-28 透传改造后：1264 例（common 134 / plugin-api 3 / plugin 44 / adapter 334 / dal 4 / service 185 / admin 110 / gateway 450） |
| 真启 | `knot-gateway` 完整上下文启动成功；两个 catalog 均按 code 建索引，重复 code 会启动即抛——`KLING_VIDEO` 在适配器与提取器两个命名空间各注册一次，无冲突 |
| 探针 | ① 去掉分辨率白名单 → 适配器测试红 1 例（该用例已随改造删除）；② `soundEnabled` 改 false → 提取器测试红 2 例；③ **`isCallbackField` 恒 false → 9 例红**（直接调用 5 / Kling 3 / 兜底透传 1）；④ **Kling 适配器改回 `return context.requestBody()` → 3 红 + 1 NPE**。改回即绿 |

### 8.4 一个必须记住的坑

**改回源码后 Maven 可能不重编译**：用「备份文件拷回」的方式还原（`shutil.copy` + 拷回）会把
mtime 一起带回去，导致源文件 mtime 早于已编译的 class，Maven 判「无变化」→ 跑的还是被改坏的字节码。
**还原后一律 `touch` 一次再跑测试**，否则会出现「源码是对的、测试还是红的」这种假象。

---

## 9. 明确不做（避免范围蔓延）

- 不做 webhook 入站端点（`callback_url` 不下发）；
- 不做 `/tasks` 批量查询与游标批量（批量会污染 `outputVideoCount`）；
- 不做产物下载转存对象存储；
- 不做画幅（`aspect_ratio`）维度 —— 本价表不分画幅，与「aspectRatio 接维度键」的后续专项分开；
- 不做 token 量纲（Kling 不回 token）；
- 不改 `VideoBillingUsage` / `VideoBillingModeCalculator` / `MediaUsageReaders` 任何一行；
- 不重启 `VENDOR_KLING`。

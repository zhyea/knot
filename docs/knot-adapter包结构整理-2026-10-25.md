# knot-adapter 包结构整理-2026-10-25

- 状态：**已交付**（后端全量门禁 BUILD SUCCESS / 995 例 0 failures，与整理前完全一致）
- 范围：仅 `knot-adapter` 模块内包重组 + 跨模块 import 修正；前端零涉及

## 1. 决策过程

robin 对包结构给出方案并两轮收敛：初版「model/usage/auth/support 挪 gateway 顶级域」→ 评估发现 `gateway.auth` 与 common/service/admin 撞车、且跨模块 import 面过大 → **收敛为单域方案：顶级域只留 `adapter`，其余全部收为子包**。一举解决撞车（`adapter.auth` ≠ `gateway.auth`）与「模块 = 域」对齐（knot-adapter 模块即 `gateway.adapter` 域）。

## 2. 目标结构

```
gateway.adapter/
├── protocol/      ← UpstreamAdapter 接口 + 9 实现 + Catalog + Definition（原 adapter.request，request 化石名清除）
├── support/
│   ├── RequestFieldSupport.java ← 通用字段搬迁原语（无供应商语义）
│   ├── PayloadMapSupport.java   ← payload Map 通用读写原语（cast/nested/firstPresent/stringOf/putLong）
│   ├── PayloadTextSupport.java  ← 跨协议 content 折文本（textOf）
│   ├── aws/       ← AwsBedrockConverseSupport + AwsBedrockEndpointResolver + AwsSigV4Support（原 adapter.aws）
│   └── anthropic/ ← AnthropicMessagesRequestSupport（仅 max_tokens 兜底；move 已拆至根，见 §4 坑 4）
├── auth/          ← UpstreamAuthApplier 族 7 + AuthConfigSupport 归位（位置不变，仅吸收孤类）
├── upstream/      ← UpstreamRequestContext + UpstreamAccess + UpstreamSignableRequest（*Ref 抽走后剩 3）
├── model/         ← 10 个计费/用量模型（原 gateway.model）
│   └── ref/       ← ModelApiBindingRef + ModelRef + ProviderAccountRef + BillingRuleRef（*Ref 统一收拢）
└── usage/         ← 根 10（原 gateway.usage 6 行为 + 原 model.usage 三件并入）
    ├── calculator/ (5)
    └── extractor/  (9)
```

## 3. 移动清单

| 类别 | 数量 | 说明 |
|---|---|---|
| git rename（已跟踪文件） | 52 | model 10 / model.usage 3 / usage 6+5+9 / ref 4 / aws 2 / AuthConfigSupport + 测试镜像，历史保留 |
| 新文件归位（未跟踪） | 24 主 + 11 测试 | protocol 12 / support 4 / 测试 11，随批挪入新包 |
| 跨模块 import 修正 | ~120 点 / ~65 文件 | gateway 39（model）+ 29（usage）+ auth/protocol/support/ref 各若干，全部按类名精确替换（17 个类名事先全仓查重零冲突） |
| 模块内 import 补齐 | 7 文件 | 见 §4 坑 1/2 |

## 4. 四个真实坑（都已修，门禁兜底）

1. **跨模块同名包可见性断裂**：`knot-gateway` 的 `ProxyResult`（在 gateway 模块自己的 `gateway.model` 包里）原来靠「classpath 同名包可见性」直接用 `UsageAccounting` 短名、**无 import**；adapter 的 model 包挪走后断编。同类风险全仓扫描（各模块 `gateway/model` 目录逐类词边界匹配）仅此一例，补 import 修复。
2. **包私有方法跨子包失效**：`AnthropicMessagesRequestSupport` 的 `move` / `ensureMaxTokens` 原是包私有（与 4 个适配器同包可直接调），挪入 `support.anthropic` 后 protocol 包不可见——类与方法补 `public`。aws 三个 Support 原本就被 auth/upstream 跨包调用（早已 public），无此问题。
3. **javadoc `{@code}` 被误判为符号引用**：自动补 import 脚本用词边界匹配类名，把 `TokenBillingUsage` javadoc 里 `{@code ModelUsage.InputTokens}` 的文本提及当成引用，误插一条 import——已删。脚本类工具对 javadoc 必须排除 `{@code}`/`{@link}` 外的纯文本（本例 `{@code}` 不解析符号）。
4. **通用能力住了供应商专用类**（robin 指出）：`AnthropicMessagesRequestSupport.move` 是无供应商语义的字段搬迁原语（key→key 搬值、目标已存在不覆盖、删源键），与 Anthropic 无关——住在 Anthropic 专用支持类里，别处复用会违和。已拆至 `adapter.support.RequestFieldSupport`（support 根，无供应商段），AnthropicMessagesRequestSupport 只留真正特有的 `ensureMaxTokens`。规范立规：通用原语收 `support.XxxSupport`，供应商特有语义收 `support.<供应商>.XxxSupport`。

## 5. 规范更新

`10-backend-java.md` §4.7 重写：新增「knot-adapter = `gateway.adapter` 单域六子包」结构表（protocol / support（根通用 + <供应商>）/ auth / upstream / model(+ref) / usage(+calculator+extractor)）；接口位置改 `adapter.protocol`；Support 条款改为两层归属——无供应商语义的通用原语收 `adapter.support.XxxSupport`、供应商特有语义收 `support.<供应商>.XxxSupport`，且静态方法必须 public；补三条判例警示——**`gateway.model` 同名包跨 4 模块，跨模块引用必须显式 import 全 FQCN**；包私有 Support 移子包须补 public；通用能力不住供应商专用类（判例：`move` 拆出 `RequestFieldSupport`）。

## 6. 门禁

`mvn -f knot-server/pom.xml clean test` → **BUILD SUCCESS**，八模块（127/3/44/182/4/177/110/348）合计 **995 例，0 failures，0 errors**——与整理前逐一相等（纯移动零行为变化，账目吻合）。日志 `audit/adapter_pkg_gate.log`。前端零改动不涉及。

## 7. 追加批次：textOf 与 5 个读写原语拆出（同日，robin 指出）

robin 追问「`AwsBedrockConverseSupport` 下的方法是否也有类似情况」。逐方法审查 289 行里约 150 行私有辅助，判定**6 个公开/转换方法（resolvePath / buildConverseBody / handleConverseResponse / inferenceConfig / toOpenAiUsage / finishReason）是 Converse 特有语义、归属正确**；其余 7 个私有辅助全是通用能力：

| 拆出方法 | 新归属 | 原问题 |
|---|---|---|
| `cast` / `nested` / `firstPresent` / `stringOf` / `putLong` | `adapter.support.PayloadMapSupport`（新建） | 零供应商语义的读写原语住 AWS 专用类（与 `move` 同款） |
| `textOf` | `adapter.support.PayloadTextSupport`（新建） | 跨 OpenAI/Anthropic/Converse 三协议的 content 折文本，无供应商语义 |
| `readMap` | 删除，调用点内联 `JsonKit.fromJson` | `JsonKit:120` 已有宽容入口（失败返 null），纯间接层 |

**比 `move` 更严重的形态**：`textOf` 还导致 `AwsBedrockMessagesToConverseUpstreamAdapter` 复制了一份**逐字相同的私有副本**（适配器 96-116 行 vs Support 234-254 行，仅 cast 写法差异）——「通用能力住了供应商专用类」的直接后果就是调用方被迫复制。副本已删，改调通用款。

**方案选择**（robin 拍板 A）：通用原语拆两个类而非合一——`PayloadMapSupport`（「怎么安全读写一个 payload Map」的语法层）与 `PayloadTextSupport`（「三种协议 content 形状怎么折文本」的协议层），抽象层级不同，合并会让类 javadoc 同时解释两件事。

**契约测试**：新增 `PayloadMapSupportTest`（6 例：cast 同一性 / nested 分层缺失 / firstPresent 认 0 与空集合 / stringOf null 兜底 / putLong 负值钳 0）与 `PayloadTextSupportTest`（3 例：裸串透传 / block 按序拼接跳非文本 block / null·数字·空列表兜底），合计 9 例。`firstPresent` 与 `MapNumberUtils.firstLong` 的语义差异（认任意非 null vs 只认正数）以测试固化。

**门禁**：`mvn -f knot-server/pom.xml clean test` → **BUILD SUCCESS**，八模块合计 **1004 例**（995 + 新增 9），0 failures，0 errors——重构前 995 例逐一相等（行为零变化），新增 9 例即契约测试账目。日志 `audit/adapter_support_split2_gate.log`。前端零改动不涉及。

## 8. 追加批次：authConfig 读取力学去重（同日，robin 追问 AuthConfig.value）

robin 追问「看下 `AuthConfig.value`」。判定：`value` 方法本身语义干净（null map / 空白键 → null；`trimToNull(String.valueOf)` 空白=未配置），但周边两个问题——**① `token()` 死代码**（全仓零调用，且 `"token"` 裸字符串 vs `apiKey()` 走 `AuthConstants.API_KEY`）；**② `AwsBedrockEndpointResolver.read`（support/aws:140-146）是 `value` 的逐字重复**（只差一个 blank-key guard，`region()` / `endpointType()` 都在用它）——正是 §7 立规的「跨类逐字重复 = 归属错误信号」。

修法（robin 批准）：读取力学下沉到通用层，**避免 support.aws → auth 的包级环**（`auth.AwsSigV4AuthApplier` 已 import support.aws 两个类，反向再依赖就成环）：

| 改动 | 内容 |
|---|---|
| `PayloadMapSupport.text(Map, key)`（第 6 个原语） | `trimToNull(stringOf(get(key)))` + 双 guard；与 `stringOf` 的分工写进 javadoc（本方法带 trim 与「空白=未配置」，是凭据/配置类 map 的读取入口） |
| `AuthConfigSupport` | 删死方法 `token()`；**删 `value`**（robin 指出「明明是冗余的」——合并后它只剩一行转发，零知识增量，只是同一操作的两个名字）；`apiKey()` 直调 `PayloadMapSupport.text` |
| `AwsSigV4AuthApplier` | 3 处 `AuthConfigSupport.value(...)` → `PayloadMapSupport.text(...)`；顺手删同包自引用 import |
| `AwsBedrockEndpointResolver` | 删私有 `read`；`region()` / `endpointType()` 直调 `PayloadMapSupport.text`（support.aws → support 根的边在上批已存在，无新包级依赖） |

语义逐字等价验证：`read` 与 `value` 对 null map / 缺失键 / 空白值 / 带空白串值的返回值完全一致（`trimToNull(stringOf(v))` 与 `trimToNull(v == null ? null : String.valueOf(v))` 同解）。新增 `text` 契约测试 1 例（null map / 空白键 / 缺失 / 空白值 / trim）。

**门禁**：`mvn -f knot-server/pom.xml clean test` → **BUILD SUCCESS**，八模块合计 **1005 例**（1004 + 1），0 failures，0 errors。日志 `audit/adapter_support_split3_gate.log`；删 `value` 后复跑 `audit/adapter_support_split3b_gate.log` 同样 **1005 例** 0 failures，账目与上轮逐字相等（`AwsSigV4AuthApplierTest` 8 例 / `AwsBedrockEndpointResolverTest` 6 例 / `PayloadMapSupportTest` 7 例实跑确认）。前端零改动不涉及。

新立判据（§4.7）：**纯转发壳同样是冗余**——委托层不带知识增量时删掉、调用方直调（判例：`value` 合并后只剩一行转发，robin 指出后同批删除）。

## 9. 响应侧适配补齐：4 个泛化适配器（同日，robin 指出）

robin 指出「UpstreamAdapter 仅对请求做了处理，还没有对响应做适配」。核查：Bedrock 四个适配器有响应侧（converse→OpenAI）、Passthrough 本就双向透传，但**四个泛化适配器只有 `buildRequestBody`，`handleResponse` 走默认透传**——请求 chat.completions 的客户端会收到 Anthropic message JSON；更严重的是**计费提取吃的是 `handleResponse` 的产物**，错形状会连带算错账。

### 落地

新增 `adapter/support/anthropic/AnthropicMessagesResponseSupport`（协议级双向转换，与 `AnthropicMessagesRequestSupport` 对称），四个方法各服务一个适配器：

| 方法 | 方向 |
|---|---|
| `messagesToChatCompletion` | Messages → `chat.completion`（choices[].message + finish_reason） |
| `messagesToTextCompletion` | Messages → `text_completion`（旧版 completions，choices[].text） |
| `messagesToResponses` | Messages → Responses `response` 对象（output[].content[].output_text + status） |
| `chatCompletionToMessages` | `chat.completion` → Messages（content 文本块 + stop_reason） |

约定：① **非目标形状原样透传**——错误体（`type:error`）、非 JSON、缺 `choices` 一律不改写，绝不把失败响应包装成「看起来成功」；② **usage 双向换算**（input/output ↔ prompt/completion/total）；③ 响应方向也保留缓存明细：Messages→OpenAI 沿用 `cache_read_input_tokens`/`cache_creation_input_tokens` 键，Messages→Responses 挂到 `input_tokens_details.cached_tokens`（Responses 的明细位，计费侧 `nestedLong` 正好读这里）；④ stop/finish 原因双向映射表**与 Converse 的表刻意不共用**——词表不同（Anthropic 有 `pause_turn`/`refusal`，Converse 有 `content_filtered`/`guardrail_intervened`），合并会把两个协议枚举混成一批；⑤ 缓存口径待实测（R2，见类注释）：反向不带缓存键——OpenAI 的 `prompt_tokens` 已含 `cached_tokens`，再携带会被加回型提取器二次计入。

### 明确未做（记录在案）

- **流式 `handleStreamEvent`**：4 个适配器仍未覆写。SSE 协议级转换需要改写/丢弃 `event:` 行、合成 `[DONE]`、按需插入 `message_start`/`content_block_*` 等新事件，而当前管线（`ProxyStreamingFlux`）只把 `data:` 行交给适配器、其余行原样透传，**无抑制/合成能力**——需先改管线，属独立设计决策，不在本批硬凑。
- **工具调用块**：`tool_use` ↔ `tool_calls` 的块级转换未实现（请求侧 `tools` 同样未做 OpenAI/Anthropic 形状转换），工具场景下文本为空、finish 原因照实映射——两侧必须一起做才有意义。

### 测试

+9 例（ChatCompletions 6→9、Completions 5→7、Messages→ChatCompletions 6→8、Responses 5→7）：转换形状 + usage 换算 + finish/stop 双向映射 + **错误体/非 JSON/异形响应原样透传**（反向断言，防止把失败包装成成功）。

**门禁**：`mvn -f knot-server/pom.xml clean test` → 见下条；日志 `audit/response_adapt_gate.log`。

**未 commit，待批准。**

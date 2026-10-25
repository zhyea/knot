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

**未 commit，待批准。**

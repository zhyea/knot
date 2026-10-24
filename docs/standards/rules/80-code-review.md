# 80 · 代码审查尺子与检查清单

> 适用：所有改动提交前自查、交叉评审、AI 会话产出代码的合规判定。
> 完整索引见 `docs/standards/PROJECT-STANDARDS.md`。
> 立规日期：2026-10-24（robin 拍板校准）

---

## 1. 审查尺子（正式校准版）

**主尺：SOLID 五条**（设计模式经典基础原则）。**辅尺：DRY / KISS / YAGNI**。
**工程判据 5 条**（⚠ 明确标注：**不属于设计模式原则**，是本项目按故障史追加的诚实性判据）。

| 编号 | 原则 | 一句话 | 本项目判定方式 |
|---|---|---|---|
| S | 单一职责 | 一个类只有一个引起它变化的原因 | 一个类是否同时承担管理面 CRUD、运行时执行、外部 I/O、展示状态 |
| O | 开闭 | 对扩展开放，对修改封闭 | 新增协议/异常/计费模式时，是否必须修改多处 `if/switch` 集合 |
| L | 里氏替换 | 子类必须能替换父类而不破坏正确性 | 替身/子类覆写是否收窄了契约 |
| I | 接口隔离 | 客户端不应被迫依赖它不使用的方法 | 上下文/句柄是否把大量无关字段塞进同一个宽接口 |
| D | 依赖倒置 | 高层低层都依赖抽象；细节依赖抽象 | `adapter` 是否反向依赖 `dal`；业务类是否直接 `new` 外部客户端 |
| — | DRY | 不要重复自己 | 同一规则/集合/枚举是否多处各自维护 |
| — | KISS | 保持简单 | 是否有假成功、空实现、恒等抽象 |
| — | YAGNI | 你不会需要它 | 是否有为"将来可能"预留却无人使用的扩展点 |
| ⚠E1 | 无假成功 | 返回成功态必须有真实计算/比对支撑 | 恒定返回 `DONE`/`OK`/`true` 且入参未参与计算 |
| ⚠E2 | 无死代码/死配置 | 声明的字段/方法/类必须有消费点 | 字段传到某层后再无读取；私有方法全仓无调用 |
| ⚠E3 | 无恒等抽象 | 方法必须做实际变换 | 纯 `return this` / `return 入参` 且 javadoc 宣称"归一化/规范化" |
| ⚠E4 | 失败不静默 | 吞异常、静默降级必须有注释说明理由 | `catch {}` 无日志无注释；降级路径不可观测 |
| ⚠E5 | 测试必须真的在跑 | 写了测试但 CI 不执行等于没写 | `npm test` / `mvn test` 是否在 CI 门禁里 |

> 尺子的使用纪律：**判违反必须给 `file:line` 证据**；同一处代码同时违反多条时按最严重那条记。

---

## 2. 分级检查清单

### 🔴 Blocker（必须改，不改不提交）

| # | 检查项 | 判据 |
|---|---|---|
| B1 | 无假成功 | 接口返回成功态但没有真实计算/比对 |
| B2 | 依赖方向 | `adapter` / `plugin-api` / `common` 出现反向依赖 |
| B3 | 错误出口一致性 | 同一异常在 HTTP / Kafka / 日志三个出口的 `type`/`code`/`httpStatus`/`stage` 不一致 |
| B4 | 权威来源未复用 | 已存在枚举/常量/Support 承载该语义，代码却另写一套字面量 |
| B5 | 安全相关静默放行 | 频控/限额/审核在故障时返回"通过/0/无"而非 fail-hard |
| B6 | 测试未进 CI | 新增测试文件但 CI 门禁不执行它 |
| B7 | 契约改动漏同步 | 改枚举取值/消息字段但没同步协议文档与下游派生函数 |

### 🟡 Suggestion（应当处理，可带债提交）

| # | 检查项 | 判据 |
|---|---|---|
| S1 | 单一职责 | 一个类 >600 行且混杂多个变化原因（判据是职责，**不是行数**） |
| S2 | 开闭 | 新增一类协议/模式需要改 ≥2 处集合 |
| S3 | DRY | 同一段逻辑（含 `try/catch` 兜底模式）复制 ≥3 次 |
| S4 | 恒等/空实现 | 方法体等价于原样返回，或空实现无注释 |
| S5 | 吞异常无痕迹 | `catch` 后 `return null` 且无日志无注释 |
| S6 | `\|\|` 兜底误用 | `x.status \|\| "ENABLED"` 形式（空串/0 被当默认值） |
| S7 | 命名纪律 | 后缀不在白名单 / 泛化主干名 / 同域缺前缀 |
| S8 | 测试缺口 | 新增分支逻辑无对应测试 |

### 💭 Nit（有时间就做）

| # | 检查项 |
|---|---|
| N1 | 注释与方法体不同步（javadoc 宣称做了 X，实际没做） |
| N2 | import 未使用、死字段、死私有方法 |
| N3 | 魔法数字未提常量 |
| N4 | 展示层 `\|\| "-"` 类兜底可统一为工具函数 |

---

## 3. 反模式台账（本项目实证，含 file:line）

> 只收录**已经发生过**的。新反模式出现时往这里补一行，不要凭想象预写。

| 反模式 | 违反 | 实证 |
|---|---|---|
| 假成功接口 | E1 / KISS | `BillingService.java:398` 返回 `0,0,"DONE"`；`ReconciliationStatusEnum.java:9` javadoc 自认是桩 |
| 未实现功能静默降级 | E1 / E4 | `ModerationGuard.java:111` `log.warn("MASK action is not implemented yet, falling back to FLAG")` —— 用户配了 MASK，实际从未生效 |
| 死配置 | E2 | `PluginBindingView.java:12` `timeoutMs` 经 4 层传到 `DefaultPluginDispatcher` 后无人消费 |
| 恒等抽象 | E3 / KISS | `ModelApiProtocolEnum.java:75` `canonical()` 返回 `this`，8 处调用方误以为有别名归一 |
| 错误出口分叉 | B3 / DRY | `GatewayExceptionHandler.java:200` 映射 `504/timeout_error`；`ErrorMsgDto.java:46-91` 无 timeout 分支，落 `500/upstream_error` |
| 权威枚举未复用 | B4 | `GatewayErrorTypeEnum.java:21` 已定义 `TIMEOUT_ERROR`，`ErrorMsgDto` 却手写 8 个字符串字面量 |
| 下游派生漏改 | B7 | `GatewayTrafficMsgDto.java:242` `statusOf()` 只认 4 个 type，改 type 不升本函数会让新值落 `default` |
| 适配层反向依赖数据层 | D / B2 | `knot-adapter/pom.xml` compile 依赖 `knot-dal`；7 个主类 import `org.chobit.knot.gateway.entity.*`（`UpstreamRequestContext`、`UsageNormalizationSupport`、4 个 `BillingModeCalculator`…） |
| 契约模块承载运行时 | D | `DefaultPluginDispatcher.java:23` `@Component` 位于 `knot-plugin-api` |
| 协议集合多处维护 | O / S2 | `RoutingRuleService.java:90` `DEBUGGABLE_PROTOCOLS`、`MediaProtocolExecutor.java:19`、`TextGenerationProtocolExecutor.java:19`、`UsageExtractorRegistry` if 链、`RoutingTestBillingVerdictService.java:144`、前端两处 `normalizeProtocolCode` |
| 兜底模式复制 | S3 | 8 个 Service 的 `snapshot()` 全是 `try { getById; return JsonKit.toMap } catch (BusinessException e) { return null; }` |
| `\|\|` 兜底误用 | S6 | `useAuthorizationManagement.ts:882` `row.status \|\| "ENABLED"` |
| 测试不进门禁 | E5 / B6 | `knot-front` 已有 10 个 spec 文件，`.github/workflows/ci.yml` 只跑 `vue-tsc` + `vite build` |
| 核心链路零单测 | E5 / S8 | `RoutingResolver`（路由解析）与 `PluginDispatcher`（插件分发）均 0 个测试类；`knot-dal` 77 主类 0 测试 |
| 业务类自建外部客户端 | D / B2 | `RoutingRuleService.java:175` 与 `GatewayOutageService.java:54` 自行 `RestClient.builder()`（对比：`RestClientDecisionsTransport` 在 `service.support`，是合理形态） |
| 泛化主干名残留 | S7 | `SystemController` / `SystemMapper` / `SystemConverter` / `CommonController` / `CommonMappings` / `Base36` / `UtilityProtocolExecutor` 共 8 处仍在，而 `10-backend-java.md §4.4` 声称"既有孤例已整改"——**规范与事实不符** |
| javadoc 与实现不符 | N1 | `canonical()` javadoc 宣称归一化实则恒等；`ModerationGuard` 的 MASK 语义与回落 FLAG 的实现不符 |
| 规范自身腐化 | — | `20-frontend-vue.md:7` 写「无测试框架」，实际已引入 vitest + 10 spec 文件 |

---

## 4. 合规样板（保持，不要"优化"掉）

| 样板 | 位置 | 为什么是对的 |
|---|---|---|
| 有注释的宽容解析 | `BillingConfig.java:298` javadoc 明示「宽松解析：空串或非法 JSON 均返回 null」 | 吞异常但**语义已文档化**，调用方知道 null 的含义 |
| 脏数据与故障区分 | `RedisTrafficCounterStore.java:68` 「脏数据不是 Redis 故障…不触发存储故障告警」 | fail-hard 由 `RedisCache` 统一承担，这里只处理数据脏 |
| 连接断开的空 catch | `RoutingRuleController.java:281` 注释说明无法再向客户端补发事件 | 空 catch 但有**不可为的书面理由** |
| 禁止实例化构造器 | `JsonKit` / `MapNumberUtils` 等 36 处 `private Xxx(...)` | 工具类标准写法，**不是死代码**（审查时勿报） |
| 建造者返回 this | `TokenBillingUsage.java:87` `tokenUsage(...) { return this; }` | 链式建造，**不是恒等抽象** |
| 测试替身返回 this | `CaffeineModelLoadStoreTest.java:147` `withZone(...) { return this; }` | 同上 |
| 接口默认 no-op 实现 | 4 个 Store 的 `expireAfterRead(...) { return currentDuration; }` | "使用默认值"的显式表达，**不是恒等抽象** |
| 里氏替换干净 | 31 个主代码继承/实现全为策略族与框架扩展（`UsageExtractor` 7 / `PluginHandler` 8 / `AbstractUpstreamProtocolExecutor` 5 / `UpstreamRequestAdapter` 4 / `PluginDispatchContext` 6 / `GatewayControllerSupport` 7），替身全在 test 侧 | 无契约收窄 |
| 窄接口 | `PluginDispatchContext` 仅 traceId / traceparent / attributes 三项 | "上下文字段多"**不是** ISP 违反，别误报 |
| 魔法数字已提常量 | `EjectionPolicy` / `RetryPolicy` / `RestClientDecisionsTransport` 的超时与阈值均为 `static final` | 无散落字面量 |
| 前端 TS 纪律 | `: any` / `as any` **0 处**；`console.*` 仅 1 处 | `20-frontend-vue.md §3` 执行到位 |

> 首轮全仓审查结论（🔴5 / 🟡5 / ✅3）见 `docs/基础编码原则审查报告-2026-10-24.md`。
> 该报告同时标注了**未覆盖项**（前端系统性审查、`knot-dal` 零测试的判定、计费/路由数值正确性），
> 后续审查优先补这些，不要因为"已经审过一轮"就跳过。

---

## 5. 审查流程（铁律）

```
① 明确尺子      → 先声明用哪几条判，再动手（防止把工程判据说成 SOLID）
② 脚本初筛      → python .workbuddy/audit/principle_scan.py（只读，产候选）
③ 逐个 grep 复核 → 每条候选必须读上下文，排除误报后才进结论
④ 分级定档      → 🔴 / 🟡 / 💭，附 file:line
⑤ 记录台账      → 新反模式补 §3，合规样板补 §4
```

**③ 是铁律**。初筛误报率实测很高：一次扫描 329 条候选，复核后真违规仅十余条——
`placeholder` 属性被当"占位实现"、私有构造器被当"死方法"、建造者 `return this` 被当"恒等抽象"、
跨文件引用漏数导致活字段被当死字段。**脚本输出永远不能直接当清单交付。**

**扫描器自身纪律**：注释必须替换为等长空白以保行号不变（曾因吞换行导致全部行号偏移，
结论的 file:line 全不可信）；构造器（方法名 == 类名）必须排除；跨文件引用必须计入。

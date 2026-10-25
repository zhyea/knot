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
| 假成功接口 | E1 / KISS | ~~`BillingService.java:398` 返回 `0,0,"DONE"`；`ReconciliationStatusEnum.java:9` javadoc 自认是桩~~ → **2026-10-24 批次一已删除**（对账功能整体下线：接口/服务/DTO/VO/枚举 + 前端页面/路由/API + data.sql 菜单 id=21/权限 126/绑定 65 + `NumericStatusEnumContractTest` 清单项，共 8+3+3+1 处；PRD/技术方案同步订正） |
| 未实现功能静默降级 | E1 / E4 | ~~`ModerationGuard.java:111` `log.warn("MASK action is not implemented yet, falling back to FLAG")` —— 用户配了 MASK，实际从未生效~~ → **2026-10-24 已根除（连同功能一起移除）**：robin 拍板不做 MASK，枚举值/`ofOrDefault` 静默兜底/`log.warn` 回落分支全部删除，`PluginProperties` 加 `@PostConstruct` 启动校验——写错 action（含 `MASK`）直接启动失败。**「删功能」和「删假成功」必须同时做，否则就是换个地方静默** |
| 死配置 | E2 | `PluginBindingView.java:12` `timeoutMs` 经 4 层传到 `DefaultPluginDispatcher` 后无人消费 |
| 恒等抽象 | E3 / KISS | `ModelApiProtocolEnum.java:75` `canonical()` 返回 `this`，8 处调用方误以为有别名归一 |
| 错误出口分叉 | B3 / DRY | `GatewayExceptionHandler.java:200` 映射 `504/timeout_error`；`ErrorMsgDto.java:46-91` 无 timeout 分支，落 `500/upstream_error` |
| 权威枚举未复用 | B4 | `GatewayErrorTypeEnum.java:21` 已定义 `TIMEOUT_ERROR`，`ErrorMsgDto` 却手写 8 个字符串字面量 |
| 下游派生漏改 | B7 | `GatewayTrafficMsgDto.java:242` `statusOf()` 只认 4 个 type，改 type 不升本函数会让新值落 `default` |
| 适配层反向依赖数据层 | D / B2 | `knot-adapter/pom.xml` compile 依赖 `knot-dal`；7 个主类 import `org.chobit.knot.gateway.entity.*`（`UpstreamRequestContext`、`UsageNormalizationSupport`、4 个 `BillingModeCalculator`…） |
| 契约模块承载运行时 | D | ~~`DefaultPluginDispatcher.java:23` `@Component` 位于 `knot-plugin-api`~~ → **2026-10-24 已整改**（批次三 3.12）：`DefaultPluginDispatcher` + 其 8 例单测整体迁到 `knot-plugin`（`git mv`，FQCN 保持不变 → 全仓零 import 改动、`plugin-api` 侧不留空包）。调用方只依赖接口 `PluginDispatcher`（仅 gateway 三处注入），admin 对 `knot-plugin` 无依赖也无 dispatcher 引用，故迁移对 IOC 无影响。规则写入 `40-plugin.md` §3.5 |
| 协议集合多处维护 | O / S2 | ~~`UsageExtractorRegistry` if 链~~、~~`RoutingTestBillingVerdictService.java:144`~~、~~前端两处 `normalizeProtocolCode`~~ → **2026-10-24 已整改**：前两处随批次三 2.2b 协议猜测移除消除；前端归一收敛为 `utils/protocolCode.ts`（实测是三份而非两份——`RoutingRuleTestDrawer` 的同名函数实为 canonical 别名薄包装，语义不同，已并入 `canonicalOf` 调用点）。**仍待办**：`RoutingRuleDebugSupport.DEBUGGABLE_PROTOCOLS`（批次三 3.2 从 `RoutingRuleService` 迁出，集合本身未与 `OpenAiCompatibleRequestAdapter.PROTOCOLS` 合源）、`MediaProtocolExecutor`、`TextGenerationProtocolExecutor` 三处协议集合 |
| 兜底模式复制 | S3 | ~~8 个 Service 的 `snapshot()` 全是 `try { getById; return JsonKit.toMap } catch (BusinessException e) { return null; }`~~ → **2026-10-24 已整改**（批次三 3.5）：实测 **14 处** Map 版快照统一走 `AuditSnapshotSupport.capture(id, loader)`；authorization 域 5 处返回 Entity、无吞噬语义，不套用 |
| `\|\|` 兜底误用 | S6 | ~~`useAuthorizationManagement.ts:882` `row.status \|\| "ENABLED"`~~ → **2026-10-24 批次一已改为 `??`** |
| 测试不进门禁 | E5 / B6 | ~~`knot-front` 已有 10 个 spec 文件，`.github/workflows/ci.yml` 只跑 `vue-tsc` + `vite build`~~ → **2026-10-24 批次一已加 `npm test` 步骤**（排在 type-check 之前） |
| 核心链路零单测 | E5 / S8 | `RoutingResolver`（路由解析）与 `PluginDispatcher`（插件分发）均 0 个测试类；`knot-dal` 77 主类 0 测试 |
| 业务类自建外部客户端 | D / B2 | ~~`GatewayOutageService.java:54` 自行 `RestClient.builder()`~~ → **2026-10-24 已整改**（批次三 3.3）：该类无编排职责，整体改名为 `service.support.GatewayOutageRunner`（`Runner` 后缀标识执行器角色），与 `RestClientDecisionsTransport` 同款形态，Controller 直接注入。~~**仍待办**：`RoutingRuleService.java:175`~~ → **2026-10-24 已整改**（批次三 3.2）：调试链路 `RestClient` 与两处 HTTP 出口移入 `service.support.RoutingRuleDebugRunner`，`RoutingRuleService` 不再自建外部客户端 |
| 泛化主干名残留 | S7 | ~~`SystemController` / `SystemMapper` / `SystemConverter` / `CommonController` / `CommonMappings` / `Base36` / `UtilityProtocolExecutor` 共 8 处~~ → **2026-10-24 批次一已整改**：4 处真违规改名/删除（`SystemMapper` 并入 `OperationLogMapper`、`SystemConverter`→`OperationLogConverter`、`CommonController`→`EnumOptionsController`、`CommonMappings`→`SharedMappings`、`GatewayDataService`→`GatewayEntityCacheService`、`GatewayRoutingInfo`→`GatewayRoutingContext`）；`SystemController`（系统管理模块域）/ `Base36`（算法名）/ `UtilityProtocolExecutor`（辅助协议族）判定合规并写入 §4.4 判例表 |
| javadoc 与实现不符 | N1 | `canonical()` javadoc 宣称归一化实则恒等（批次三待整改，与协议猜测清除一起动）；~~`ModerationGuard` 的 MASK 语义与回落 FLAG 的实现不符~~ → **2026-10-24 已消除**（MASK 随功能移除，不存在「语义与实现不符」的第三态） |
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

---

## 6. 拆分定式（3.6 / 4.5 / 4.7 三批实证）

### 6.1 判据：抽象层级，不是行数

```
风险分 = 当前行数 × 改动提交次数     ← 只用来排「先看哪个」
```

行数只作**门槛**，不作排序依据。反例：`DashboardView` 1145 行/26 次提交，`ModelService` 838 行/45 次提交
——前者行数多 37% 但风险分低 21%，只看行数会把结论排反。

**决定性判据是抽象层级**：

| 形态 | 处置 | 判例 |
|---|---|---|
| **跨抽象层的实现细节**（子表持久化、存量 diff） | **必拆** | `ModelApiBindingSupport`：`kb_model_api_bindings` 的读写与 diff，是 `ModelApiBindingMapper` 的唯一使用者，拆完**零委托** |
| **同抽象层的子资源 CRUD** | **不拆** | 供应商模型折扣策略：与模型 CRUD 同层级、端点同挂 `ModelController`；拆它要么留 4 个纯委托（方法数只降 2、多一跳），要么改一条 **SpEL 字符串**（编译期不校验、`contextLoads()` 也捕不到） |

⚠ 拆分目标里写死的数字（如「46→30」）**不能反过来驱动设计**。为凑数制造委托层是净负收益。

「大」≠「该拆」，三类假阳性：**大在样式**（`DashboardView` style 596/脚本仅 321）、
**大但冻结**（`BillingConfig` 784 行仅 15 次提交）、**大但一次性**（`useAuthorizationManagement` 1118 行仅 5 次提交 / 变动 102 行）。

### 6.2 落地步骤

1. **先照抄原分支写测试，再抽**——改造后仍绿即证明等价。
2. **只抽状态机/纯函数，不抽编排**（`runTest` / `runStreamingTest` / 响应侧 computed 留在原处）。
3. 外部依赖与 DOM 依赖都**走注入**；`const {...} = useXxx({...})` **解构接回同名字段** → 调用点与模板零改动。
4. 抽完必须：① 核对**成员账目**（前 N 成员 − 迁出 = 后 N 成员）与调用点数量；② 跑前端 `type-check:strict` 清掉变死的解构名与 import；③ 只有 1 个依赖的 support 补直测；④ **一次就绿的测试必须打双向探针**——去掉被测守卫看是否恰好 N 例变红，还原后 `diff` 与探针前备份逐字节一致。
5. **接线测试 ≠ 内容测试**：前者只证「输出接到了模板」，证明不了内容正确。两者都要有。
6. **拆前先量内部结构**：`.vue` 按 template/script/style 三段量；`.java` 按「顶层方法数 + 最长方法」量。方法多但无超长方法 → 是职责数量问题（抽 Support），不是单函数过胖。

### 6.3 操作陷阱

- 按原始行号多次 `sed -i` 删除会逐次错位：**先 grep 一次取全行号，再从大到小删**。
- 改 javadoc 要**整块替换**：局部替换易丢 `*/` 把下方代码变成注释；也易留下**孤儿 javadoc**（两块 `/** */` 叠在一起，Java 只认最后一块——4.7 迁 `saveApiBindings` 时发现）。
- 混有「待撤改动」与「其他批次配套改动」的文件不能整体 `git checkout` 还原。

---

## 7. 数值 / 字面量审查定式（4.6 实证）

### 7.1 查数值正确性：枚举定义点，不靠读代码猜

同一业务事实往往有多个定义点。**把它们全枚举出来，不一致即缺陷**，不必先跑出错误结果。

判例（1000× 静默错价）：计费单位基数有四处定义点——计算器兜底 / Service 兜底 / `schema DEFAULT` / 枚举 `defaultUnit()`。
三处都兜底成 `1K_TOKENS`，而该基数是金额公式的**分母**（1K=1000 vs PER_SECOND=1 差 1000 倍），
正确默认值随 `billing_mode` 变，**不存在万能默认值**。全门禁绿灯，错价静默发生。

**通用信号**：「兜底值」与「权威默认值」并存，尤其当该值进乘除法的分母。

### 7.2 扫枚举字面量

- 候选 code = **枚举常量名 ∪ 构造第一字符串参数，取并集**。
- 正则**双向**：`equals("X")` 与 `"X".equals(...)` 都要覆盖。
- 赋值处（`status: "ERROR"`）不在比较口径内，须另 grep。
- 反例：`PricingPlanEnum.code()` 返回 `name()` 而构造首参是 **label**（"固定价"）；`BillingUnitEnum` 常量名 `ONE_K_TOKENS` 但 code 是 `"1K_TOKENS"`；`GatewayErrorTypeEnum` 的 code 是**小写**。
- **改前必查调用方确认码表归属**：13 个 code 跨多族同名，选错族字符串相同、编译单测全绿。

### 7.3 报告诚实性

- 「零 X」类断言必须有 grep 支撑（4.5 曾误称「`components/` 零挂载测试」，实际早有 3 个）。
- 必须单列「已排除的误报」（4.5 排除 201 个 `placeholder` 属性误报）与「未覆盖维度」。


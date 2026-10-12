# 路由 · 最空闲模型动态选择方案设计（调研）

> 状态：**方案已评审，4 项主决策 robin 已拍板（2026-10-12），待子决策确认后落地**
> 关联代码（现状基线）：`knot-server/knot-gateway/.../routing/RoutingResolver.java`、`.../routing/ModelPoolSelection.java`、`.../runtime/GatewayRequestHandler.java`（`proxyWithFailover`）、`.../traffic/*`（计数 store）
> 关联设计：`docs/design/routing-sticky-design.md`（同日，粘性路由）、`docs/design/routing-retry-design.md`
> 需求来源：PRD 承诺「负载均衡路由」（`docs/prd/Init Prd.md:89`、候选策略含「负载均衡路由」「条件含负载情况」；`docs/prd/modules/05-模型路由规则.md:8`）
> 设计日期：2026-10-12

## 1. 结论先行

**能。** 除权重（WEIGHTED）与优先级（PRIORITY）的静态配置外，可以基于「全局缓存」实时获取每个模型的负载，动态把请求选到最空闲的模型。

依据（现状证据见 §2）：

- PRD 已把「负载均衡路由」列为路由策略之一，现状却只有顺序 failover（见 `docs/技术选型评估-Gateway是否改用SpringWebFlux-2026-09-18.md:221`「路由是顺序 failover，不是负载均衡」）。本设计即补齐该能力。
- 基础设施复用度很高：候选顺序由 `ModelPoolSelection` 的**策略注入点**产出；store 的**双实现范式**（caffeine / redis 切换）已存在；候选列表**每请求重新生成**（`RoutingResolver.resolveByRule` 每次都跑，数据走 10 min 缓存但排序实时）。
- 真正缺的只有两样：① 一个**可增减的负载 gauge 存储**（现有 `TrafficCounterStore` 是窗口单调计数，没有递减语义）；② 两处**策略分支**（`ModelPoolSelection` 池内 `LEAST_LOAD` + `RoutingResolver.orderTargets` 规则级 `LEAST_LOAD`）。
- **侵入面**（robin 拍板「同时开放规则目标级」后）：`kb_model_pools.selection_strategy` 加枚举值即可；但**规则级需新增 `kb_routing_rules.selection_strategy` 列 + 前端路由规则抽屉加策略选择器**；`selection_strategy` 走 `EnumControl` + `/api/common/enums` 自动出选项。仅 knot-gateway 一个模块做核心逻辑改动。

## 2. 现状证据（代码基线）

| 环节 | 文件 | 当前行为 |
|---|---|---|
| 候选生成 | `RoutingResolver.resolveByRule` → `resolveCandidateModels` → `orderTargets` | primary 排首，其余按 `priority desc → targetCode → targetType` 固定排序 |
| 池内选模 | `ModelPoolSelection.order(strategyCode, …)` | `PRIORITY` / `RANDOM` / `WEIGHTED`（Efraimidis-Spirakis 无放回加权抽样）；首个即选中，其余为 failover 候选 |
| 执行 | `GatewayRequestHandler.proxyWithFailover` | 逐候选 `checkTarget` → `invokeWithRetry` → `record`；成功即返回，后续候选不使用 |
| 负载相关存量 | `TrafficCounterStore`（caffeine / redis） | `addAndGet` + `get`；**RPM 事前计数（仅当策略 `limit>0` 才计数！）、TPM 事后累计**；无「在途并发」计量 |
| 元数据缓存 | `GatewayDataService`（10 min Caffeine） | 模型 / 池 / 池条目 / 频控策略缓存，**无负载语义** |

结论：权重、优先级是**静态配置**，不反映实时拥塞；现成的计数器**不能直接当选路信号**（① 只在配置了限流策略后才写计数；② TPM 是事后累加、滞后；③ 固定分钟窗口）。**没有「空闲度」计量点**——这是本设计要补的核心。

## 3. 方案选型

### 3.1 负载信号（三个候选对比）

| 信号 | 语义 | 复用度 | 短板 |
|---|---|---|---|
| **在途并发 gauge** | 进入候选即 +1、响应结束 −1。按 Little's Law：在途 ≈ 到达率×平均时长，直接反映拥塞（慢/饱和的模型在途数自然高） | 需新增一个 gauge store | 冷启动全 0（可退化到权重序，见 §3.4）|
| **RPM / TPM 窗口计数** | 复用现有 `TrafficCounterStore` 键 | 高（键已存在） | ① 当前只在配置限流策略后才计数，需改成「无条件计数」每条候选 +1；② TPM 事后累加，对「当前空闲」滞后；③ 固定分钟窗口，粒度粗 |
| **加权组合** | 如 `load = inflight + α·rpm`，归一化 | — | 引入调参，v1 不值得 |

**推荐**：v1 以**在途并发**为主信号 + **权重做平手裁决**；RPM 平滑作为 v1.1 可选增强（慢模型低在途的假空闲用 RPM 兜底）。

### 3.2 作用层级（两层都做，robin 拍板）

| 层级 | 决策 | 落点 | 说明 |
|---|---|---|---|
| **模型池内** | ✅ | `ModelPoolSelection.order` 新增 `LEAST_LOAD` 分支 | 池内模型同属一个统一模型，语义可互换，「从 N 个等价模型里挑最闲」 |
| **路由规则目标级** | ✅ | `kb_routing_rules.selection_strategy` 新列 + `RoutingResolver.orderTargets` 分支 | 规则的多个目标（模型/池）之间也按最闲动态重排 |

> 两层正交、互不替代：池内 `LEAST_LOAD` 决定「池里选哪个模型」，规则级 `LEAST_LOAD` 决定「多个目标里先打哪个」。复用同一 `ModelLoadStore` 读数。

#### 3.2.1 路由规则目标级语义

- **新增列** `kb_routing_rules.selection_strategy VARCHAR(32) NOT NULL DEFAULT 'PRIORITY'`（默认 PRIORITY，与现状 `priority desc` 行为一致，向后兼容存量数据）。
- **排序键**（规则级 `LEAST_LOAD`）：`primary 置顶 → load 升序 → priority 降序 → targetCode asc`。
  - `is_primary` 目标**始终钉在首位**——保留「主目标」运维契约，不随负载漂移。
  - 其余目标按 load 升序；load 相同按 `priority` 降序（沿用现状口径）；再同按 `targetCode` 稳定排序。
- **目标负载取值**：
  - `MODEL` 目标 → 直接读 `RoutingRuleTargetDto.targetId()` 对应的模型在途数。
  - `MODEL_POOL` 目标 → 读该池**所有启用池条目的在途数之和**（代表整池压力）。备选：取池内最闲条目（min）/ 均值（avg），默认 sum（文档明示，待 robin 确认）。
- 与 failover 关系：规则级重排后，候选顺序即 failover 顺序——最闲优先、拥塞靠后，天然实现「异常场景秒级切到更闲路径」（对齐 PRD 验收 §6）。

### 3.3 负载存储设计 `ModelLoadStore`

新建与 `TrafficCounterStore` 同构的 gauge 存储（不扩展 `TrafficCounterStore`，理由见 §3.3 末）：

- **接口** `ModelLoadStore`：`enter(key, ttlSeconds)` / `leave(key)` / `long get(key)`。
- **键** `knot:load:MODEL:{modelId}`（modelId 取自 `RoutingRuleTargetDto.targetId()`）。
- **双实现**：
  - `CaffeineModelLoadStore`：用 Caffeine（同 `CaffeineTrafficCounterStore` 的逐键可变过期 `CounterExpiry` 范式）或 `ConcurrentHashMap<String, AtomicLong>` + 写时刷新 TTL。
  - `RedisModelLoadStore`：`enter` = Lua `INCRBY + PEXPIREAT`（原子，杜绝进程崩溃留永不过期键）；`leave` = Lua `DECR` 且 clamp ≥ 0（`key` 不存在直接忽略，幂等）；`get` = `GET`（失败 fail-open 返回 0）。
- **装配**：`@ConditionalOnProperty(name="knot.load.store", havingValue=…, matchIfMissing=true)` 二选一，完全对照 `TrafficCounterConfiguration`。默认 caffeine；**多节点部署切 `redis` 才是全局口径**。
- **fail-open**：Redis 不可用时 `get` 返 0（视为「不拥塞」），记 WARN，绝不阻断选路——与频控 store 同口径。
- **为什么不扩展 `TrafficCounterStore`**：该接口契约是「窗口内单调累加 + 自然过期」，gauge 需要**递减**，且会把负载键混进频控键空间、破坏语义边界；独立抽象更清晰，也契合项目「interface + 子类 + 配置切换」惯用法。

### 3.4 选择算法（`ModelPoolSelection` 新分支）

`ModelPoolSelection.order` 现有签名 `(candidates, strategyCode, priorityOf, weightOf, tieBreakerOf)`，新增 4 个函数参数 `loadOf`（同 `ToIntFunction` 范式；缺失计数按 0 计 = 空闲）。`LEAST_LOAD` 分支：

```
sort:  load 升序  →  weight 降序  →  tieBreaker(id) 升序
```

- **确定性排序**（非随机）：与 `WEIGHTED`/`RANDOM` 的本质区别是稳定可复现，便于排障。
- **冷启动 / 全 0**：退化为「weight 降序 + id 升序」——即 WEIGHTED 的确定性版本，行为可预期。
- **平手裁决用权重**：保留运维的权重意图作为次级信号，避免拥塞归零时抖动。
- **排序时点**：解析时（`RoutingResolver.resolvePoolCandidates`）每请求一次；failover 内**不重排**（对齐粘性设计「可用性优先，不把请求打失败」——同一请求内 failover 顺延是既定事实，不回炉重选）。

> 后续可扩展：① 每次 failover 前按当前负载重排剩余候选（v1.1）；② 把 `load` 扩展成 `max(modelInflight, accountInflight)`（同账户多模型共拥塞）；③ RPM/TPM 平滑。

### 3.5 在途计数生命周期（enter / leave 成对）

| 场景 | 计数点 | 说明 |
|---|---|---|
| 非流式 | `proxyWithFailover` 循环内 `enter(candidate)`，`finally leave(candidate)` | proxy 返回即结束，finally 完整覆盖 |
| 流式 | `ProxyStreamingBody` 的 `pumpEventStream`/`pumpRaw` 的 `finally` 回调 `leave` | 首字节后请求线程已返回，在途须覆盖到流写完；`ProxyStreamingBody` 构造时注入 `Runnable onComplete` |
| failover | `leave(A)` 后 `enter(B)`，成对 | 同一请求不双计 |
| 客户端断连 | 流 pump 抛 `IOException` → finally 仍触发 `leave` | ✓ |
| enter 失败（redis 故障） | 记 WARN，当 0 处理 | 不阻断请求 |

**泄漏防护**：`enter` 每次刷新 TTL（如 300s），Caffeine 逐键过期兜底；泄漏键最多存活一个 TTL 即归零。

### 3.6 与现有机制交互

| 机制 | 交互 |
|---|---|
| 重试（`invokeWithRetry`） | 正交：LEAST_LOAD 只改候选顺序，重试仍在单候选内部 |
| failover | 命中即返回；未在请求内重排——对齐现状与粘性设计 |
| 频控/额度（`GatewayTrafficGuard`） | 无改动；被限的模型在 LEAST_LOAD 下依然低分优先，但 `checkTarget` 拒即顺延，可用性优先 |
| 粘性路由（同日，v2 一致性 hash 置顶） | 粘性只在模型池内开启，命中目标被置顶为候选[0]；**LEAST_LOAD 是池内策略的另一种选择，二者皆作用于同一池内选模点**：若池开了粘性则按 hash 置顶、否则若策略=LEAST_LOAD 则按最闲选首位，其余候选按既定序作 failover，二者正交 |
| 计费 / knot_extend | 无改动，不透出任何对外字段 |
| `GatewayDataService` 10 min 缓存 | 互不感知：那是策略数据缓存，负载 gauge 是独立键空间 |

### 3.7 可观测

- `GatewayRequestContext.attributes` 记录：选中的 `modelLoad`（在途数）、`loadStrategyHit`。
- WARN 日志：load store 故障 fail-open、**单节点 caffeine 在集群部署下的近似提示**（启动日志明示当前 store）。
- 不新增响应头（对齐粘性与重试的收敛决策）。

## 4. 落地影响面（文件级，未执行）

**DB（2 处）**
- `kb_model_pools.selection_strategy`：枚举加值 `LEAST_LOAD`，**列已存在、无需 DDL**。
- `kb_routing_rules` 新增列 `selection_strategy VARCHAR(32) NOT NULL DEFAULT 'PRIORITY'`（schema.sql 加列；迁移 `docs/database/migration/2026-10-12-routing-least-load.sql`：`ADD COLUMN IF NOT EXISTS` 幂等，先跑再 package）。存量数据默认 PRIORITY，行为不变。

**后端（knot-server，仅 knot-gateway 改动为主）**
- 新包 `org.chobit.knot.gateway.load`：`ModelLoadStore`（接口）、`CaffeineModelLoadStore`、`RedisModelLoadStore`、`ModelLoadStoreConfiguration`（`@ConditionalOnProperty` 双实现 + `knot.load.*`）、`ModelLoadKeys`（键拼装，对照 `TrafficCounterKeys`）。
- `knot-common/.../gateway/constants/enums/ModelPoolSelectionStrategyEnum.java`：加 `LEAST_LOAD("LEAST_LOAD", "最少负载")`（前端 `EnumControl` 自动下发，池级零前端改动）。
  - ⚠ 规则级策略建议**复用同一枚举**（PRIORITY/LEAST_LOAD），不另起枚举——规则级与池级语义同源。
- `knot-gateway/.../routing/ModelPoolSelection.java`：`order(…)` 加 `loadOf` 参数 + `LEAST_LOAD` 分支；单调用方 `RoutingResolver` 同步补参。
- `knot-gateway/.../routing/RoutingResolver.java`：
  - 注入 `ModelLoadStore`；池展开处传 `loadOf = id -> loadStore.get(key(id))`。
  - `orderTargets(ruleCode, strategyCode)` 加规则级 `LEAST_LOAD` 分支：`primary 置顶 → load 升序 → priority 降序 → targetCode`；读规则级策略（`rule.getSelectionStrategy()`，空/未知回落 PRIORITY）。
- `knot-gateway/.../runtime/GatewayRequestHandler.java`：`proxyWithFailover` 内 enter/leave（非流式 finally）。
- `knot-gateway/.../upstream/stream/ProxyStreamingBody.java`：构造注入 `Runnable onComplete`，两个 pump 的 finally 调用（流式 leave）。
- `RoutingRuleEntity` / `RoutingRuleDto` / `RoutingRule` VO / `RoutingRuleMapper.xml` / `RoutingRuleConverter`：加 `selectionStrategy` 字段（对照既有 `logicalModelCode` 链路逐站补）。

**前端（knot-front，1 处）**
- `components/routing/RoutingRuleFormDrawer.vue`：加「目标选择策略」下拉（`EnumControl enum-name="ModelPoolSelectionStrategyEnum"`，复用池级枚举），默认 PRIORITY；`useDraftModel.ts` 深比较勿漏字段（10-06 `RetryPolicySection` 漏比 `mode` 教训）。池级表单 `ModelPoolFormDrawer.vue` 已接 `EnumControl`，无需改。

**测试（新增）**
- `ModelPoolSelectionTest`（当前缺该类的单测——`order` 三种策略无专属单测，本设计顺带补）：LEAST_LOAD 排序（load 升序 + 平手权重 + 全 0 退化权重序）。
- `ModelLoadStore` caffeine/redis 实现测（enter/leave/get/clamp/fail-open），对照 `CaffeineTrafficCounterStoreTest`。
- 网关集成测试：高在途模型落选、空闲模型选中；流式结束才 leave（无泄漏）。
- 真启 jar 冒烟：回声上游 + 并发压一模型 → 验证请求流向空闲模型；冒烟后清理。

## 5. 验证门禁

1. 后端：`mvn -f knot-server/pom.xml package`（含测试）。
2. 前端：`npm run type-check` + `npm run build`（build 后还原 `components.d.ts`）。
3. 真启 admin jar 冒烟（一次性库 + `KNOT_SQL_INIT_MODE=NEVER`）：压测验证最闲选中 → 复原；冒烟数据零残留。
4. 全量 grep 残留确认；未经显式批准不 `git commit`。

## 6. 风险与限制

- **多节点无 Redis → 退化节点内**：不同节点各自计各自在途，流量均匀时近似有效，倾斜时不准。启动日志明示当前 store；文档要求多节点必须 `redis`。
- **振荡风险**：全部空闲时新请求都冲最闲模型（经典 JSQ 抖动）。权重平手裁决 + 确定性排序把抖动降到最低；如需更稳，v1.1 引入「仅在负载差 > 阈值才切换」的迟滞。
- **慢模型假空闲**：纯在途对「高吞吐快模型」可能偏高、对「低吞吐慢模型」偏低。RPM/TPM 平滑（v1.1）兜底。
- **gauge 泄漏**：靠 TTL + Caffeine 逐键过期兜底，最坏残留一个 TTL。
- **容量单位**：v1 用模型级在途；同账户多模型共拥塞的账户级计量留 v1.1。

## 7. 决策状态（robin 已拍板 + 子决策确认默认值，2026-10-12）

**4 项主决策**（拍板）与 **4 项子决策**（确认用默认值）均已锁定，设计完成，可进入落地。

| # | 项 | 决策 |
|---|---|---|
| 1 | 负载信号口径 | **在途并发**（新建 `ModelLoadStore` gauge）；RPM/TPM 平滑留 v1.1 |
| 2 | 作用范围 | **两层都做**：模型池内 + 路由规则目标级 |
| 3 | 存储开关 | **独立 `knot.load.store`**（caffeine 默认 / redis 多节点），对齐粘性范式 |
| 4 | 与粘性 v2 组合 | **粘性置顶优先，其余按 LEAST_LOAD** |

| # | 子决策 | 确认值（默认） |
|---|---|---|
| 1 | 规则级 `is_primary` 是否始终钉首 | **是**（保留主目标契约，不随负载漂移） |
| 2 | `MODEL_POOL` 目标负载取值 | **sum（池内启用条目在途之和）** |
| 3 | 规则级策略枚举 | **复用池级 `ModelPoolSelectionStrategyEnum`** |
| 4 | caffeine 单节点集群告警 | 启动日志明示「单节点近似、多节点请切 redis」 |

## 8. 落地批次建议（按风险排序，逐批显式确认）

> 每批末尾跑对应门禁（mvn test / vue-tsc / vite / 真启 jar 冒烟），全绿再进下一批；未经显式批准不 `git commit`。

**批次 1（隔离基础设施，零行为变更）**
- 新包 `org.chobit.knot.gateway.load`：`ModelLoadStore` 接口、`CaffeineModelLoadStore`、`RedisModelLoadStore`、`ModelLoadStoreConfiguration`（`knot.load.store` 双实现）、`ModelLoadKeys`。
- `ModelPoolSelectionStrategyEnum` 加 `LEAST_LOAD("LEAST_LOAD","最少负载")`。
- 测试：`ModelLoadStore` caffeine/redis 实现测（enter/leave/get/clamp/fail-open）。

**批次 2（池内策略分支，默认 PRIORITY 行为不变）**
- `ModelPoolSelection.order` 加 `loadOf` 参数 + `LEAST_LOAD` 分支（`load 升序 → weight 降序 → id 升序`；全 0 退化权重序）。
- `RoutingResolver` 注入 `ModelLoadStore`，池展开处传 `loadOf = id -> loadStore.get(key(id))`。
- 测试：新增 `ModelPoolSelectionTest`（LEAST_LOAD 排序 + 平手 + 冷启动退化）。

**批次 3（规则级列 + 字段 + 前端，schema/迁移有数据风险）**
- DB：`schema.sql` 加 `kb_routing_rules.selection_strategy VARCHAR(32) NOT NULL DEFAULT 'PRIORITY'`；迁移 `docs/database/migration/2026-10-12-routing-least-load.sql`（幂等 ADD COLUMN）；先跑迁移再 package。
- 后端：`RoutingRuleEntity`/`Dto`/`VO`/`Mapper.xml`/`Converter`/`Service` 加 `selectionStrategy`；`RoutingResolver.orderTargets(ruleCode, strategyCode)` 加规则级 `LEAST_LOAD` 分支（`primary 置顶 → load 升序 → priority 降序 → targetCode`）。
- 前端：`RoutingRuleFormDrawer.vue` 加「目标选择策略」下拉（复用池级 `EnumControl`）+ `useDraftModel.ts` 深比较补字段。

**批次 4（在途计数生命周期，热路径正确性，功能真正生效）**
- `GatewayRequestHandler.proxyWithFailover`：`enter(candidate)` / `finally leave(candidate)`（非流式）。
- `ProxyStreamingBody` 构造注入 `Runnable onComplete`，两 pump 的 `finally` 调 `leave`（流式覆盖到写完）。
- 集成测试：高在途模型落选、空闲模型选中；流式结束才 leave（无泄漏）。
- 真启 jar 冒烟：回声上游 + 并发压一模型 → 验证请求流向空闲模型；冒烟后清理零残留。

> **落地状态（2026-10-12 一次性排期全部批次，已实施完成，待 robin 批准 commit）**
> | 批次 | 范围 | 门禁结论 |
> |---|---|---|
> | 1 | load 包 + 枚举 | mvn test BUILD SUCCESS（CaffeineModelLoadStoreTest 6 例 PASS）；零行为变更 |
> | 2 | 池内策略分支 | mvn test BUILD SUCCESS（ModelPoolSelectionTest 7 例 PASS） |
> | 3 | 规则级列 + 字段 + 前端 | mvn test BUILD SUCCESS；vue-tsc EXIT=0；vite build EXIT=0；迁移探针两轮幂等通过；冒烟 S1-S5 PASS |
> | 4 | 在途计数生命周期 | mvn test BUILD SUCCESS（ProxyStreamingBodyTest 4 例 PASS）；真启 jar 冒烟全绿；零残留 |
> - 存储开关：`knot.load.store`（caffeine 默认 / redis 集群）；集群部署日志提示切 redis。
> - 与同日粘性路由 v2 组合语义：粘性置顶优先、其余按 LEAST_LOAD。
> - 源码零 commit，待显式批准。

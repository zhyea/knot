# 模型池 · 粘性路由方案设计

> 状态：**v2 已按 robin 拍板修订（2026-10-12），待评审后落地；未写任何代码**
> 拍板记录（robin，2026-10-12 20:00）：
> 1. 粘性**仅在模型池内**开启，不在路由规则层；
> 2. **默认不开启**；
> 3. 默认从 **Header** 取粘性 key，头名默认 `X-Knot-Sticky-Key`，**可修改**；
> 4. 对粘性 key 做**一致性 hash**，按运算结果取池中模型作为**首选模型**；
> 5. 统一缓存接口（caffeine/redis 可配）——经论证**不引入**，见 §7。
>
> v1（状态式绑定 + 规则级配置）已整体作废，本文档为完整修订版，非增量补丁。
> 关联代码（现状基线）：`knot-server/knot-gateway/.../routing/RoutingResolver.java`（`resolvePoolCandidates`）、`.../routing/ModelPoolSelection.java`、`.../runtime/GatewayRequestHandler.java`
> 参照样板：`docs/design/routing-retry-design.md`（规则级 JSON 策略列 + record 归一化）

## 1. 背景与现状

### 1.1 现状链路（池展开段）

```
RoutingResolver.resolveByRule(apiKey, ruleCode)
  └─ resolvePoolCandidates(pool, target)
       ├─ available = 池内启用条目（status=enabled 且模型 enabled）
       ├─ ordered = ModelPoolSelection.order(available, selection_strategy)   // PRIORITY / RANDOM / WEIGHTED
       └─ 候选 DTO 列表（ordered[0] 即本次首选，其余为 failover 链）
```

### 1.2 缺口：会话多轮请求在池内漂移

- `RANDOM` / `WEIGHTED` 策略**每请求重掷**：同一会话（同一终端用户）的第 2、3…N 轮落到池内不同模型/供应商账户。
- 后果一（主）：**上游 prompt cache 全部落空**。OpenAI / Anthropic / Google 的缓存按「账户 + 区域 + 模型部署」命中，漂移即丢缓存 → 输入成本与首 token 延迟双劣化。
- 后果二：排障路径不唯一。
- 现状无任何会话亲和机制（全库 grep `粘性|sticky` 零命中）。

### 1.3 为什么粘性在池层而不在规则层（拍板 1 的落地依据）

| 层 | 目标间语义 | 粘性是否有意义 |
|---|---|---|
| 路由规则 targets | **主备**（primary + priority 降序，故障转移语义） | 无意义——主备路径的切换本就该由故障驱动 |
| 模型池 items | **均衡**（同统一模型的多账户/多部署，权重/随机/优先级都是分流手段） | **有意义**——粘性是池内分流的一个额外维度：会话亲和 |

池内模型全部映射同一统一模型（`kb_model_pools.logical_model_code` 约束），功能等价，粘性不改变行为语义，只改变「谁接到这单」。

## 2. 设计目标与原则

- **opt-in**：默认关闭（拍板 2）。粘性是流量塑形（用均衡换缓存），与 retry 的「内 healing」性质不同。
- **无状态**：一致性 hash 决定首选，**不记录、不存储、不过期**（拍板 4）。多节点天然一致，无绑定失效、无并发双写、无 TTL 管理。
- **只改首选，不改 failover**：hash 命中目标置顶为候选 `[0]`，其余保持池策略原序。故障转移、频控顺延逻辑零改动。
- **可用性优先**：粘性目标任何不可用（被频控顺延、上游失败）都不打失败请求，靠既有 failover 兜底。
- **零侵入策略层**：`ModelPoolSelection` 不改；粘性是 `resolvePoolCandidates` 内对 `ordered` 列表的一次置顶改写。
- **配置最小面**：2 个字段（开关 + 头名），单 TEXT JSON 列，不设双模式、不暴露 hash 参数。

## 3. 粘性模型

### 3.1 粘性键（Sticky Key）

| 维度 | 决策 |
|---|---|
| 来源 | **仅 Header**（拍板 3）：`X-Knot-Sticky-Key`（默认，可配），控制器 `@RequestHeader(required = false)` 透传 |
| 归一化 | trim；空值（未传/空白）→ **整个粘性逻辑跳过**，等同未启用；长度上限 512 字符（防滥用，不截断——MD5 输入无上限需求） |
| 隐私 | 日志与 trace 只出 hash 后的键（MD5 hex 前 8 位），不出原文 |
| body 来源 | **不纳入首版**（robin 字面只要 header）。后续如需 `user` / `metadata.session_id` 点分路径，作为独立增量排期 |

### 3.2 一致性 hash（拍板 4）

```
ring = ConsistentHashRing(candidates)   // candidates = 池内可用模型（model_code）
   对每个候选建 VIRTUAL_NODES=160 个虚拟节点：
   nodeKey = md5long(modelCode + "#" + i)   // i ∈ [0, 160)
   全部 nodeKey 装入 TreeMap<Long, modelCode>

select(stickyKey):
   h = md5long(stickyKey)
   entry = ring.ceilingEntry(h)           // 首个 ≥ h 的虚拟节点
   return entry == null ? ring.firstEntry() : entry   // 绕回环首
```

| 决策 | 理由 |
|---|---|
| **一致性 hash 而非 `hash % n`** | 池成员变化（增删模型/停用启用）时只影响环上相邻段，未变化的 key 保持命中——把「池变更导致的缓存重击」限制在最小范围 |
| **MD5 取前 8 字节为 long** | Ketama 经典口径；JDK `MessageDigest` 自带，零依赖；只要确定性与均匀度，不需抗碰撞 |
| **虚拟节点 160，固定不暴露** | Ketama 经典值；池典型 ≤20 模型时分布均匀度足够。该参数只影响均匀度、不影响正确性，暴露给用户是配置噪音 |
| **环成员 = 池内可用候选** | 与 `resolvePoolCandidates` 的 `available` 同源；成员变化 → 环自然重建 |

### 3.3 语义：hash 置顶

```
池启用粘性 且 stickyKey 非空：
    preferred = ring.select(stickyKey)
    ordered    = [preferred, ordered 除 preferred]      // preferred 必在 ordered 中（环成员同源）
否则：
    ordered 原样（走 selection_strategy）
```

- hash 只决定首选 `[0]`；**failover 链保持池策略原序**（`ModelPoolSelection` 的输出）。
- 无绑定、无 TTL、无失效逻辑、无 bind/invalidate——**没有任何需要「删绑定」的失败路径**。
- 与 `selection_strategy` 的关系：策略决定「无粘性时的首选 + failover 顺序」；粘性开启时首选被 hash 改写，策略仍管其余顺序，三者不互斥。

### 3.4 与既有机制的交互

| 机制 | 交互 |
|---|---|
| failover（`proxyWithFailover`） | 首选被 hash 改写，失败后顺延逻辑原样；**无删绑定动作**（对比 v1 状态式方案的正确性三规则，v2 全部消失） |
| 重试（`invokeWithRetry`） | 正交，零改动 |
| 频控/额度（`checkTarget`） | 无改动；首选被限流/限额拒 → 顺延下一候选，请求不失败 |
| 池策略（`ModelPoolSelection`） | 无改动；其输出作为 failover 基序 |
| 计费 / knot_extend / 对外契约 | 零改动，粘性不透出任何对外字段 |

## 4. 执行流程

### 4.1 链路改动点（3 处）

1. **入口透传**：控制器新增可选头 `X-Knot-Sticky-Key`（`GatewayHeaders` 加常量），`handle / handleMultipart` 透传，`GatewayRequestContext` 加 final 字段。
2. **池展开置顶**：`RoutingResolver.resolvePoolCandidates` 内在 `modelPoolSelection.order(...)` 之后、映射候选 DTO 之前，按池的 `sticky_policy` 做 §3.3 置顶。**`GatewayRequestHandler` 零改动**。
3. **配置解析**：`ModelPoolEntity` 加 `stickyPolicy`（String/JSON），经既有 Dto/VO/Converter/Mapper 链路落库与回显。

```java
// RoutingResolver.resolvePoolCandidates 内（示意，非落地代码）
List<PoolItemCandidate> ordered = modelPoolSelection.order(available, pool.getSelectionStrategy(), ...);
StickyPolicy sticky = StickyPolicy.parse(pool.getStickyPolicy());
if (sticky.enabled() && stickyKey != null) {
    ConsistentHashRing ring = ConsistentHashRing.of(ordered, PoolItemCandidate::modelCode);
    String preferred = ring.select(stickyKey);       // 命中必在 ordered 中
    ordered = moveToFront(ordered, preferred);
}
```

### 4.2 边界规则

- stickyKey 为空（头未传/空白）→ 跳过粘性，`ordered` 原样。
- 池未启用粘性（默认）→ 同左。
- 环为空/单候选 → `select` 直接返回唯一候选，等效无操作。
- **热点倾斜**：少数高频 key（如全部传同一个会话 id）会把流量集中到环上相邻段——这是一致性 hash 的固有特性，由频控层兜底；文档需向用户明示「粘性 = 用均衡换缓存」。

## 5. 配置 Schema（模型池级 `sticky_policy` JSON 列）

`kb_model_pools` 新增列 `sticky_policy`（`TEXT DEFAULT NULL`）：

```json
{
  "enabled": false,
  "headerName": "X-Knot-Sticky-Key"
}
```

| 字段 | 含义 | 默认 | 约束 |
|---|---|---|---|
| `enabled` | 是否启用粘性 | `false` | **默认关，opt-in**（拍板 2） |
| `headerName` | 取粘性 key 的头名 | `X-Knot-Sticky-Key` | 非空、trim；大小写不敏感（HTTP 头语义） |

约定：`sticky_policy` 为空/解析失败 → `StickyPolicy.DEFAULT`（关闭）。**不设 simple/professional 双模式**（字段共 2 个且平权）；**不暴露虚拟节点数/TTL/键来源**——分别是内部常数、无状态方案不存在的概念、首版不支持的来源。

> **头名可配的边界说明**：控制器按默认头 `X-Knot-Sticky-Key` 收参（Spring `@RequestHeader` 必须在映射期声明，无法运行时改）。`headerName` 配置项的真实语义是**接入侧约定**：管理端配置改名后，由接入文档/客户端按新头名发送；控制器侧固定读默认头。若需「运行时任意头名」，要改控制器为 `HttpServletRequest` 全量取头——复杂度与收益不匹配，首版不做，此处显式标注避免误解。

## 6. 可观测性

- `GatewayRequestContext.attributes` 记录：`stickyKeyHash`（MD5 hex 前 8）、`stickyApplied`（是否应用了粘性置顶）、`stickyTargetCode`（hash 选中的目标）。
- **不加响应头**（对齐 retry 的收敛决策）。
- WARN 日志：仅 stickyKey 超长被拒等异常输入，不打原文。

## 7. 缓存接口论证（拍板 5：统一缓存接口是否有必要）

**结论：不引入 `StickyStore` 接口，不配 caffeine/redis 二选一。** 分层论证：

| 候选缓存对象 | 是否必要 | 结论 |
|---|---|---|
| 粘性结果 / 绑定 | **无** | 一致性 hash 是 `(池成员, key)` 的确定性纯函数，每次现算即得，无状态可存——robin 的判断成立，v1 的整套 bind/invalidate/TTL 全部消失 |
| hash 环结构 | **有，但只需进程内 Caffeine 单实现** | 160 虚拟节点 × 池大小（典型 ≤20）= ≤3200 次 MD5 + TreeMap 插入，每请求约 1–3ms，网关热路径不可忽略。缓存环（bounded、`expireAfterWrite` 与池数据缓存同周期）后降为一次 GET |
| 共享存储（Redis） | **无** | 环是池成员的确定性纯函数，**各节点各自算出完全一致的环**，无共享必要；Redis 往返（~1ms）反而比本地缓存更慢，还引入一致性依赖与故障面 |

**为什么不抽象统一接口**：双实现接口的价值在于「语义相同、存储可换」（如频控计数的全局口径需求）。粘性场景两个实现语义不同（本地缓存是性能优化，Redis 是无性能收益的共享），为一个假设的「将来可能共享」提前抽象接口属于空转。若未来出现真共享需求——**故障退避**（把近期连续失败的目标临时摘出环，§11 follow-up）——届时按需引入，接口形态由真实需求决定，不预埋。

对照组：频控层 `TrafficCounterStore`（caffeine/redis 双实现）是**真需要**可配的场景——计数有状态且要求全局口径。两个场景并排，恰好说明「缓存接口按场景定，不为统一而统一」。

## 8. 默认值

默认关闭。开启后：仅 header 来源、头名 `X-Knot-Sticky-Key`、虚拟节点 160、hash 环进程内 Caffeine 缓存（`maximumSize=512`，`expireAfterWrite=10min`，与 `GatewayDataService` 池缓存同周期）。

## 9. 落地影响面（文件级，待 robin 批准后实施）

**DB**
- `knot-admin/src/main/resources/db/schema.sql`：`kb_model_pools` 加 `sticky_policy TEXT DEFAULT NULL COMMENT '池内粘性路由策略（JSON）；空表示未启用'`。
- `docs/database/migration/2026-10-12-model-pool-sticky.sql`：幂等 `ADD COLUMN IF NOT EXISTS`。**先跑迁移再 package。**

**后端（knot-server）**
- `knot-common/.../gateway/model/StickyPolicy.java`：record（`enabled` / `headerName`），紧凑构造器归一化 + `parse` / `serialize`，对照 `RetryPolicy.java`。
- `knot-common/.../gateway/model/StickyKeyNormalizer.java`（或并入 StickyPolicy）：trim、空判、512 上限、MD5 hash 输出。
- `knot-gateway/.../routing/ConsistentHashRing.java`：虚拟节点环 + `select`，纯函数无 Spring 依赖。
- `knot-gateway/.../routing/StickyRingCache.java`：Caffeine 环缓存（以「池码 + 成员指纹」为键，成员变化即换环）。
- `knot-gateway/.../routing/RoutingResolver.java`：`resolvePoolCandidates` 内置顶逻辑；`resolveByRule` 透传 stickyKey（来自 `GatewayRequestContext`）。
- `knot-gateway/.../runtime/GatewayRequestContext.java`：加 stickyKey final 字段；`AbstractGatewayRequestTemplate` / `GatewayControllerSupport` / 各控制器（`GatewayTextController` 等 7 个）透传可选头；`constants/GatewayHeaders.java` 加 `X_KNOT_STICKY_KEY`。
- 模型池链路逐站补 `stickyPolicy`：`ModelPoolEntity` → `ModelPoolDto` → `ModelPool` VO → `ModelPoolConverter` → `ModelPoolMapper.xml`（`PoolSelectColumns` + `insert` + `update`）→ `ModelPoolService`（`enrich` / `normalize` / `updateStatus` 三处 record 构造 + 保存校验）。

**前端（knot-front）**
- `components/model/ModelPoolFormDrawer.vue`：「基础信息」section 加「粘性路由」卡片（开关 + 头名输入，对照 `RetryPolicySection` 的卡片样式但更轻）；`buildPayload` / `resetForm` / `PoolFormState` 同步。
- 类型定义（`ModelPool` interface）+ 校验（头名非空、开启时头名必填）。

**测试**
- 单测：`StickyPolicy` 归一化（默认关、头名归一/空值回落）；`ConsistentHashRing`（同键稳定命中、分布均匀度抽样、成员增删的最小漂移断言、空环/单候选）；`StickyRingCache`（成员指纹变化换环）。
- 集成测试（RoutingResolver 或网关层）：同 sticky key 两请求 → 第二请求候选 `[0]` = 首次 hash 目标（WEIGHTED 池同样成立）；key 为空 → 候选序 = 原策略序；首选被频控拒 → 顺延且不失败。
- 真启 jar 冒烟：node 回声上游 + 同 sticky key 连续请求验证候选置顶（trace/日志）；冒烟后清理。

## 10. 验证门禁

1. 后端：`mvn -f knot-server/pom.xml package`（含测试）。
2. 前端：`npm run type-check` + `npm run build`（build 后还原 `components.d.ts`）。
3. 真启 admin jar 冒烟（一次性库 + `KNOT_SQL_INIT_MODE=NEVER`）：建池开粘性 → 同键两发验证置顶 → 关粘性验证回落原策略 → 复原；DB 还原为 NULL。
4. 全量 grep 残留确认零残留；未经显式批准不 `git commit`。

## 11. 风险与限制

- **上游长时间故障时反复首撞**：无状态方案下，hash 首选恰好持续故障时，每轮请求都先撞它再 failover（请求不失败，但多一次失败往返）。这是「无绑定」的代价。**缓解 = follow-up 故障退避**（近期失败目标临时摘出环，需少量存储，届时按 §7 口径按需引入），首版不做。
- **热点倾斜**：一致性 hash 固有特性，频控兜底 + 文档明示。
- **池成员变更即漂移**：最小化（相邻段）但非零——这是特性：新成员加入本就该承接一部分 key。
- **头名运行时不可配**（§5 边界说明）：配置项是接入约定，控制器固定读默认头。
- **prompt cache 最终主权在上游**（region / deploy / 账户维度），网关粘性是必要非充分条件，不承诺命中率数字。
- 首版仅 header 单来源；body 来源（`user` / `metadata.session_id`）列为后续增量。

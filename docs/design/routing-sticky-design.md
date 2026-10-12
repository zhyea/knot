# 路由规则 · 粘性路由方案设计

> 状态：**设计中，待 robin 评审**（未落地）
> 关联代码（现状基线）：`knot-server/knot-gateway/.../routing/RoutingResolver.java`（候选生成）、`.../routing/ModelPoolSelection.java`（池选择策略）、`.../runtime/GatewayRequestHandler.java`（`proxyWithFailover` / `invokeWithRetry`）
> 设计日期：2026-10-12
> 参照样板：`docs/design/routing-retry-design.md`（规则级 JSON 策略列 + record 归一化 + 双模式存储）

## 1. 背景与现状

### 1.1 现状链路

```
控制器（Authorization + Rule + traceparent 头）
  → AbstractGatewayRequestTemplate.handleMap
    → RoutingResolver.resolveByRule(apiKey, ruleCode)
        candidates = [primary 目标, 其余按 priority 降序]
        池目标经 ModelPoolSelection.order(PRIORITY / RANDOM / WEIGHTED) 展开
    → GatewayRequestHandler.proxyWithFailover
        for candidate : candidates
            trafficGuard.checkTarget(candidate)   // 频控/额度，每候选一次
            invokeWithRetry(retryPolicy, candidate, proxy)  // 同目标原地重投
            trafficGuard.record(...) → return
```

### 1.2 缺口：会话多轮请求在上游之间漂移

- 模型池的 `RANDOM` / `WEIGHTED` 策略**每请求重掷**：同一会话（同一终端用户）的第 2、3…N 轮会落到池内不同模型/供应商账户。
- 后果一（主）：**上游 prompt cache 全部落空**。OpenAI / Anthropic / Google 的缓存按「账户 + 区域 + 模型部署」命中，漂移即丢缓存 → 输入成本与首 token 延迟双劣化。
- 后果二：排障路径不唯一，同一会话的行为随请求变化。
- 现状无任何会话亲和机制（全库 grep `粘性|sticky` 零命中）。

### 1.3 设计定位

粘性路由是**选择策略之上的一层候选重排**：同一会话的请求优先复用上次成功的上游目标。它不替换 `ModelPoolSelection`，只在「已有绑定」时把绑定目标提到候选首位。

## 2. 设计目标与原则

- **opt-in**：默认关闭。粘性是流量塑形（改变流量分布），与 retry 的「内 healing」性质不同，必须显式开启。
- **可用性优先**：粘性目标不可用（不在候选列表 / 上游失败 / 被频控顺延）时**绝不把请求打失败**，回落普通选择并失效绑定。
- **偏好而非独占**：只做 `preferred` 语义，不做 exclusive/strict（见 §11 风险）。
- **零侵入策略层**：`ModelPoolSelection` 与 failover 主循环结构不变，粘性只做「读绑定 → 重排 → 成功后写绑定 → 失败后删绑定」。
- **存 code 不存 id**：绑定值是 `targetCode`（`kb_models.model_code`），对齐跨模块绑定口径；模型被删/停用 → 候选列表自然不含它 → 绑定自动失效，无需主动清理。
- **规则级 JSON 列**：完全复用 `retry_policy` 的存储与解析范式（单 TEXT 列 + record 紧凑构造器归一化 + `parse` 容错）。
- **多节点降级可见**：无 Redis 时粘性退化为节点内 best-effort，启动日志明示，不静默。

## 3. 粘性模型

### 3.1 粘性键（Sticky Key）

| 维度 | 决策 |
|---|---|
| 来源（`keySource`，按优先级取第一个非空） | `header`（客户端传约定头）/ `body`（请求体点分路径取字符串叶子）/ `consumer`（兜底，按消费者聚合） |
| 头名称（`keyHeader`） | 默认 `X-Knot-Sticky-Key`，可配；全协议通用（含 multipart） |
| 体路径（`bodyKeyPath`） | 点分路径，默认 `user`（OpenAI 标准 end-user 字段）；备选 `metadata.session_id` / `metadata.conversation_id`（Responses API 常见约定） |
| consumer 兜底 | 键 = `consumer_code`；粒度最粗（同一消费者所有请求粘同一目标），仅适合单会话型接入 |
| 归一化 | trim；上限 128 字符，超长取 **SHA-256 前 16 hex**；仅接受字符串叶子，非字符串/空值忽略该来源 |
| 隐私 | 日志与存储键只出现 hash 后的键，不出现原文 |

> 键为空（客户端未传、路径不存在、值为空串）→ **整个粘性逻辑跳过**，等同未启用。不允许空串当键——那会把所有无键客户端粘成一个大热点。

### 3.2 粘性粒度与作用域

- 粘性对象 = **候选目标身份 `targetCode`**（model_code 唯一确定上游账户 + 模型，`provider_account_code` 已挂在候选 DTO 上，无需再降一层）。
- 存储作用域：`ruleCode + stickyKeyHash → targetCode`。**跨规则不共享**——各规则候选列表不同，共享只会互相污染。
- 首版只做 preferred，不提供 exclusive（§11）。

### 3.3 语义：preferred

```
有绑定且目标仍在候选列表  → 候选顺序 = [绑定目标, 其余按原策略顺序]
无绑定 / 目标已失效       → 原策略顺序不变；本次选中目标成功后写绑定
绑定目标被频控顺延        → 顺延到下一候选；成功即把绑定改写到实际命中目标
绑定目标上游失败          → 重试耗尽 → failover → 删除绑定（下轮重新选择）
```

### 3.4 存储（双实现，对齐频控计数 store）

| 项 | 决策 |
|---|---|
| 接口 | `StickyStore`：`get(key)` / `bind(key, targetCode, ttlSeconds)` / `touch(key, ttlSeconds)` / `invalidate(key)` |
| 键格式 | `knot:sticky:{ruleCode}:{keyHash}`，值 = targetCode |
| TTL | **滑动过期**：命中即刷新。默认 30 min，可配 1–1440。选滑动而非绝对：会话活跃即续期、静默消亡；绝对过期会在长会话中途意外断粘 |
| 装配 | `knot.sticky.store=caffeine`（默认，单节点 best-effort）/ `redis`（多节点共享），`@ConditionalOnProperty` 二选一，与 `TrafficCounterConfiguration` 同构 |
| Redis 故障 | fail-open：视为未命中 + WARN，绝不阻断请求 |
| 并发首请求 | 两个并发首请求可能各选各的、互相覆写——**last-write-wins，可接受**（下一轮即收敛）。不引 SETNX 选举，复杂度不配收益 |
| 原子性 | 无复合操作需求（绑定幂等），GET / SETEX / EXPIRE / DEL 单命令即可，不需要 Lua |

## 4. 执行流程

### 4.1 链路改动点（3 处）

1. **入口**：控制器新增可选头 `X-Knot-Sticky-Key`（`GatewayHeaders` 加常量），`handle / handleMultipart` 透传，`GatewayRequestContext` 加 final 字段持有。
2. **解析**：`ResolvedRouting` 增加 `StickyPolicy stickyPolicy()`（对齐 `retryPolicy()` 的 nullable + `effectiveStickyPolicy()` 兜底），由 `RoutingResolver` 构造时 `StickyPolicy.parse(rule.getStickyPolicy())` 塞入。
3. **代理**：`proxyWithFailover` 内在候选循环外包一层粘性协调：

```java
// proxyWithFailover 内，候选循环之前
StickyResolution sticky = stickyCoordinator.resolve(routing, routing.candidateModels(), context);
//   resolve：策略关闭/键为空 → 原样返回、hit=false
//   有绑定且命中候选 → 绑定目标提至首位、hit=true
List<RoutingRuleTargetDto> orderedCandidates = sticky.candidates();

for (RoutingRuleTargetDto candidate : orderedCandidates) {
    ... checkTarget ... invokeWithRetry ... record ...
    stickyCoordinator.bind(routing, context, candidate);   // 成功：写/刷绑定（滑动 TTL）
    return result;
    } catch (GatewayUpstreamException e) {
        lastUpstreamException = e;
        if (sticky.isStickyTarget(candidate)) {
            stickyCoordinator.invalidate(routing, context); // 粘住的目标也失败了 → 删绑定
        }
    }
}
```

### 4.2 边界规则

- **绑定只在成功后写**——失败不写，避免把坏目标钉住。
- **绑定删除只在「粘性目标自身重试耗尽」时做**；普通候选失败不碰绑定（该键本就没绑它）。
- **checkTarget 拒当前候选即顺延**，与现状一致；若被拒的是粘性目标，下一顺延候选成功后会自然改写绑定。
- **流式无特殊分支**：粘性发生在首字节前的目标选择阶段，与流式重试边界（`invokeWithRetry` 内的现有约束）正交。

## 5. 与现有机制的交互

| 机制 | 交互 |
|---|---|
| 重试（`invokeWithRetry`） | 正交：粘性只改候选顺序，重试仍在单候选内部，行为不变 |
| failover | 粘性目标重试耗尽后正常 failover，额外动作仅「删绑定」 |
| 频控/额度（`GatewayTrafficGuard`） | 无改动；粘性目标被限即顺延，可用性优先 |
| 池策略（`ModelPoolSelection`） | 无改动；有绑定时策略结果被「绑定目标置顶」改写，无绑定时策略原样生效 |
| `GatewayDataService` 10min 缓存 | 互不感知：那是策略数据缓存，粘性存储是独立键空间 |
| 计费 / knot_extend | 无改动，粘性不透出任何对外字段 |

## 6. 配置 Schema（规则级 `sticky_policy` JSON 列）

`kb_routing_rules` 新增列 `sticky_policy`（`TEXT DEFAULT NULL`，注释对齐 `retry_policy`）：

```json
{
  "enabled": true,
  "keySource": "header",
  "keyHeader": "X-Knot-Sticky-Key",
  "bodyKeyPath": "user",
  "ttlMinutes": 30
}
```

| 字段 | 含义 | 默认 | 约束 |
|---|---|---|---|
| `enabled` | 是否启用粘性 | `false` | **默认关，opt-in** |
| `keySource` | 键来源：`header` / `body` / `consumer` | `header` | 非法值归一为 `header` |
| `keyHeader` | 头名称（`keySource=header` 时生效） | `X-Knot-Sticky-Key` | 非空；trim |
| `bodyKeyPath` | 体取键点分路径（`keySource=body` 时生效） | `user` | 点分；只取字符串叶子 |
| `ttlMinutes` | 绑定滑动 TTL（分钟） | `30` | clamp 1–1440 |

约定：`sticky_policy` 为空/解析失败 → `StickyPolicy.DEFAULT`（关闭）；`keySource=consumer` 时忽略 `keyHeader` / `bodyKeyPath`。
**不设 simple/professional 双模式**——字段共 4 个且全部平权，双模式只会增加前端显隐与持久化归一化的复杂度，收益为零（与 retry 的 10 字段场景不同）。

## 7. 可观测性

- `GatewayRequestContext.attributes`（或 `GatewayTraceContext`）记录：`stickyKeyHash`、`stickyHit`（是否命中绑定）、`stickyTargetCode`（最终绑定/写入的目标）。
- WARN 日志：绑定失效回落、Redis 故障 fail-open（与频控 store 的 fail-open 日志同格式）。
- **不新增响应头**（对齐 retry 的收敛决策：`X-Knot-Retry-Attempts` 收益低于复杂度已暂缓；如后续排期可加 `X-Knot-Sticky-Hit`）。

## 8. 默认值

默认关闭。开启后：`keySource=header`、`keyHeader=X-Knot-Sticky-Key`、`bodyKeyPath=user`、`ttlMinutes=30`、caffeine 单节点存储（多节点部署显式切 `knot.sticky.store=redis`）。

## 9. 落地影响面（文件级）

**DB**
- `knot-admin/src/main/resources/db/schema.sql`：`kb_routing_rules` 加 `sticky_policy TEXT DEFAULT NULL COMMENT '粘性路由策略（JSON）；空表示未启用'`。
- `docs/database/migration/2026-10-12-routing-sticky.sql`：幂等 `ADD COLUMN IF NOT EXISTS`。**先跑迁移再 package。**

**后端（knot-server）**
- `knot-common/.../gateway/model/StickyPolicy.java`：record，紧凑构造器归一化（enabled/keySource/keyHeader/bodyKeyPath/ttlMinutes clamp）+ `parse` / `serialize`，完全对照 `RetryPolicy.java`。
- `knot-common/.../gateway/model/StickyKeyExtractor.java`：按 `keySource` 从头/体/消费者取键 + 归一化 + SHA-256 截 16 hex。
- `knot-gateway/.../sticky/StickyStore.java`（接口）、`CaffeineStickyStore.java`、`RedisStickyStore.java`、`StickyStoreConfiguration.java`（`@ConditionalOnProperty` 双实现 + `knot.sticky.*`  properties，对照 `traffic/TrafficCounterConfiguration.java`）。
- `knot-gateway/.../sticky/StickyCoordinator.java`：`resolve`（读绑定 + 重排）/ `bind` / `invalidate` / `isStickyTarget`，返回 `StickyResolution`（ordered candidates + hit + stickyTargetCode）。
- `knot-gateway/.../model/ResolvedRouting.java`：加 `stickyPolicy()` + `effectiveStickyPolicy()`。
- `knot-gateway/.../routing/RoutingResolver.java`：构造 `ResolvedRouting` 时塞 `StickyPolicy.parse(rule.getStickyPolicy())`。
- `knot-gateway/.../runtime/GatewayRequestHandler.java`：`proxyWithFailover` 按 §4.1 包粘性协调；`AbstractGatewayRequestTemplate` / `GatewayControllerSupport` / 各控制器透传 sticky 头；`GatewayRequestContext` 加字段；`constants/GatewayHeaders.java` 加 `X_KNOT_STICKY_KEY`。
- `RoutingRuleEntity` / `RoutingRuleDto` / `RoutingRule` VO / `RoutingRuleConverter`：加 `stickyPolicy` 字段（对照 `retryPolicy` 已有链路逐站补）。
- 既有 `ResolvedRouting` 构造点（含 `RoutingRuleStreamControllerTest` 等测试）同步补 `stickyPolicy` 参数。

**前端（knot-front）**
- `components/routing/RoutingRuleFormDrawer.vue`：新增「粘性路由」卡片（`components/common/StickyPolicySection.vue`，对照 `RetryPolicySection.vue`：开关 + 键来源 + 头名/体路径 + TTL）。
- form model / `useDraftModel.ts` 同步加 `stickyPolicy`（**深比较勿漏字段**——2026-10-06 `RetryPolicySection` 漏比 `mode` 的教训）。
- `utils/` 校验（TTL 范围、路径非空）+ 类型定义。

**测试**
- 单测：`StickyPolicy` 归一化（默认关、ttl clamp、keySource 归一）；`StickyKeyExtractor`（头/体路径/consumer/超长 hash/非字符串忽略/空键跳过）；`StickyCoordinator`（重排、绑定、失效、fail-open）。
- 网关集成测试（对齐现有风格）：同键第二请求候选首位 = 首次命中目标（WEIGHTED 池同样成立）；粘性目标连续 5xx 耗尽 → failover 且绑定删除（第三请求重新选择）；目标停用后绑定自动失效。
- 真启 jar 冒烟：node 回声上游 + 同 sticky key 连续请求，验证 写绑定 → 命中置顶 → 失效回落 全链路；冒烟后清理。

## 10. 验证门禁

1. 后端：`mvn -f knot-server/pom.xml package`（含测试）。
2. 前端：`npm run type-check` + `npm run build`（build 后还原 `components.d.ts`）。
3. 真启 admin jar 冒烟（一次性库 + `KNOT_SQL_INIT_MODE=NEVER`）：开粘性 → 同键两发验证置顶 → 停用目标验证回落 → 复原；DB 还原为 NULL。
4. 全量 grep 残留确认零残留；未经显式批准不 `git commit`。

## 11. 风险与限制

- **多节点无 Redis → 粘性退化节点内**：不同节点各自粘各自的，命中率打折。启动日志明示当前 store；文档要求多节点必须 `redis`。
- **负载倾斜**：少数热点键把流量集中到单一目标。频控层（RPM/TPM/额度）已是既有保护网；文档需向用户明示「粘性 = 用均衡换缓存」。
- **prompt cache 最终主权在上游**（region / deploy / 账户维度），网关粘性是必要非充分条件，不承诺命中率数字。
- **首版仅 preferred**：不做 exclusive/strict。理由：AI 网关场景下「漂移」的代价是成本与延迟，「失败」的代价是业务事故，可用性优先；如确需强语义，后续以独立开关排期，不混入本期。
- **滑动 TTL 无绝对上界**：活跃会话永续存在，键空间随会话数线性增长——由 Redis/Caffeine 的淘汰与 TTL 共同兜底。
- 粘性键由客户端提供，**存在伪造/撞键可能**：归一化（长度截断 + hash）已防键空间爆炸；敏感场景建议 `keySource=consumer` 或由接入层注入不可伪造的头。

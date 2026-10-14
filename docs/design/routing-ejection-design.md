# 路由规则 · 供应商模型异常摘除方案设计

> 状态：**已定稿**（2026-10-13 设计 → 2026-10-14 拍板收口）
> 实现进度：**B1 + B2 已落地**（2026-10-15，530 例后端测试零失败 + 前端 type-check/vite build 全绿）；B3 慢信号、B4 管理面、B5 冒烟未开工
> 关联代码：`knot-gateway/.../runtime/GatewayRequestHandler.java`（`proxyWithFailover` / `attemptCandidates`）、`.../retry/*`、`.../load/*`、`.../outage/*`
> 关联设计：`docs/design/routing-retry-design.md`（retry → failover 两层降级已落地）
>
> **一次拍板**（robin，2026-10-13）：作用域=**规则×模型**；触发=**窗口内失败计数 + 首 token 延迟过长**；恢复=**冷却 + 半开探测**；存储=**运行时存储 + 管理面可查可控**。
>
> **二次拍板**（robin，2026-10-14，§16 四条全部收口）
> 1. `enabled` **默认关闭**（摘除主动削容量，误判代价高于漏判）。
> 2. 阈值组**保持默认**：`windowSec=60 / threshold=5 / slowFirstTokenMs=15000 / cooldownMs=60000`。
> 3. 管理面**手动摘除 / 手动恢复写操作日志**（`ks_operation_logs`）；自动摘除不写。
> 4. 列表**不做常显列**，抽屉内「当前摘除目标」面板够用。

## 1. 背景与现状

网关当前的降级链条只有两层（见 retry 设计 §3）：

| 层 | 行为 | 现状 |
|---|---|---|
| **Retry** | 同一目标失败后原地重投 | ✅ 已落地（规则级 `retry_policy`） |
| **Failover** | 重试耗尽 → 切下一候选 | ✅ 已落地 |
| **Eject（摘除）** | 已确认异常的目标**在后续请求中不再进入候选链** | ❌ 本次新增 |

**缺口**：候选链对「已知故障目标」无记忆。上游某供应商模型持续 5xx / 严重超时期间，每个请求仍然从坏目标开始走一遍「重试 N 次 → 失败 → failover」，付出的是**可观测的额外延迟**（默认 maxAttempts=2 × 退避 200ms，加上超时场景可达数十秒）与上游无效压力；更糟的是慢节点——它不报错、只是慢，failover 永远不会被触发，请求会持续被拖到上游读超时。

本次设计补第三层：**异常摘除（ejection）**——把「上一个时间窗里反复异常的目标」从候选链里临时剔除，冷却结束后用单个探活请求半开恢复。

## 2. 目标与原则

- **只做减法的事前过滤**：摘除不替代 retry/failover，只是让后续请求跳过已被证明异常的目标。
- **异常定义必须窄**：只有当「这个模型本身有问题」时才计数——上游 5xx、上游 429、网络超时/连接错误；**4xx 业务错误（参数错、鉴权错）与配置类错误不计数**，网关自身频控/额度拒绝也不计数。
- **误判代价高于漏判**：默认**关闭**（见 §16 待拍板项 1），阈值保守；摘除减少可用容量，宁可慢一点也不能把健康模型摘掉。
- **绝不因摘除把请求打挂**：候选全被摘除时 fail-open 回原链（§7.4）。
- **不落库**：运行时状态存缓存（caffeine / redis，复用 `ModelLoadStore` 的双实现 + fail-open 范式），规则级配置沿用单 JSON 列 `ejection_policy`（类比 `retry_policy`）。
- **无历史包袱**：不加兼容分支、不冗余列，配置为空即走内置默认。

## 3. 作用域与键设计

按 robin 拍板：**规则 × 模型**。

- 键 = `knot:outage:RULE:<ruleCode>:MODEL:<modelCode>`，其中 `modelCode` 是**展开后**的供应商模型码（`kb_models.model_code`）——模型池目标在候选展开阶段已摊平为具体模型（见 `RoutingResolver.resolvePoolCandidates`），故池内单模型异常不会误伤同池其他模型。
- 同模型在不同规则下**独立累计、独立恢复**（符合「各规则 SLA 不同」的运维现实）；跨规则共享是本次明确不做的方向。

## 4. 异常信号定义

两类信号共用一个窗口计数器，任一达阈值即摘除：

| 信号 | 触发条件 | 计数口径 |
|---|---|---|
| `FAILURE` | 候选调用最终以「上游故障类」失败 | 5xx 全部；上游 429；无 HTTP 状态的网络/超时类（`UPSTREAM_ERROR`）；**不计**：4xx（429 除外）、`MODEL_NOT_FOUND` / `PROVIDER_NOT_FOUND` / `API_PROTOCOL_NOT_CONFIGURED` 等配置类 |
| `SLOW` | 请求**成功**但首 token 延迟 > `slowFirstTokenMs` | 复用已落地的 TTFT（`X-Knot-First-Token-Time`，端到端口径，含鉴权/插件/重试/failover 耗时） |

要点：

- **一次候选调用最多计 1 次**：重试内部的单次失败不单独计数（`maxAttempts=2` 不会让计数翻倍），在 `catch (GatewayUpstreamException)` 处按候选计一次。
- **SLOW 只在成功路径上产生**：首 token 出现即代表上游已成功，此时「慢」才是模型的锅；失败路径已由 FAILURE 覆盖。
- **TTFT 是端到端口径**（含重试与插件耗时）。若重试把端到端拖长，可能被判 SLOW——这本身也是异常信号，可接受；阈值默认 15s 足以吸收网关自身开销（§14 风险 3）。

## 5. 状态机

```
                 窗口内异常数 >= threshold
   HEALTHY ─────────────────────────────────► EJECTED (ejectedUntil = now + cooldown)
      ▲                                          │
      │ 成功清零窗口计数                          │ now >= ejectedUntil
      │                                          ▼
      │                                       PROBING（惰性：放行 1 个探活请求，
      │                                               抢租约 probeUntil = now + probeTimeoutMs）
      │                                          │
      │◄──────── 探活成功（清除整条状态）─────────┤
      │                                          │
      │                                          └── 探活失败 → cooldown = min(cooldown × multiplier, maxCooldownMs)
      │                                              重设 ejectedUntil，回到 EJECTED
      └──────── 窗口过期 / 手动恢复 / 强制摘除解除
```

- **PROBING 不持久化**：状态里只留 `probeUntil` 租约；「是否处于探测」由读入时的租约抢占决定，无需单独状态字段，避免额外状态转换复杂度。
- 租约过期未归还（进程崩/请求超时）→ 下次读入时租约已过期，重新抢占，不会永久卡死。

## 6. 存储设计（`TargetOutageStore`）

复用 `ModelLoadStore` 的成熟范式：接口 + caffeine/redis 双实现 + `fail-open` + 配置切换。

```java
public interface TargetOutageStore {
    String id();                                   // "caffeine" / "redis"
    OutageState state(String key, long now);       // 读；不存在/读取失败 → HEALTHY（fail-open）
    OutageState recordAnomaly(String key, EjectionPolicy policy, AnomalyKind kind, long now);
    void recordSuccess(String key, boolean probing, long now);
    void release(String key);                      // 手动恢复
    void forceEject(String key, long cooldownMs, String reason, long now);
    List<OutageEntry> list();                      // 管理面列表
}
```

**键与值**

- 键：`knot:outage:RULE:<ruleCode>:MODEL:<modelCode>`（前缀 `knot.outage.key-prefix` 可配）
- 值（JSON）：`{"failures":3,"slows":1,"windowEnd":1760,"ejectedUntil":0,"probeUntil":0,"cooldownMs":60000,"reason":"FAILURE","updatedAt":1760}`
- TTL：`max(windowEnd, ejectedUntil, probeUntil) + 60s`。Redis 用 `PEXPIREAT`；Caffeine 用逐键 `Expiry`（照抄 `CaffeineTrafficCounterStore.CounterExpiry`）。条目自然回收，无清理任务。

**原子性**

- Caffeine：`cache.asMap().compute(...)`（per-key 排他，同 `CaffeineTrafficCounterStore`）。
- Redis：单段 Lua 完成「读 JSON → 判窗口/冷却/租约 → 改 → SET + PEXPIREAT」，多节点下读-改-写必须原子；**禁止拆成多条命令**（进程中途崩会留下永不过期键——该教训已写在 `RedisModelLoadStore` 注释里）。

**fail-open**：存储不可用时一律按「HEALTHY」处理（不摘除），只 WARN。缓存故障绝不能演变成网关选路故障。

**管理面列表**：Caffeine 直接遍历；Redis 用 `SCAN <prefix>:RULE:*:MODEL:*`（管理面低频，仅 caffeine 模式下才是单实例视图）。

## 7. 执行流程

### 7.1 候选循环内插入摘除过滤

```java
for (RoutingRuleTargetDto candidate : routing.candidateModels()) {
    String outageKey = outageKeys.of(routing.ruleCode(), candidate.targetCode());
    OutageState st = outageStore.state(outageKey, now);          // fail-open 读
    boolean probing = false;
    if (st.ejected(now)) {
        probing = outageStore.tryAcquireProbe(outageKey, policy.probeTimeoutMs(), now);
        if (!probing) {                                          // 冷却中 / 已有他人在探测
            skippedByEjection++;
            continue;
        }
    }
    TrafficDecision targetDecision = trafficGuard.checkTarget(candidate, trafficContext);
    ...
    try {
        ProxyResult result = invokeWithRetry(retryPolicy, candidate, () -> ...);
        outageStore.recordSuccess(outageKey, probing, now);      // 探测成功即恢复；健康态则清零窗口
        context.putAttribute(ATTR_HIT_TARGET, candidate);        // 供 TTFT 回调归属慢信号
        return result;
    } catch (GatewayUpstreamException e) {
        if (OutageClassifier.countable(e, policy)) {
            OutageState after = outageStore.recordAnomaly(outageKey, policy, FAILURE, now);
            if (after.ejected(now)) log.warn("Routing target ejected: ...");   // 仅首次摘除打日志
        }
        lastUpstreamException = e;
    }
}
```

顺序要点：

1. **摘除判定在 `checkTarget` 之前**——被摘除的目标不消耗该目标的频控/额度预算。
2. 被摘除而跳过 ≠ 被频控拒绝，**不写入 `firstRejection`**，否则会把 429 错误地返回给客户端。

### 7.2 慢信号注入（TTFT 回调）

`FirstTokenHeaderWriter.mark()` 已有 `elapsed`；新增可选观察者，把首次 mark 的耗时回传给 tracker：

- `FirstTokenHeaderWriter.of(context, observer)`（保留 `of(context)` 兼容）；`mark()` 内先 `observer.accept(elapsed)` 再写头，仍保持幂等只触发一次。
- 流式：`GatewayRequestHandler.streamResponse()` 构造 writer 时绑定观察者。
- 非流式：`AbstractGatewayRequestTemplate:86` 的 `FirstTokenHeaderWriter.of(context).mark()` 同样换成带观察者版本。
- 观察者从 `context.attribute(ATTR_HIT_TARGET)` 取本次命中候选（§7.1 写入），取不到（异常路径）直接跳过——异常路径无首 token，本就不产生慢信号。

### 7.3 全摘除 fail-open

遍历结束若无任何可用候选，且确实存在「因摘除被跳过」的目标，且 `failOpenWhenAllEjected=true`（默认）→ **忽略摘除再走一遍原链**，并 WARN `all candidates ejected, fail-open`。容量被摘光时宁可慢，不可 503。

### 7.4 与重试/频控/计费交互

| 维度 | 口径 |
|---|---|
| Retry | 正交。摘除按「候选调用」计数，重试内部的单次失败不额外计数 |
| RPM/TPM 频控 | 被摘除目标不做 `checkTarget`，不消耗预算；`record` 仍只在成功时写 |
| 计费 | 不变。摘除只影响候选选择，不影响用量与成本计算 |
| 粘性路由（sticky） | 被摘除目标即使被一致性 hash 命中也照样跳过，由 failover 链顺延（粘性本就是「只改首选」的软约束） |
| 最空闲选模（LEAST_LOAD） | 摘除发生在候选展开之后、在途计量之前，被摘除目标不进入 `enter/leave`，其负载自然回落为 0（恢复后重新参与） |

## 8. 配置 Schema（规则级 `ejection_policy` JSON 列）

```json
{
  "enabled": false,
  "mode": "simple",
  "windowSec": 60,
  "threshold": 5,
  "countFailures": true,
  "countSlow": true,
  "slowFirstTokenMs": 15000,
  "cooldownMs": 60000,
  "cooldownMultiplier": 2.0,
  "maxCooldownMs": 600000,
  "probeTimeoutMs": 30000,
  "failOpenWhenAllEjected": true
}
```

| 字段 | 含义 | 默认 | 模式 |
|---|---|---|---|
| `enabled` | 是否启用摘除 | `false`（**已拍板默认关闭**） | 全 |
| `mode` | `simple` / `professional` | `simple` | 全 |
| `windowSec` | 异常计数窗口（秒） | `60` | 全 |
| `threshold` | 窗口内异常次数阈值（failures + slows） | `5` | 全 |
| `countSlow` | 是否统计首 token 过慢 | `true` | 全 |
| `slowFirstTokenMs` | 首 token 超时阈值（毫秒） | `15000` | 全 |
| `cooldownMs` | 首次摘除冷却时长 | `60000` | 全 |
| `countFailures` | 是否统计失败类异常 | `true` | 仅 professional |
| `cooldownMultiplier` | 探测失败后的冷却增长倍数 | `2.0` | 仅 professional |
| `maxCooldownMs` | 冷却上限 | `600000` | 仅 professional |
| `probeTimeoutMs` | 半开探活租约时长 | `30000` | 仅 professional |
| `failOpenWhenAllEjected` | 候选全摘时是否放行原链 | `true` | 仅 professional |

归一化沿用 `RetryPolicy` 的成熟写法：**紧凑构造器内完成**缺省填充/越界收敛/模式归一，Jackson 反序列化出来的实例同样是有效值；`EjectionPolicy.parse(json)` 空值或非法 JSON 退回 `DEFAULT`（`enabled=false`）。`mode=simple` 时 professional 专属字段在持久化与解析时退回内置默认（后端与前端两侧同时收敛，与 retry 设计 §9 同构）。

## 9. 配置落点：DB

- `kb_routing_rules` 新增列 `ejection_policy TEXT DEFAULT NULL COMMENT '异常摘除策略（JSON）；空表示走内置默认策略'`。
- 迁移脚本 `docs/database/migration/2026-10-14-routing-ejection.sql`（落地当天日期命名）：`information_schema` 判列 → `ADD COLUMN` → 幂等可重跑。
- ⚠ 既有库必须先跑迁移再启应用：`CREATE TABLE IF NOT EXISTS` 不给既有表补列。
- ⚠ 新增 admin 接口**必须连带权限绑定 INSERT**（默认拒绝，漏配即 403）。

## 10. 管理面：可查可控

**网关侧**（新包 `org.chobit.knot.gateway.outage.admin`，路径前缀 `/_admin`，避开 OpenAI 兼容路径）：

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/_admin/outages?ruleCode=&modelCode=` | 当前非健康条目列表（含 状态/原因/计数/窗口/ejectedUntil） |
| POST | `/_admin/outages/release` | 手动恢复 `{ruleCode, modelCode}` |
| POST | `/_admin/outages/eject` | 手动摘除 `{ruleCode, modelCode, cooldownSec, reason}` |

安全：网关无全局鉴权拦截器（鉴权在 handler 内按 key/rule 做），故端点自带保护——共享令牌头 `X-Knot-Admin-Token`（`KNOT_GATEWAY_ADMIN_TOKEN`，未配置时仅允许 loopback 访问）；`knot.outage.admin-api.enabled=false` 可整体关闭。

> ⚠ 2026-10-14 复核：本段为设计阶段描述，代码中**尚未实现**——`KNOT_GATEWAY_ADMIN_TOKEN` 与
> `knot.outage.admin-api.enabled` 在 `knot-server` 下零引用（无绑定类、无 `@Value`）。落地本节时需同步补
> `knot.outage.admin-api` 的 `@ConfigurationProperties` 绑定与网关侧配置，勿直接照抄部署。

**admin 侧**：复用已有 `knot.gateway.base-url` + `RestClient` 转发先例（`RoutingRuleService.executeGatewayTest` 同款），新增：

- `GET /api/gateway/outages`（可按 ruleCode 过滤）
- `POST /api/gateway/outages/release`、`POST /api/gateway/outages/eject`
- 三个接口同步补 `ks_` 权限绑定（随迁移脚本 INSERT）。
- 多网关实例：状态在 redis 模式下全局共享，查任一实例即为全局视图；caffeine 模式只代表该实例。默认沿用单 `base-url`，多实例场景配负载均衡地址（限制记入 §14）。

**前端**

- 路由规则编辑抽屉：在「失败重试」之后加 `<EjectionPolicySection>`（开关 + 模式切换 + 窗口/阈值/慢阈值/冷却；professional 展开其余项），视觉与 8 点网格对齐 `RetryPolicySection`。
- 新增 `utils/ejectionPolicy.ts`（Mirror 后端归一化：`defaultEjectionPolicy()` / `normalizeEjectionPolicy()` / 常量），类型与 `retryPolicy.ts` 同构。
- 运行时面板（**已拍板：够用，列表不做常显列**）：规则抽屉内新增「当前摘除目标」小表（模型 / 状态 / 原因 / 窗口计数 / 剩余冷却），提供「恢复」按钮；「手动摘除」入口同放此处（选模型 + 冷却时长 + 原因）。

### 10.1 操作日志（已拍板：手动动作写，自动摘除不写）

走现成 `@OperationLog` 注解 + `OperationLogAspect`（`knot-common/.../annotation/OperationLog.java`），不手写落库：

- `module = "routing"`、`entityType = "RoutingTarget"`、`operation = "UPDATE"`（改变运行时状态）。
- 目标是 code 维度、无主键 id → `entityId` 留空，`entityName` 用 SpEL 拼 `ruleCode/modelCode`，`newValueSpel = "#p0"` 直接序列化请求体（含 reason / cooldownSec）。
- 无「旧值」概念（运行时状态不在 DB）→ `recordOldValue = false`。
- 失败由切面按 `OperationLogStatusEnum` 记 2-失败，控制器不额外处理。

```java
@OperationLog(module = "routing", operation = "UPDATE", entityType = "RoutingTarget",
        entityName = "#p0.ruleCode() + '/' + #p0.modelCode()",
        description = "'手动摘除路由目标'",
        recordOldValue = false,
        newValueSpel = "#p0")
@PostMapping("/outages/eject")
public void eject(@RequestBody EjectRequest request) { ... }

@OperationLog(module = "routing", operation = "UPDATE", entityType = "RoutingTarget",
        entityName = "#p0.ruleCode() + '/' + #p0.modelCode()",
        description = "'手动恢复路由目标'",
        recordOldValue = false,
        newValueSpel = "#p0")
@PostMapping("/outages/release")
public void release(@RequestBody ReleaseRequest request) { ... }
```

自动摘除 / 自动恢复**不写操作日志**（高频、非人因，避免把日志表打爆），只走 WARN 日志（§11）。

## 11. 可观测性

- WARN 日志三处：首次摘除（含 rule/model/窗口计数/末次 status-code）、探测失败重新冷却、全摘除 fail-open。
- `GatewayTraceContext` 记录本次跳过的摘除目标数与命中目标（不进响应体，避免污染 `knot_extend`）。
- 不新增响应头（retry 设计的 `X-Knot-Retry-Attempts` 亦未落地，保持口径一致）。

## 12. 测试计划

| 层 | 用例 |
|---|---|
| `EjectionPolicyTest`（common） | 默认/归一化/越界收敛/模式归一/`parse` 容错/空 JSON |
| `OutageClassifierTest`（gateway） | 5xx 计、429 计、超时计、400/401 不 计、配置类错误不计、网关自身 429 不计 |
| `CaffeineTargetOutageStoreTest` | 窗口内累计达阈值摘除；窗口过期重开；成功清零；冷却内跳过；冷却后放行 1 个探测；探测成功清除；探测失败冷却翻倍并封顶；手动 release/forceEject；TTL 回收 |
| `GatewayRequestHandler` 集成测试 | 连续 5xx → 第 N+1 次请求跳过坏候选直取下一候选；慢首 token → 摘除；全摘 fail-open 仍返回 200 |
| `FirstTokenHeaderWriterTest` | 观察者只触发一次（幂等）、null response 仍触发、无命中目标不触发 |
| Redis 实现 | 本机无 testcontainers 基建 → **不写自动化单测**，靠真启 jar 冒烟验证（见 §13），并在报告中显式标注「未自动化验证」 |

## 13. 验证门禁

1. 后端：`mvn -f knot-server/pom.xml test`（全 reactor）。
2. 前端：`vue-tsc --noEmit` + `npx vite build`（build 后还原 `components.d.ts`）。
3. 真启 jar 冒烟：
   - 建一次性库 `knot_smoke_options`，admin（18092）+ gateway（19091，每场景重启，ModelEntity 缓存 10min），`KNOT_SQL_INIT_MODE=NEVER`。
   - 假上游（`.workbuddy/audit/ttft_fake_upstream.py` 模板，已含 chunked 解析 + 首 chunk 延迟参数）：
     - 场景 A：持续 500 → 连续请求验证「阈值内仍打、达阈值后跳过、日志出现 ejected」；
     - 场景 B：首 chunk 延迟 > slowFirstTokenMs → 验证慢信号摘除；
     - 场景 C：管理面 GET 列表可见、`release` 后流量回归；
     - 场景 D：两候选全摘 → fail-open 仍 200。
   - 冒烟后 DROP 库、停进程、零残留。
4. 全量 grep 残留（`RetryPolicy` 同款清单改 `EjectionPolicy`）；未经显式批准不 `git commit`。

## 14. 风险与限制

1. **caffeine 模式是单实例视图**：多节点各摘各的，同一模型可能被部分节点继续打；要全局一致必须 `knot.outage.store=redis`。
2. **窗口是「首次异常起算的固定窗口」**（键 TTL = windowSec），不是严格滑动窗口；窗口边界处可能延迟一个周期才清零。取舍：与 `TrafficCounterStore` 同构、实现成本最低。
3. **TTFT 是端到端口径**，含重试/插件耗时，理论上会误判「网关自身慢」。默认阈值 15s 吸收；后续若要纯上游口径，需在 `UpstreamProxyClient` 另加计时（本设计不做）。
4. **探测请求由真实用户流量承担**：半开放行的是真实请求，若上游仍坏，该请求失败（但会被 failover 兜住，不会 503）。主动探活（后台定时探针）本次不做。
5. **Redis 实现无自动化单测**（无 testcontainers 基建），只能冒烟覆盖；落地时须显式标注验证级别。
6. **管理面列表在 caffeine 模式下只反映被轮询到的那一个实例**。

## 15. 落地影响面（文件级）

**DB**
- `knot-admin/src/main/resources/db/schema.sql`：`kb_routing_rules` 加 `ejection_policy`。
- `docs/database/migration/2026-10-14-routing-ejection.sql`：幂等加列 + 3 个 admin 接口的权限绑定 INSERT。

**后端**
- `knot-common/.../model/EjectionPolicy.java`（新增）：record + 紧凑构造器归一化 + `parse/serialize` + `DEFAULT`。
- `knot-gateway` 新包 `outage`：`OutageKeys`、`OutageProperties`、`TargetOutageStore`、`CaffeineTargetOutageStore`、`RedisTargetOutageStore`、`OutageStoreConfiguration`、`OutageState`、`OutageEntry`、`OutageClassifier`、`OutageTracker`、`admin/OutageAdminController`。
- `knot-gateway/.../model/ResolvedRouting.java`：增 `EjectionPolicy ejectionPolicy` + `effectiveEjectionPolicy()`（构造点含测试需同步补参）。
- `knot-gateway/.../routing/RoutingResolver.java:94` 附近：塞入 `EjectionPolicy.parse(rule.getEjectionPolicy())`。
- `knot-gateway/.../runtime/GatewayRequestHandler.java`：`proxyWithFailover` 加摘除过滤/异常计数/成功记录/全摘 fail-open；`streamResponse()` 绑定 TTFT 观察者。
- `knot-gateway/.../runtime/FirstTokenHeaderWriter.java`：加观察者参数（保持 `of(context)` 兼容）。
- `knot-gateway/.../runtime/AbstractGatewayRequestTemplate.java:86`：非流式 mark 带观察者。
- `knot-gateway/.../runtime/GatewayRequestContext.java`：新增命中目标属性常量（与 `ATTR_LOAD_RELEASE` 同级）。
- `knot-dal/.../entity/RoutingRuleEntity.java`、`knot-service/.../dto/routing/RoutingRuleDto.java`、`vo/routing/RoutingRule.java`、`converter/RoutingRuleConverter.java`：字段贯通 + mapper resultMap/insert 同步。
- `knot-service`：`GatewayOutageService`（RestClient 转发）+ `knot-admin/.../controller/GatewayOutageController`（`/api/gateway/outages*`）。

**前端**
- `src/components/common/EjectionPolicySection.vue`（新增）、`src/utils/ejectionPolicy.ts`（新增）、`src/api/gatewayOutage.ts`（新增）。
- `src/components/routing/RoutingRuleFormDrawer.vue`：接入 Section + form 字段 + `normalizeEjectionPolicy`。
- 路由规则列表/面板：摘除状态展示与恢复入口。

## 16. 拍板结论（robin，2026-10-14）

| # | 议题 | 结论 | 文档落点 |
|---|---|---|---|
| 1 | `enabled` 默认值 | **默认关闭** | §8 `enabled=false`、§2 原则 |
| 2 | 阈值默认组 | **保持默认**：`windowSec=60 / threshold=5 / slowFirstTokenMs=15000 / cooldownMs=60000` | §8 默认值表 |
| 3 | 手动摘除/恢复是否写操作日志 | **写**（`ks_operation_logs`，`@OperationLog` 注解）；自动摘除不写 | §10.1 |
| 4 | 列表是否常显「摘除中」列 | **不做常显列**，规则抽屉内「当前摘除目标」面板够用 | §10 前端 |

至此设计无未决项，可进入实施。

## 17. 建议实施批次（待 robin 一句话排期）

| 批次 | 内容 | 门禁 |
|---|---|---|
| **B1 配置骨架** | `EjectionPolicy`（common）+ DB 加列 + 迁移脚本 + entity/dto/vo/converter/mapper 贯通 + 前端 `ejectionPolicy.ts` + `EjectionPolicySection.vue` + 抽屉接入 | `mvn test` / `vue-tsc` / `vite build` |
| **B2 运行时核心** | `outage` 包（Keys/Properties/Store 双实现/State/Classifier/Tracker）+ handler 接入摘除过滤与异常计数 + `FallbackTargetOutageStore` 降级适配器（Redis 优先 → Caffeine 降级 → 回灌恢复） | `mvn test`（含 store、classifier、适配器单测） |
| **B3 慢信号** | `FirstTokenHeaderWriter` 观察者 + 流式/非流式两处注入 + 命中目标属性 | 单测 + gateway 模块全量 |
| **B4 管理面** | 网关 `/_admin/outages*` + admin 转发 + 权限绑定 + 操作日志 + 前端面板（列表/恢复/手动摘除） | `mvn test` + 前端门禁 |
| **B5 冒烟** | 假上游 5xx / 慢首 token / release / 全摘 fail-open 四场景真启 jar 验证，收尾 DROP 库 | 冒烟脚本退出码 0 |

### 17.1 B1 + B2 实现记录（2026-10-15）

**B1 配置骨架**

| 文件 | 说明 |
|---|---|
| `knot-common/.../model/EjectionPolicy.java` | 12 字段 record + 紧凑构造器归一化 + `parse/serialize` + `DEFAULT`（默认关闭） |
| `knot-common/.../model/EjectionPolicyTest.java` | 11 例：默认/容错/越界/模式/冷却增长/序列化往返 |
| `knot-admin/src/main/resources/db/schema.sql` | `kb_routing_rules` 加 `ejection_policy TEXT`（位于 `retry_policy` 之后） |
| `docs/database/migration/2026-10-15-routing-rule-ejection-policy.sql` | 幂等加列（information_schema 判定 + PREPARE），与 retry 迁移同范式 |
| `RoutingRuleEntity` / `RoutingRuleDto` / `RoutingRule`(VO) | 均加 `ejectionPolicy` 字段 |
| `RoutingRuleMapper.xml` | RuleSelectColumns / insert / update 三处同步 |
| `RoutingRuleService` | 三处手工构造点 + `serialize/parse` 落库与回读 |
| `knot-front/src/utils/ejectionPolicy.ts` | Mirror 后端归一化（`defaultEjectionPolicy` / `normalizeEjectionPolicy`） |
| `knot-front/src/components/common/EjectionPolicySection.vue` | 对齐 `RetryPolicySection` 的卡片：开关 + 简单/深度模式 + 4 主参数 + 深度 7 参数 |
| `RoutingRuleFormDrawer.vue` | 接入 Section、回填、submit 恒送归一化结果（与 retry 同口径：关闭也要显式落库） |

**B2 运行时核心**

| 文件 | 说明 |
|---|---|
| `outage/OutageState.java` | 不可变状态：failures/slows/windowEndAt/ejectedUntil/probeUntil/cooldownMs/reason |
| `outage/AnomalyKind.java` | FAILURE / SLOW |
| `outage/OutageStateMachine.java` | 纯函数状态机（5 个转换），两种存储共享 |
| `outage/OutageKeys.java` | `knot:outage:RULE:<ruleCode>:MODEL:<modelCode>` |
| `outage/OutageProperties.java` | `knot.outage.*`：store / keyPrefix / expireGraceMs / maximumSize / failOpen |
| `outage/TargetOutageStore.java` + `CaffeineTargetOutageStore` + `RedisTargetOutageStore` + `OutageStoreConfiguration` | 双实现 + 装配；Redis 走 Hash（非 JSON，规避 cjson 大数科学计数法）+ 五段单段 Lua |
| `outage/OutageClassifier.java` | 计数口径：5xx / 上游 429 / 无状态 UPSTREAM_ERROR 计；4xx（除 429）与配置类不计 |
| `outage/OutageTracker.java` + `EjectionDecision.java` | 请求链门面：evaluate → recordFailure / recordSuccess |
| `ResolvedRouting` / `RoutingResolver` | record 加 `ejectionPolicy` + `effectiveEjectionPolicy()`，解析处 `EjectionPolicy.parse` |
| `GatewayRequestHandler` | `proxyWithFailover` 拆为两轮 `attemptCandidates`：摘除过滤在 `checkTarget` **之前**；全摘除时 fail-open 重跑一轮；`FailoverOutcome` 承载跨轮统计 |

**B2 单测**：`OutageStateMachineTest` 10 + `CaffeineTargetOutageStoreTest` 7 + `OutageClassifierTest` 5，共 22 例。

**实现偏差（相对设计稿）**

1. **Redis 状态用 Hash 而非 JSON**：设计稿的值是 JSON，实现改为 Hash（字段 `f/s/w/e/p/c/r`）——Lua 5.1 数字经 cjson 编码会把毫秒时间戳写成科学计数法，Java 端解析会炸；Hash 字段是纯字符串，`Long.parseLong` 精确还原。
2. **Lua 拆成五段独立脚本**（ANOMALY / SUCCESS / PROBE / FORCE_EJECT / DEL），每段仍是「读-改-写-过期」原子；单段大脚本难维护且 Redis 重编译开销大。
3. **`release()` 直接走 `redis.delete`**，没有专用 Lua 脚本。
4. **测试基准时刻取 `System.currentTimeMillis()`**：固定假时间戳（1970）会让 Caffeine 逐键过期立刻回收条目（`CaffeineTrafficCounterStore` 同款陷阱，已实测踩中）。

**未覆盖**：Redis 实现无自动化单测（本机无 testcontainers 基建），仅靠 B5 冒烟验证；慢信号（B3）与管理面（B4）未接。

### 17.2 存储降级适配器实现记录（2026-10-15）

**需求**：单节点不装 Redis，用 Caffeine 顶替；多节点仍要 Redis 的全局视图。二者不是「二选一装配」——Redis 一抖动，纯 Redis 档只剩 fail-open（摘除能力消失）或抛错（缓存故障升级为选路故障）两种坏结果，故增加第三条路：**Redis 优先，不可用自动降级 Caffeine，恢复后回灌切回**。

| 文件 | 说明 |
|---|---|
| `outage/FallbackTargetOutageStore.java` | 适配器：读路径 primary 优先，抛异常即降级；恢复探测节流（默认 10s）；恢复时回灌「本地仍摘除中」条目；`release/forceEject` 双写两侧 |
| `outage/OutageProperties.java` | 新增 `knot.outage.recover-probe-interval-ms`（默认 10_000） |
| `outage/OutageStoreConfiguration.java` | 新增 `fallback` 档 bean：Redis + Caffeine + ping 探针；`store=caffeine`（默认）与 `store=redis` 档不变 |
| `FallbackTargetOutageStoreTest` | 9 例契约测试：读写路由 / 降级 / 探针节流 / 回灌 / 过期条目不回灌 / 运维双写 / list 跟随 / 探针通但操作失败不假恢复 |

**三种部署形态**：多节点 + Redis 健康 → 全局共享视图；多节点 + Redis 抖动 → 降为各节点本地近似，功能不中断；单节点 → `store=caffeine` 直接用本地存储。

**实现偏差与修复记录（相对初版适配器）**

1. **恢复成功后当次读写直接走 primary**：初版 `recoverIfDue()` 切回后仍穿透到 `fallback.state(...)`，恢复瞬间那一跳会读到已 release 的空状态——刚回灌的摘除被漏掉，坏目标多吃一个请求。改为 recover 后不再穿透，当次调用重走 primary。
2. **探针通 ≠ 存储可用**：恢复探测只证明 ping 可达（如只读副本），当次 primary 操作仍可能抛异常——此时重新降级，绝不假恢复（`probeOkButOpsStillFailStaysDegraded` 锁定）。
3. **测试替身读语义对齐真实存储**：`FakeStore.state()` 初版用 `computeIfAbsent`（读即落库），与 `CaffeineTargetOutageStore` 的 `getIfPresent` 不符，会把「不回灌」断言反过；已改 `getOrDefault`。
4. **固有限制（写进类注释）**：降级期间的窗口计数/探活租约不回灌（只搬仍摘除中的条目），恢复前的本地过程量丢失；要严格一致须 `store=redis` + Redis 高可用。

**未覆盖**：`RedisTargetOutageStore` 本体仍无自动化单测（无 testcontainers），降级适配器只验证编排逻辑，不验证 Redis 侧行为——后者靠 B5 冒烟。


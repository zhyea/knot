# 敏感词识别方案（knot AI 网关）

> 状态：设计稿（决策已确认；2026-10-13 晚追加：流式不截断 + 惩罚性处理；**再调整：改走同步插件**）
> 日期：2026-10-13（初稿 2026-10-12）
> 范围：仅方案设计，不含实现代码

---

## 0. 背景与目标

knot AI 网关当前缺少对请求/响应文本的内容安全审核能力。本方案在网关链路内置敏感词识别，覆盖入站 prompt 与出站 completion 双向检测，支持分级处置（拦截/脱敏/标记），并尽量复用既有基础设施（Caffeine 缓存、枚举机制、Kafka sink），避免重复造轮子。

**设计原则：**

1. **走同步插件位，不硬编码进主链路** —— 同步插件（`PluginHandler`，`knot-plugin/.../PluginHandler.java`）在请求线程**内联执行**：`DefaultPluginDispatcher.dispatch` 无 try/catch，handler 抛出的异常直接冒泡打断链路。因此敏感词检测可以复用插件的启用/禁用、排序、绑定机制来拦截请求，无需在 `GatewayRequestHandler` 里插桩。
   > ⚠ 口径修正（2026-10-13）：初稿写「`PluginDispatcher.dispatch` 返 `void` → 插件纯观测不能拦截」是**误判**。`dispatch` 返 void 只表示「不回传改写结果」，但 handler 是内联同步执行的，异常照样传播。`PluginHandler` 的 javadoc 也写明「内联执行，可观测/改写请求与响应」。
2. **策略/分类走既有机制** —— 分类复用 `ks_enum_configs` 枚举机制（零 schema 变更），策略用 Java 枚举 + 全局配置，不新建策略/分类表。
3. **词库 Caffeine 缓存 + 版本号热更新** —— 对齐 `GatewayDataService` 的命中 10min / 未命中 30s（L30-44）。
4. **分级动作 BLOCK / MASK / FLAG**，默认 FLAG（影子模式）上线，避免误杀全站。
5. **出站流式检测复用现有管道** —— 跨 chunk 边界用滑动窗口，复用 `ProxyStreamingBody.pumpEventStream`（L69-80）。

---

## 1. 范围

| 维度 | 纳入 | 暂不入（MVP） |
|---|---|---|
| 方向 | 入站 prompt **+** 出站 completion 双向 | — |
| 协议 | chat/completions、completions、embedding 文本、moderation input | 图片/音视频内容审核（转上游或后续期） |
| 文本来源 | `messages[].content`、`prompt`、`input`、`instructions`、`system` role | 文件附件、媒体内嵌文本 |
| 计费影响 | BLOCK 请求不计费；出站命中惩罚性计费（§7.2）；MASK 按替换后 token 计 | 多模态语义审核 |

---

## 2. 总体架构与挂点

管线主入口 `AbstractGatewayRequestTemplate.handleMap`（`runtime/AbstractGatewayRequestTemplate.java:57-82`）：

```
validate → resolveRouting → GATEWAY_REQUEST 插件 → proxy → applyUsageAccounting → GATEWAY_RESPONSE 插件
```

| 检测点 | 插件（capability=`sensitive-word-moderation`） | 位置 | 说明 |
|---|---|---|---|
| **入站** | `SensitiveWordRequestPlugin`（`GATEWAY_EXCHANGE` / `GATEWAY_REQUEST`，`order_no=200`） | 在 `proxy` 之前，因此**天然早于** `trafficGuard.checkRouting` | 被拦请求不消耗 RPM 事前计数与额度 |
| 出站·整包 | `SensitiveWordResponsePlugin`（`GATEWAY_EXCHANGE` / `GATEWAY_RESPONSE`，`order_no=200`） | 在 `applyUsageAccounting` 之后 | 费用已记账 → 命中 400 时**费用照记** |
| 出站·流式 | —（M2） | 该阶段响应体是 `StreamingResponseBody`，拿不到文本 → 直接放行 | 不截断流，见 §7.1 |

两个插件与审计日志插件（同阶段 `order_no=100`）**排序刻意错开**：先落审计日志，再由敏感词插件抛 400，保证被拦截的请求/响应仍可追溯。

**插件实例注册**：代码里的 `@Component` handler 是权威来源，启动期 `PluginReconcileRunner` 按 entrypoint 校正 `kb_plugin_instances`；`data.sql` 另给两条种子（id 7/8，带人类可读名称与 `order_no=200`），避免首启动窗口内实例行缺失导致检测不生效。禁用/启用直接在插件管理页操作，无需改代码。

### 2.1 模块归属（2026-10-14 落定，方案 B）

| 模块 | 内容 |
|---|---|
| **knot-plugin** | `plugin.moderation` 包：`ModerationTerm`（中性词条 record）、`AhoCorasickMatcher`、`TextNormalizer`、`ModerationTextExtractor`、`ModerationConstants`、`ModerationSnapshot`、`ModerationSnapshotCache`（Caffeine）、`ModerationProperties`、`ModerationHitEvent`、`ModerationEventSink`、`ModerationGuard`、`ModerationRejectException`、两个 `PluginHandler`、`ModerationConfiguration` |
| **knot-service** | `DbModerationWordSource implements ModerationWordSource`：唯一碰持久层的类，注入 `ModerationWordMapper`，只做「查全量启用词条 + 映射成 `ModerationTerm`」 |
| **knot-dal** | `ModerationWordEntity` + `ModerationWordMapper`（既有） |
| **knot-gateway** | **零 moderation 代码**：只剩 `GatewayExceptionHandler` 捕获 `ModerationRejectException` 转 400，以及 `GatewayErrorTypeEnum.MODERATION_ERROR`（网关异常体系） |

**为什么必须这么分层**：初版把 871 行整体塞在 knot-gateway，直接咬出缺陷——`knot-admin` 只依赖 knot-service、**不依赖 knot-gateway**，于是 `PluginReconcileRunner`（admin 与 gateway 都跑）在 admin 侧 IOC 里看不到这两个 handler，把 `data.sql` 种的行**标成 orphaned=1**，而 gateway 侧又置回 0，两个应用反复对冲、管理页在「浅红孤儿」与「正常」间横跳，且存在被运维按孤儿误删的风险。

依赖倒置照抄既有先例 `PluginBindingProvider`（契约在 knot-plugin）/ `PluginService`（实现在 knot-service）：契约与引擎进插件模块，DB 实现放 service，**knot-plugin 仍只依赖 knot-common**。

⚠ `ModerationRejectException` 定义在 knot-plugin 而非网关异常家族：插件实现类在 plugin 模块，无法反向依赖 gateway 的 `GatewayRequestException`；由 `GatewayExceptionHandler` 捕获后补 `type`，从而**不必把整套网关异常体系下沉**。同理，命中事件**不并入** `PluginLogPayload`（sealed，permits 只列 6 种链路日志）——moderation hit 是面向数仓的**业务事件**，只带元数据、不含原文，与链路日志职责不同。

**全局场景**：无资源级策略绑定（已砍 `bindings`），网关加载唯一一份全局配置（策略枚举 + 全量词库编译的 AC 自动机 + 分类枚举），每个请求无差别应用。无 `RULE>CONSUMER>APP>GLOBAL` 解析步骤。

---

## 3. 匹配引擎

### 3.1 算法选型
- **主：Aho-Corasick 多模式自动机**，O(n)，万级词库单次扫描微秒级，一次扫描命中多分类。
- **辅：正则** 仅限 PII 模式（手机号/身份证），限量 + 超时熔断，防灾难性回溯。
- 明确不用：纯 `contains` 循环（词库万级 × body 几十 KB = 千万次比较）、数据库 `LIKE`。

### 3.2 归一化管道（命中率关键）
对文本做**归一化副本**判定，**原文保留**用于 MASK 替换：
- NFKC（全角→半角）、大小写折叠
- 剔零宽/控制符（ZWSP、ZWJ、BOM）
- 繁简归一、变体映射：leetspeak（`@`→a、`0`→o）、谐音/拼音、间隔符插入（`敏*感*词`→去分隔符）

**已确认**：MVP 默认 STANDARD 强度 = NFKC + 零宽剔除 + 大小写折叠；繁简/leetspeak/谐音等高级变体留 M4（默认即可，不额外配置）。

⚠ MASK 必须在**原文**做位置映射替换：对每个归一化字符记录源 offset，否则替换出乱码。

### 3.3 词库数据结构（三层）
- **词条**：`word, category_code, severity(1低→3高), lang, 归一化键` —— severity 挂在**词条**上（已确认，不放分类）
- **分类**：涉政/暴恐/色情/广告/侮辱/自定义（走枚举机制，见 §5.1）
- **策略**：动作 + 方向 + 失败模式（走枚举 + 全局配置，见 §5.2）
- **白名单**：短语级豁免（"正常词"含敏感子串时跳过）

---

## 4. 数据模型（收敛后）

经 4 处削减（详见 §12），持久化收敛为：**1 张新增表 + 既有枚举机制（零变更）+ 全局配置 + Kafka/日志事件**。对既有表**零 schema 变更**。

| 原表 | 处置 | 落到哪 |
|---|---|---|
| `kb_moderation_categories` | 删 | 既有枚举 `ks_enum_configs`（不加列；severity 移到词条） |
| `kb_moderation_policies` / `kb_moderation_policy_items` | 删 | Java 枚举 + `application.yml` 全局配置 |
| `kb_resource_moderation_bindings` | 删 | 全局场景，无资源解析 |
| `kb_moderation_hit_logs` | 删 | `ModerationHitEvent` → 日志（先）/ Kafka（后） → 数仓 |
| `kb_moderation_word_lists` | **保留** | 唯一新增表 |

### 4.1 唯一新增表 `kb_moderation_word_lists`

建议 DDL（草稿）：

```sql
CREATE TABLE IF NOT EXISTS kb_moderation_word_lists (
  id              BIGINT       PRIMARY KEY AUTO_INCREMENT,
  word            VARCHAR(255) NOT NULL COMMENT '原始词条（支持 emoji/全角）',
  normalized_word VARCHAR(255) NOT NULL COMMENT '归一化键（NFKC+小写+去零宽），判定走它、展示走 word',
  category_code   VARCHAR(64)  NOT NULL COMMENT '绑分类（ks_enum_configs.item_code，category=moderation_category），非主键 id',
  severity        TINYINT      NOT NULL DEFAULT 2 COMMENT '严重度：1-低 2-中 3-高；配合常量 SEVERITY_THRESHOLD 决定是否触发动作（见 §5.2）',
  lang            VARCHAR(8)   DEFAULT 'zh' COMMENT '语言/变体标识',
  status          TINYINT      NOT NULL DEFAULT 1 COMMENT '启用状态（EnabledStatusEnum）：1-启用 0-停用',
  is_deleted      TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-否 1-是',
  remark          VARCHAR(255) DEFAULT NULL,
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_word_category (word, category_code),
  KEY idx_norm (normalized_word, category_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='敏感词条（Aho-Corasick 自动机数据源）';
```

**逻辑删除四必配**：`countByXxxCode` 物理行判；列表 `includeDeleted` + mapper `<if>` + 排序 `is_deleted asc,id desc`；`PUT /{id}/restore` 端点；引用检查（仅词库自引用，无跨表引用，删除即停参与编译）。

### 4.2 版本号与缓存刷新
- **版本号** = 已加载词条行的 `MAX(updated_at)`（不建版本表、不额外查库），随命中事件的 `wordVersion` 落审计，供回溯。
- **缓存刷新** = Caffeine TTL 10 分钟（`ModerationConfigSource`，对齐 `GatewayDataService`）：词库变更最迟 10 分钟生效；需秒级生效时调 `invalidate()`（词库管理 UI 保存动作）。
- 分类枚举**不参与运行时匹配**（仅是标签维度，是否匹配只看词条行 `status`/`is_deleted`），故枚举变更不影响词库版本。

---

## 5. 分类、策略、审计三要素的落地

### 5.1 分类 → 既有枚举机制（不加列、不建表）
复用 `ks_enum_categories`（`schema.sql:670`）+ `ks_enum_configs`（`schema.sql:684`），**零 schema 变更**：分类就是普通枚举项（`item_code`/`item_label`/`is_enabled`），强监管分类靠 `ks_enum_categories.is_system=1` 保护不可删。

`data.sql` 种子（ADMIN 块末分区）：插一条 `ks_enum_categories(category='moderation_category', is_system=1)`，项下 涉政/暴恐/色情/广告/侮辱/自定义。同走 `/api/common/enums` 下发前端。

> severity 不落分类（已确认）：同一分类下不同词严重度不同（如"涉政"分类里既有 3 高也有 1 低的边缘词），放词条上更准。

### 5.2 策略 → 枚举 + 最小配置（不建表）
| 维度 | 形式 | 取值 / 当前值来源 |
|---|---|---|
| `ModerationAction` | Java 枚举 `EnumOption` | `BLOCK` / `MASK` / `FLAG` —— 当前值走配置 `action` |
| `ModerationScope` | 同上 | `REQUEST` / `RESPONSE` / `BOTH` —— 当前值走配置 `scope` |
| `ModerationFailMode` | 同上 | `OPEN` / `CLOSE` —— 当前值**代码常量 `OPEN`** |

策略表被否，但枚举只给候选值、不给"当前选哪个"，故"当前策略实例"只保留 **3 个配置项**（`@ConfigurationProperties` 注入）：

```yaml
knot:
  moderation:
    enabled: true    # 总开关（风控 kill switch）
    action: FLAG     # BLOCK / MASK / FLAG（影子模式上线）
    scope: BOTH      # REQUEST / RESPONSE / BOTH
```

> 📌 2026-10-14 现状：这段**已从 `application.yml` 移除**（死配置审计判定「值 == 绑定类默认值」，行为零变化）。默认值现由 `ModerationProperties` 字段初始化器给出，仍可通过环境变量覆盖 —— Spring Boot 的 relaxed binding 把 `KNOT_MODERATION_ACTION` / `KNOT_MODERATION_ENABLED` / `KNOT_MODERATION_SCOPE` 直接绑到 `knot.moderation.*`，**不依赖 yml 是否声明该段**。

其余维度**不进配置**，一律走代码常量，避免"配置即冗余"：

| 常量 | 值 | 对应被砍的配置项 |
|---|---|---|
| `MAX_TEXT_LENGTH` | `65536`（单段文本参与检测的**字符数**上限） | `max-body-bytes` |
| `SEVERITY_THRESHOLD` | `1`（全部命中触发动作） | `severity-threshold` |
| fail-open | 词库不可用 → 放行 + 告警（内联行为，**未建枚举**：当前无分支使用） | `fail-mode` |
| `MASK_CHAR` / `PENALTY_MULTIPLIER` | M2 随脱敏改写 / 惩罚性计费一并落地（提前声明即死代码） | `mask-char` / `penalty-multiplier` |

`penalty-enabled` **删除**（已确认）：`action=BLOCK` 已含惩罚语义，单独开关会制造"BLOCK 但无惩罚"的矛盾态。

⚠ 口径修正：当前未接 Nacos/配置热刷，改 `application.yml` 仍需重启 —— 配置的价值是**环境可差异 + 未来接 Nacos 后可热刷**，**不是"免 redeploy"**。

### 5.3 命中审计 → 日志先行，Kafka 后置（不建表、不阻塞）
- **已确认：不必等 Kafka 就绪**。M1 即按结构化日志降级实现：`log.info("moderation-hit {}", 事件JSON)`，运维侧采集即用。
- 定义 `ModerationHitEvent`，**只带元数据**（已确认：原文不出库）：
  `traceId / appCode / ruleCode / direction / categoryCode / severity / action / hitCount / matchedLength / wordListVersion / timestamp`
  —— **不带 word、不带上下文片段、不带原文**（合规）。
  ⚠ 消费者维度**暂不入事件**：插件上下文的 `GatewayRoutingSnapshot` 只有 `consumerId`，按「跨模块绑定一律存 code 不存 id」的口径，等快照补 `consumerCode` 后再加。
- Kafka 就绪后（`PluginSinkProperties.kafka.enabled=true` + `bootstrap-servers`，`application.yml:35` 现有占位）新增 `ModerationEventSink`（同 `KafkaPlaceholderPluginEventSink` 模式，`knot-plugin/.../runtime/`），发 topic `${prefix}.moderation.hits`，由数仓消费。切换对主链路无感。
- 对外 `error.detail` 只暴露 `category` code，不返回词库原文（防探测）。

---

## 6. 词库加载与热更新

- 版本号 = `MAX(updated_at)` 跨 `word_lists` + 枚举项；缓存 key = 全局版本。
- 编译产物（AC 自动机按版本编译一次）进 Caffeine：命中 10min / 未命中 30s（对齐 `GatewayDataService`）。
- 多节点：词库/枚举变更 → 版本号变 → 各网关节点按版本 key 自然加载新版本（版本号即失效机制，免广播）。
- 提供 admin 手动失效入口，把生效延迟压到秒级。

---

## 7. 分级动作与计费

| 动作 | 入站（REQUEST） | 出站（RESPONSE） | 计费 |
|---|---|---|---|
| **BLOCK** | **请求直接打回**：HTTP 400 `moderation_error`，不转发上游，**不计费** | **整包：400 打回**（内容不返回），费用照记；**流式：不截断**，完整转发 + 惩罚性处理（§7.1/§7.2） | 请求未转发→不计；出站命中→照常计 + **惩罚加价** |
| **MASK** | 替换命中词为常量 `MASK_CHAR`(`*`) 后转发（`AiPayloadFields` 改写 body，token 按替换后算） | 逐 chunk 替换后输出 | 按替换后实际转发内容计 |
| **FLAG** | 放行 + 发 `ModerationHitEvent` + metrics | 同左 | 照常 |

### 7.1 出站 BLOCK 细则（已确认）
- **整包响应**（尚未提交状态码）：命中即**不返回原响应体**，直接以 HTTP 400 `moderation_error` 打回（与请求侧同一异常出口）；上游已产生的用量照常记账，并施加惩罚性计费（§7.2）。
- **流式响应**（首字节已提交，改不了状态码）：**不截断** —— 命中后继续完整转发后续内容，不吞、不在流内注入 error 事件（保 SSE 契约干净，客户端不会误判流失败）；usage 随流天然完整，无 drain / 提前关闭的复杂度与丢 usage 风险；威慑改由**惩罚性处理**承担（§7.2）。

### 7.2 惩罚性处理（替代截断的威慑手段）
流式无法拒绝交付，改用惩罚让滥用者付代价；整包出站被拒场景同样叠加：

| 手段 | 说明 | 状态 |
|---|---|---|
| **惩罚性计费** | 命中请求成本 × 常量 `PENALTY_MULTIPLIER`(2.0)，计入 `totalCost` 与额度消耗（多扣）；`action=BLOCK` 即含惩罚，无独立开关 | M2 |
| **审计事件** | `ModerationHitEvent`（元数据，原文不出库）发日志/Kafka | M1 |
| **调用方可见标记** | `knot_extend` 增补 `moderation: {hit, severity, penaltyMultiplier, penaltyCost}`（仅 `return_usage_detail` 开启时）；**流内不注入 error 事件** | M2 |
| **累计熔断（strike）** | 窗口内命中 N 次 → 自动停用 consumer / 升级处置 | 后续期，先留口 |

⚠ 金额口径：惩罚系数乘在成本上后仍走 `plainMoney()` 8 位小数字符串出口（`AbstractBillingModeCalculator.detail()` 唯一构造点），不得回归科学计数法（对齐 `NormalizedUsageDetail.cost` String 化决策）。

---

## 8. 网关集成细节

### 8.1 文本抽取
只抽文本字段（`messages[].content` 为 string 或 `parts[]` 数组取 text part、`prompt`、`input`(string/array)、`instructions`、`system`）；大 body（> 常量 `MAX_BODY_BYTES`=65536）跳过或截断检测并记 metrics（防御性）。

### 8.2 流式出站增量检测（核心）
在 `pumpEventStream` 循环内（`ProxyStreamingBody.java:69-80`）：
- **滑动窗口**：上一 chunk 尾部保留 `maxWordLen-1` 字符 + 新 chunk 拼接再扫，避免跨 chunk 漏命中。
- 每 chunk 只扫增量，整体线性 O(n)，不破坏首 token 时延。
- MASK：窗口内命中区间替换后输出。
- BLOCK：**只观测、零侵入** —— 不改输出、不截断、不在流内注入 error 事件；命中即发审计事件，成本走惩罚性计费（§7.2）。转发管道行为与未开启审核时完全一致，usage 随流天然完整。

### 8.3 拒绝契约
- 新增 `ModerationRejectException`（定义在 knot-plugin，见 §2.1 归属说明）+ `GatewayErrorTypeEnum.MODERATION_ERROR`（留在网关枚举），**HTTP 400**（429 留给流量），由 `GatewayExceptionHandler` 单一 handler 出口转 `GatewayErrorResponse`。
- 🔒 `error.detail` 只暴露分类 code 与命中位置，不返回词库原文（防词库探测）。

---

## 9. 与现有机制关系

- **knot_extend**：BLOCK 请求未转发上游 → 无 usage → 合理无 `knot_extend`；出站命中（流式不截断）usage 照常注入，并按 `return_usage_detail` 增补 `moderation` 惩罚标记（§7.2）；MASK 按替换后文本计。
- **频控/额度**：入站检测**早于** `trafficGuard`（RPM 是事前 +1 计数，若在其后执行，被拦请求会白占限流与额度）；出站整包检测在记账之后执行 → 费用照记。FLAG、出站命中场景正常记账（后者再叠加惩罚）。
- **插件**：敏感词检测**本身即插件**（占 `GATEWAY_REQUEST` / `GATEWAY_RESPONSE` 两个同步位），复用插件的启用/禁用与 `order_no` 排序机制；其它观测型插件不受影响。禁用检测 = 插件管理页把实例置为停用，无需改配置重启。
- **retry/failover**：BLOCK 是网关自身拒绝（`GatewayRequestException` 分支），不重试不 failover（对齐 `GatewayRequestHandler.java:145` 注释语义）。

---

## 10. 性能与容量

- AC 内存：万词×均 6 字 ≈ 数 MB，可接受；单核每秒数百 MB 扫描，瓶颈仍在上游。
- **失败模式**：默认 **fail-open + 告警**（内容安全不能因自身故障挂全站），常量 `FAIL_MODE=OPEN`；需改 CLOSE 则改常量（不走配置，见 §5.2）。

---

## 11. 分期路线

| 阶段 | 范围 | 验收门 |
|---|---|---|
| M1 | 入站抽取 + AC 引擎 + 枚举分类 + 词条 severity + 单全局策略（3 项配置）+ BLOCK/FLAG + 词库表 + 结构化日志审计事件 | 单测（引擎/归一化/边界）+ 冒烟（命中 400、未命中放行、白名单豁免、`action` FLAG→BLOCK 灰度） |
| M2 | 出站整包 + 流式增量检测（只观测不改输出）+ MASK + 惩罚性计费 | 流式跨 chunk 命中用例；惩罚成本进入 totalCost/额度用例；knot_extend moderation 标记用例 |
| M3 | 词库管理 UI（admin CRUD/导入导出）+ 版本热更新 + metrics 看板 + Kafka sink 切换 | vue-tsc + vite build；词库更新 ≤10min 生效实测 |
| M4 | 高级归一化（繁简/leetspeak/谐音）+ 正则 PII | 变体用例集 |

---

## 12. 收敛记录（robin 拍板）

**初版 4 处削减（2026-10-12）：**
1. `kb_moderation_categories` 不必要 → 分类走既有枚举机制 `ks_enum_configs`。
2. `kb_moderation_hit_logs` 不必要 → 命中事件发 Kafka → 数仓，不发 DB。
3. `kb_resource_moderation_bindings` 不必要 → 全局场景，无资源级绑定解析。
4. 策略不必建表 → 动作/方向/失败模式定义 Java 枚举，全局策略实例走 `application.yml` 配置；`kb_moderation_policies` / `kb_moderation_policy_items` 均无必要。

**决策细化（2026-10-13）：**
5. 分类枚举**不加列、不建表**，`severity` 移到**敏感词条**上（`kb_moderation_word_lists.severity`）。
6. Kafka **不必等就绪**：M1 结构化日志先行，Kafka 后置切换，不阻塞交付。
7. 出站命中：**流式不截断**（完整转发，避免丢最终 usage），改做**惩罚性处理**（惩罚性计费 + 审计事件 + `knot_extend` 标记）；整包出站仍 400 打回（内容不返回、费用照记+惩罚）；入站命中请求直接打回。
8. 归一化强度**默认即可**（STANDARD：NFKC+零宽+大小写）。
9. 审计事件**原文不出库**：只带元数据（traceId/分类/severity/action/hitCount/matchedLength/版本/时间）。
10. **配置最小化（口径 A）**：只留 `enabled` / `action` / `scope` 三项；`mask-char`、`max-body-bytes`、`fail-mode`、`severity-threshold`、`penalty-multiplier` 一律改**代码常量**；`penalty-enabled` **删除**（与 `action=BLOCK` 语义重合，会制造"BLOCK 但无惩罚"矛盾态）。
11. **口径修正**：当前未接 Nacos/热刷，改 `application.yml` 仍需重启 —— 配置价值是"环境可差异 + 未来接 Nacos 后可热刷"，文档早期写的"免 redeploy"作废。
12. **改走同步插件（2026-10-13 晚）**：敏感词检测不硬编码进 `GatewayRequestHandler`，改为两个 `PluginHandler`（`SensitiveWordRequestPlugin` / `SensitiveWordResponsePlugin`）。依据是同步插件在请求线程内联执行、`DefaultPluginDispatcher.dispatch` 不吞异常 → handler 抛异常可打断链路；原设计原则 1 的「dispatch 返 void 故不能拦截」是误判，已订正。
13. **模块归位（2026-10-14）**：871 行从 knot-gateway 迁出——契约/引擎/插件进 `knot-plugin/plugin/moderation`，DB 实现 `DbModerationWordSource` 进 knot-service，gateway 零 moderation 代码。动因是「admin 不依赖 gateway → 两插件不在 admin IOC → `PluginReconcileRunner` 把实例行反复标孤儿」的实证缺陷，以及「复用既有依赖倒置先例（`PluginBindingProvider`/`PluginService`）」的归类口径。

---

## 13. 残留实现关注点（无开放决策）

1. **惩罚成本出口**：惩罚系数乘后必须走 `plainMoney()` 同一构造点（`AbstractBillingModeCalculator.detail()`），保持 8 位小数字符串；额度消耗按含惩罚成本走（`trafficGuard.record` 的 totalCost 入参）。
2. **SSE 契约零侵入**：流式只观测不改输出，不注入 error 事件；惩罚信息仅经 `knot_extend`（`return_usage_detail` 开启时）暴露。
3. **strike 升级留后续**：M1-M2 只记录事件，自动停用 consumer 等升级处置后续期再做。
4. **灰度路径**：上线初期 `action: FLAG` 全量观察（只记事件不动作）→ 确认误杀率后切 `action: BLOCK`（含惩罚性计费）；如需"只拦高危"再改常量 `SEVERITY_THRESHOLD`（不走配置，见 §5.2）。

---

## 14. 落地进度

**M1 已落地（2026-10-13）**
- 数据：`kb_moderation_word_lists` 进 `schema.sql` + `data.sql` 分类枚举种子 + 幂等迁移 `docs/database/migration/2026-10-13-moderation-word-lists.sql`。
- 枚举：`ModerationActionEnum` / `ModerationScopeEnum`（实现 `EnumOption`，**未注册** `EnumOptionRegistry`，UI 上线时再注册）。
- 引擎：`AhoCorasickMatcher` + `TextNormalizer`（STANDARD）+ `ModerationTextExtractor` + `ModerationConfigSource`（Caffeine 快照）。
- 集成：**两个同步插件**承载检测 —— `SensitiveWordRequestPlugin`（`GATEWAY_REQUEST`，位于 `proxy` 前故早于 `trafficGuard`）/ `SensitiveWordResponsePlugin`（`GATEWAY_RESPONSE`，位于记账后故费用照记）；`ModerationGuard` 只被插件调用，网关主链路零插桩。拒绝出口 `ModerationRejectException`（knot-plugin）→ `GatewayExceptionHandler` 转 400 + `GatewayErrorTypeEnum.MODERATION_ERROR`。
- 归属（10-14）：`knot-plugin/plugin/moderation` 包（含两个 `PluginHandler`）+ knot-service 的 `DbModerationWordSource`；网关主链路零残留，详见 §2.1。
- 注册：`data.sql` 种子两条 `kb_plugin_instances`（id 7/8，`order_no=200`，entrypoint 指向 `plugin.moderation.*`）；启动期 `PluginReconcileRunner` 按 entrypoint 兜底校正。
- 审计：`ModerationHitEvent` → `ModerationEventSink` 结构化日志（只带元数据，原文不出库；消费者维度待快照补 code）。
- 单测：`AhoCorasickMatcherTest` / `TextNormalizerTest` / `ModerationTextExtractorTest` / `ModerationGuardTest`（整包响应 Map 形态、流式响应体放行两例）。
- 归属（10-14 追加）：871 行从 knot-gateway 迁到 `knot-plugin/plugin/moderation`（契约/引擎/两插件/装配）+ knot-service 的 `DbModerationWordSource`；网关仅剩 400 handler 与 `GatewayErrorTypeEnum.MODERATION_ERROR`。4 个测试类随包迁到 knot-plugin。迁移 `docs/database/migration/2026-10-14-moderation-plugin-entrypoint.sql`（换 entrypoint + 删 reconcile 重复行）。

**M3 已落地（2026-10-14）**
- 后端 CRUD：`ModerationWordController`（`/api/moderation-words`：list/check-code/get/create/update/status/delete/restore/import/export）+ `ModerationWordService`（导入去重 + 逻辑删除恢复 + 唯一键 `uk_word_category(word,category_code)` 物理行判定）+ `ModerationWordConverter`。
- 授权种子：`ks_modules(5 内容安全)/ks_menus(24 moderation.words)/ks_permissions(144-151，含插件删除 151 system:plugin:delete)/ks_api_permission_bindings(196-206)`；data.sql 直落 + 幂等迁移 `docs/database/migration/2026-10-14-moderation-word-authz-seed.sql`（status 写数字 1，规避 tinyint 严格模式 DataTruncation）。
- 前端（4 文件）：`src/api/moderationWords.ts` + `components/moderation/ModerationWordFormDialog.vue` + `ModerationWordImportDialog.vue` + `views/moderation/ModerationWordManageView.vue`（列表 + 含已删除 checkbox + CSV 导出带 BOM）；路由 `moderation/words` + i18n 四语 + `MainLayout.iconMap` 加 Warning。
- 导入导出：`ModerationWordService.importWords` 逐行校验、本批内重复报错/库内已存在跳过、超 2 万行拒绝；`exportWords` 仅活行；`existingKeys()` 含逻辑删除行避免重导已删词撞唯一键整批回滚。
- 单测：`ModerationWordServiceTest`（16 例，用 `CountingSnapshotCache` 计数子类规避 JDK25 Mockito inline 限制）覆盖导入跳过已删/已存、本批内重复报错、超量拒绝、仅插入时 invalidate、导出仅活行、删除拒启用词、恢复拒未删词、create 拒重复/归一化/severity 越界、update/status 拒缺失。
- 既有缺陷修复：`PluginController DELETE /api/plugins/{id}` 补权 `system:plugin:delete`（perm 151 + bind 206 + OPERATOR/ADMIN 授权），前端删除按钮不再恒 403。
- `gen_api_bindings.py`：补 moderation 元数据 + import 动作；修复孤儿权限 bug（绑定去重判断前移至权限创建前）。
- 真启 jar 冒烟（13/13 通过）：admin 18092 连 `knot_smoke_options`（`KNOT_SQL_INIT_MODE=NEVER`），登录 admin/admin123，验证 list/check-code/create/list/status/delete/restore/duplicate-block/import/export + plugin delete 非 403 + plugin list；收尾 DROP 冒烟库零残留。

**仍未做**
- M2：MASK（需归一化→原文 offset 映射并在原 body 上改写）、流式出站增量检测（滑动窗口，只观测不改输出）、惩罚性计费。
- M4：高级归一化（繁简/leetspeak/谐音）+ 正则 PII。
- 真启 jar 冒烟（网关侧 5s 版本轮询）：admin 侧 CRUD/授权/插件删除 403 修复已于 2026-10-14 真启验证（13/13）；`DbModerationWordSource` → `ModerationSnapshotCache` 版本轮询失效 + `ModerationGuard` 命中由单测覆盖，网关 e2e（需假上游解析 chunked）待测。
- ⚠ **既有库必须先跑迁移**：dev 库 `knot` 当前有 4 行指向同一份插件代码（id 7/8 旧 entrypoint orphaned=1 + id 9/10 reconcile 自动补的新 entrypoint），不跑迁移会导致同一 handler 被调用两次。

**未落地**
- M2：MASK（需归一化→原文 offset 映射并在原 body 上改写）、流式出站增量检测（滑动窗口，只观测不改输出）、惩罚性计费。
- M4：高级归一化（繁简/leetspeak/谐音）+ 正则 PII。

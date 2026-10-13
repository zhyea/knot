# 敏感词识别方案（knot AI 网关）

> 状态：设计稿（决策已确认；2026-10-13 晚追加：流式不截断 + 惩罚性处理）
> 日期：2026-10-13（初稿 2026-10-12）
> 范围：仅方案设计，不含实现代码

---

## 0. 背景与目标

knot AI 网关当前缺少对请求/响应文本的内容安全审核能力。本方案在网关链路内置敏感词识别，覆盖入站 prompt 与出站 completion 双向检测，支持分级处置（拦截/脱敏/标记），并尽量复用既有基础设施（Caffeine 缓存、枚举机制、Kafka sink），避免重复造轮子。

**设计原则：**

1. **内置管线阶段，不占插件位** —— `PluginDispatcher.dispatch` 返 `void`（`knot-plugin/.../PluginDispatcher.java:5-7`），插件是纯观测，无法阻断/改写；敏感词必须能拦截请求，故必须是内置阶段（对齐 `trafficGuard` 先例）。
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

| 检测点 | 位置 | 说明 |
|---|---|---|
| **入站** | `resolveRouting` 之后、`proxy` 之前（与 `trafficGuard.checkRouting` 并列，`GatewayRequestHandler.java:85`） | 此时已有 app/rule/consumer 上下文 |
| 出站·整包 | `applyUsageAccounting` 内对 `responseBody` 检测（`GatewayRequestHandler.java:216`） | 尚未提交，可改 HTTP 状态码 |
| 出站·流式 | `ProxyStreamingBody.pumpEventStream` 循环内（`upstream/stream/ProxyStreamingBody.java:69-80`） | 逐 chunk 增量，见 §8.2 |

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
  severity        TINYINT      NOT NULL DEFAULT 2 COMMENT '严重度：1-低 2-中 3-高；配合全局 severity-threshold 决定是否触发动作',
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

### 4.2 版本号（缓存失效信号）
不建版本表，取 `MAX(updated_at)` 跨 `word_lists` + `ks_enum_configs`（moderation_category 项下）。任一变更 → 版本变 → Caffeine 重载 AC 自动机（对齐 `GatewayDataService` 10min/30s 命中/未命中 TTL）。

---

## 5. 分类、策略、审计三要素的落地

### 5.1 分类 → 既有枚举机制（不加列、不建表）
复用 `ks_enum_categories`（`schema.sql:670`）+ `ks_enum_configs`（`schema.sql:684`），**零 schema 变更**：分类就是普通枚举项（`item_code`/`item_label`/`is_enabled`），强监管分类靠 `ks_enum_categories.is_system=1` 保护不可删。

`data.sql` 种子（ADMIN 块末分区）：插一条 `ks_enum_categories(category='moderation_category', is_system=1)`，项下 涉政/暴恐/色情/广告/侮辱/自定义。同走 `/api/common/enums` 下发前端。

> severity 不落分类（已确认）：同一分类下不同词严重度不同（如"涉政"分类里既有 3 高也有 1 低的边缘词），放词条上更准。

### 5.2 策略 → 枚举 + 全局配置（不建表）
| 维度 | 形式 | 取值 |
|---|---|---|
| `ModerationAction` | Java 枚举 `EnumOption` | `BLOCK` / `MASK` / `FLAG` |
| `ModerationScope` | 同上 | `REQUEST` / `RESPONSE` / `BOTH` |
| `ModerationFailMode` | 同上 | `OPEN` / `CLOSE` |

全局策略实例绑定在 `application.yml` 的 `knot.moderation.*` 配置块，经 `@ConfigurationProperties` 注入：

```yaml
knot:
  moderation:
    enabled: true             # 总开关
    action: FLAG              # BLOCK / MASK / FLAG（影子模式上线）
    scope: BOTH               # REQUEST / RESPONSE / BOTH
    fail-mode: OPEN           # OPEN=引擎故障放行+告警；CLOSE=拦截
    mask-char: "*"            # MASK 动作替换字符
    max-body-bytes: 65536     # 超大 body 跳过/截断检测防御阈值
    severity-threshold: 1     # 触发动作的最低严重度（1=全部触发）；低于阈值的命中只记事件不动作。灰度可调 3=只拦高危
    penalty-enabled: true      # 惩罚性计费开关（命中请求成本加价，见 §7.2）
    penalty-multiplier: 2.0    # 惩罚系数：命中请求成本 × 该系数，计入 totalCost 与额度消耗
```

运维改 `action: FLAG` 即可影子模式上线、调 `severity-threshold: 3` 可只拦高危词、改 `fail-mode: CLOSE` 即故障拦截，**免 redeploy**。

### 5.3 命中审计 → 日志先行，Kafka 后置（不建表、不阻塞）
- **已确认：不必等 Kafka 就绪**。M1 即按结构化日志降级实现：`log.info("moderation-hit {}", 事件JSON)`，运维侧采集即用。
- 定义 `ModerationHitEvent`，**只带元数据**（已确认：原文不出库）：
  `traceId / appCode / ruleCode / consumerCode / direction / categoryCode / severity / action / hitCount / matchedLength / wordListVersion / timestamp`
  —— **不带 word、不带上下文片段、不带原文**（合规）。
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
| **MASK** | 替换命中词为 `mask-char` 后转发（`AiPayloadFields` 改写 body，token 按替换后算） | 逐 chunk 替换后输出 | 按替换后实际转发内容计 |
| **FLAG** | 放行 + 发 `ModerationHitEvent` + metrics | 同左 | 照常 |

### 7.1 出站 BLOCK 细则（已确认）
- **整包响应**（尚未提交状态码）：命中即**不返回原响应体**，直接以 HTTP 400 `moderation_error` 打回（与请求侧同一异常出口）；上游已产生的用量照常记账，并施加惩罚性计费（§7.2）。
- **流式响应**（首字节已提交，改不了状态码）：**不截断** —— 命中后继续完整转发后续内容，不吞、不在流内注入 error 事件（保 SSE 契约干净，客户端不会误判流失败）；usage 随流天然完整，无 drain / 提前关闭的复杂度与丢 usage 风险；威慑改由**惩罚性处理**承担（§7.2）。

### 7.2 惩罚性处理（替代截断的威慑手段）
流式无法拒绝交付，改用惩罚让滥用者付代价；整包出站被拒场景同样叠加：

| 手段 | 说明 | 状态 |
|---|---|---|
| **惩罚性计费** | 命中请求成本 × `penalty-multiplier`（默认 2.0），计入 `totalCost` 与额度消耗（多扣） | M2 |
| **审计事件** | `ModerationHitEvent`（元数据，原文不出库）发日志/Kafka | M1 |
| **调用方可见标记** | `knot_extend` 增补 `moderation: {hit, severity, penaltyMultiplier, penaltyCost}`（仅 `return_usage_detail` 开启时）；**流内不注入 error 事件** | M2 |
| **累计熔断（strike）** | 窗口内命中 N 次 → 自动停用 consumer / 升级处置 | 后续期，先留口 |

⚠ 金额口径：惩罚系数乘在成本上后仍走 `plainMoney()` 8 位小数字符串出口（`AbstractBillingModeCalculator.detail()` 唯一构造点），不得回归科学计数法（对齐 `NormalizedUsageDetail.cost` String 化决策）。

---

## 8. 网关集成细节

### 8.1 文本抽取
只抽文本字段（`messages[].content` 为 string 或 `parts[]` 数组取 text part、`prompt`、`input`(string/array)、`instructions`、`system`）；大 body（>`max-body-bytes`）跳过或截断检测并记 metrics（防御性）。

### 8.2 流式出站增量检测（核心）
在 `pumpEventStream` 循环内（`ProxyStreamingBody.java:69-80`）：
- **滑动窗口**：上一 chunk 尾部保留 `maxWordLen-1` 字符 + 新 chunk 拼接再扫，避免跨 chunk 漏命中。
- 每 chunk 只扫增量，整体线性 O(n)，不破坏首 token 时延。
- MASK：窗口内命中区间替换后输出。
- BLOCK：**只观测、零侵入** —— 不改输出、不截断、不在流内注入 error 事件；命中即发审计事件，成本走惩罚性计费（§7.2）。转发管道行为与未开启审核时完全一致，usage 随流天然完整。

### 8.3 拒绝契约
- 新增 `GatewayModerationException` + `GatewayErrorTypeEnum.MODERATION_ERROR`，**HTTP 400**（429 留给流量），body 走对齐 `GatewayTrafficRejectException` 的单一 handler + `TrafficRejectDetail` 结构（`controller/GatewayExceptionHandler.java:88`）。
- 🔒 `error.detail` 只暴露分类 code 与命中位置，不返回词库原文（防词库探测）。

---

## 9. 与现有机制关系

- **knot_extend**：BLOCK 请求未转发上游 → 无 usage → 合理无 `knot_extend`；出站命中（流式不截断）usage 照常注入，并按 `return_usage_detail` 增补 `moderation` 惩罚标记（§7.2）；MASK 按替换后文本计。
- **频控/额度**：BLOCK 在 `trafficGuard` 之后执行，被拦请求**不占 RPM/TPM/额度**（对齐"上游成功才记账"语义）；FLAG、截断场景正常记账。
- **插件**：`GATEWAY_REQUEST/RESPONSE` 观测位可见 moderation 结果（context attribute），不承担判定。
- **retry/failover**：BLOCK 是网关自身拒绝（`GatewayRequestException` 分支），不重试不 failover（对齐 `GatewayRequestHandler.java:145` 注释语义）。

---

## 10. 性能与容量

- AC 内存：万词×均 6 字 ≈ 数 MB，可接受；单核每秒数百 MB 扫描，瓶颈仍在上游。
- **失败模式**：默认 **fail-open + 告警**（内容安全不能因自身故障挂全站），可配 `fail-mode: CLOSE`。

---

## 11. 分期路线

| 阶段 | 范围 | 验收门 |
|---|---|---|
| M1 | 入站抽取 + AC 引擎 + 枚举分类 + 词条 severity + 单全局策略 + BLOCK/FLAG + 词库表 + 结构化日志审计事件 | 单测（引擎/归一化/边界）+ 冒烟（命中 400、未命中放行、白名单豁免、severity-threshold 灰度） |
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

---

**决策细化（2026-10-13 晚追加）：**
10. **流式不截断**：命中后继续完整转发 —— 既避免截断丢最终 usage 的复杂度，也把威慑改为**惩罚性处理**（§7.2）：惩罚性计费（成本 × `penalty-multiplier`，默认 2.0，计入 totalCost 与额度消耗）+ 审计事件 + `knot_extend` moderation 标记；流内不注入 error 事件。

## 13. 残留实现关注点（无开放决策）

1. **惩罚成本出口**：惩罚系数乘后必须走 `plainMoney()` 同一构造点（`AbstractBillingModeCalculator.detail()`），保持 8 位小数字符串；额度消耗按含惩罚成本走（`trafficGuard.record` 的 totalCost 入参）。
2. **SSE 契约零侵入**：流式只观测不改输出，不注入 error 事件；惩罚信息仅经 `knot_extend`（`return_usage_detail` 开启时）暴露。
3. **strike 升级留后续**：M1-M2 只记录事件，自动停用 consumer 等升级处置后续期再做。
4. **`severity-threshold` 灰度**：上线初期建议 `action: FLAG` 全量观察 → 调 `severity-threshold: 3` 只拦高危 → 确认误杀率后全量 BLOCK（`penalty-enabled` 同步评估）。

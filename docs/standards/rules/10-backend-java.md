# 10 · 后端 Java 规范

> 适用：`knot-server/**/*.java`。完整索引见 `docs/standards/PROJECT-STANDARDS.md`。

## 1. 模块结构与依赖红线

reactor 8 模块：`common` / `plugin-api` / `plugin` / `adapter` / `dal` / `service` / `admin` / `gateway`。

| 规则 | 说明 |
|---|---|
| `knot-plugin-api` 是**轻量契约层** | 只有接口/枚举/上下文，不含业务实现、缓存、配置 |
| `knot-plugin` 的类**禁止外部模块 import（含测试）** | 对它的依赖一律 `<scope>runtime</scope>`，让编译器拦住违规 import |
| `gateway` / `service` 依赖 `plugin-api`(compile) + `plugin`(runtime) | 只为保证插件 bean 进 IOC |
| `admin` **不依赖** `gateway` | `admin` 经 `service` 传递拿到插件 bean |
| `PluginHandler` 实现放 `knot-plugin` | 放 `gateway` 单独依赖会导致 admin IOC 没有它 → 反复被标 orphaned |

> 教训（2026-10-14）：曾有会话把 `knot-plugin` 换成 `knot-plugin-api`，编译+单测全绿，但插件 bean 不在 classpath，分发器 IOC 空转——**此类回归只有真启 jar 冒烟能发现**。

## 2. 归属判例

- **管理端专属配置放 `knot-admin`**（如 `GatewayRuntimeProperties` / `GatewayConfiguration`）；controller 注入 properties，把 baseUrl 当参数透传给 service，**service 不持有管理端概念**。
- **两个 app 共用的才留 `knot-service`**（如 `CredentialProperties` 加解密两进程都用）。

## 3. 分层

```
controller  → 参数校验、权限、请求追踪
service     → 业务编排、事务边界
converter   → MapStruct 实体 ⇄ DTO/VO
mapper      → MyBatis SQL（XML 在 knot-dal）
entity      → 表映射（Entity 后缀）
dto / vo    → 入参 / 出参
model       → 领域值对象（非表映射）
```

## 4. 命名纪律（2026-10-15 立规，robin 拍板）

### 4.1 角色后缀白名单

新增类**只能**用以下后缀，不许自造：

`Service` `Support` `Source` `Resolver` `Registry` `Runner` `Provider` `Migrator`
`Controller` `Converter` `Mapper` `Entity` `Dto` `Vo` `Item` `Request` `Response`
`Properties` `Configuration` `Constants` `Enum` `Exception` `Cache` `Store`
`Handler` `Dispatcher` `Descriptor` `Context` `Sink` `Writer` `Extractor` `Calculator` `Policy` `Template` `Publisher`（2026-10-18 robin 拍板 `KafkaPublisher` 补入）

> 判例（2026-10-23）：双视图载体命名评审**否决** `UsageExtractResult`——`Result` **不在白名单**；改用 `UsageExtractItem`（`Item` 在白名单内）。
> 拿不准后缀时按此判例办：先查白名单，**不在即换名**，不要为了顺口自造。

### 4.2 service 包纪律

- `org.chobit.knot.gateway.service` 包**顶层只允许 `@Service` 业务服务**（`XxxService`）。
- 非 `@Service` 的协作者组件（Support/Source/Resolver/Registry/Runner/Migrator）一律进 **`service.support` 子包**。
- **数据类型不进 service 包**：record DTO 归 `dto.<域>`，资源句柄/上下文随所属链路的 dto 或 model 包。

### 4.3 域前缀

同域出现 ≥2 个类时，一律 `Domain + XxxService`：`Authorization*`、`Routing*`、`Provider*`、`Model*`、`Plugin*`、`Moderation*`、`Billing*`、`User*`、`App*`、`Enum*`、`OperationLog*`。

### 4.4 禁止泛化主干名

`System` / `Common` / `Base` / `Manager` / `Util` / `Helper` / `Info` / `Data` / `Object` 不得作为类名主干。

**合规判例（2026-10-24 批次一整改后口径，主干名审查以此为准）**：

| 名称 | 判定 | 理由 |
|---|---|---|
| `SystemController` / `SystemLogItem` | ✅ 合规 | `System` = 系统管理模块域前缀（`ks_modules.code='system'`、左侧菜单「系统管理」），与 `Routing*` / `Billing*` 同级，非泛化主干 |
| `Base36` / `ShortCode` | ✅ 合规 | 精确算法名（同 `Base64`），语义自明 |
| `UtilityProtocolExecutor` / `GatewayUtilityController` | ✅ 合规 | `Utility` = 非生成式辅助协议族（RERANK / MODERATIONS / DECISIONS）的域术语，与 `TextGeneration` / `Embedding` / `Media` 家族并列；枚举集闭合 |
| `AdminAuthorizationInfoResponse` / `MapNumberUtils` | ✅ 合规 | 主干是 `AdminAuthorization` / `MapNumber`，`Info` / `Utils` 是后缀非主干 |

**已整改（2026-10-24）**：`SystemMapper` 删除（`listOperationLogs` 并入 `OperationLogMapper`）；`SystemConverter` → `OperationLogConverter`；`CommonController` → `EnumOptionsController`（路径同步 `/api/common/enums` → `/api/enum-options`）；`CommonMappings` → `SharedMappings`；`GatewayDataService` → `GatewayEntityCacheService`；`GatewayRoutingInfo`（含嵌套 `*Info`）→ `GatewayRoutingContext`（含 `*Snapshot`）。

### 4.6 重命名必检清单（2026-10-15 血泪补充）

改服务类名**不是只改类型引用**。Spring bean 名 = 类名首字母小写，下列引用形式**编译期与单测都拦不住**，必须逐个 grep：

| 形式 | 示例 | 检查命令 |
|---|---|---|
| SpEL `@beanName` | `@OperationLog(oldValueSpel = "@xxxService.snapshot(#p0)")` | `grep -rn 'oldValueSpel\|newValueSpel\|entityName = "@'` |
| `@Qualifier` / `@Resource(name=)` | 本项目暂无 | `grep -rn "Qualifier\|@Resource"` |
| `getBean("...")` | 本项目暂无 | `grep -rn "getBean"` |
| MyBatis XML namespace / resultType | `<mapper namespace="...XxxMapper">` | 改名 Mapper 必同步 XML |
| 前端按类型名/枚举值的匹配 | — | 跨端 grep |

> 真实事故（2026-10-15）：`TestRequestPresetService` → `RoutingTestPresetService` 时漏改 `RoutingTestPresetController` 里的 `@testRequestPresetService...` SpEL，编译与 103 个单测全绿，但操作日志快照会在运行时求值失败。

**反过来，这些字符串不许跟着改**（是业务数据，改了历史数据对不上）：
- `@OperationLog(entityType = "...")`、`module` 等业务串
- `@RequestMapping("/api/...")` 路径（API 契约）
- DB 里已落库的 `code` / `entity_type` 取值

### 4.5 已落地整改（2026-10-15，robin 拍板）

| 动作 | 说明 | 状态 |
|---|---|---|
| `SystemService` → 并入 `OperationLogService` | 读写一家，去掉泛化名；`SystemController` 改注入 `OperationLogService` | ✅ |
| `AdminAuthorizationService` → `AuthorizationAccessService` | 去掉孤例前缀，职责是运行时 API 访问校验（含测试类同步改名） | ✅ |
| `TestRequestPresetService` → `RoutingTestPresetService` | 补域前缀 | ✅ |
| 全链路 `TestRequestPreset*` → `RoutingTestPreset*` | Controller / Entity / Mapper(+XML) / Converter / Dto / Vo / OptionMeta 共 8 个文件同步改名 | ✅ |
| 6 个协作者 → `service.support` 子包 | AuthorizationManageSupport / ProviderCredentialSupport / ResourceTrafficPolicySupport / EnumOptionRegistry / ModelFamilyResolver / PluginReconcileRunner | ✅ |
| `RoutingTestPreparation` / `RoutingTestStreamHandle` → `dto.routing` | 数据类型归位 | ✅ |
| `ProviderCredentialEncryptionMigrator` 删除 | no-op 死代码 | ✅ |
| `DbModerationWordSource` | 随敏感词架构调整被另一会话删除（2026-10-15） | 已随架构移除 |

**门禁（第一批）**：`mvn -pl knot-service,knot-admin -am test` → BUILD SUCCESS / 103 tests, 0 failures。
**门禁（第二批，TestRequestPreset* 全链路统一）**：`mvn -pl knot-dal,knot-service,knot-admin test` → **BUILD SUCCESS / Tests run: 103, Failures: 0, Errors: 0**。
⚠ 第二批首次跑失败：knot-dal `target/classes/mapper/` 里残留旧的 `TestRequestPresetMapper.xml`（namespace 指向已删除的接口）导致 `sqlSessionFactory` 解析失败 —— **改 Mapper 名必须先 clean，否则陈旧 XML 进 target 直接挂上下文**。
⚠ 全量 reactor 当次被并行会话阻塞（`knot-common/EjectionPolicy.java` 处于编译不过的中间态），已用 `-pl knot-dal,knot-service,knot-admin` 定向验证，全量待对方收口后补跑。
**上下文证据**：`AppControllerTest`（`@SpringBootTest`，无 mock）完整启动 `AdminApplication` 成功 → `service.support` 子包在组件扫描范围内，且改名后的 Mapper XML 被 MyBatis 正常解析。

**follow-up**：① 真启 jar 冒烟未跑（并行会话活跃），待补；② 前端 `TestRequestPreset` 类型名 / `TestRequestPresetFormDrawer.vue` / `TestRequestPresetView.vue` 是否同步改名待定（API 路径 `/api/test-request-presets` 与 `entityType` 业务串不动）。

## 5. 编码细节

- **跨模块绑定一律存 code 不存 id**（残留例外：`kx_model_items.logical_model_id`）。
- **权限三表与所有 `ks_` / `kx_` 表结构默认不动**（改错全站 403）。
- **逻辑删除表 + UNIQUE(code) 四必配**：`countByXxxCode` 物理行判；列表 `includeDeleted` + mapper `<if>` + 排序 `is_deleted asc, id desc`；恢复端点 `PUT /{id}/restore`；引用检查覆盖 inner join 方。样板：`kb_models` / `kb_model_pools` / `kb_logical_models`。
- **状态枚举**：删除语义移 `is_deleted`（Integer）；`EntityStatusEnum` / `UserStatusEnum` 并入 `EnabledStatusEnum`。⚠ 前端 truthy（`if (status)`）是头号风险，**0 是合法值**。
- **枚举双源不混用**：代码枚举经 `GET /api/enum-options` 下发；DB 只留 `scope_type` / `discount_type` / `channel` / `model_family`。
- **可空绑定列的 mapper `<set>` 不能用 `<if>` 包**（会写出 `set` 空语法错误）。
- `PageQuery` 加字段要连带改所有 `new PageQuery(null × n)`。
- ⭐ **record 加语义字段优先补便利构造**：新增字段时提供一个旧参数列表的便利构造（新字段取默认值），让既有调用点与测试桩零改动。不要无脑要求所有调用点补参数（噪声大、易漏）。先例 `BillingFact`（加 `extractorFallback` / `usageAbsent` 时保留 6 参构造）。
- **JsonKit 分两档，别用错**：`fromJson` / `toJson` / `parse` 是**吞异常**入口（失败 `log.error` 后返回 `null`）；`fromJsonOrThrow` / `toJsonOrThrow` / `parseOrThrow` 上抛受检 `JsonProcessingException`。
  ⭐ 判据：**如果 `null` 会与「对端没给」混淆，就必须用 OrThrow 档**。计费、鉴权、任何影响正确性的热路径一律 OrThrow。先例 `UsageRawReader.readMap`（曾因吞异常把「响应非法 JSON」与「上游没回 usage」混为一谈，见 60 §8.4）。
- **MapStruct 报 "Unmapped target" 常是真 bug**，不要无脑 `@Mapping(target = "x", ignore = true)`。
- 无历史包袱：**字段变更全链路一次做干净，不留兼容兜底/冗余列，不写回填**；`schema.sql` + `data.sql` 即唯一权威。
- ⭐⭐ **依赖方向红线（DIP，2026-10-24 实证违反）**：`knot-adapter` **禁止依赖 `knot-dal`**，禁止 import `org.chobit.knot.gateway.entity.*`。适配层是协议/计费语义层，只应依赖 `common` 与 `adapter` 内的抽象。
  现状违反：`knot-adapter/pom.xml` compile 依赖 `knot-dal`，`UpstreamRequestContext` / `NormalizedUsageContext` / `UsageNormalizationSupport` / 4 个 `BillingModeCalculator` 等 7 个主类 import entity。整改方向：把适配层真正需要的字段下沉为 `adapter` 自有 model/record，不让 entity 穿透到协议层。
- ⭐ **新增"类型/状态/维度"前先 grep 是否已有枚举承载**；有就必须复用，禁止平行手写字面量。判例：`GatewayErrorTypeEnum` 已含 `TIMEOUT_ERROR`，而 `ErrorMsgDto` 手写 8 个错误码字符串——这是 B4 级违规（详见 80-code-review §3）。

## 6. 异常与鉴权

- `BusinessException` **无 `@ResponseStatus`** → 返回 HTTP 200 + `success:false`，前端按业务码处理。
- admin 接口**默认拒绝**：无权限绑定即 403；**新增接口必须补权限绑定**。
- Spring 6.1+ `NoResourceFoundException` 转 404。
- 删除端点成功返回 200 + 空响应体（验证删除用"二次删除确认法"）。
- ⭐⭐ **错误出口单一来源（2026-10-24 立规）**：同一异常的 `type` / `code` / `httpStatus` / `stage` 必须由**唯一映射**决定，HTTP 响应体、Kafka 流量事件、结构化日志三个出口不许各写一棵异常类型树。
  - 权威来源优先复用已有枚举（`GatewayErrorTypeEnum`），不要新造平行抽象。
  - 改 `type`/`code` 取值时，**必须同步 grep 下游派生函数**（如 `GatewayTrafficMsgDto.statusOf()`、`httpStatusOf()`），否则新值会静默落 `default`。
  - 判例：`TimeoutException` 在 `GatewayExceptionHandler:200` 是 `504/timeout_error`，在 `ErrorMsgDto:46-91` 却落 `500/upstream_error`——数仓把网关总超时记成"上游故障"。
- **恒等抽象禁止**：`normalize(x)` / `canonical(x)` / `convert(x)` 这类名字宣称做了变换，方法体却 `return x` / `return this`。要么实现变换，要么改名（如 `identity()`）或删除。判例 `ModelApiProtocolEnum.canonical()`。
  ⚠ 例外（**不算违规**，审查时勿报）：建造者/流式 API 的 `return this`、接口默认实现的"使用默认值"、测试替身的链式返回。
- **审计快照模式（2026-10-24 立规，同日收口）**：`@OperationLog(oldValueSpel = "@xxxService.snapshot(#p0)")` 的
  Map 版快照方法**一律经 `AuditSnapshotSupport.capture(id, loader)`**（`service.support`，静态纯函数）：
  ```java
  public Map<String, Object> xxxAuditSnapshot(Long id) {
      return AuditSnapshotSupport.capture(id, key -> JsonKit.toMap(getById(key)));
  }
  ```
  骨架统一为「id 为空 → `null`；加载抛 `BusinessException`（含实体不存在、重复删除）→ `null`」——
  目标已删除时快照为 null 属合法语义，**不要**给它加日志噪音。loader 里只放「加载 + 序列化」：
  含已删除查询、`toMaskedMap` 脱敏、后处理（如 `authConfig` 脱敏）都放这里。
  ⚠ **只吞 `BusinessException`**：其它 RuntimeException 必须冒出去——吞掉等于把转换器缺陷洗成「快照为空」。
  ⚠ 实测口径（2026-10-24）：Map 版快照共 **14 处**已全部切换（旧文档写「8 个 Service」是未实测的估计）；
  返回 Entity 的 authorization 域 5 处不走本骨架（无异常吞噬语义，形态不同，勿套用）。
  新增 Service 需要快照时**不得再手抄** try/catch 骨架。

## 7. 测试

- ⚠⚠ **JDK 25 + Mockito inline 不能 mock 任何具体类**（Byte Buddy 无法 instrument 类层级）。测试替身用**子类覆写**（先例 `CountingSnapshotCache`）；mock 接口不受影响。
- 先写复现测试再改代码；改完反向验证。
- ⭐ **测试桩要传真实依赖，不为测试给生产代码加 null 防御**：桩里传 `null` 撞 NPE 时，修桩（换真实实现或最小可用实例），**不要**在生产代码加 `if (x == null)` 兜底——那等于把桩的缺陷固化进生产语义。先例 `UpstreamWebClientEndToEndTest` 的 `UsageExtractorRegistry` 桩改用真实 `UsageExtractorCatalog`。

## 8. Lombok 应用规范（2026-10-15 立规，robin 拍板）

**可用 Lombok 消除的样板**：

- **数据载体（entity / dto / vo / `*Properties` 配置类）**：用 `@Getter` / `@Setter` / `@Data` / `@EqualsAndHashCode` / `@ToString`。`@ConfigurationProperties` 类用 `@Getter @Setter`（**不**用 `@Data`，避免无谓的 `equals/hashCode/toString`）。
- **日志**：一律 `@Slf4j`（Lombok `lombok.extern.slf4j.Slf4j`），**不要**手写 `private static final Logger log = LoggerFactory.getLogger(Xxx.class);`。生成的字段名即 `log`，引用不变。
- **全参构造器**：纯赋值构造器（构造体只有 `this.x = x;`）可用 `@AllArgsConstructor` / `@RequiredArgsConstructor` 取代。

**其他约束**：

- Lombok 注解写在类声明上方；删除手写样板后，**清理不再使用的 `import`**（`org.slf4j.Logger` / `org.slf4j.LoggerFactory` 等），import 顺序保持 `package` 之后首组。
- 改动后跑门禁：`mvn -f knot-server/pom.xml test`（后端）+ `cd knot-front && npx vue-tsc --noEmit && npx vite build`（前端），确认零回归。

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

### 1.1 模块 ↔ 顶级包映射（2026-10-25 包结构迁移，robin 拍板）

一个模块 = 一个顶级包，**模块名与顶级包不再同名**，查代码位置按此表：

| 模块 | 顶级包 | 说明 |
|---|---|---|
| `knot-common` | `org.chobit.knot.common` | 通用原语（api/error/model/constants/crypto/pricing/util/annotation/auth/moderation） |
| `knot-plugin-api` | `org.chobit.knot.plugin` | 插件契约（根包，只有接口/枚举/上下文） |
| `knot-plugin` | `org.chobit.knot.plugin.<子包>` | 插件实现（`builtin` / `moderation` / `components` / `runtime` / `gateway`） |
| `knot-adapter` | `org.chobit.knot.adapter` | 上游协议适配单域，见 §4.7 |
| `knot-dal` | `org.chobit.knot.dal` | `entity` + `mapper`（XML 在 `knot-dal/src/main/resources/mapper`） |
| `knot-service` | `org.chobit.knot.svc` | 数据访问（`svc.service` 的 `XxxService`）、MapStruct converter（含 `service.support`）、两 app 共用的业务服务；**全项目唯一能碰 Mapper 的模块**，见 §3.1。⚠ 只有一个 app 用的编排留在该 app，判据见 §3.2 |
| `knot-admin` | `org.chobit.knot.admin` | 管理端启动类 + controller/security/rw/config |
| `knot-gateway` | `org.chobit.knot.gateway` | 网关启动类 + controller/routing/store/traffic/... |

**启动类必须在 `scanBasePackages` 上提到 `org.chobit.knot`**（两个 app 都是）。原因：组件分布在 `.admin`/`.svc`/`.adapter`/`.plugin` 四个**平行**顶级包，而 `@SpringBootApplication` 默认只扫启动类所在包及子包——不上提则 svc/adapter/plugin 的 bean 一个都注册不上。上提到 root 而非逐项列举，是因为 root 包名不随模块增删漂移。

⚠ **字符串形式包名是包迁移的唯一致命风险类型**（编译期不报错、运行期才炸）。迁移后实测踩到的四处：

| 形式 | 症状 | 修法 |
|---|---|---|
| `@MapperScan("...")` | 一个 Mapper 都注册不上，编译全绿 | 改 `@MapperScan(basePackageClasses = AppMapper.class)`，**类引用参与编译校验** |
| `springdoc.packages-to-scan` | `/api/docs/openapi.json` 变成空文档 | 明确写 `org.chobit.knot.admin.controller`，由冒烟核对 paths 数量 |
| 测试里写死的源码路径 / 包名字面量 | 扫描目录不存在 → 断言「空跑也通过」 | 由测试自身包名反推（判例：`MapperStatementCoverageTest`），或保留「防空跑」守卫断言 |
| 测试 import MapStruct 生成类 | 生成类随 `@Mapper` 接口所在包走，跨模块引用即断编 | 只引接口 + `Mappers.getMapper(XxxConverter.class)` |

> ⚠ 反向纪律：包迁移后必须跑一次「全量 `package` 声明 vs 磁盘路径」比对 + 跨模块重复 FQCN 检查。两条都是纯静态可判，一条命令出结果，不要靠人眼。

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

### 3.1 数据访问的归属：只有 `knot-service` 能碰 Mapper（2026-10-28 立规，robin 拍板）

⭐⭐ **`knot-admin` / `knot-gateway` 不得注入、调用任何 `org.chobit.knot.dal.mapper.*`**。
数据库读写与事务边界一律收在 `org.chobit.knot.svc`（`knot-service`），
两个 app 通过 svc 暴露的接口/服务间接用库。

⚠ **本条只约束「谁碰表」，不约束「编排放哪」**：编排按 §3.2 跟随使用方。
2026-10-29 的判例里，`AsyncTaskService`（碰表）在 svc、`AsyncTaskRunner`（编排）在 gateway，
两者分居两个模块而互不违反——把它们塞进同一层才是违规（Mapper 进 app，或 gateway 专用语义进共享层）。

**判据不是「代码放哪好看」，是「同一件事有几个入口」**：

- Mapper 散在 app 层时，「这张表谁能改」没有单点答案；加一张表要先决定放哪个模块，
  而两个模块都能放 → 迟早两边各写一份（判例：`ScheduledTask` 的 DTO 在 svc、实现却在
  admin 直连 mapper，读代码的人要跨两个模块才知道一次任务执行写了什么。
  ✅ 2026-10-29 已改完：数据访问收进 `svc.service.ScheduledTaskService`，
  4 个原本各注入一次 mapper 的类全部改走它）。
- svc 是 admin 与 gateway **共用的层**（两个 app 的 pom 都已依赖它）。放在这里，
  「管理端改配置」与「网关读配置」走的是同一份代码，口径不可能漂移；
  放在各自的 app 里，两边各写一遍只是时间问题。

配套纪律：

- ⭐ **svc 对外出自己的模型（record DTO），不裸传 `dal.entity`**：把实体原样吐给 app，
  红线就从「禁止碰 Mapper」退化成「禁止碰 Mapper 但可以碰 Entity」，
  等于给 dal 留了后门——下次有人把实体塞进另一个 mapper 调用，没有编译信号。
  svc 内部 58 个文件用 entity 是允许的：**边界在「跨模块出去的那一刻」**。
- ⭐ **状态/语义枚举：一方写库就跟着写库那方走；三方都用就进 `common`**。
  `AsyncTaskStatusEnum` 最终住在 `knot-common`：svc 用它的 `inFlightCodes()` 拼 SQL 谓词，
  gateway 编排用它判迁移，admin 展示用它出文案——放任何一端都会逼另两端反向依赖或复制，
  而复制一份就是「同一状态集两份定义」，必然漂移。
- **实体转换收敛到一个 Converter**：全模块只有它允许 import `dal.entity`，
  于是「有没有实体漏出去」是一条 grep 就能出结果的事。
  加实体字段时 Converter 必须同步——漏一个字段编译不报错，
  表现是「新字段读出来恒为 null」，靠逐字段断言钉住（判例 `AsyncTaskConverterTest`）。
- **启动类的 `@MapperScan(basePackageClasses = AppMapper.class)` 是唯一豁免**：
  它注册 bean，不执行 SQL；且规范强制要求用类字面量形式（§1.1），必然要 import 一个 mapper。

⭐ **这条红线由测试守住，不停在文档里**（两个 app 各一份，别只守一边）：
`knot-gateway/.../LayeringGuardTest` 与 `knot-admin/.../LayeringGuardTest` 都扫描 `src/main/java`
断言「除豁免项外无 `dal.mapper` import」，并带防空跑守卫。
admin 那份还额外把 9 个仍直接 import `dal.entity` 的 controller 列入**只许降不许升的棘轮清单**
（新增一个就红，清掉一个也红并要求删名单）。
理由是纯静态可判的约束都不该靠 review 记住——第一个赶时间的人破例之后，
第二个人看到「这里已经有一处了」就会再加一处。

⚠ **存量违规用「会自我失效的名单」登记，不用注释 TODO**：名单里每一项都被另一条断言
要求「现在确实还在违规」，改干净了那条就红并提示删名单。TODO 会静默腐烂，这条会响。
当前登记项：`GatewayEntityCacheService`（10 张配置表预热成本地缓存，改动面大、单独排期）。
⚠ 判据当前只禁 `dal.mapper`、不禁 `dal.entity`，是因为上面这条存量违规还没清；
**清完之后判据应当收紧到整个 `org.chobit.knot.dal`**。

### 3.2 编排跟随使用方：数据访问归 svc，编排归用它的 app（2026-10-29 三轮定稿，robin 拍板）

⭐⭐ **「谁能碰表」与「这段编排放哪」是两个问题，不许用一个答案糊过去**。分开答：

| 问题 | 判据 | 结论 |
|---|---|---|
| 谁碰 Mapper / 表 | 保留唯一入口（见 §3.1） | **只能 `knot-service`** |
| 编排放哪 | **有几个 app 用** | 两端共用 → `svc`；只有一个 app 用 → 留在那个 app |

**为什么「只有一个 app 用」就该留在 app**：把只服务一端的编排放进共享层，共享层会长出那一端的
专用语义（traceparent 重放、Kafka 事件、渠道凭据、计费管线），另一个 app 永远用不到，
却要跟着它一起改、一起发版。

⚠ **判例（2026-10-29，一天内改过三次方向；过程比结论更值得记）**：

1. `AsyncTaskService`（编排）先被抽进 `svc.async`，理由是「svc 只剩 Store 就是 DAO」；
2. 随即搬回 `knot-gateway`——因为量了一个事实：**`knot-admin` 对异步任务能力的引用是 0 处**，
   全部消费方只有 gateway。判据由此从「凭感觉分层」改成「数消费方」；
3. 再一轮回头看 svc 里剩下的四个 `Store`：`AsyncTaskProfileStore` 是 17 行接口 + 24 行实现、
   方法体一行转发，`ScheduledTaskStore` 与 `ScheduledTaskRunStore` 甚至**注入同一个 Mapper**。
   于是四个 `Store` + 四个 `Db*Store` 收成两个 `@Service`。

最终形态：

- `svc.service`：`AsyncTaskService`（`kb_async_tasks` 读写）/ `AsyncTaskProfileService`（档案读取）/
  `ScheduledTaskService`（定义 + 流水）——**直连 Mapper，与 `BillingService` 同构**
- `svc.dto.async` / `svc.dto.system`：数据载体（`AsyncTask*Dto` / `ScheduledTask*Dto`，record）
- `svc.converter`：`AsyncTaskConverter` / `ScheduledTaskConverter`（与既有 15 个 Converter 同包）
- `gateway.async`：`AsyncTaskRunner`（编排）/ 状态机四件套 / `AsyncTaskSpecRules` / 两个端口 /
  `AsyncTaskContext`（提交快照）
- `gateway.archive`：`ArchiveStore` / `ArchiveAsset`（不碰库、admin 零引用，只被终态那一步调用一次）
- `common.constants.enums`：`AsyncTaskStatusEnum`（三方共用：gateway 编排、svc 的 SQL 在途态谓词、admin 展示）

⭐⭐ **判据：单实现接口 = 纯转发壳，不抽**。抽接口的前提是「要换实现」——全仓其余 `*Store`
（Caffeine/Redis、Passthrough/S3）都是 1 接口 + ≥2 实现；没有第二个实现、也没有换的预期时，
接口只是把「一个 Mapper 上的方法」按表切成两个类型，外加两个 `Db*` 壳。svc 的 30 个 `XxxService`
（含 692 行的 `BillingService`）全部直连 Mapper，`AsyncTaskService` 与它们同构，
才不会让「svc 里怎么访问数据」出现两种写法。

⚠ 收成同名类会撞车，改名是收敛的必然副产品：`svc.async.AsyncTaskService` 与 gateway 的编排类
同名 → 编排类叫 `AsyncTaskRunner`（它跑一轮、推进一行，不持有任何表）；admin 原有的
`ScheduledTaskService`（管理面校验 + Quartz 重排期）→ `ScheduledTaskAdminService`。
**svc 侧拿正名、编排侧改名**——因为 svc 侧那个才是「XxxService 就该直连表」的通例。

**判断顺序**：先数消费方（`grep` 各 app 的 import），再看它依赖什么。别凭「类名像服务」拍。

判据细则：

- **业务判断**（什么算成功/失败、要不要计费、退避多久、终态怎么落）→ 跟编排走，不单独留一份。
- **部署决策**（跑不跑定时、并发几路、批量多大）→ 各 app，且**用值对象传进去**而不是让另一层读配置前缀
  （判例 `AsyncTaskRunPolicy`；否则 `knot.gateway.*` 会变成两个 app 的隐式契约）。
- **传输细节**（HTTP 客户端、签名、URL 拼装）→ `adapter`，编排层只定义端口
  （判例 `AsyncTaskQueryPort` / `AsyncTaskSettlementPort`）。
- **纯部署能力**（对象存储桶、凭据、区域）→ 留在 app，哪怕它看起来像「业务口径」。
- **共享词汇**（状态码、枚举）→ 三方都用时进 `common`，不能放在其中一端的包里：
  否则另两端只能「反向依赖」或复制一份，后者是同一状态集两份定义，必然漂移。

⚠ 端口化的收益不只是解耦，**测试也不用再继承带 5 个构造参数的网关类**（判例：
`AsyncTaskRunnerTest` 的 `RecordingQueryClient` 从 extends 改成 implements 之后就干净了）。

⚠ 判例 `AsyncTaskPoller` → `AsyncTaskRunner`（2026-10-29）：轮询器原本 496 行，
判定的、计费的、转储的、调度的全在一处。拆完只剩「什么时候扫 + 同时扫几个」约 200 行，
测试也随之拆成 `AsyncTaskRunnerTest`（编排，17 例）/ `AsyncTaskPollerTest`（节拍，7 例）
——**能力搬家时测试必须跟着搬，一条都不能删**。两者最终同在一个模块，但这不改变结论：
**拆的判据是抽象层级，不是模块**。

⚠ **首版条文（同日被推翻）写的是「编排必须进 svc」，教训不是「别分层」，而是判据要可验证**：
「svc 退化成 DAO」是感受，「admin 引用 0 处」是数字。可验证的判据才能被一条命令复查。

## 4. 命名纪律（2026-10-15 立规，robin 拍板）

### 4.1 角色后缀白名单

新增类**只能**用以下后缀，不许自造：

`Service` `Support` `Source` `Resolver` `Registry` `Runner` `Provider` `Migrator`
`Controller` `Converter` `Mapper` `Entity` `Dto` `Vo` `Item` `Request` `Response`
`Properties` `Configuration` `Constants` `Enum` `Exception` `Cache` `Store`
`Handler` `Dispatcher` `Descriptor` `Context` `Sink` `Writer` `Extractor` `Calculator` `Policy` `Template` `Publisher`（2026-10-18 robin 拍板 `KafkaPublisher` 补入）

> 判例（2026-10-23）：双视图载体命名评审**否决** `UsageExtractResult`——`Result` **不在白名单**；改用 `UsageExtractItem`（`Item` 在白名单内）。
> 拿不准后缀时按此判例办：先查白名单，**不在即换名**，不要为了顺口自造。

⚠ 判例（2026-10-29）：`AsyncTaskRecord` / `AsyncTaskProfileRecord` / `AsyncTaskDraft` / `ScheduledTaskRecord` /
`ScheduledTaskRunRecord` 全数改名——**`Record` 与 `Draft` 都不在白名单**。诱因值得记：把 **Java 的 `record` 关键字
当成了角色**。两者是两件事——`Dto` 是角色，`record` 是实现方式；项目里 46/50 的 Dto 本身就是 record。
改名后为 `AsyncTaskDto` / `AsyncTaskProfileDto` / `AsyncTaskDraftDto` / `ScheduledTaskDto` / `ScheduledTaskRunDto`
（`Dto` 作后缀、`Draft` 降为中缀，同 `RequestMsgDto` 的先例）。
⭐ **自造的包会替自造的后缀打掩护**：这批类当时住在新建的 `svc.async` / `svc.schedule` 里，
与既有 `dto.*` / `converter.*` 无从对照，于是 5 个不合规后缀躲过了 4 轮 review。
判据：**全仓同名后缀一起数**——`find . -name "*Xxx.java" | wc -l`，只有自己造的那几个就是信号。

### 4.2 service 包纪律

- `org.chobit.knot.svc.service` 包**顶层只允许 `@Service` 业务服务**（`XxxService`）。
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

### 4.7 UpstreamAdapter 命名与职责条款（2026-10-25 立规，robin 拍板）

**knot-adapter 模块 = `org.chobit.knot.adapter` 单域**（2026-10-25 包结构整理，robin 拍板）：顶级域只留 `adapter` 一个，其余全部收为其子包——

| 子包 | 内容 |
|---|---|
| `adapter.protocol` | 协议适配族：`UpstreamAdapter` 接口 + 9 实现 + `UpstreamAdapterCatalog` / `UpstreamAdapterDefinition` |
| `adapter.support.<供应商>` | 跨适配器复用的供应商级共享支持：`support.aws`（Converse / Endpoint / SigV4）、`support.anthropic`（Messages 请求字段搬迁） |
| `adapter.auth` | 上游鉴权策略族：`UpstreamAuthApplier` + 4 实现 + Catalog / Definition + `AuthConfigSupport` |
| `adapter.upstream` | 上游调用上下文：`UpstreamRequestContext` / `UpstreamAccess` / `UpstreamSignableRequest` |
| `adapter.model` / `adapter.model.ref` | 计费/用量数据模型；跨域引用句柄 `*Ref`（`ModelRef` / `ProviderAccountRef` / `ModelApiBindingRef` / `BillingRuleRef`） |
| `adapter.usage` / `.calculator` / `.extractor` | 用量域：归一化行为 + 计费计算器族 + 用量提取器族 |

⚠ **同名子包 `model` 仍遍布各模块**（`common.model` / `gateway.model` / `svc.model` / `adapter.model`）：**全限定名已各不相同**（2026-10-25 迁移前 `org.chobit.knot.gateway.model` 曾同时存在于 4 个模块，靠 classpath 同名包可见性才能用短名，`ProxyResult` 因此断编）。现在跨模块引用这些包**仍必须显式 import 全 FQCN**——理由从「躲重名」变成「短名不可读、看不出住哪个模块」，且**绝不允许再让同一个 FQCN 出现在两个模块里**（包迁移后须跑一次跨模块重复 FQCN 检查，见 §1.1）。

上游适配器接口为 `UpstreamAdapter`（`adapter.protocol` 包），是**双向角色**：请求适配（`resolvePath` / `buildRequestBody` / `resolveContentType`）与响应适配（`handleResponse` 整包 / `streamAdapter` 流式）由同一实现持有。**不存在**「只适配请求」或「只适配响应」的拆分角色——一次协议转换的两个方向必须同源，拆开只会让两侧各漂移一份口径。

⚠ **协议转换类适配器必须同时覆写 `handleResponse` 与 `streamAdapter`**：默认透传只服务 `PassthroughUpstreamAdapter`（同协议）。只做请求侧转换会让客户端拿到上游协议形状（判例：四个泛化适配器 2026-10-25 前只有 `buildRequestBody`，chat.completions 请求收到的是 Anthropic JSON），且**计费提取吃的是转换后产物**——错形状会连带算错账。响应转换的约定：非目标形状（错误体、非 JSON）一律原样透传，绝不改写成成功形状；usage 必须双向换算。

命名规则：

1. **结构** `<供应商>+<原协议>+<新协议>+UpstreamAdapter`，如 `AwsBedrockChatCompletionsToConverseUpstreamAdapter`；code = 类名去后缀 SCREAMING_SNAKE 化（`AWS_BEDROCK_CHAT_COMPLETIONS_TO_CONVERSE`），即 `kb_model_api_bindings.request_adapter` 的绑定值（列名不变）。
2. **泛化转换免供应商段**：不隶属单一供应商的协议转换（如 ChatCompletions→Messages）直接 `<原协议>To<新协议>UpstreamAdapter`。
3. **透传豁免**：`PassthroughUpstreamAdapter`（code `PASSTHROUGH`）是唯一多协议适配器；**除透传外，一个适配器只服务一种协议**（`protocol()` 返回单元素集）。
4. **共享逻辑抽 Support，按归属分两层**：**无供应商语义的通用原语**收 `adapter.support.XxxSupport`（`RequestFieldSupport.move` 字段搬迁、`PayloadMapSupport` 的 `cast`/`nested`/`firstPresent`/`stringOf`/`text`/`putLong`、`PayloadTextSupport.textOf` 跨协议 content 折文本、`SseLineSupport` SSE 行语法读写——**SSE 是 HTTP 传输层语法，与供应商和 AI 协议都无关**，故住 support 根而非 `support.anthropic`）；**供应商特有语义**收 `adapter.support.<供应商>.XxxSupport`（如 `support.aws.AwsBedrockConverseSupport` Bedrock 全族、`support.anthropic.AnthropicMessagesRequestSupport` 的 `max_tokens` 兜底与工具字段换形编排、`AnthropicMessagesResponseSupport` 的整包双向转换、`AnthropicToolSupport` 的工具语义、`AnthropicStreamSupport` 的流式双向转换）；适配器类只做协议分发与口径注释。⚠ **通用能力不住供应商专用类**（判例：`move` 曾住 `AnthropicMessagesRequestSupport`、`textOf` 等 6 个读写原语曾住 `AwsBedrockConverseSupport`，均 2026-10-25 拆出）；⚠ **跨类逐字重复是归属错误的直接后果**——见到两份逐字相同的私有实现，先怀疑其中一份住错了地方，而不是先合并（判例：`textOf` 住进 AWS 专用类后，`AwsBedrockMessagesToConverseUpstreamAdapter` 被迫复制了一份私有副本；authConfig 读取力学也曾有 `AuthConfigSupport.value` 与 `AwsBedrockEndpointResolver.read` 两份，2026-10-25 合并至 `PayloadMapSupport.text`）；⚠ **纯转发壳同样是冗余**——委托层不带知识增量时删掉，调用方直调（判例：`value` 合并后曾短暂保留为一行委托，同批删除）；⚠ **同一张词表只能有一个所有者**——`stop_reason`↔`finish_reason` 表归 `AnthropicMessagesResponseSupport` 并以 `public static` 暴露给流式复用，流式侧再抄一份就是跨类重复（判例：2026-10-25 `AnthropicStreamSupport` 直接调那两个方法，不复制映射表）；⚠ Support 静态方法必须 `public`——从同包子类化细节改为跨子包调用时，包私有方法会静默变成编译错误（判例：本次整理 `AnthropicMessagesRequestSupport` 两方法补 public）。

流式契约：`streamAdapter(context)` 与 `handleResponse` 按执行路径分工（前者只服务流式逐行、后者只服务整包），两条路径的转换产物都同时喂客户端转发与用量提取（同构：提取吃的永远是转换后的数据）。默认透传实现零解析、零序列化。**响应适配是协议正确性，不受 `appendUsage`（计费注入开关）gate**——行一律过适配器，是否提取用量才看开关。

流式钩子返回 **`StreamLineAdapter`（行级、0..N 行）而非 `data` 载荷级**，三个理由缺一不可：
1. **抑制**：Anthropic 的 `event:` 行对 OpenAI 客户端是噪音、`ping` 事件无对应物，必须能「不发这一行」；
2. **合成**：反向（chat chunk → Messages）必须为一个 `data` 行补出配套的 `event:` 行，甚至一个上游事件合成多个下游事件（`message_start` → `response.created` + `output_item.added` + `content_part.added`）；
3. **状态**：chunk id 全程一致、`message_start` 只合成一次、tool 块序号连续——这些状态**挂不得在适配器上**（适配器是单例 Bean，会被并发请求互相污染），故 `streamAdapter` **每请求返回新实例**。
⚠ 返回列表语义：空列表 = 抑制、单元素 = 改写、多元素 = 合成。⚠ 空白行（SSE 事件分隔符）**必须保留**，丢了两个事件会黏成一条。⚠ `knot_extend` 的插入位置按**适配后**的 `[DONE]` 判断——协议转换会把上游收尾事件合成 `[DONE]`，按原始行判断会插在客户端已不认的位置。

工具语义（`tools` / `tool_choice` / `tool_calls`↔`tool_use`↔`tool_result`）是**跨请求声明、消息载体、响应产物三类载体的同一份语义**，必须收在一个 Support 里（`support.anthropic.AnthropicToolSupport`），禁止三个适配器各写一段。三条硬约束：① **形状不认识就不动**（返回 `null` 表示保留原值），不猜第三方自定义形状；② **不丢内容**——消息里出现未知 content 块类型时整条原样保留，半懂比不懂更危险；③ **两协议工具参数编码不同**：Anthropic `input` 是对象、OpenAI `arguments` 是 JSON 文本，反向解析失败退化为空对象（`input` 必须是对象，退化成空好过整个请求 400）。⚠ OpenAI 侧 `tools` 有**两种规范形状**（chat 的 `{type,function:{...}}` 包装与 Responses 的扁平 `{type,name,parameters}`），识别判据是「读得到 `name`」，不是「有没有 `function`」。

配套命名（同批落地）：目录 `UpstreamAdapterCatalog` / `UpstreamAdapterDefinition`（随接口在 `adapter.protocol`），网关侧 `upstream.request.UpstreamAdapterRegistry`（未知 code 或留空回落透传）。

## 5. 编码细节

- **跨模块绑定一律存 code 不存 id**（残留例外：`kx_model_items.logical_model_id`）。⚠ **反向澄清（2026-10-26）**：**同一聚合内的从属明细按主体主键绑定**——判据是「是否跨聚合 / 能否独立存在」，不是「是不是两张表」。先例 `kb_billing_rule_versions.rule_id → kb_billing_rules.id`：版本是规则主体的从属明细，此前用 `rule_code` 关联，导致每条规则恒有版本行 → 改码守卫恒判「已引用」，规则编码实际不可改；且改码时按新码查不到旧版本行，会另起一条 `v1` 并把旧行留成孤儿。用 code 承载内部外键 = 把「可改的业务标识」升级成主键语义，必被改码反向咬住。
- **权限三表与所有 `ks_` / `kx_` 表结构默认不动**（改错全站 403）。
- **逻辑删除表 + UNIQUE(code) 四必配**：`countByXxxCode` 物理行判；列表 `includeDeleted` + mapper `<if>` + 排序 `is_deleted asc, id desc`；恢复端点 `PUT /{id}/restore`；引用检查覆盖 inner join 方。样板：`kb_models` / `kb_model_pools` / `kb_logical_models`。
- **状态枚举**：删除语义移 `is_deleted`（Integer）；`EntityStatusEnum` / `UserStatusEnum` 并入 `EnabledStatusEnum`。⚠ 前端 truthy（`if (status)`）是头号风险，**0 是合法值**。
- ⭐⭐ **数字列的 `<if test>` 只判 `!= null`，禁写 `x != ''`（2026-10-29 第三次撞车后立规）**：OGNL 把 `''` 当数字 0 比较，于是 `Integer status = 0` 时整个 `<if>` 被判 **false 并静默丢弃**——筛选返回全量、不报错、不告警、日志无痕，前端还以为筛选生效了。`!= ''` 只对 `String` 筛选列（`keyword` / `xxxCode`）正确，那是挡空串的，别一起删掉（删了「传空串」会变成「按空串精确匹配」）。
  - 判例连撞三处同一形态：`ScheduledTaskMapper`（`listTasks` / `listRuns`）、`ModelMapper`、`ModelPoolMapper`——而 0 恰是「停用 / 运行中」这类最常用筛选，必踩。
  - 守门 **`MapperBlankStringPredicateTest`**：全库扫 XML 的 `<if test>`，按接口 `@Param`（退到实体同名字段）反射出**真实入参类型**，非 `String` 即失败；**判不出类型也失败**，不许跳过（跳过正是这类 bug 活下来的方式）。
  - 单点测试不够用：同一事实散在 70+ 个 XML 里，逐个补必漏 → 这类「口径一致性」问题写**全库扫描判据**，不写单点。
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
- ⭐⭐ **判断分支禁止裸 code 字面量（2026-10-24 立规，后端已清零）**：凡拿枚举 code 做 `equals` / `==` / `===` 判断，一律走 `XxxEnum.YYY.code()`，不许写 `"YYY"`；也**不许为已有枚举再造一个常量 Support/Constants 类**（判例：3.2 曾自造 `RoutingTargetTypeSupport` 重复 `RouteTargetTypeEnum`，已删）。
  - ⚠⚠ **改前必须查调用方确认码表归属**：13 个 code **跨多族同名**——`SUCCESS`(OperationLog/RoutingTest/ScheduledTaskRun)、`FAILURE`(OperationLog/ScheduledTaskRun)、`MODEL`(RouteTargetType/TrafficResourceType)、`UPSTREAM_ERROR`(GatewayErrorType/**ProxyErrorCode**)、`REQUEST`(BillingMode/ModerationScope)、`CUSTOM`(4 族)、`IMAGE`/`VIDEO`/`AUDIO`/`EMBEDDING`/`RERANK`(BillingMode/ModelType)、`DISABLED`(EnabledStatus/PluginPackageStatus)。选错族**字符串相同、编译与单测全绿**，将来某族改名才静默失效。
  - 判例：`OutageClassifier.countableByCode()` 判的是 `GatewayUpstreamException.code()`，属 **`ProxyErrorCodeEnum` 族**（构造点 `AbstractUpstreamProtocolExecutor:115/264`），与 `GatewayErrorTypeEnum.UPSTREAM_ERROR` 只是同名。
  - 扫法（**三次翻车的定稿口径**）：① 正则必须**双向**——`equals("X")` 与 `"X".equals(...)` 都要覆盖，只写前者会漏（首次扫描就漏了 `BillingFactChecks`）；② 候选 code 取「**枚举常量名** ∪ **构造第一个字符串参数**」的**并集**——只取其一必错：`PricingPlanEnum.code()` 返回 `name()`、第一个参数是 **label**（"固定价"），`BillingUnitEnum` 常量名 `ONE_K_TOKENS` 但 code 是 `"1K_TOKENS"`，`GatewayErrorTypeEnum` code 是**小写**（`upstream_error`）。③ 赋值处（`status: "ERROR"` / `unit: "1K_TOKENS"`）不在比较口径内，收口时须另行 grep。
  - 前端同名规则见 `20-frontend-vue.md`。

## 6. 异常与鉴权

- `BusinessException` **无 `@ResponseStatus`** → 返回 HTTP 200 + `success:false`，前端按业务码处理。
- admin 接口**默认拒绝**：无权限绑定即 403；**新增接口必须补权限绑定**。
- Spring 6.1+ `NoResourceFoundException` 转 404。
- 删除端点成功返回 200 + 空响应体（验证删除用"二次删除确认法"）。
- ⭐⭐ **错误出口单一来源（2026-10-24 立规，同日由 3.8 落地）**：同一异常的 `type` / `code` / `httpStatus` / `stage` 必须由**唯一映射**决定，HTTP 响应体、Kafka 流量事件、结构化日志三个出口不许各写一棵异常类型树。
  - **唯一映射 = `GatewayErrorDescriptor.of(Throwable)`**（gateway `model` 包）。新增异常类型只改它一处；两侧出口都得取它，不许在出口侧写死状态码或 type。
  - 阶段码走 `GatewayErrorStageEnum`（与 `GatewayErrorTypeEnum` 正交：type 答「什么性质」，stage 答「链路哪一段」）；只有数仓出口消费 stage。
  - 权威来源优先复用已有枚举（`GatewayErrorTypeEnum`），不要新造平行抽象。
  - 改 `type`/`code` 取值时，**必须同步 grep 下游派生函数**（如 `GatewayTrafficMsgDto.statusOf()`、`httpStatusOf()`），否则新值会静默落 `default`。
  - 例外：Spring 框架异常（`ServerWebInputException` 等）只出现在 HTTP 入口、不进数仓，不存在两棵树问题，由 handler 自处理即可。
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
- **构建器**：参数多且同类型相邻的载体（`record` / DTO / 计费用量模型）用 `@Builder`，调用方走链式；**归一逻辑留在规范构造器 / 紧凑构造器里**，不搬进 builder——`build()` 调用的仍是规范构造器，搬走等于让 `empty()` 与 builder 两条路径口径分叉。

**其他约束**：

- Lombok 注解写在类声明上方；删除手写样板后，**清理不再使用的 `import`**（`org.slf4j.Logger` / `org.slf4j.LoggerFactory` 等），import 顺序保持 `package` 之后首组。
- 改动后跑门禁：`mvn -f knot-server/pom.xml test`（后端）+ `cd knot-front && npx vue-tsc --noEmit && npx vite build`（前端），确认零回归。

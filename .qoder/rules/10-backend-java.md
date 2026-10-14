---
trigger: glob
description: knot 后端 Java 规范：模块依赖红线、分层、命名纪律、编码细节、异常与测试
glob: knot-server/**/*.java
---

> 本文件由 `docs/standards/generate-tool-rules.py` 自动生成，**勿手工编辑**。权威源：`docs/standards/rules/rules/10-backend-java.md`。

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
`Handler` `Dispatcher` `Descriptor` `Context` `Sink` `Writer` `Extractor` `Calculator` `Policy` `Template`

### 4.2 service 包纪律

- `org.chobit.knot.gateway.service` 包**顶层只允许 `@Service` 业务服务**（`XxxService`）。
- 非 `@Service` 的协作者组件（Support/Source/Resolver/Registry/Runner/Migrator）一律进 **`service.support` 子包**。
- **数据类型不进 service 包**：record DTO 归 `dto.<域>`，资源句柄/上下文随所属链路的 dto 或 model 包。

### 4.3 域前缀

同域出现 ≥2 个类时，一律 `Domain + XxxService`：`Authorization*`、`Routing*`、`Provider*`、`Model*`、`Plugin*`、`Moderation*`、`Billing*`、`User*`、`App*`、`Enum*`、`OperationLog*`。

### 4.4 禁止泛化主干名

`System` / `Common` / `Base` / `Manager` / `Util` / `Helper` / `Info` / `Data` / `Object` 不得作为类名主干。既有孤例已整改（`SystemService` 并入 `OperationLogService`）。

### 4.5 已落地整改（2026-10-15，robin 拍板）

| 动作 | 说明 | 状态 |
|---|---|---|
| `SystemService` → 并入 `OperationLogService` | 读写一家，去掉泛化名；`SystemController` 改注入 `OperationLogService` | ✅ |
| `AdminAuthorizationService` → `AuthorizationAccessService` | 去掉孤例前缀，职责是运行时 API 访问校验（含测试类同步改名） | ✅ |
| `TestRequestPresetService` → `RoutingTestPresetService` | 补域前缀 | ✅ |
| 6 个协作者 → `service.support` 子包 | AuthorizationManageSupport / ProviderCredentialSupport / ResourceTrafficPolicySupport / EnumOptionRegistry / ModelFamilyResolver / PluginReconcileRunner | ✅ |
| `RoutingTestPreparation` / `RoutingTestStreamHandle` → `dto.routing` | 数据类型归位 | ✅ |
| `ProviderCredentialEncryptionMigrator` 删除 | no-op 死代码 | ✅ |
| `DbModerationWordSource` | 随敏感词架构调整被另一会话删除（2026-10-15） | 已随架构移除 |

门禁：`mvn -pl knot-service,knot-admin -am test` **BUILD SUCCESS / 103 tests, 0 failures**。
上下文证据：`AppControllerTest`（`@SpringBootTest`，无 mock）以 `AdminApplication` 完整启动成功 → `service.support` 子包被组件扫描覆盖（`AppService` → `ResourceTrafficPolicySupport` 注入链成立）。

**follow-up**：`TestRequestPresetController` / `TestRequestPresetDto` / `TestRequestPresetMapper` 仍用旧名（Service 已改 `RoutingTestPresetService`），是否一并统一待定；真启 jar 冒烟未跑（并行会话活跃，避免抢占共享 m2 与数据库），待收口后补跑。

## 5. 编码细节

- **跨模块绑定一律存 code 不存 id**（残留例外：`kx_model_items.logical_model_id`）。
- **权限三表与所有 `ks_` / `kx_` 表结构默认不动**（改错全站 403）。
- **逻辑删除表 + UNIQUE(code) 四必配**：`countByXxxCode` 物理行判；列表 `includeDeleted` + mapper `<if>` + 排序 `is_deleted asc, id desc`；恢复端点 `PUT /{id}/restore`；引用检查覆盖 inner join 方。样板：`kb_models` / `kb_model_pools` / `kb_logical_models`。
- **状态枚举**：删除语义移 `is_deleted`（Integer）；`EntityStatusEnum` / `UserStatusEnum` 并入 `EnabledStatusEnum`。⚠ 前端 truthy（`if (status)`）是头号风险，**0 是合法值**。
- **枚举双源不混用**：代码枚举经 `GET /api/common/enums` 下发；DB 只留 `scope_type` / `discount_type` / `channel` / `model_family`。
- **可空绑定列的 mapper `<set>` 不能用 `<if>` 包**（会写出 `set` 空语法错误）。
- `PageQuery` 加字段要连带改所有 `new PageQuery(null × n)`。
- **JsonKit 收敛**：外部只用 `fromJson` / `toJson` / `parse`。
- **MapStruct 报 "Unmapped target" 常是真 bug**，不要无脑 `@Mapping(target = "x", ignore = true)`。
- 无历史包袱：**字段变更全链路一次做干净，不留兼容兜底/冗余列，不写回填**；`schema.sql` + `data.sql` 即唯一权威。

## 6. 异常与鉴权

- `BusinessException` **无 `@ResponseStatus`** → 返回 HTTP 200 + `success:false`，前端按业务码处理。
- admin 接口**默认拒绝**：无权限绑定即 403；**新增接口必须补权限绑定**。
- Spring 6.1+ `NoResourceFoundException` 转 404。
- 删除端点成功返回 200 + 空响应体（验证删除用"二次删除确认法"）。

## 7. 测试

- ⚠⚠ **JDK 25 + Mockito inline 不能 mock 任何具体类**（Byte Buddy 无法 instrument 类层级）。测试替身用**子类覆写**（先例 `CountingSnapshotCache`）；mock 接口不受影响。
- 先写复现测试再改代码；改完反向验证。

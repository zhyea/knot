# 30 · 数据库与迁移规范

> 适用：`**/*.sql`、`**/db/**`、涉及表结构的代码改动。完整索引见 `docs/standards/PROJECT-STANDARDS.md`。

## 1. 表前缀与归属（沿用现状，不改）

| 前缀 | 语义 | 数量 | 示例 |
|---|---|---|---|
| `ks_` | **系统域**（用户/角色/权限/菜单/模块/枚举/操作日志/定时任务/部门/用户设置） | 15 | `ks_users`、`ks_roles`、`ks_menus`、`ks_modules`、`ks_permissions`、`ks_enum_configs`、`ks_operation_logs` |
| `kb_` | **业务域**（应用/供应商/模型/路由/计费/插件/通知/敏感词/频控额度） | 25 | `kb_models`、`kb_apps`、`kb_billing_rules`、`kb_plugin_instances`、`kb_routing_rules` |
| `kx_` | **明细/扩展域** | 2 | `kx_model_items`、`kx_model_sources` |

⚠ **权限三表与所有 `ks_` / `kx_` 表结构默认不动**——改错会导致全站 403。

## 2. 权威源与迁移

- 权威 schema：`knot-server/knot-admin/src/main/resources/db/schema.sql`；初始化数据：`同目录 data.sql`。
- **改 `db/*.sql` 必须同时写迁移脚本**，落 `docs/database/migration/YYYY-MM-DD-*.sql`。
- 迁移写法：`information_schema` 判列是否存在 → 加**可空列** → 幂等可重复执行。
- 顺序：**先跑迁移，再 package/启应用**。既有库必须先迁移才能起新版。

## 3. 硬陷阱

- ⚠⚠ **`CREATE TABLE IF NOT EXISTS` 不给既有表补列。** schema.sql 加列后不跑迁移，`@SpringBootTest` 执行 schema+data 会因 data.sql 引用新列报 `Unknown column` 直接挂。
- ⚠ **DDL 迁移必须连带权限绑定 INSERT**——否则既有库跑完必然 403。
- ⚠ MariaDB **DROP COLUMN 不会自动删复合索引** → 先 `DROP INDEX` 再 `DROP COLUMN`。
- 建表统一 `COLLATE utf8mb4_unicode_ci`。
- `data.sql` 按段分区（10 段，ADMIN 块在末尾）。

## 4. 逻辑删除表的四必配

任何"逻辑删除 + UNIQUE(code)"的表必须同时具备：

1. `countByXxxCode` —— **物理行判重**（含已删除行）
2. 列表支持 `includeDeleted` + mapper `<if>` + 排序 `is_deleted asc, id desc`
3. 恢复端点 `PUT /{id}/restore`
4. 引用检查覆盖 inner join 的双方

样板：`kb_models`、`kb_model_pools`、`kb_logical_models`。

## 5. 变更纪律

- **无历史包袱**：字段变更全链路一次做干净，不留兼容兜底、不留冗余列、**不写回填**。
- 跨表绑定一律存 **code 不存 id**（残留例外：`kx_model_items.logical_model_id`）。
- schema + data 改列 → 幂等迁移 → 全链路代码 → grep 残留 → `mvn test` + `vue-tsc` → 真启 jar 冒烟 → 清理临时库。

## 6. 冒烟库

- 一次性库 `knot_smoke_options`：由 `.workbuddy/audit/ProbeBuildSmokeDb` 从 schema + data 建库并授 `admin/admin`。
- ⚠ **dev 库 `knot` schema 落后，禁止用于验证。**
- MariaDB 无 CLI → 用 `.workbuddy/audit/Probe*.java`（connector-j，root 凭据在脚本内）按分号切 SQL 执行。

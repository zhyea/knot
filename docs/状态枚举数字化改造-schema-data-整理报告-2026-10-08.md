# 状态枚举数字化改造 — schema.sql / data.sql 整理报告（2026-10-08）

> 依据：`docs/状态枚举数字化改造-技术方案与实施计划-2026-10-07.md` §7.6。
> 2026-10-08 robin 拍板：全新项目无历史数据，**不写迁移脚本**，schema.sql / data.sql 为唯一权威。
> 因此 §7.6.3 原「schema ↔ 迁移脚本列级比对」退化为「schema.sql 自身一致性校验」。

## 一、schema.sql 校验（换算法复验）

| # | 检查项 | 命令口径 | 结果 |
|---|---|---|---|
| 1 | 状态列不得再为 VARCHAR | `grep -nE "(status\|publish_status\|sync_status\|result_status\|send_status)[a-z_]*\s+VARCHAR"` | **0 命中** ✅ |
| 1' | 换算法：TINYINT 状态列计数 | `grep -cE "(status\|publish_status\|sync_status\|result_status\|send_status)\s+TINYINT"` | **36** ✅（与方案 §4 统计 36 列一致） |
| 2 | ks_users / ks_departments 保留 INT | `grep -nE "...status\s+INT"` | 仅 17 行（ks_users）、31 行（ks_departments）两处 ✅ |
| 3 | `kb_billing_rules.is_deleted` 紧跟 status | 人工核对 schema.sql:581-582 | ✅ |
| 4 | `kb_plugin_instances.fail_mode` 已删 | `grep -c fail_mode schema.sql` | **0** ✅ |
| 5 | 状态列总数 | `^\s*(status\|publish_status\|sync_status\|result_status\|send_status)\s+(TINYINT\|INT)` | **38**（36 TINYINT + 2 INT）✅ |

## 二、data.sql 校验

| # | 检查项 | 结果 |
|---|---|---|
| 1 | 状态字符串字面量（含 17 个状态词全量宽查） | **0 命中** ✅ |
| 2 | `kb_plugin_instances` 6 行种子无 fail_mode 列/值 | ✅（列清单已去掉 fail_mode，status=2 生效） |
| 3 | `PUT /api/billing/rules/{id}/restore` 权限绑定 | ✅ 绑定 id=191（复用 billing:rule:update=69），id 连续无跳号 |
| 4 | 10 段分区 / 段内顺序 / ADMIN 末块 | 未改动结构，仅替换字面量 ✅ |

## 三、探针复验（报 0 必换算法）

向 data.sql 临时副本追加一行含 `'ENABLED'` 的种子，grep 计数变 1 → 检测链路正常，0 是真 0。探针已删除。

## 四、枚举注册终态

| 枚举 | code 类型 | 注册方式 |
|---|---|---|
| `EnabledStatusEnum` | int（1/0） | putNumeric |
| `LogicalModelPublishStatusEnum` | int（1/2/3） | putNumeric |
| `OperationLogStatusEnum` | int（1/2） | putNumeric |
| `ScheduledTaskRunStatusEnum` | int（1/2/3） | putNumeric |
| `PluginPackageStatusEnum` / `PluginInstanceStatusEnum` / `PluginExecutionResultStatusEnum` | int | putNumeric |
| `NotificationSendStatusEnum` / `ExternalModelSyncStatusEnum` | int | putNumeric |
| `HealthStatusEnum` / `ReconciliationStatusEnum` | int | 未注册（拍板：只建枚举不接接口） |
| `EntityStatusEnum` / `UserStatusEnum` | — | **已删除** |

## 五、门禁

| 门禁 | 结果 |
|---|---|
| `mvn -f knot-server/pom.xml test` | BUILD SUCCESS，305 用例 0 失败，EXIT=0 |
| `npm run type-check` | EXIT=0 |
| `npm run build` | EXIT=0，dist 产物核验，components.d.ts 已还原 |

## 六、真启 jar 冒烟（2026-10-08 执行）

**环境**：全新空库 `knot_smoke_status`（root 建库 + GRANT 给业务用户 admin），
`KNOT_SQL_INIT_MODE=ALWAYS` 启动 `knot-admin-0.0.1-SNAPSHOT.jar`（18080）。
**首次启动即建表 + 灌种子一次成功**，`Started AdminApplication in 3.321s`，0 ERROR ——
schema.sql / data.sql 的零起点建库能力得到实证。

| # | 冒烟项 | 结果 |
|---|---|---|
| 1 | 登录 + 21 个域接口（billing/provider/models/pools/logical/routing/consumers/plugins/notifications/operation-logs/scheduled-tasks/users/apps/model-families/apps/enums/report/external 等） | **全部 HTTP 200** ✅（3 个 500 系冒烟脚本误用 POST 打 GET 端点，非缺陷） |
| 2 | `/api/common/enums` 数组结构 + 数字 code | ✅ 19 个枚举；EnabledStatusEnum `[{code:1},{code:0}]`；`EntityStatusEnum` 不存在 |
| 3 | 计费规则创建 | ✅ enabled=true |
| 4 | 停用 | ✅ enabled=false |
| 5 | 逻辑删除 | ✅ HTTP 200 |
| 6 | 默认列表隔离 | ✅ 删除行不可见 |
| 7 | `includeDeleted=true` 回收站 | ✅ 可见且 enabled=false（删时强制停用落库正确） |
| 8 | `PUT /rules/{id}/restore` | ✅ 恢复成功，**enabled 保持 false**（不自动生效） |
| 9 | 恢复后默认列表可见 + 显式启用 | ✅ |
| 10 | 插件实例种子 status=2（生效） | ✅ 数字落库 |
| 11 | 操作日志数字化落库 | ✅ 成功=1、失败=2（含一笔真实 BusinessException 落 2） |

**冒烟发现并当场修复的 bug**：`BillingRuleMapper.xml` 的 `RuleColumns` 漏查
`br.is_deleted` 列 → restore 前置校验读不到已删标记，误报"未被删除"。
已补列 + 重新打包复验（上表 6-9 项即为修复后复跑结果）。
另发现 `PluginInstanceStatusEnum` 未注册 `/api/common/enums`（前端插件表单下拉无数据），
已注册（18→19 枚举）并同步契约测试。

## 七、最终门禁

| 门禁 | 结果 |
|---|---|
| `mvn test`（修复后最终轮） | BUILD SUCCESS，305 用例 0 失败，EXIT=0 |
| `vue-tsc --noEmit` | EXIT=0 |
| `vite build` | EXIT=0 + dist 核验 |

## 八、清理

冒烟库 `knot_smoke_status` 用后即弃（DROP），冒烟进程已终止，种子数据无残留。

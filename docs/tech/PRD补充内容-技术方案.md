# PRD补充内容技术方案（详细设计）

本文对应 `docs/prd/Init Prd.md` 第五章“需求细化补充清单”，给出可直接开发的技术落地方案。

## 1. 统一状态机定义
- 统一状态枚举规范：`DRAFT`、`ENABLED`、`DISABLED`、`ARCHIVED`。
- 对象状态机（示例）：
  - 供应商：`DRAFT -> ENABLED -> DISABLED -> ARCHIVED`
  - 模型版本：`DRAFT -> GRAY -> ACTIVE -> ROLLED_BACK`
  - 路由规则：`DRAFT -> ENABLED -> DISABLED`
  - 告警：`OPEN -> ACK -> RESOLVED -> CLOSED`
- 技术实现：
  - `StateMachineGuard` 统一校验状态跳转；
  - 非法跳转抛出 `GW-STATE-001`。

## 2. 统一错误码体系
- 规则：`GW-{DOMAIN}-{NNN}`，如 `GW-AUTH-001`。
- 域划分：
  - `AUTH`、`RATE`、`QUOTA`、`ROUTE`、`PROVIDER`、`BILLING`、`SYSTEM`。
- 技术实现：
  - `ErrorCode` 枚举 + `BusinessException`；
  - `GlobalExceptionHandler` 输出统一结构：`code/message/requestId/details`。

## 3. 接口契约与幂等规范
- 写接口统一支持请求头：`Idempotency-Key`。
- 幂等记录表建议：`idempotency_records(key, api_path, request_hash, response_json, expire_at)`。
- 重试语义：
  - 同 key 同 payload：返回首次结果；
  - 同 key 不同 payload：返回 `GW-SYSTEM-409`。

## 4. 性能与容量目标
- 基线目标（可调整）：
  - 可用性：`99.95%`
  - 网关推理接口延迟：`P95 < 800ms`，`P99 < 1500ms`（不含大模型服务端极端慢请求）
  - 吞吐：单节点 `300 RPS`，支持水平扩展
- 技术方案：
  - 压测基准数据集 + 固定回放脚本；
  - 每次发布前执行性能回归。

## 5. 多租户权限矩阵
- 角色层级：平台管理员 > 租户管理员 > 部门管理员 > 普通用户。
- 数据范围：
  - 租户管理员：本租户全数据；
  - 部门管理员：本部门及下级部门；
  - 普通用户：本人负责资源。
- 技术实现：
  - `DataScopeInterceptor` 注入 `tenant_id + dept_scope` 条件；
  - 审计记录写入 `tenant_id`。

## 6. 频控与额度优先级规则
- 推荐优先级：`应用 > 模型 > 供应商`。
- 扣减顺序：
  1) 先校验应用额度与频控；
  2) 再校验模型额度与频控；
  3) 最后校验供应商额度与频控。
- 失败返回：
  - 应用层触发：`GW-QUOTA-APP` / `GW-RATE-APP`
  - 模型层触发：`GW-QUOTA-MODEL` / `GW-RATE-MODEL`
  - 供应商层触发：`GW-QUOTA-PROVIDER` / `GW-RATE-PROVIDER`

## 7. 对账差异处理闭环
- 差异分类：`DELAY`、`RULE_MISMATCH`、`SUPPLIER_BILL_ERROR`、`MISSING_RECORD`。
- 流程：导入账单 -> 自动比对 -> 差异归类 -> 指派责任人 -> 冲正/确认 -> 归档。
- 表结构建议：
  - `reconciliation_tasks`
  - `reconciliation_diffs`
  - `reconciliation_actions`

## 8. 灰度发布门禁规则
- 每阶段门禁：
  - 错误率不高于基线 `+20%`
  - P95 延迟不高于基线 `+15%`
  - 告警级别不得出现 P1
- 观察窗口：每阶段至少 15 分钟。
- 技术实现：
  - 发布平台调用 `GateChecker` 自动判定是否放量。

## 9. 数据字典与审计字段标准
- 通用字段标准：
  - 主键：`id`
  - 租户：`tenant_id`
  - 审计：`created_by/created_at/updated_by/updated_at`
  - 追踪：`trace_id`
- 脱敏等级：
  - `L3`（强敏感）：密钥、token、密码，仅摘要存储；
  - `L2`（中敏感）：手机号、邮箱，掩码存储；
  - `L1`（低敏感）：普通业务字段。

## 10. 测试与验收标准
- 测试分层：
  - 单元测试：规则引擎、计费、折扣、状态机；
  - 集成测试：鉴权、路由、计费、对账；
  - 性能测试：核心链路压测；
  - 安全测试：越权、重放、注入。
- 上线阻断项（必须满足）：
  - P1 安全漏洞未关闭；
  - 幂等冲突未处理；
  - 对账差异不可追踪；
  - 审计日志不完整。

## 11. 第1轮补充项技术落地
- 主键与时间规范：
  - 在 `common` 模块提供 `IdGenerator` 组件；
  - 数据库存储统一 UTC，API 层通过 `TimeZoneContext` 转换。
- 追踪与审批：
  - `TraceFilter` 注入 `traceId`；
  - 高风险配置变更接入 `change_approval_records` 审批表。

## 12. 第2轮补充项技术落地
- 接口兼容：
  - OpenAPI 文档增加 `deprecated` 标记与版本说明；
  - 破坏性变更仅允许在 `/v2` 路径发布。
- 批量操作防护：
  - 批量接口增加 `dryRun=true` 预演模式；
  - 生成回滚任务 `batch_rollback_jobs`。
- 异步幂等：
  - 任务表新增 `dedup_key` 唯一索引。

## 13. 第3轮补充项技术落地
- 健康评分：
  - `provider_health_score = w1*successRate + w2*(1-latencyNorm) + w3*availability`；
  - 每分钟聚合并写入 `provider_metrics_minute`。
- 计费精度：
  - 统一使用 `DECIMAL(18,6)`；
  - 舍入规则固定 `HALF_UP`。
- 告警降噪：
  - 告警引擎增加去重窗口和合并键（`alert_type + biz_id + level`）。

## 14. 第4轮补充项技术落地
- 发布门禁：
  - 发布平台增加 `release_windows` 校验；
  - 高风险发布需双人审批。
- 回放测试：
  - `traffic_replay_tasks` 支持历史样本重放与结果比对。
- 指标口径：
  - 新增 `metrics_dictionary` 表，固定统计口径与公式。

## 15. 第5轮补充项技术落地
- 容量治理：
  - 接入资源水位规则引擎，支持阈值告警与自动限流。
- 数据库治理：
  - 建立慢 SQL 采样任务、索引巡检任务、DDL 审核流水。
- 容灾演练：
  - 新增 `drill_plans`、`drill_reports` 记录演练计划与结果。
- 文档版本治理：
  - 文档仓库采用 `doc_version` + `change_log` 模板强制发布记录。

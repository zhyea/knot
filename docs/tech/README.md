# AI网关技术方案总览（详细设计）

## 1. 目标与范围
- 将 `docs/prd` 中的产品需求落地为可实施技术设计，覆盖核心 7 模块 + 可选 4 模块。
- 输出级别为详细设计（模块边界、分层职责、表结构映射、关键流程、异常处理、扩展点）。

## 2. 技术栈约束
- 后端：`Spring Boot 3`、`MyBatis`、`MySQL 8`、`Redis`、`Kafka/RabbitMQ(可选)`。
- 前端：`Vue3 + Element Plus`。
- 可观测：`Micrometer + Prometheus + Grafana`，日志可对接 `ELK`。

## 3. 通用分层规范
- Controller：参数校验、权限注解、请求追踪。
- Service：业务编排、事务边界、幂等控制。
- Domain/Policy：规则计算（路由、计费、折扣、频控）。
- Mapper：MyBatis SQL 与分页查询。
- Infra：缓存、消息、供应商 SDK、加密组件。

## 4. 通用横切能力
- 认证鉴权：App 鉴权 + 用户鉴权（后台）。
- 审计日志：AOP + 拦截器双通道，强制记录增删改前后快照。
- 多租户：统一 `tenant_id`，数据访问自动注入租户条件。
- 幂等：写接口支持 `Idempotency-Key`。
- 异常码：统一业务错误码体系（`GW-xxxx`）。

## 5. 文档索引
- 核心模块：
  - `modules/01-系统管理-技术方案.md`
  - `modules/02-供应商管理-技术方案.md`
  - `modules/03-模型管理-技术方案.md`
  - `modules/04-应用管理-技术方案.md`
  - `modules/05-模型路由规则-技术方案.md`
  - `modules/06-计费规则及成本统计-技术方案.md`
  - `modules/07-安全与监控管理-技术方案.md`
- 可选模块：
  - `modules/08-插件管理-技术方案.md`
  - `modules/09-灰度发布-技术方案.md`
  - `modules/10-API文档管理-技术方案.md`
  - `modules/11-通知管理-技术方案.md`
- 注意事项闭环：
  - `注意事项解决方案.md`
- PRD补充项落地：
  - `PRD补充内容-技术方案.md`
- 可直接开发手册：
  - `开发落地手册-模块详细设计.md`
  - 其中第12章为 PRD 对齐补充项

# API文档管理模块技术方案

## 1. 架构设计
- 基于 OpenAPI 3 自动生成文档，网关服务启动时聚合接口元数据。
- 支持文档版本快照，和网关发布版本绑定。

## 2. 数据设计
- 推荐新增：`api_docs_versions`、`api_docs_changes`。

## 3. 接口设计
- `GET /api/docs/openapi.json` —— ✅ 已实现：springdoc 运行时生成（`springdoc.api-docs.path=/api/docs/openapi.json`，
  `packages-to-scan=org.chobit.knot.admin.controller`）。注意 `packages-to-scan` 是字符串，指错会让文档**空**而不报错，
  由真启冒烟核对 paths 数量兜住。
- `GET /api/docs/changelog` —— ❌ 未实现（2026-11-12 起明确）：原有一个 `POST /api/docs/changelog` 占位端点返回两条
  硬编码版本，`POST /api/docs/openapi.json` 同样是硬编码骨架（paths 只有 `/api/health`）。两者都不随代码演进、
  仓库内零引用，已一并删除（含 `data.sql` 的权限绑定 54/55；存量库须手工删这两行——
  原迁移脚本已随历史批次清理）。
  ⚠ 删除前文档写 `GET`、实现是 `POST`，本来就不一致——这正是不该留占位端点的理由。

## 4. 关键流程
- 构建阶段生成 OpenAPI -> 启动时注册 -> 发布时固化快照。

## 5. 异常与性能
- 文档读取走 CDN/缓存，避免后台接口高频计算。

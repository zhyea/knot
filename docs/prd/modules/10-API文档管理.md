# API文档模块产品文档（可选）

## 1. 模块目标
- 自动生成并维护网关 API 文档，降低接入门槛。

## 2. 功能范围
- 文档自动生成：接口、参数、响应、错误码、示例。
- 鉴权说明：AppKey/AppSecret、签名规则、频控额度说明。
- 多版本文档：按网关版本维护文档快照。

## 3. 关键接口（建议）
- `GET /api/docs/openapi.json`
- `GET /api/docs/changelog`

## 4. 实现状态（2026-11-12 复核）

| 接口 | 状态 | 说明 |
|---|---|---|
| `GET /api/docs/openapi.json` | ✅ 已上线 | 由 springdoc 运行时生成（`springdoc.api-docs.path`），扫 `org.chobit.knot.admin.controller` |
| `GET /api/docs/changelog` | ❌ 未实现 | 从未有真实实现。曾有一个 `POST /api/docs/changelog` 占位端点返回两条硬编码版本，不随代码演进，已连同类占位 `POST /api/docs/openapi.json` 一并删除 |

⚠ 占位端点与本文档本来就对不上：文档写 `GET`，占位实现是 `POST` 且返回硬编码骨架（paths 只有 `/api/health`）。
删除后 `openapi.json` 才与文档一致；`changelog` 仍是待做项，真要做需先定「版本快照从哪来」（`api_docs_versions` 表尚未建）。

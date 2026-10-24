# AGENTS.md — knot 项目契约

> 跨工具入口（Cursor / Trae / Qoder / Codex 原生读取）。**完整规范在 `docs/standards/`，遇到具体域任务时先读对应文件。**

## 项目

knot = **AI 网关**：统一代理多家大模型供应商，做路由、计费、频控、插件与敏感词审核。

| 层 | 技术栈 | 事实 |
|---|---|---|
| 后端 | Java 21 / Spring Boot 3 / MyBatis / MariaDB | reactor 根 `knot-server/`（**仓库根无 pom**），8 模块：`common` `plugin-api` `plugin` `adapter` `dal` `service` `admin` `gateway` |
| 前端 | Vue 3 + Vite + Element Plus + TypeScript | `knot-front/`，**无测试框架** |

## 门禁（真跑，不许跳过）

```bash
mvn -f knot-server/pom.xml test                       # 后端全量
mvn -f knot-server/pom.xml -pl knot-dal,knot-service,knot-admin test   # 定向（其他模块被阻塞时）
cd knot-front && npx vue-tsc --noEmit && npx vite build                # 前端两道
```

## 铁律

1. **禁止伪造测试结果。** 没跑就标注"未验证"；后台 exit code 不可信，须 grep 日志 `BUILD SUCCESS|BUILD FAILURE|Tests run:.*Failures: [1-9]`（Maven 日志先 `tr -d '\000'`）。
2. **未经 robin 显式批准，绝不 `git commit`。**
3. **改契约前全量 grep 依赖者**，改完再 grep 残留。
4. **断言"已写入"必须读回来核对。**
5. **回复一律简体中文**（技术术语可保留英文）。
6. 多会话会抢改：宣告完成前重跑最终门禁；被阻塞时用 `-pl` 定向并**如实标注**，不得伪称全量通过。
7. **判定违反前先声明尺子**：SOLID 为主、DRY/KISS/YAGNI 为辅、5 条工程判据为辅（⚠E1–E5，**不属设计模式原则，不得混称**）。详见 `docs/standards/rules/80-code-review.md`。
8. **规范与代码冲突以代码为准，并立即回来修订规范**（规范腐化有实证：`20-frontend-vue.md` 曾长期写「无测试框架」）。
9. **未实测的列举不得写进实施清单**：待迁移/待删除项必须逐项 grep 实测，未核实的显式标注「未核实」。

## 红线速记

- **`knot-plugin` 的类禁止外部模块 import（含测试）**，依赖一律 `<scope>runtime</scope>`。
- **权限三表与所有 `ks_` / `kx_` 表默认不动**（改错全站 403）；admin 接口**默认拒绝**，新增接口必须补权限绑定。
- **`schema.sql` 加列必须同步写迁移脚本** 到 `docs/database/migration/YYYY-MM-DD-*.sql`；`CREATE TABLE IF NOT EXISTS` 不给既有表补列。
- **JDK 25 + Mockito 不能 mock 具体类**，测试替身用子类覆写。
- **`status` 为 0 是合法值**，前端禁用 truthy 判断。
- **跨表绑定一律存 code 不存 id。**
- 无历史包袱：字段变更一次做干净，**不留兼容兜底、不写回填**。

## 规范索引（按任务查）

| 任务 | 读这份 |
|---|---|
| 任何任务（总则、门禁、环境陷阱） | `docs/standards/rules/00-core.md` |
| 写/改后端 Java | `docs/standards/rules/10-backend-java.md`（含命名纪律） |
| 写/改前端 | `docs/standards/rules/20-frontend-vue.md` |
| 改表结构 / 写迁移 | `docs/standards/rules/30-database-sql.md` |
| 插件 / 敏感词 | `docs/standards/rules/40-plugin.md` |
| 收口验证 / 冒烟 / 写测试 | `docs/standards/rules/50-verification.md` |
| 计费 / 频控 / 重试 / 路由 / 流式 | `docs/standards/rules/60-business-rules.md` |
| 交付 / 文档落盘 / git | `docs/standards/rules/70-delivery.md` |
| 代码审查 / 质量判定 / 写新代码前 | `docs/standards/rules/80-code-review.md`（审查尺子 + 🔴🟡💭 分级清单 + 反模式台账） |

## 命名纪律（摘要，详见 10-backend-java §4）

- 角色后缀**只能用白名单**（Service / Support / Source / Resolver / Registry / Runner / Provider / Migrator / Controller / Converter / Mapper / Entity / Dto / Vo / Item …），不许自造。
- `service` 包顶层只放 `@Service`；其他协作者组件进 **`service.support` 子包**；**数据类型（DTO/句柄）不进 service 包**。
- 同域 ≥2 个类必须域前缀（`Authorization*` / `Routing*` / `Provider*` …）。
- **禁止泛化主干名**：System / Common / Base / Manager / Util / Helper / Info / Data / Object。

# knot 代码质量评估报告

- **评估日期**：2026-09-18
- **范围**：全栈（knot-front + knot-server）
- **方法**：轻量静态扫描（规则 + 正则），不编译、不安装依赖
- **项目定位**：AI 网关脚手架。后端 `Spring Boot 3.3.4 + MyBatis + MySQL`（7 个 Maven 模块）；前端 `Vue3 + Vite + Element Plus`。

## 1. 代码规模（排除 node_modules / target）

| 端 | 规模 |
|---|---|
| 前端 knot-front/src | ~97 `.vue` + ~50 `.js` ≈ 150 文件（几乎无业务 `.ts`） |
| 后端 knot-server | ~160 `.java` + 40+ MyBatis Mapper XML，模块：common / plugin / adapter / dal / service / admin / gateway |
| 测试 | 前端 **0**；后端 **8** 个（仅 `knot-admin` 5 + `knot-common` 1 + test 辅助 2） |

## 2. 关键安全与正确性（高危项）—— 全部通过 ✅

| 维度 | 结果 | 说明 |
|---|---|---|
| MyBatis SQL 注入（`${}` 拼接） | **0 处** | 所有 Mapper 均使用 `#{}` 参数化 |
| 前端 XSS（`v-html`） | 2 处，**均安全** | `ShellCodeBlock.vue`、`JsonCodeEditor.vue` 均先 `escapeHtml()` 再高亮渲染 |
| 密钥/密码硬编码 | **0 处** | 前端 6 处命中均为 i18n 文案 / 表单字段（误报） |
| 字符串 `==` 比较（Java） | **0 处** | 无字面量 `==`/`!=` 比较陷阱 |
| 调试残留（console/debugger/alert/System.out/printStackTrace） | **0 处**（生产） | 后端 2 处仅在 `src/test` 辅助类 |
| TODO/FIXME/HACK 注释债务 | **0 处** | |
| 前端鉴权令牌存储位置 | **P1 风险** | `useAuth.js:33` 经 `utils/storage.js` 将 JWT 写入 `localStorage`（持久化），XSS 场景下可被脚本读取外泄 |

> 结论：实现层面**无注入、无 XSS、无硬编码密钥、无类型比较陷阱**，基础安全与正确性扎实。

## 3. 异常处理（中危，需关注）

后端 `catch (Exception)` 共 **17 处**，绝大多数处理规范（`log.error/warn` 后转业务异常或安全回退）：

- 规范示例：`AuthInterceptor`→抛 `UnauthorizedException`；`AesGcmCipher`/`JwtUtil`→抛包装异常或返回安全值；`OperationLogAspect`/`ScheduledTaskExecutor`→`log.warn` 不影响主流程；`JsonKit`→`log.error` + 返回 null。
- **3 处静默吞异常、无日志**（建议补 `log.debug` 或注释忽略原因，避免丢失根因）：
  - `knot-adapter/.../usage/UsageExtractor.java:66` — `catch (Exception ignored) { return null; }`
  - `knot-adapter/.../adapter/request/UpstreamRequestAdapter.java:92` — 同上
  - `knot-service/.../external/OpenRouterModelSyncProvider.java:268 / :282` — 同上

## 4. 结构性问题（最高工程风险）

**核心风险不在"代码写坏"，而在"没有防止变坏的护栏"。**

1. **质量门禁几乎空白**
   - 前端：无 ESLint、无 `tsconfig.json`、无 vitest/jest；`package.json` scripts 仅 `dev/build/preview`。TS 经 Vite/esbuild 直接转译，**类型错误不会被发现**。
   - 后端：无 Checkstyle / SpotBugs / PMD；仅 `knot-admin`、`knot-common` 引入测试依赖，**其余 5 个核心模块（dal/service/plugin/adapter/gateway）无测试依赖、无单测**。
   - 全仓：无 CI 配置（无 `.github`/`.gitlab-ci`）、无 Sonar；仅根目录 `.editorconfig` 做编辑器规范（但无工具强制）。
2. **测试覆盖极低**：路由规则、计费、鉴权、上游适配、调度等核心业务基本无自动化测试，回归风险高。
3. **前端无类型系统**：纯 JS + Vue，缺少类型约束，重构与接口契约保障弱。

## 5. 其他正面观察

- 后端分层清晰（common / dal / service / adapter / gateway / admin），JWT/加密工具（`AesGcmCipher`、`JwtUtil`）写法规范。
- 前端组件拆分细致（panel / dialog / drawer 分层），结构清晰。

## 6. 优先级行动项

| 优先级 | 行动 | 预期收益 |
|---|---|---|
| **P0** | 后端为 `service/dal/adapter/gateway` 引入 test 依赖并补充关键路径单测（路由 / 计费 / 鉴权） | 防回归 |
| **P0** | 前端引入 ESLint + 基础配置，CI 加入 lint 卡点 | 防规范退化 |
| **P1** | 后端接入 Checkstyle / SpotBugs（或 SonarQube） | 自动抓坏味道 |
| **P1** | 修复 3 处静默 `catch`，补日志 | 提升可观测性 |
| **P2** | 前端评估迁移 TS（`vue-tsc` 类型检查） | 类型安全 |
| **P2** | 建立 CI 流水线（lint + test + build） | 门禁闭环 |

## 7. 结论

代码**实现质量良好**，高危项全部通过；真正的风险是**质量门禁与测试体系空白**。建议以 P0 两项（补齐后端核心模块单测 + 前端 ESLint/CI）为起点，把"护栏"立起来，再逐步引入静态分析与类型系统。

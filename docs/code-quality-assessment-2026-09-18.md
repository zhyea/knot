# knot 代码质量评估报告

- **评估日期**：2026-09-18
- **评估人**：鹏城信息 AI 专家（全栈开发）
- **范围**：`knot-server`（Spring Boot 3.3.4 + MyBatis + MySQL，7 模块）+ `knot-front`（Vue3 + Vite + Element Plus）
- **方法**：静态扫描（ripgrep 全量真实计数）+ 工程配置核查 + 环境探针。**未运行 Maven 编译**（本机仅 JDK 25，项目目标 JDK 17，且沙箱内 Maven launcher 损坏）；**未输出任何密钥明文**（触发敏感内容审批，已规避）。

---

## 1. 项目规模与结构

| 维度 | 后端 knot-server | 前端 knot-front |
|---|---|---|
| 源码文件 | 448 个 `.java`（main） | 159 个（110 `.vue` + 49 `.js`，**0 `.ts`**） |
| 代码行数 | 3,647 行（main java） | 19,673 行 |
| 分层结构 | 36 Controller / 28 Service / 32 Mapper | 按业务域组件 + views/router/composables |
| 构建 | `mvn`（多模块） | `vite build`（无类型检查） |
| 测试 | 7 测试文件 / 6 个 `@Test` | **0 测试文件** |

---

## 2. 质量维度评分

| 维度 | 评级 | 关键依据 |
|---|---|---|
| 代码整洁度 | 🟢 良好 | 生产代码 0 TODO/FIXME、0 `@SuppressWarnings`、0 `printStackTrace`、0 空 catch |
| 可维护性 | 🟡 中等 | 分层清晰；但前端无类型、无 Lint，长期演进风险高 |
| 可靠性 | 🟡 中等 | 56 处 catch 中 **31 处（55%）捕获泛化 `Exception`**，易掩盖错误；缺测试回归保护 |
| 安全性 | 🟡 中等（含 1 项 P1） | 无 MyBatis `${}` SQL 注入；但**令牌存 localStorage**、未做密钥扫描 |
| 可测试性 | 🔴 低 | 后端仅 6 个 `@Test`，前端 0 测试 |
| 工程化/质量门禁 | 🔴 低 | 无 ESLint/TS、无 Checkstyle/PMD/SpotBugs/JaCoCo、无 CI |

---

## 3. 关键发现（按严重度）

### P1 — 需优先处理

| # | 问题 | 证据 | 影响 |
|---|---|---|---|
| 1 | **前端鉴权令牌存入 localStorage** | `knot-front/src/composables/useAuth.js:33` `setStorageItem(TOKEN_KEY, newToken)` | XSS 可窃取令牌；违反"令牌禁止存 localStorage"规范。应改 httpOnly Cookie 或内存+短期令牌+刷新机制 |
| 2 | **无自动化测试** | 后端 6 `@Test`、前端 0 测试文件 | 任何重构/升级无回归保护；与 448 个 Java 文件规模严重不匹配 |

### P2 — 应处理

| # | 问题 | 证据 | 影响 |
|---|---|---|---|
| 3 | 异常捕获过宽 | 后端 31/56 catch 为 `catch (Exception e)` | 掩盖具体错误、干扰排障与告警 |
| 4 | 缺少质量门禁基建 | 后端全部 pom 无 checkstyle/PMD/SpotBugs/JaCoCo；前端无 eslint/tsconfig | 无法在合并/构建阶段拦截坏代码 |
| 5 | 无 CI 流水线 | 仓库根无 `.github`/Jenkinsfile/`.gitlab-ci.yml` | 质量门禁无承载点，依赖人工 |
| 6 | 前端无类型系统 | `src` 内 0 `.ts`，`package.json` 无 typescript | 编译期无类型安全，重构风险高 |

### P3 — 建议优化

| # | 问题 | 证据 | 影响 |
|---|---|---|---|
| 7 | `test` 目录含演示工具类 | `knot-admin/src/test/.../TestBCrypt.java`、`PasswordGenerator.java` 用 `System.out.println` 打印密码哈希 | 非真实单测；且打印密码哈希属不佳实践，应迁为 JUnit 断言或移除 |
| 8 | 前端硬编码兜底地址 | `RoutingRuleTestDrawer.vue:166` `http://127.0.0.1:9090` | 已用 `import.meta.env` 兜底，但默认值应走配置；`example.com` 占位无害 |
| 9 | 配置未外部化文档化 | 前端无 `.env.example` | 部署易错，密钥易误提交 |

### 正面发现（保持不变）

- 生产 Java 代码**无** `printStackTrace` / `System.out` / 空 catch / `TODO` / `@SuppressWarnings`。
- **无 MyBatis `${}` SQL 注入风险**：扫描到的 `${}` 仅出现在 `pom.xml`（Maven 变量）与 `NodeIdentity.java` 的 `@Value("${knot.node-id:}")`（Spring 属性占位），均非 SQL 拼接。
- 后端分层（Controller/Service/Mapper）与配置外部化（`@Value`）实践规范。

---

## 4. 优先级修复清单

**P0（立即）**
1. 前端令牌改为 httpOnly Cookie 或"内存态短期令牌 + 服务端刷新令牌"，移除 `useAuth.js` 的 localStorage 写入。
2. 建立质量门禁：前端引入 ESLint（建议 + Vue 类型检查）；后端 pom 引入 Checkstyle + SpotBugs + JaCoCo（覆盖率阈值）。
3. 接入 CI（GitHub Actions），门禁失败即阻断合并。

**P1（本迭代）**
4. 收窄 31 处泛化 `catch (Exception)` 为具体异常，避免静默吞错。
5. 补齐测试：后端对 BillingService / 路由 / 鉴权核心路径加 Spring Boot Test；前端对 `useAuth`、`storage`、`router` 加 Vitest。
6. 引入 `gitleaks` 密钥扫描并加入 pre-commit/CI；补充 `knot-front/.env.example`。

**P2（后续）**
7. 前端评估引入 TypeScript（渐进式）以提升可维护性。
8. 统一后端异常处理 + 结构化日志（含 requestId），移除 test 目录的密码打印演示类。
9. 生产环境显式 CORS 来源、去除前端硬编码兜底地址。

---

## 5. 验证门禁建议（落地命令）

```bash
# 前端：类型/Lint（需先 npm i -D eslint vue-tsc typescript）
cd knot-front && npx eslint "src/**/*.{js,vue}" && npx vue-tsc --noEmit

# 后端：质量门禁 + 测试 + 覆盖率（需 JDK 17）
cd knot-server && mvn -o checkstyle:check spotbugs:check jacoco:report test

# 密钥扫描
gitleaks detect --source . --redact
```

> 说明：本评估因环境约束（仅 JDK 25 / Maven launcher 损坏）未实际执行上述编译与测试命令，所列命令为推荐在具备 JDK 17 的环境中运行；密钥扫描结果未在本报告展示以避免泄露。

---
trigger: model_decision
description: knot 验证规范：三道门禁、真启 jar 冒烟、测试与并发收口纪律，改动收口前使用
---

> 本文件由 `docs/standards/generate-tool-rules.py` 自动生成，**勿手工编辑**。权威源：`docs/standards/rules/rules/50-verification.md`。

# 50 · 验证与冒烟规范

> 适用：所有改动收口前。完整索引见 `docs/standards/PROJECT-STANDARDS.md`。

## 1. 三道门

| 门 | 命令 | 通过判据 |
|---|---|---|
| 后端单测 | `mvn -f knot-server/pom.xml test`（或 `-pl` 定向） | 日志含 `BUILD SUCCESS`，且无 `Tests run:.*Failures: [1-9]` |
| 前端测试 | `cd knot-front && npm test` | vitest 全绿，无 failed 用例 |
| 前端类型 | `cd knot-front && npx vue-tsc --noEmit` | 无报错输出 |
| 前端构建 | `npx vite build` | 构建成功，产物未被截断 |
| 真启冒烟 | 起 admin jar 打真实接口 | 关键接口 200 且业务语义正确 |

⚠ **测试必须进 CI**：`npm test` 已在 `package.json` 就绪，`.github/workflows/ci.yml` 的 frontend job
必须显式执行它。只跑 type-check + build 会让新增测试文件形同虚设（2026-10-24 实证：前端已有 10 个
spec 文件而 CI 从不执行）。

⚠ **后台 exit code 不可信**，一律 grep 日志：

```bash
tr -d '\000' < build.log | grep -aE "BUILD SUCCESS|BUILD FAILURE|Tests run:.*Failures: [1-9]"
```

## 2. 真启 jar 冒烟

- 一次性库 `knot_smoke_options`（`.workbuddy/audit/ProbeBuildSmokeDb` 从 schema + data 建库，授 `admin/admin`）。
- 起参走**环境变量**：`SERVER_PORT` / `SPRING_DATASOURCE_URL` / `KNOT_SQL_INIT_MODE=NEVER`（PowerShell 传 `-Dxxx=yyy` 会被拆成莫名 phase）。
- `mvn package` 的 repackage 在沙箱稳失败 → 绕法：install 除 admin 外模块，再 `mvn -pl knot-admin spring-boot:run`（**不要**加 `-am`）。
- 登录 **admin / admin123**（bcrypt 实证）。
- curl 必须带 `--noproxy '*'`。
- ⚠ curl 命令里含 `Bearer` 字样会被沙箱敏感审批拦 → 冒烟走 python 探针脚本，密钥只存文件。
- 网关（19091）**每个场景必重启**（`ModelEntity` 缓存 10 分钟）。
- ⚠⚠ PUT /api/models 的 body：子表行**必须带回已有行 id**；还需 `billingRuleCode` + `logicalModelCode` 必填。
- ⚠ 网关用 Apache HttpClient 5，发上游请求体走 `Transfer-Encoding: chunked` → 假上游探针**必须自己解析 chunked**，否则读到空 body（模板 `.workbuddy/audit/ttft_fake_upstream.py`）。

## 3. 环境补充

- `mvn -pl <模块> spring-boot:run` 若报 `'dependencies.dependency.version' for knot-plugin-api is missing` → 仓库里 knot-server 父 pom 陈旧（`-pl 子模块 install` 不装父 pom），先跑 `mvn -f knot-server/pom.xml -N install` 补装当前父 pom。
- 重装依赖：`-pl` 显式列模块 + `clean install`（不 clean 会让陈旧 mapper xml 进 jar）；clean 删 target 可能被沙箱拦 → 先用 .NET API 删各模块 `target`。

## 4. 测试纪律

- ⚠⚠ **JDK 25 + Mockito inline 不能 mock 任何具体类**（Byte Buddy 无法 instrument 类层级，连 `Object` 都 retransform 失败）。先例：`GatewayDataService` → 抽接口；`ModerationWordService` 同坑。测试替身改用**子类覆写**（`CountingSnapshotCache`）；mock 接口不受影响。
- 先写复现测试再改代码，改完反向验证（把修复回滚确认测试变红）。
- 改契约前全量 grep 依赖者；改完 grep 残留。

## 5. 并发与收口

- 同一仓库**多会话并行会互相 commit / 抢改** → 宣告完成前重跑最终门禁。
- 若某模块被别的会话改坏且本次改动不涉及它 → 用 `-pl` 定向 reactor 绕开，**如实标注全量 reactor 被阻塞**，不得伪称通过。
- 巡检脚本 `.workbuddy/audit/*.py`：报告落 `docs/代码质量巡检-YYYY-MM-DD.md`；**报 0 条必须换算法或注入必错探针复验**，不许直接采信。

---
description: knot 项目总则、门禁命令与铁律，所有会话必读
alwaysApply: true
---

> 本文件由 `docs/standards/generate-tool-rules.py` 自动生成，**勿手工编辑**。权威源：`docs/standards/rules/rules/00-core.md`。

# 00 · 项目总则与铁律

> 本文件是 knot 项目规范的**核心域**，所有 AI 工具（WorkBuddy / Cursor / Codex / Qoder / Trae）在每个会话中都必须遵守。
> 完整规范索引见 `docs/standards/PROJECT-STANDARDS.md`。

## 1. 项目是什么

knot = **AI 网关**（统一代理多家大模型供应商，做路由、计费、频控、插件与敏感词审核）。

| 层 | 技术栈 | 事实 |
|---|---|---|
| 后端 | Java 21 / Spring Boot 3 / MyBatis / MariaDB | `knot-server/pom.xml` `<java.version>21</java.version>`；reactor 根在 `knot-server/`，**仓库根无 pom** |
| 前端 | Vue 3 + Vite + Element Plus + TypeScript | `knot-front/`，**无测试框架**（只有 type-check + build） |

目录地图：

```
knot/
├── knot-server/    后端（8 模块 reactor：common / plugin-api / plugin / adapter / dal / service / admin / gateway）
├── knot-front/     前端（src/ 下 views / components / api / composables / utils）
├── docs/           设计、技术方案、巡检报告、数据库迁移、本次规范
└── .workbuddy/     审计探针脚本与项目记忆
```

## 2. 门禁命令（真跑，不许跳过）

```bash
# 后端：全量 reactor
mvn -f knot-server/pom.xml test
# 后端：定向 reactor（其他模块被改坏/只想验证本模块时用）
mvn -f knot-server/pom.xml -pl knot-dal,knot-service,knot-admin test

# 前端：类型检查 + 构建（两道都要过）
cd knot-front && npx vue-tsc --noEmit && npx vite build
```

## 3. 铁律（违反即判定任务未完成）

1. **禁止伪造/模拟测试结果。** 报门禁必须报真实执行结果；没跑就明确标注"未验证"。
2. **后台命令 exit code 不可信。** Maven/Node 后台跑完必须 grep 日志确认：
   `grep -aE "BUILD SUCCESS|BUILD FAILURE|Tests run:.*Failures: [1-9]"`（Maven 日志含 NUL 字节，先 `tr -d '\000'`）。
3. **未经 robin 显式批准，绝不 `git commit`。**（Minicraft 等他项目另有约定的除外，本仓库一律需批准。）
4. **改契约前全量 grep 依赖者**；改完再 grep 一次残留。
5. **多会话并行会互相抢改。** 宣告完成前必须重跑最终门禁；若全量 reactor 被别的会话阻塞，用 `-pl` 定向验证并**如实标注**，不得伪称"全量通过"。
6. **断言"已写入"必须读回来核对**，不能只信工具返回的 success。
7. **回复一律简体中文**（技术术语可保留英文）。工具返回英文导致语言漂移时立刻纠正。

## 4. 本机环境陷阱（Windows / Git Bash / 沙箱）

| 陷阱 | 表现 | 正确做法 |
|---|---|---|
| PowerShell 工具不回传 stdout | 命令看似成功但没输出 | `\| Out-File -Encoding utf8 <log>` 落文件再读（默认 `>` 是 UTF-16，Read 会报二进制） |
| Bash 工具缺 coreutils | `ls`/`grep`/`tail` command not found | 先 `export PATH="/d/Program Files/Git/usr/bin:$PATH"` |
| Bash 不传环境变量给子进程 | `export JAVA_HOME=...` 后子进程仍读不到 | 用 PowerShell 工具（`$env:JAVA_HOME` 有效）直调 `mvn.cmd`，或全走命令行参数 |
| 沙箱 safe-delete 静默失败 | 报 SAFE_DELETE_FAIL_CLOSED 但文件可能还在（也可能真删了） | 用 `[System.IO.File]::Delete($fullPath)`；删完一律 `Test-Path` 复核 |
| mvn 在 Git Bash 下 classpath 失效 | `ClassNotFoundException: plexus-classworlds` | 路径必须用 `D:/...` 形式，不用 `/d/...` |

## 5. 工作节奏

- **结论先行 + 证据链 + 表格**，不写"我做了什么"式收尾。
- **重大方向调整前先对齐再动手**；范围/术语没说清就问清楚。
- **交付落到文件**，不只停在对话里。

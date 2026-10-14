# knot 项目规范（权威源）

> 本目录是 knot 项目**唯一权威规范来源**，同时供 WorkBuddy / Cursor / Codex / Qoder / Trae 五类工具消费。
> 准则：**只在这里改规范**，工具专属文件由脚本生成，不手工编辑。

## 1. 文件结构

```
docs/standards/
├── PROJECT-STANDARDS.md      ← 本文件：索引 + 跨工具加载矩阵 + 维护方式
├── rules/                    ← 权威正文（按域切分，工具规则由它生成）
│   ├── 00-core.md            项目总则与铁律（所有会话必读）
│   ├── 10-backend-java.md    后端模块/分层/命名/编码/异常/测试
│   ├── 20-frontend-vue.md    前端 TS + Vue + 既定模式
│   ├── 30-database-sql.md    表前缀、迁移、逻辑删除四必配
│   ├── 40-plugin.md          插件架构与敏感词拓扑
│   ├── 50-verification.md    三门禁、真启冒烟、测试纪律
│   ├── 60-business-rules.md  计费/频控/重试/路由/流式
│   └── 70-delivery.md        落盘位置、git 纪律、收口检查表
└── generate-tool-rules.py    ← 生成器：把 rules/ 分发到各工具的规则目录
```

## 2. 跨工具加载矩阵

| 工具 | 入口文件 | 生成物 | 说明 |
|---|---|---|---|
| **AGENTS.md**（跨工具标准） | 仓库根 `AGENTS.md` | 手写 | Cursor / Trae / Qoder / Codex 均原生读取；含核心条款 + 索引 |
| **WorkBuddy** | `.workbuddy/memory/MEMORY.md` | 手写（精简指针） | WorkBuddy 每会话自动注入该目录的项目记忆 |
| **Cursor** | `.cursor/rules/*.mdc` | 生成器 | frontmatter：`description` / `globs` / `alwaysApply` |
| **Trae** | `.trae/rules/*.md` | 生成器 | frontmatter 同 Cursor 款；支持子目录递归（≤3 层） |
| **Qoder** | `.qoder/rules/*.md` | 生成器 | frontmatter：`trigger: always_on \| model_decision \| glob \| manual` + `description`；也兼容根目录 AGENTS.md |
| **Codex** | `AGENTS.md` | — | 读仓库根 `AGENTS.md`（`.codex/config.toml` 为本地 IDE 配置，不影响规范） |

## 3. 规则触发策略（避免 always-on 过长）

| 规则 | Cursor / Trae | Qoder | 触发范围 |
|---|---|---|---|
| `00-core` | `alwaysApply: true` | `trigger: always_on` | 每次会话 |
| `10-backend-java` | `globs: knot-server/**/*.java` | `trigger: glob` + `glob:` | 后端文件 |
| `20-frontend-vue` | `globs: knot-front/src/**` | 同上 | 前端文件 |
| `30-database-sql` | `globs: **/*.sql` | 同上 | SQL / 迁移 |
| `40-plugin` | `description`（智能生效） | `trigger: model_decision` | 插件/敏感词任务 |
| `50-verification` | `description`（智能生效） | `trigger: model_decision` | 收口/验证任务 |
| `60-business-rules` | `description`（智能生效） | `trigger: model_decision` | 计费/频控/路由/流式任务 |
| `70-delivery` | `description`（智能生效） | `trigger: model_decision` | 交付/文档/git 任务 |

> 只有 `00-core` 常驻。其余按文件或场景触发，避免规则过长被模型当背景噪音忽略。

## 4. 维护方式

1. 改规范 → 只编辑 `docs/standards/rules/*.md`。
2. 重新生成工具文件：

```bash
python docs/standards/generate-tool-rules.py
```

3. 若改动影响核心条款，同步更新根目录 `AGENTS.md` 与 `.workbuddy/memory/MEMORY.md` 的指针段落。
4. 生成的工具规则文件**不要手工改**（下次生成会覆盖）。

## 5. 规范的效力

- 本规范**优先于模型通用知识**。与代码实际状态冲突时，**以代码为准并回来修订本规范**（记忆与文档会过期，代码不会）。
- 命名纪律（10-backend-java §4）适用于新增与重命名；既有代码按批次整改，不要求一次性全改。

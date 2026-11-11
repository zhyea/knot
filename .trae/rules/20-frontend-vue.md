---
description: knot 前端 Vue3 + TypeScript 规范：类型约定、已知陷阱、既定模式
globs: ["knot-front/src/**"]
alwaysApply: false
---

> 本文件由 `docs/standards/generate-tool-rules.py` 自动生成，**勿手工编辑**。权威源：`docs/standards/rules/20-frontend-vue.md`。

# 20 · 前端 Vue / TS 规范

> 适用：`knot-front/src/**`。完整索引见 `docs/standards/PROJECT-STANDARDS.md`。

## 1. 技术栈与脚本

Vue 3 + Vite + Element Plus + TypeScript（`knot-front/`）。

**测试已引入 vitest + @vue/test-utils（2026-10-21 起）**。质量门**五道，顺序固定**（2026-11-11 起）：

```bash
npm test                                   # 1. vitest run
npx vite build                             # 2. 构建：生成 components.d.ts / auto-imports.d.ts（后三道的前置）
npx vue-tsc --noEmit                       # 3. 类型检查（生产代码，排除 *.test.ts）
npx vue-tsc --noEmit --noUnusedLocals      # 4. 死代码检查
npm run type-check:test                    # 5. 测试代码类型检查（tsconfig.test.json）
```

每次前端改动**五道都要过**。⚠ 构建必须排在三道类型检查之前：`components.d.ts` /
`auto-imports.d.ts` 是 unplugin 的构建产物且被 gitignore，缺它们时 `<el-xxx>` 全部退化成 any
（干净 checkout 首跑会得到 35 个 `TS7006`）。⚠ **CI 必须执行 `npm test`**——写了测试而 CI 不跑等于没写。

> 第 5 道是新增的（2026-11-11）：`tsconfig.json` 的 `exclude` 排除 `**/*.test.ts`，vitest 只证明
> 运行时路径通过；单独用 `tsconfig.test.json`（只放开 `exclude`）首跑即扫出 15 处，包括
> 「给裸数组端点 mock 了 `{list,total}` 信封」和「`toContain` 的消息参数错位导致断言消息从未生效」。

> 历史订正：本文件 §1 曾写「无测试框架，质量门只有两道」，与代码实际情况脱节，2026-10-24 修订。
> 这就是"规范腐化"的实例，教训见 `00-core.md` 铁律 9。

## 2. 已知陷阱

| 陷阱 | 表现 | 处理 |
|---|---|---|
| `components.d.ts` 被 vite 改写/缺失 | vue-tsc 满屏 `TS7006`（隐式 any） | 它是 unplugin 生成物且被 gitignore；修复方式是 `npx vite build` 重新生成（**不要** `npm run build`，那会先跑 type-check 直接失败） |
| `dist/` 残留 | 构建产物混入 | build 前删除 `dist`；build 后 diff 复核产物未被截断 |
| npm 并发装包 | `node_modules` 互相抢占 | 装依赖前先查残留 `npm-cli.js install` 进程 |
| `package-lock.json` 被写坏（空 version 条目） | `TypeError: Invalid Version:` | 直接删锁文件重装，不要逐条修 |
| 新增的 `*.json` 配置被 `.gitignore` 的 `*.json` 吞掉 | `git status` 里根本不出现，本地全绿、干净 checkout 直接失败 | 落地前先 `git check-ignore -v <file>`；命中就回 `.gitignore` 的「可复现构建资产」段加 `!` 否定规则。2026-11-11 一天内踩了两次（夹具、`tsconfig.test.json`） |

## 3. TypeScript 约定

- 组件 props **显式类型**：`Array as PropType<Row[]>`，不用裸 `Array`。
- 避免 `Map` / 松散对象，倾向 `interface` / `type` / 枚举显式结构化。
- 不要用 `any` 兜底；第三方无类型时先补 `*.d.ts`。
- **API 层必须显式写响应泛型**（`src/api/*.ts`）：`http.ts` 的 `get/post/put/del` 默认 `T = any`，
  不写泛型就等于放弃校验。至少钉住**信封层**：`/list` 用 `PageResult<Dict>`、裸数组端点用 `Dict[]`、
  写操作回整行用 `Dict`、`delete` 返回 void 的才用 `void`。⚠ 光看前端看不出来——**逐条对照 admin
  控制器的方法返回类型**（同一批接口里「权限四表的 delete 返回整行、预设的 delete 返回 void」就是实例）。
- 业务码唯一性校验统一用 `CodeAvailability`（`src/types`），别各自写 `{available: boolean}`。
- `defineEmits` 声明必须与模板实际派发一致（死声明要清掉）。

## 4. 状态值与枚举

- ⚠ **`status` 为 0 是合法值**（禁用）。禁止 `if (status)` / `status || 1` 这类 truthy 判断，一律写 `status === 1` 或判 `!= null`。
- ⭐ **字符串默认值一律用 `??`，不要用 `||`**（2026-10-24 立规）。`||` 会把空串 `""`、`0`、`false` 一并当"没值"，而空串常是合法输入。
  反例：`useAuthorizationManagement.ts:882` `row.status || "ENABLED"` —— 后端返回空串时被误置为启用态。
  正例：`row.status ?? "ENABLED"`。
  ⚠ 例外：**纯展示兜底**（`health.status || "--"`）与**非状态类默认值**（`action.type || 'primary'`、`field.placeholder || '请选择'`）可以用 `||`——它们不承载业务状态语义。
- 枚举选项统一来自 `GET /api/enum-options`（与 DB 枚举不混用）。

## 5. 既定模式（沿用，不要另起炉灶）

| 场景 | 唯一出口 |
|---|---|
| 下拉选项 | `POST /api/{resource}/options` → `OptionPage<OptionItem>`；`OptionItem(value/label/code/disabled/meta)`，meta 承载派生属性 |
| 远程选择器 | `RemoteEntitySelect`（remote 模式，extraParams 变化自动清空重载） |
| 树形两级下拉 | `TreeSelect`（纯本地组件：两级树、`leafOnly` 默认仅叶子可选、filterable 本地过滤+高亮、默认全展开、面板宽度自适应）；远程资源另写场景封装（加载+分组），如 `ProviderAccountTreeSelect`（供应商→账户） |
| 列表查询 | `composables/useListQuery` |
| 表单本地草稿 + 防回环 | `composables/useDraftModel.ts`（守卫：normalize 后 `JSON.stringify` 比较） |
| 空闲登出 | `idleActivity.ts`（30 分钟） |

## 6. 业务计算

- ⭐ **枚举 code 一律走 `constants/status.ts` 的常量对象，禁止裸字面量比较**（2026-10-24 立规，全仓已清零）。现有：`EnabledStatus`/`OperationLogStatus`/`ScheduledTaskRunStatus`/`LogicalModelPublishStatus`/`ExternalModelSyncStatus`/`PluginPackageStatus`/`RouteTargetType`/`BillingMode`/`PricingPlan`/`BillingUnit`/`RoutingTestStatus`/`RoutingTestRunState`。
  - ⚠ **两个易错点**：`PricingPlanEnum` 的 code 是 `name()`（`FIXED`/`TIERED`/`PEAK_OFF_PEAK`），构造第一个参数是 **label**（"固定价"），别拿 label 当 code；`BillingUnitEnum` 常量名 `ONE_K_TOKENS` 但 code 是 **`"1K_TOKENS"`**。
  - ⚠ **自造态必须与后端枚举分开声明**：`RoutingTestStatus`（后端结论，仅 `SUCCESS`/`FAILED`）与 `RoutingTestRunState`（抽屉本地运行态 `RUNNING`/`STOPPED`/`ERROR`）分两个常量，javadoc 写明后者「一律按无结论处理」——**不要把前端自造态混进后端枚举常量里**，否则读代码的人会以为后端真会返回它。
  - label 展示仍走 `enumLabelOf` / `useEnumOptions`，本文件只做字面量收口。
- 计费相关前端工具：`utils/billingTier.ts` / `utils/billingPrice.ts`，精度常量 `UNIT_PRICE_PRECISION = 8`。
- 计费金额字段是**字符串**（8 位小数），前端不要按 number 做四则运算。

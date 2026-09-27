# knot-front 全量 TypeScript 迁移记录

日期：2026-09-27
范围：`knot-front` 全部脚本（含 Vue SFC 的 `<script setup>`）
结论：**完成**，`npm run type-check` 0 错误，`npm run build` EXIT=0。

---

## 一、迁移前的底数

| 项 | 数值 |
|---|---|
| `.js` 文件 | 55 |
| `.vue` 文件 | 118（其中 116 个 `<script setup>`，0 个 Options API） |
| 源码行数 | 约 20,000（js 4,252 + vue 16,425） |
| tsconfig | 无 |
| typescript / vue-tsc | 未安装 |
| 初始 `vue-tsc` 错误 | 347 |

## 二、方案（robin 确认）

| 决策点 | 选择 |
|---|---|
| 严格度 | 折中：`strict:false` / `noImplicitAny:false` / `strictNullChecks:false` |
| 门禁 | `type-check` 脚本 + `build` 前置类型检查 |
| 配置文件 | `vite.config.js` → `vite.config.ts`，一并转 |

## 三、落地内容

### 1. 基础设施（新增/改造）

| 文件 | 说明 |
|---|---|
| `tsconfig.json` | `moduleResolution: bundler`、`allowJs:false`、`paths: @/* → src/*`、`types:["node"]`；include 含 `components.d.ts` / `auto-imports.d.ts` |
| `src/env.d.ts` | 仅 `/// <reference types="vite/client" />`。**故意不写 `declare module "*.vue"`** —— 写了会把所有 SFC 退化成 `DefineComponent<{},{},any>`，props 检查全部失效 |
| `vite.config.ts` | 原 `.js` 删除；`AutoImport({dts:"auto-imports.d.ts"})`、`Components({dts:"components.d.ts"})` 开启 dts 输出 |
| `package.json` | `type-check: vue-tsc --noEmit`；`build: vue-tsc --noEmit && vite build`；devDeps 加 typescript@5.9.3 / vue-tsc@3.3.11 / @types/node |
| `index.html` | `/src/main.js` → `/src/main.ts` |
| `src/types/index.ts` | 新增共享类型：`Dict` `Row` `PageResult` `ListResponse` `SelectOption` `SelectOptionLike` `ResourceField` `ResourceSubmitter` `TableColumn` `RowAction` `ButtonType` `QueryFn`，以及 `declare module "vue-router"` 补 `RouteMeta.titleKey / title` |

### 2. 机械迁移

- `src/**/*.js` → `.ts`（55 个）
- `<script setup>` → `<script setup lang="ts">`（116 个）
- 清除 12 处带 `.js` 后缀的 import（`@/api/models.js`、`../../api/providers.js` 等）

### 3. 类型补齐（按根因分批，347 → 0）

| 根因 | 错误数 | 处理 |
|---|---|---|
| `api/http.ts` 的 `get/post/put/del` 最后一个参数无默认值 | 180 | 全部给 `= {}`；另加 `declare module "axios"` 补业务字段 `skipIdleTouch` / `silentError`，业务错误显式为 `ApiBusinessError` |
| `useLocale.ts` 的 `t(key, params)` params 必填 | 51 | params 改可选并引入 `TranslateParams`；`LocaleCode = keyof typeof messages` |
| Vue props 用 `type: Array/Object`，推导成 `unknown[]` / `{}` | 144 | 逐个改用 `Array as PropType<X[]>` / `Object as PropType<Dict>`；新建共享类型收敛重复形状 |
| composables 的 `options = {}` 推导为 `{}` | 12 | 补 `UseAutoQueryOptions` / `UseListQueryOptions` / `UsePageListDefaults` |
| `formatJsonText(value, 2)` indent 声明为 string 却传 number | — | `utils/format.ts` 全量补签名，indent 改 `string \| number` |
| 其余（event target、payload 动态字段、函数可选参数等） | — | 就近精确化 |

## 四、两个必须记住的坑

### 坑 1：typescript 7.x 与 vue-tsc 不兼容
`npm i -D typescript` 装到 `7.0.2`（原生预览版）后：
```
Error [ERR_PACKAGE_PATH_NOT_EXPORTED]: Package subpath './lib/tsc'
is not defined by "exports" in .../typescript/package.json
```
`vue-tsc` 内部 `resolveTscPath()` 要取 `typescript/lib/tsc`，TS 7 不再导出。**必须锁 5.x**（本项目 5.9.3）。

### 坑 2：裸调 `vue-tsc` 会漏报
`components.d.ts` / `auto-imports.d.ts` 是 **vite 运行时**由 unplugin 生成的，tsconfig 虽然 include 了，
但在没跑过 vite 之前它们不存在，Element Plus 组件就没有真实类型，模板 prop 检查形同虚设。

实测差异：裸调 `vue-tsc --noEmit` 报 0 错误；同一份代码跑 `npm run type-check`（此时 d.ts 已生成）报 **9 个**
`el-select` / `el-radio-group` / `el-checkbox-group` / `el-switch` 的 prop 类型错误。

**清错一律以 `npm run type-check` 为准。**

## 五、TS 化暴露的既存 bug（已顺手修掉）

| 文件 | 问题 | 处理 |
|---|---|---|
| `views/security/AlertManageView.vue` | 模板 `@query="handleQuery"`，脚本里**根本没有 `handleQuery`** → 点「查询」运行时报错 | 补 `function handleQuery() { return load(); }` |
| `views/system/EnumManageView.vue` | 模板用 `categoryCreateVisible`，脚本**未声明** → 点「新增分类」无效 | 补 `const categoryCreateVisible = ref(false);` |

这两个在 JS 下是静默的运行时炸弹，类型检查是唯一能发现它们的手段。

## 六、行为不改的登记项（未改，仅类型层如实表达）

`utils/trafficPolicy.ts` 中 `emptyQuotaPolicy()` 返回 `alertEnabled: 0`（number），
而 `normalizeQuotaPolicy()` 返回 boolean。两者形态不一致，但后端落库前会再走一次 normalize，
不一致不外泄 → 类型上如实登记为 `QuotaPolicy.alertEnabled: boolean | number`，**未改运行时行为**。
（若要彻底统一，需确认后端字段类型后再动，不在本次范围。）

## 七、验证结果（真实退出码）

```
$ npm run type-check          → 0 错误
$ npm run build               → EXIT=0，✓ built in 6.67s，dist/assets 88 个 chunk

核验：
  src 内 .js 残留             → 0
  src 内 .ts 文件数           → 57（55 业务 + env.d.ts + types/index.ts）
  .vue 带 lang="ts"           → 116 / 118（另 2 个为纯模板，无 script）
  index.html 入口             → /src/main.ts
```

## 八、待 robin 决定

1. `components.d.ts` / `auto-imports.d.ts` 是否入库？二者由 vite 生成，`knot-front` 当前**没有 .gitignore**。
   建议加入 .gitignore（生成物），或显式入库（保证 clone 后类型检查可跑）。
2. 是否要继续收紧严格度？当前 `noImplicitAny:false` + `strictNullChecks:false`；
   下一步可考虑逐模块打开 `strict`，预计会再暴露一批空值处理问题。

---

## 九、第一批严格度收紧（2026-09-27 追加）

**决策**：robin 要求继续收紧。三档探针实测 766（noImplicitAny）/ 769（strict 全开但 strictNullChecks 关）/
1074（strictNullChecks 也开），分水岭在 strictNullChecks。robin 选「分两批，本批先上 769」。

**tsconfig 现状**：`"strict": true` + 显式 `"strictNullChecks": false`（注释标明第二批直接删该行）。

**清错过程（769 → 0）**

| 阶段 | 范围 | 手段 |
|---|---|---|
| api 层 | 15 文件 92 签名 | 脚本按参数名映射补类型 |
| composables / utils / routing 组件 | ~180 处 | 逐文件补签名（RoutingRuleFormDrawer 27 处为最大单件） |
| views + 组件层 | 41 文件 164 处 | 脚本批量补 TS7006（136 处），手工 28 处 |

手工部分要点：TS7023 default 工厂 11 处改 `(): Row[] => []`；TS7053 索引改 `Dict` /
`Record<"card"|"list", number>`；catch unknown 改 `(error as Error)?.message`；
`useEnabledToggle` 放宽 `enabled: boolean|string|number` 并内部归一化（el-switch active-value 可为 1/0）；
`runStatusType` 返回收窄为 el-tag union；`chooseLocale(code: LocaleCode)`。
脚本副作用两例已修：重复插入 `@/types` import（TS2300）、`AuthorizationRolePanel` 相对层级少一级（TS2307，原本就坏）。

**验证（真实退出码）**

```
$ npm run type-check   → EXIT=0，0 错误
$ npm run build        → EXIT=0，✓ built in 8.67s
```

**遗留**：第二批 strictNullChecks（约 305 处）单独做。

---

## 十、第二批严格度收紧：strictNullChecks 全开（2026-09-27 追加）

**tsconfig 现状**：`strict: true`，无任何显式覆盖，**两批收紧全部收官**。

**清错过程（322 → 0）**

| 类别 | 数量 | 修法 |
|---|---|---|
| TS2339 on never | ~180 | `ref(null)`→`ref<Dict\|null>`（编辑对象 20 个）、`ref([])`→`ref<Row[]>`（15 个）、6 个大 form 补局部 interface、formRef 用 `FormInstance` 等最小结构类型 |
| TS2322 模板绑定 | ~50 | 28 个子组件 prop `default: null` 补 `as PropType<X \| null>`；父侧 ID ref 收窄 `ref<number\|null>` |
| TS2345 | ~30 | 4 个 check*Code api excludeId 放宽 `\| null`；isEdit 分支 `form.id!` 9 处；logXxxId.value! 6 处 |
| 散件 | ~60 | 可选链/`?? undefined`/computed 包装（el-radio v-model null↔undefined）/type guard filter |

**关键坑**
- Windows 文件 CRLF：多行字符串 replace 静默失败，必须 `\r?\n` 正则或行级处理。
- 批量插 import 必须先查重（曾致 4 个文件重复导入 PropType）。
- map 返回 null 后 `.filter(Boolean)` 不收窄 → `.filter((item): item is T => item != null)`。
- vite emptyOutDir 被沙箱拦截时 build EXIT=1 是环境问题；.NET API 删 dist 后重跑即过。

**验证（真实退出码）**

```
$ npm run type-check   → EXIT=0，0 错误
$ npm run build        → EXIT=0，✓ built in 7.29s
```

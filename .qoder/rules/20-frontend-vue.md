---
trigger: glob
description: knot 前端 Vue3 + TypeScript 规范：类型约定、已知陷阱、既定模式
glob: knot-front/src/**
---

> 本文件由 `docs/standards/generate-tool-rules.py` 自动生成，**勿手工编辑**。权威源：`docs/standards/rules/rules/20-frontend-vue.md`。

# 20 · 前端 Vue / TS 规范

> 适用：`knot-front/src/**`。完整索引见 `docs/standards/PROJECT-STANDARDS.md`。

## 1. 技术栈与脚本

Vue 3 + Vite + Element Plus + TypeScript（`knot-front/`）。**无测试框架**，质量门只有两道：

```bash
npx vue-tsc --noEmit    # 类型检查
npx vite build          # 构建
```

每次前端改动**两道都要过**，且顺序固定。

## 2. 已知陷阱

| 陷阱 | 表现 | 处理 |
|---|---|---|
| `components.d.ts` 被 vite 改写/缺失 | vue-tsc 满屏 `TS7006`（隐式 any） | 它是 unplugin 生成物且被 gitignore；修复方式是 `npx vite build` 重新生成（**不要** `npm run build`，那会先跑 type-check 直接失败） |
| `dist/` 残留 | 构建产物混入 | build 前删除 `dist`；build 后 diff 复核产物未被截断 |
| npm 并发装包 | `node_modules` 互相抢占 | 装依赖前先查残留 `npm-cli.js install` 进程 |
| `package-lock.json` 被写坏（空 version 条目） | `TypeError: Invalid Version:` | 直接删锁文件重装，不要逐条修 |

## 3. TypeScript 约定

- 组件 props **显式类型**：`Array as PropType<Row[]>`，不用裸 `Array`。
- 避免 `Map` / 松散对象，倾向 `interface` / `type` / 枚举显式结构化。
- 不要用 `any` 兜底；第三方无类型时先补 `*.d.ts`。
- `defineEmits` 声明必须与模板实际派发一致（死声明要清掉）。

## 4. 状态值与枚举

- ⚠ **`status` 为 0 是合法值**（禁用）。禁止 `if (status)` / `status || 1` 这类 truthy 判断，一律写 `status === 1` 或判 `!= null`。
- 枚举选项统一来自 `GET /api/common/enums`（与 DB 枚举不混用）。

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

- 计费相关前端工具：`utils/billingTier.ts` / `utils/billingPrice.ts`，精度常量 `UNIT_PRICE_PRECISION = 8`。
- 计费金额字段是**字符串**（8 位小数），前端不要按 number 做四则运算。

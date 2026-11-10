import {describe, expect, it} from "vitest";
import {readFileSync, readdirSync} from "node:fs";
import {join, relative, resolve} from "node:path";

/**
 * 列表页「打开即加载」守门。
 *
 * <p>背景判例（2026-11-10）：`AsyncTaskView` 用了 `useListQuery`，却既没 `onMounted(load)`
 * 也没在 setup 顶层调 `load()` —— 打开页面永远是空表，必须手点「查询」才有数据。
 * 这类遗漏不会报错、不会让类型检查变红，只能靠人眼发现，所以用源码断言兜住。</p>
 *
 * <p>判据：`useListQuery` 自身不发起首屏请求（它要等调用方给时机，否则与已有的顶层
 * `load()` 重复请求），因此凡是使用它的视图，必须至少满足一条 ——</p>
 * 1. 出现 `onMounted(...)`（`onMounted(load)` / `onMounted(() => load())` 都算）；
 * 2. setup 顶层有一条无缩进的调用语句（`load();` / `pageLoad();` / `refreshAll();` 等）。
 *
 * <p>第 2 条刻意宽松到「任意顶层调用」：`DepartmentManageView` 的入口是 `refreshAll()`
 * （内部再调 `load()`），`UserManageView` 解构时把 `load` 重命名成 `pageLoad`。
 * 只要首屏有语句被执行到，就等于会加载；反过来，一个顶层调用都没有的文件必然不会加载。</p>
 */

const viewsRoot = resolve(process.cwd(), "src/views");

function collectViews(dir: string, acc: string[] = []): string[] {
  for (const entry of readdirSync(dir, {withFileTypes: true})) {
    const full = join(dir, entry.name);
    if (entry.isDirectory()) {
      collectViews(full, acc);
    } else if (entry.name.endsWith(".vue")) {
      acc.push(full);
    }
  }
  return acc;
}

/** setup 顶层语句：行首无空白，形如 `foo();` */
const TOP_LEVEL_CALL = /^[A-Za-z_$][\w$]*\(\);/m;

describe("列表页初始加载", () => {
  const listViews = collectViews(viewsRoot).filter((file) =>
    readFileSync(file, "utf8").includes("useListQuery(")
  );

  it("扫描到全部 useListQuery 视图（防止断言因匹配失效而空转）", () => {
    // 数量下限只是防退化：漏一份就是这份守门没意义，不追求精确数字
    expect(listViews.length).toBeGreaterThanOrEqual(20);
    // Windows 路径分隔符是反斜杠，比较前先归一，别让断言因平台而空转
    const normalized = listViews.map((file) => file.replace(/\\/g, "/"));
    expect(normalized.some((file) => file.endsWith("routing/AsyncTaskView.vue"))).toBe(true);
  });

  it("每个列表视图在打开时都会发起首次查询", () => {
    const missing = listViews.filter((file) => {
      const source = readFileSync(file, "utf8");
      return !/onMounted\s*\(/.test(source) && !TOP_LEVEL_CALL.test(source);
    });

    for (const file of missing) {
      console.error(`[list-view] 缺少首屏加载：${relative(process.cwd(), file)}`);
    }
    expect(missing).toEqual([]);
  });
});

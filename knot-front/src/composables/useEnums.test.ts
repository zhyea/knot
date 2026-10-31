import {describe, it, expect, vi, beforeEach} from "vitest";
import {listEnumItemsByCategory} from "@/api/enums";
import {clearEnumCache, useEnums} from "@/composables/useEnums";

/**
 * 枚举加载的失败路径——这组用例就是 2026-10-31 那 **43 个 Unhandled Rejection** 的守门。
 *
 * 起因：`loadOptions` 当时只有 `try/finally`，而全站调用点都是
 * `onMounted(() => loadOptions(true))` 这种不接 Promise 的写法，接口一 5xx 就没人接异常。
 * 修法是在 composable 内 catch，所以必须把下面四条钉住，防止哪天被「顺手」改回去：
 *
 * ① `loadOptions` **永不 reject**（抛出去就是 unhandled rejection）；
 * ② 失败**保留**上一次成功的选项，不清空；
 * ③ 失败**不写缓存**，一次抖动不会锁死 5 分钟；
 * ④ 重试成功后 `error` 自动清零。
 *
 * ⚠ `CACHE` 是模块级的，每个用例前必须 `clearEnumCache()`，否则用例之间互相串。
 */
vi.mock("@/api/enums", () => ({listEnumItemsByCategory: vi.fn()}));

const mockList = vi.mocked(listEnumItemsByCategory);

function item(code: string, enabled = true) {
  return {itemCode: code, itemLabel: `${code}-label`, isEnabled: enabled};
}

describe("useEnums 失败路径", () => {
  beforeEach(() => {
    clearEnumCache();
    mockList.mockReset();
  });

  it("接口失败不 reject，loading 复位，error 被记录", async () => {
    mockList.mockRejectedValueOnce(new Error("500"));

    const {options, loading, error, loadOptions} = useEnums("demo");

    // ⭐ 本缺陷的核心断言：调用方不接 Promise 也不会炸
    await expect(loadOptions(true)).resolves.toBeUndefined();

    expect(loading.value).toBe(false);
    expect(error.value).toBeInstanceOf(Error);
    expect(options.value).toEqual([]);
  });

  it("先成功后失败：保留上一次成功的选项，不清空", async () => {
    mockList.mockResolvedValueOnce([item("A"), item("B")]);
    const {options, error, loadOptions} = useEnums("demo");
    await loadOptions(true);
    expect(options.value.map((i) => i.itemCode)).toEqual(["A", "B"]);

    // force 重载失败：选项必须还在（清空会让已选值对应的选项消失，界面变成裸 code）
    mockList.mockRejectedValueOnce(new Error("502"));
    await loadOptions(true);

    expect(error.value).toBeInstanceOf(Error);
    expect(options.value.map((i) => i.itemCode)).toEqual(["A", "B"]);
  });

  it("失败不写缓存：连续三次 force 都重新请求", async () => {
    mockList.mockRejectedValue(new Error("500"));
    const {loadOptions} = useEnums("demo");

    await loadOptions(true);
    await loadOptions(true);
    await loadOptions(true);

    // 若失败也写了缓存，后面两次会命中而不再请求
    expect(mockList).toHaveBeenCalledTimes(3);
  });

  it("失败后重试成功：error 自动清零，选项刷新", async () => {
    mockList.mockRejectedValueOnce(new Error("500"));
    const {options, error, loadOptions} = useEnums("demo");
    await loadOptions(true);
    expect(error.value).toBeInstanceOf(Error);

    mockList.mockResolvedValueOnce([item("C")]);
    await loadOptions(true);

    expect(error.value).toBeNull();
    expect(options.value.map((i) => i.itemCode)).toEqual(["C"]);
  });
});

describe("useEnums 正常路径（确认 catch 没改变成功行为）", () => {
  beforeEach(() => {
    clearEnumCache();
    mockList.mockReset();
  });

  it("enabledOnly 过滤掉 isEnabled === false 的项", async () => {
    mockList.mockResolvedValueOnce([item("ON"), item("OFF", false)]);

    const enabled = useEnums("demo");
    await enabled.loadOptions(true);
    expect(enabled.options.value.map((i) => i.itemCode)).toEqual(["ON"]);

    clearEnumCache();
    mockList.mockResolvedValueOnce([item("ON"), item("OFF", false)]);
    const all = useEnums("demo", false);
    await all.loadOptions(true);
    expect(all.options.value.map((i) => i.itemCode)).toEqual(["ON", "OFF"]);
  });

  it("非 force 且缓存未过期：第二次不发请求", async () => {
    mockList.mockResolvedValueOnce([item("A")]);
    const {options, loadOptions} = useEnums("demo");

    await loadOptions();
    await loadOptions();

    expect(mockList).toHaveBeenCalledTimes(1);
    expect(options.value.map((i) => i.itemCode)).toEqual(["A"]);
  });
});

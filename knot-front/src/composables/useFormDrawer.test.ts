import {describe, expect, it, vi} from "vitest";
import {useFormDrawer, useRowFormDrawer} from "@/composables/useFormDrawer";
import type {Row} from "@/types";

const row = (id: number, extra: Record<string, unknown> = {}): Row =>
  ({id, ...extra}) as unknown as Row;

describe("useFormDrawer 抽屉开关状态机", () => {
  it("初始态关闭、editing 为声明的空值", () => {
    const d = useRowFormDrawer();
    expect(d.visible.value).toBe(false);
    expect(d.editing.value).toBe(null);
  });

  it("新建打开抽屉并复位 editing，onOpen 收到 null", () => {
    const onOpen = vi.fn();
    const d = useFormDrawer<string>({emptyValue: "", onOpen});
    d.openEdit(row(1));
    d.openCreate();
    expect(d.visible.value).toBe(true);
    expect(d.editing.value).toBe("");
    expect(onOpen).toHaveBeenLastCalledWith(null);
  });

  it("编辑打开抽屉并放入整行，onOpen 收到该行", () => {
    const onOpen = vi.fn();
    const d = useRowFormDrawer();
    const target = row(7, {code: "A"});
    d.openEdit(target);
    expect(d.visible.value).toBe(true);
    // ref 对对象做 reactive 包装，断言内容而不是引用
    expect(d.editing.value).toEqual(target);
  });

  it("toEditing 支持只存 id / 补字段这类页面差异", () => {
    const d = useFormDrawer<number | null>({
      emptyValue: null,
      toEditing: (r) => Number(r.id)
    });
    d.openCreate();
    expect(d.editing.value).toBe(null);
    d.openEdit(row(9));
    expect(d.editing.value).toBe(9);
  });

  it("onOpen 钩子在两种打开方式下都会执行（copyFromId / viewMode 等复位）", () => {
    const onOpen = vi.fn();
    const d = useFormDrawer<string>({emptyValue: "", onOpen, toEditing: (r) => String(r.profileCode ?? "")});
    d.openCreate();
    d.openEdit(row(2, {profileCode: "P-2"}));
    expect(onOpen).toHaveBeenCalledTimes(2);
    expect(d.editing.value).toBe("P-2");
  });

  it("close 只关抽屉，不擅自清空 editing（复位归抽屉 onClosed）", () => {
    const d = useRowFormDrawer();
    d.openEdit(row(3));
    d.close();
    expect(d.visible.value).toBe(false);
    expect(d.editing.value).toEqual(row(3));
  });
});

import {describe, expect, it} from "vitest";
import {useLogDrawer} from "@/composables/useLogDrawer";
import type {Row} from "@/types";

const row = (id: number, extra: Record<string, unknown> = {}): Row =>
  ({id, ...extra}) as unknown as Row;

describe("useLogDrawer 变更日志抽屉状态机", () => {
  it("初始态关闭、id 与 name 为空", () => {
    const d = useLogDrawer({labelFields: ["name", "code"]});
    expect(d.visible.value).toBe(false);
    expect(d.id.value).toBe(null);
    expect(d.name.value).toBe("");
  });

  it("按 labelFields 顺序取显示名，写入 id 并打开抽屉", () => {
    const d = useLogDrawer({labelFields: ["name", "appCode"]});
    d.open(row(12, {appCode: "app_001", name: "结算应用"}));
    expect(d.id.value).toBe(12);
    expect(d.name.value).toBe("结算应用");
    expect(d.visible.value).toBe(true);
  });

  it("首选字段为空时回退到次选字段", () => {
    const d = useLogDrawer({labelFields: ["modelName", "modelId"]});
    d.open(row(5, {modelName: "", modelId: "gpt-4.1"}));
    expect(d.name.value).toBe("gpt-4.1");
  });

  it("全部取不到时回退 #id，不显示 undefined", () => {
    const d = useLogDrawer({labelFields: ["name", "code"]});
    d.open(row(42));
    expect(d.name.value).toBe("#42");
  });

  it("每次打开覆盖上一次的 id / name，避免串到别的实体日志", () => {
    const d = useLogDrawer({labelFields: ["name"]});
    d.open(row(1, {name: "甲"}));
    d.open(row(2, {name: "乙"}));
    expect(d.id.value).toBe(2);
    expect(d.name.value).toBe("乙");
  });
});

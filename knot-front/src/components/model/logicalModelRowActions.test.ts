import {describe, expect, it} from "vitest";
import {logicalModelRowActions} from "@/components/model/logicalModelRowActions";
import type {Row} from "@/types";

const keysOf = (row: Row) => logicalModelRowActions(row).map((action) => action.key);

describe("logicalModelRowActions 表格/卡片共用行操作", () => {
  it("正常态提供编辑、日志、删除，删除带确认", () => {
    const actions = logicalModelRowActions({id: 1} as Row);
    expect(keysOf({id: 1} as Row)).toEqual(["edit", "log", "delete"]);
    expect(actions[2].confirm).toBe("确认删除该统一模型？");
    expect(actions[2].type).toBe("danger");
  });

  it("已逻辑删除只提供日志与恢复，恢复带确认", () => {
    const actions = logicalModelRowActions({id: 1, deleted: true} as Row);
    expect(actions.map((a) => a.key)).toEqual(["log", "restore"]);
    expect(actions[1].confirm).toBe("确认恢复该统一模型？");
    expect(actions[1].type).toBe("success");
  });

  it("deleted 必须严格等于 true，其他真值不触发删除态分支", () => {
    expect(keysOf({id: 1, deleted: 1} as unknown as Row)).toEqual(["edit", "log", "delete"]);
  });
});

// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach } from "vitest";
import { nextTick } from "vue";
import { mount, flushPromises } from "@vue/test-utils";
import { ElSwitch, ElTag } from "element-plus";
import type { Row } from "@/types";
import LogicalModelList from "@/components/model/LogicalModelList.vue";
import { EMPTY_PLACEHOLDER } from "@/utils/format";

// 隔离网络：列表行启用开关最终调这个 API
vi.mock("@/api/logicalModels", () => ({
  updateLogicalModelStatus: vi.fn().mockResolvedValue(undefined)
}));

// 隔离枚举下拉加载（组件 onMounted 会调，避免真实请求）
vi.mock("@/composables/useEnums", () => ({
  useEnums: () => ({ options: { value: [] as unknown[] }, loadOptions: vi.fn() }),
  resolveEnumLabel: vi.fn(() => "-")
}));

// 类型列改走 useModelTypeText() -> useEnumOptions() 后，组件不再接收 modelTypeOptions。
// 在枚举数据源层隔离 /api/enum-options，同时保留工厂加载链可供断言。
const { labelOf, useEnumOptions } = vi.hoisted(() => {
  const labelOf = vi.fn((enumName: string, code: unknown, fallback?: string) => {
    if (code === null || code === undefined || code === "") return fallback ?? "—";
    return fallback || `${enumName}:${String(code)}`;
  });
  const useEnumOptions = vi.fn(() => ({ labelOf }));
  return { labelOf, useEnumOptions };
});

vi.mock("@/composables/useEnumOptions", () => ({ labelOf, useEnumOptions }));

import { updateLogicalModelStatus } from "@/api/logicalModels";

/**
 * 数据契约（已查后端）：LogicalModelDto.enabled 是 primitive boolean，
 * 由 EnabledStatusEnum.isEnabled(status) 从数值 status 列派生，Jackson 序列化为
 * JSON true/false。所以前端 rows[].enabled 永远是 boolean——本测试按真实契约喂数据，
 * 钉死 `:model-value="row.enabled !== false"` 的渲染与 useEnabledToggle 的接线。
 * 若未来某端点误把数值 status(0/1) 当 enabled 下发，此处的渲染/交互断言会立刻变红，
 * 提示回归。
 */
function row(id: number, enabled: boolean): Row {
  return { id, modelCode: `M${id}`, enabled: enabled as Row["enabled"] };
}

async function mountList(rows: Row[]) {
  const wrapper = mount(LogicalModelList, { props: { rows } });
  // el-table 在 jsdom 下需要一次 nextTick 把 body 行渲染出来
  await nextTick();
  await nextTick();
  return wrapper;
}

describe("LogicalModelList 启用状态映射（boolean 契约 + 防 truthy 回归）", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("enabled=false 的行：开关 modelValue 必须为 false（禁用）", async () => {
    const wrapper = await mountList([row(1, false)]);
    const switches = wrapper.findAllComponents(ElSwitch);
    expect(switches).toHaveLength(1);
    expect(switches[0].props("modelValue")).toBe(false);
  });

  it("enabled=true 的行：开关 modelValue 必须为 true（启用）", async () => {
    const wrapper = await mountList([row(1, true)]);
    const switches = wrapper.findAllComponents(ElSwitch);
    expect(switches[0].props("modelValue")).toBe(true);
  });

  it("多行混合：按行顺序正确映射 false/true/false", async () => {
    const wrapper = await mountList([row(1, false), row(2, true), row(3, false)]);
    const switches = wrapper.findAllComponents(ElSwitch);
    expect(switches.map((s) => s.props("modelValue"))).toEqual([false, true, false]);
  });

  it("开启禁用行：触发 change(true) 调 updateLogicalModelStatus(id, true) 并发 changed 事件", async () => {
    const rows = [row(7, false)];
    const wrapper = await mountList(rows);
    const switches = wrapper.findAllComponents(ElSwitch);
    await switches[0].vm.$emit("change", true);
    await flushPromises();
    await nextTick();

    expect(updateLogicalModelStatus).toHaveBeenCalledTimes(1);
    expect(updateLogicalModelStatus).toHaveBeenCalledWith(7, true);
    expect(wrapper.emitted("changed")).toBeTruthy();
    // 乐观更新：行内 enabled 已翻成 true
    expect((rows[0].enabled as unknown)).toBe(true);
  });

  it("关闭启用行：触发 change(false) 调 updateLogicalModelStatus(id, false) 且行内翻 false", async () => {
    const rows = [row(8, true)];
    const wrapper = await mountList(rows);
    const switches = wrapper.findAllComponents(ElSwitch);
    await switches[0].vm.$emit("change", false);
    await flushPromises();
    await nextTick();

    expect(updateLogicalModelStatus).toHaveBeenCalledWith(8, false);
    expect((rows[0].enabled as unknown)).toBe(false);
  });

  it("已删除行：开关 disabled（isDeletedRow 严格判 true）", async () => {
    const rows: Row[] = [{ id: 9, modelCode: "M9", enabled: true, deleted: true }];
    const wrapper = await mountList(rows);
    const switches = wrapper.findAllComponents(ElSwitch);
    expect(switches[0].props("disabled")).toBe(true);
  });
});

/**
 * 2026-11-10 回归第二处：表格视图的「类型」列守卫写的是单引号 '-'，而
 * useLogicalModelPresentation.modelTypeLabel 的空值已改为 EMPTY_PLACEHOLDER（—）。
 * 守卫失效会把占位符渲染成一个 el-tag；这里钉死「空值走 span 占位，不成标签」。
 */
describe("LogicalModelList 类型列空值占位与唯一出口同步", () => {
  it("modelType 为空：不渲染 el-tag", async () => {
    const wrapper = await mountList([{ id: 1, modelCode: "M1", enabled: true } as Row]);
    expect(wrapper.findAllComponents(ElTag)).toHaveLength(0);
  });

  it("modelType 为 null / 空串：同样不渲染 el-tag", async () => {
    const rows = [
      { id: 2, modelCode: "M2", enabled: true, modelType: null },
      { id: 3, modelCode: "M3", enabled: true, modelType: "" }
    ] as Row[];
    const wrapper = await mountList(rows);
    expect(wrapper.findAllComponents(ElTag)).toHaveLength(0);
  });

  it("modelType 有值：渲染一个 el-tag 展示 code（未命中枚举时回显 code）", async () => {
    const wrapper = await mountList([{ id: 4, modelCode: "M4", enabled: true, modelType: "CHAT" } as Row]);
    const tags = wrapper.findAllComponents(ElTag);
    expect(tags).toHaveLength(1);
    expect(tags[0].text()).toContain("CHAT");
  });

  it("空值占位文本走唯一出口", async () => {
    const wrapper = await mountList([{ id: 5, modelCode: "M5", enabled: true } as Row]);
    expect(wrapper.text()).toContain(EMPTY_PLACEHOLDER);
  });
});

/** 合并两套 modelTypeLabel 后，类型列只能经代码枚举工厂取值，不能再有 props 透传实现。 */
describe("LogicalModelList 模型类型走唯一出口", () => {
  it("挂载时经 useEnumOptions() 触发加载，并按 ModelTypeEnum 取 label", async () => {
    labelOf.mockClear();
    useEnumOptions.mockClear();
    const wrapper = await mountList([{ id: 10, modelCode: "M10", enabled: true, modelType: "CHAT" } as Row]);
    expect(useEnumOptions).toHaveBeenCalled();
    expect(labelOf).toHaveBeenCalledWith("ModelTypeEnum", "CHAT", "CHAT");
    expect(wrapper.findAllComponents(ElTag)[0].text()).toContain("CHAT");
  });

  it("列表组件不再声明 modelTypeOptions prop", () => {
    const declared = Object.keys((LogicalModelList as unknown as { props: Record<string, unknown> }).props ?? {});
    // 先证明读到的确实是 props 声明表，否则「不含」会假绿
    expect(declared).toContain("rows");
    expect(declared).not.toContain("modelTypeOptions");
  });
});

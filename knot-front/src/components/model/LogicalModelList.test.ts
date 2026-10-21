// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach } from "vitest";
import { nextTick } from "vue";
import { mount, flushPromises } from "@vue/test-utils";
import { ElSwitch } from "element-plus";
import type { Row } from "@/types";
import LogicalModelList from "@/components/model/LogicalModelList.vue";

// 隔离网络：列表行启用开关最终调这个 API
vi.mock("@/api/logicalModels", () => ({
  updateLogicalModelStatus: vi.fn().mockResolvedValue(undefined)
}));

// 隔离枚举下拉加载（组件 onMounted 会调，避免真实请求）
vi.mock("@/composables/useEnums", () => ({
  useEnums: () => ({ options: { value: [] as unknown[] }, loadOptions: vi.fn() }),
  resolveEnumLabel: vi.fn(() => "-")
}));

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

  it("已删除行：开关 disabled（:disabled=\"row.deleted === true\"）", async () => {
    const rows: Row[] = [{ id: 9, modelCode: "M9", enabled: true, deleted: true }];
    const wrapper = await mountList(rows);
    const switches = wrapper.findAllComponents(ElSwitch);
    expect(switches[0].props("disabled")).toBe(true);
  });
});

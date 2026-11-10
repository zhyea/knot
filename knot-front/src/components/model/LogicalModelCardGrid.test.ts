// @vitest-environment jsdom
import {describe, it, expect, vi} from "vitest";
import {mount} from "@vue/test-utils";
import type {Row} from "@/types";
import LogicalModelCardGrid from "@/components/model/LogicalModelCardGrid.vue";

// 卡片视图 onMounted 会加载 DB 枚举，测试里隔离掉；resolveEnumLabel 供 familyLabel 使用。
vi.mock("@/composables/useEnums", () => ({
  useEnums: () => ({options: {value: [] as unknown[]}, loadOptions: vi.fn()}),
  resolveEnumLabel: vi.fn(() => "模型族A")
}));

function mountGrid(row: Row, modelTypeOptions: {value: string; label: string}[] = []) {
  return mount(LogicalModelCardGrid, {
    props: {rows: [row], modelTypeOptions},
    global: {directives: {loading: {}}}
  });
}

function tagsOf(row: Row, modelTypeOptions: {value: string; label: string}[] = []): string[] {
  const wrapper = mountGrid(row, modelTypeOptions);
  return wrapper.findAll(".tag-list .el-tag").map((tag) => tag.text().trim());
}

/**
 * 2026-11-10 回归：抽 useLogicalModelPresentation 后，modelTypeLabel 的空值从手写的 "-"
 * 变成 EMPTY_PLACEHOLDER（—），而 displayTags 的守卫还在判 "-"，空模型类型会被 unshift
 * 成一个真实标签。这组用例钉死「占位符不能被当标签渲染」。
 */
describe("LogicalModelCardGrid.displayTags 空模型类型不得变成标签", () => {
  it("modelType 为 null：只保留真实标签，不出现占位符", () => {
    expect(tagsOf({id: 1, modelCode: "M1", modelType: null, tags: ["热门"]} as Row))
      .toEqual(["热门"]);
  });

  it("modelType 为 undefined 且无标签：整个标签区不渲染", () => {
    // 只断言标签区：页脚的模型族 / 模型名本来就按唯一出口显示占位符，不能混为一谈
    const wrapper = mountGrid({id: 2, modelCode: "M2"} as Row);
    expect(wrapper.find(".tag-list").exists()).toBe(false);
  });

  it("modelType 是空字符串：同样不渲染占位标签", () => {
    expect(tagsOf({id: 3, modelCode: "M3", modelType: ""} as Row)).toEqual([]);
  });

  it("命中的模型类型仍作为首个标签展示", () => {
    const row = {id: 4, modelCode: "M4", modelType: "CHAT", tags: ["热门"]} as Row;
    expect(tagsOf(row, [{value: "CHAT", label: "对话"}])).toEqual(["对话", "热门"]);
  });

  it("未命中的 code 回显为标签（占位符之外的字符串不该被过滤）", () => {
    const row = {id: 5, modelCode: "M5", modelType: "UNKNOWN"} as Row;
    expect(tagsOf(row)).toEqual(["UNKNOWN"]);
  });

  it("标签总数仍封顶 3 个", () => {
    const row = {id: 6, modelCode: "M6", modelType: "CHAT", tags: ["a", "b", "c"]} as Row;
    expect(tagsOf(row, [{value: "CHAT", label: "对话"}])).toHaveLength(3);
  });
});

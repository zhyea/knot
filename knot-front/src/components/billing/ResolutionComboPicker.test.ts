// @vitest-environment jsdom
import {describe, it, expect, vi, beforeEach} from "vitest";
import {mount, flushPromises} from "@vue/test-utils";
import ResolutionComboPicker from "@/components/billing/ResolutionComboPicker.vue";
import {
  DEFAULT_COMBOS,
  RESOLUTION_COMBOS,
  comboOf,
  isAllCombos,
  normalizeCombos
} from "@/utils/billingResolution";
import type {ResolutionComboCode} from "@/utils/billingResolution";

/**
 * 计费组合选择器的行为固化。
 *
 * <p>⭐ 这里的核心不是「多选框能点」，而是三条会直接影响金额的约束：
 * ① BASE（无声）恒在且不可取消——它是逐级回落链的终点；
 * ② 取消勾选回传的数组必须已归一（不含未知码、按固定顺序）；
 * ③ 有档位时全部锁定——组合一变，已填价格就会错位到别的键。</p>
 */

function mountPicker(modelValue: ResolutionComboCode[] = [...DEFAULT_COMBOS], disabled = false) {
  return mount(ResolutionComboPicker, {props: {modelValue, disabled}});
}

/** 取第 index 个复选框的原生 input（Element Plus 的 change 入口） */
function checkboxInput(wrapper: ReturnType<typeof mountPicker>, index: number) {
  const input = wrapper.findAll(".el-checkbox")[index].find("input");
  if (!input.exists()) {
    throw new Error(`第 ${index} 个复选框没有原生 input`);
  }
  return input;
}

describe("ResolutionComboPicker 计费组合选择器", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("渲染四种组合，无声（BASE）恒勾选且不可取消", () => {
    const wrapper = mountPicker();
    const boxes = wrapper.findAll(".el-checkbox");

    expect(boxes).toHaveLength(RESOLUTION_COMBOS.length);
    expect(boxes[0].text()).toContain("无声");
    expect(boxes[0].text()).toContain("必选");
    expect(boxes[0].classes()).toContain("is-checked");
    expect(boxes[0].classes()).toContain("is-disabled");
    // 其余组合可取消
    expect(boxes[1].classes()).not.toContain("is-disabled");
  });

  it("取消「有声」后回传不含 AUDIO，但保留 BASE", async () => {
    const wrapper = mountPicker();

    await checkboxInput(wrapper, 1).setValue(false);
    await flushPromises();

    const emitted = wrapper.emitted("update:modelValue");
    expect(emitted).toBeTruthy();
    expect(emitted!.at(-1)![0]).toEqual(["BASE", "REFERENCE", "REFERENCE_AUDIO"]);
  });

  it("勾选「含参考」后回传包含 REFERENCE", async () => {
    const wrapper = mountPicker(["BASE"]);

    await checkboxInput(wrapper, 2).setValue(true);
    await flushPromises();

    const emitted = wrapper.emitted("update:modelValue");
    expect(emitted!.at(-1)![0]).toEqual(["BASE", "REFERENCE"]);
  });

  it("有档位锁定：全部复选框禁用，提示改为「已锁定」", () => {
    const wrapper = mountPicker([...DEFAULT_COMBOS], true);

    expect(wrapper.findAll(".el-checkbox.is-disabled")).toHaveLength(RESOLUTION_COMBOS.length);
    expect(wrapper.text()).toContain("已锁定");
    expect(wrapper.text()).toContain("先删除全部档位");
  });

  it("未锁定时提示引导「先勾组合」", () => {
    const wrapper = mountPicker();
    expect(wrapper.text()).toContain("只显示已选组合的价格列");
  });
});

describe("计费组合契约（utils/billingResolution）", () => {
  it("normalizeCombos 恒补 BASE、过滤未知码、按固定顺序输出", () => {
    expect(normalizeCombos(["REFERENCE_AUDIO", "AUDIO"])).toEqual(["BASE", "AUDIO", "REFERENCE_AUDIO"]);
    expect(normalizeCombos(["base", "unknown", "AUDIO"])).toEqual(["BASE", "AUDIO"]);
    expect(normalizeCombos([])).toEqual(["BASE"]);
    expect(normalizeCombos(null)).toEqual(["BASE"]);
  });

  it("isAllCombos 只在四种全选时为真", () => {
    expect(isAllCombos([...DEFAULT_COMBOS])).toBe(true);
    expect(isAllCombos(["BASE", "AUDIO", "REFERENCE"])).toBe(false);
    // 逆序/重复也应归一后判定为全选
    expect(isAllCombos(["REFERENCE_AUDIO", "BASE", "AUDIO", "REFERENCE", "BASE"])).toBe(true);
  });

  it("comboOf 大小写不敏感，未知码返回 undefined", () => {
    expect(comboOf("audio")?.field).toBe("audioPrice");
    expect(comboOf("REFERENCE_AUDIO")?.suffix).toBe(":REFERENCE:AUDIO");
    expect(comboOf("MOTION")).toBeUndefined();
  });
});

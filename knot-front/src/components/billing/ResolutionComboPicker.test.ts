// @vitest-environment jsdom
import {describe, it, expect, vi, beforeEach} from "vitest";
import {mount, flushPromises} from "@vue/test-utils";
import ResolutionComboPicker from "@/components/billing/ResolutionComboPicker.vue";
import {
  DEFAULT_COMBOS,
  LEGACY_COMBOS,
  RESOLUTION_COMBOS,
  applyCombosChange,
  comboOf,
  createResolutionRow,
  hasAudioPriceTier,
  hasComboAxis,
  isAllCombos,
  normalizeCombos,
  withComboAxis
} from "@/utils/billingResolution";
import type {CombosFormSlice, ResolutionComboCode} from "@/utils/billingResolution";

/**
 * 计费组合选择器的行为固化（2026-10-30 改版：四复选框 → 两个正交开关）。
 *
 * <p>⭐ 这里钉的是三条会直接影响金额的约束：</p>
 * <ol>
 *   <li><b>基础价恒含</b>——它是逐级回落链的终点，缺了它「请求未声明音频开关」就无价可取；
 *       但它不再以「默认勾选的无声」出现：那个措辞把主力价说成了边角价。</li>
 *   <li><b>默认两个开关都关</b>——关 = 该维度与基础价同价，只有要卖更贵时才开。</li>
 *   <li><b>回传必已归一</b>（恒补 BASE、按固定顺序、过滤未知码）；
 *       有档位时全部锁定，组合一变已填价格就会错位到别的键。</li>
 * </ol>
 */

function mountPicker(modelValue: ResolutionComboCode[] = [...DEFAULT_COMBOS], disabled = false) {
  return mount(ResolutionComboPicker, {props: {modelValue, disabled}});
}

/** 取第 index 个开关的原生 input（Element Plus 的 change 入口） */
function checkboxInput(wrapper: ReturnType<typeof mountPicker>, index: number) {
  const input = wrapper.findAll(".el-checkbox")[index].find("input");
  if (!input.exists()) {
    throw new Error(`第 ${index} 个开关没有原生 input`);
  }
  return input;
}

describe("ResolutionComboPicker 计费组合选择器", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("只有两个开关，基础价是必含说明而不是可取消的选项", () => {
    const wrapper = mountPicker();
    const boxes = wrapper.findAll(".el-checkbox");

    expect(boxes).toHaveLength(2);
    expect(boxes[0].text()).toContain("有声单独定价");
    expect(boxes[1].text()).toContain("含参考输入单独定价");
    // 「无声」这个把主力价说成边角价的措辞必须消失
    expect(wrapper.text()).not.toContain("无声");
    // 基础价恒含：出现在摘要里，且不是复选框
    expect(wrapper.find(".combo-picker__summary").text()).toContain("基础价");
    // 默认都关 = 只有一列
    expect(wrapper.findAll(".el-checkbox.is-checked")).toHaveLength(0);
    expect(wrapper.find(".combo-picker__columns").text()).toBe("基础价");
  });

  it("勾「含参考输入单独定价」→ 只补 REFERENCE，不牵连有声", async () => {
    const wrapper = mountPicker();

    await checkboxInput(wrapper, 1).setValue(true);
    await flushPromises();

    expect(wrapper.emitted("update:modelValue")!.at(-1)![0]).toEqual(["BASE", "REFERENCE"]);
  });

  it("勾「有声单独定价」→ 只补 AUDIO", async () => {
    const wrapper = mountPicker();

    await checkboxInput(wrapper, 0).setValue(true);
    await flushPromises();

    expect(wrapper.emitted("update:modelValue")!.at(-1)![0]).toEqual(["BASE", "AUDIO"]);
  });

  it("两轴都开 → 自动补 REFERENCE_AUDIO（取价链最具体档，不减不加）", async () => {
    const wrapper = mountPicker(["BASE", "AUDIO"]);

    await checkboxInput(wrapper, 1).setValue(true);
    await flushPromises();

    expect(wrapper.emitted("update:modelValue")!.at(-1)![0]).toEqual([
      "BASE",
      "AUDIO",
      "REFERENCE",
      "REFERENCE_AUDIO"
    ]);
  });

  it("关掉「含参考」→ 该轴两码一起去掉，有声那档保留", async () => {
    const wrapper = mountPicker([...LEGACY_COMBOS]);

    await checkboxInput(wrapper, 1).setValue(false);
    await flushPromises();

    expect(wrapper.emitted("update:modelValue")!.at(-1)![0]).toEqual(["BASE", "AUDIO"]);
  });

  it("有档位锁定：两个开关都禁用，提示改为「已锁定」", () => {
    const wrapper = mountPicker([...LEGACY_COMBOS], true);

    expect(wrapper.findAll(".el-checkbox.is-disabled")).toHaveLength(2);
    expect(wrapper.text()).toContain("已锁定");
    expect(wrapper.text()).toContain("先删除全部档位");
  });

  it("未锁定时提示「默认只用基础价」", () => {
    const wrapper = mountPicker();
    expect(wrapper.text()).toContain("默认只用基础价");
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
    expect(isAllCombos([...LEGACY_COMBOS])).toBe(true);
    expect(isAllCombos(["BASE", "AUDIO", "REFERENCE"])).toBe(false);
    // 逆序/重复也应归一后判定为全选
    expect(isAllCombos(["REFERENCE_AUDIO", "BASE", "AUDIO", "REFERENCE", "BASE"])).toBe(true);
  });

  it("默认值与存量回显口径必须分开：新建只有基础价，无字段按四列全开", () => {
    // ⚠ 这条是防回归：把 DEFAULT_COMBOS 改回全选，界面又会变成「默认先勾无声 + 三列空价」
    expect(DEFAULT_COMBOS).toEqual(["BASE"]);
    expect(LEGACY_COMBOS).toEqual(["BASE", "AUDIO", "REFERENCE", "REFERENCE_AUDIO"]);
    expect(RESOLUTION_COMBOS.map((combo) => combo.code)).toEqual(LEGACY_COMBOS);
    // 基础价的界面文案是「基础价」，不是「无声」
    expect(comboOf("BASE")?.label).toBe("基础价");
  });

  it("comboOf 大小写不敏感，未知码返回 undefined", () => {
    expect(comboOf("audio")?.field).toBe("audioPrice");
    expect(comboOf("REFERENCE_AUDIO")?.suffix).toBe(":REFERENCE:AUDIO");
    expect(comboOf("MOTION")).toBeUndefined();
  });

  it("hasComboAxis：REFERENCE_AUDIO 同时属于两轴", () => {
    expect(hasComboAxis(["BASE"], "AUDIO")).toBe(false);
    expect(hasComboAxis(["BASE"], "REFERENCE")).toBe(false);
    expect(hasComboAxis(["BASE", "REFERENCE_AUDIO"], "AUDIO")).toBe(true);
    expect(hasComboAxis(["BASE", "REFERENCE_AUDIO"], "REFERENCE")).toBe(true);
  });

  it("withComboAxis 按完整笛卡尔积归一，关轴时连带去掉叠加码", () => {
    expect(withComboAxis(["BASE"], "AUDIO", true)).toEqual(["BASE", "AUDIO"]);
    expect(withComboAxis(["BASE", "AUDIO"], "REFERENCE", true)).toEqual([
      "BASE",
      "AUDIO",
      "REFERENCE",
      "REFERENCE_AUDIO"
    ]);
    expect(withComboAxis([...LEGACY_COMBOS], "AUDIO", false)).toEqual(["BASE", "REFERENCE"]);
    // 非完整存量态（只有 REFERENCE_AUDIO）：改任一轴即归一成完整形状
    expect(withComboAxis(["BASE", "REFERENCE_AUDIO"], "REFERENCE", true)).toEqual([
      "BASE",
      "AUDIO",
      "REFERENCE",
      "REFERENCE_AUDIO"
    ]);
  });

  it("hasAudioPriceTier：只认 AUDIO / REFERENCE_AUDIO 两个键", () => {
    expect(hasAudioPriceTier(["BASE"])).toBe(false);
    expect(hasAudioPriceTier(["BASE", "REFERENCE"])).toBe(false);
    expect(hasAudioPriceTier(["BASE", "AUDIO"])).toBe(true);
    expect(hasAudioPriceTier(["BASE", "REFERENCE_AUDIO"])).toBe(true);
  });
});

describe("applyCombosChange：组合变更的唯一落地口", () => {
  function slice(overrides: Partial<CombosFormSlice> = {}): CombosFormSlice {
    const row = createResolutionRow("720P");
    row.audioPrice = 0.9;
    row.referencePrice = 0.3;
    row.referenceAudioPrice = 0.8;
    return {
      videoDimensionCombos: ["BASE"],
      videoQuantityMode: "SUM",
      videoAudioSurcharge: null,
      videoResolutionRows: [row],
      ...overrides
    };
  }

  it("清掉被取消组合在所有行上的价格，并把量口径回落 OUTPUT", () => {
    const form = slice();

    applyCombosChange(form, ["BASE"]);

    expect(form.videoDimensionCombos).toEqual(["BASE"]);
    // 没有「含参考」组合 → 输入秒不再折进计费量
    expect(form.videoQuantityMode).toBe("OUTPUT");
    expect(form.videoResolutionRows[0].audioPrice).toBeNull();
    expect(form.videoResolutionRows[0].referencePrice).toBeNull();
    expect(form.videoResolutionRows[0].referenceAudioPrice).toBeNull();
  });

  it("保留已选组合的价格，且不误清基础价", () => {
    const form = slice({videoResolutionRows: [Object.assign(createResolutionRow("720P"), {price: 0.15})]});

    applyCombosChange(form, ["BASE", "REFERENCE"]);

    expect(form.videoResolutionRows[0].price).toBe(0.15);
    expect(form.videoResolutionRows[0].referencePrice).toBeNull();
  });

  it("勾上有声单列时清空音频直出附加费并回报，由宿主提示（不做无声改动）", () => {
    const form = slice({videoAudioSurcharge: 15});

    const result = applyCombosChange(form, ["BASE", "AUDIO"]);

    expect(result.clearedAudioSurcharge).toBe(true);
    expect(form.videoAudioSurcharge).toBeNull();
  });

  it("无声单列时不动附加费", () => {
    const form = slice({videoAudioSurcharge: 15});

    const result = applyCombosChange(form, ["BASE", "REFERENCE"]);

    expect(result.clearedAudioSurcharge).toBe(false);
    expect(form.videoAudioSurcharge).toBe(15);
  });
});

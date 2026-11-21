// @vitest-environment jsdom
import {describe, expect, it} from "vitest";
import {mount} from "@vue/test-utils";
import {nextTick, reactive} from "vue";
import PricingPlanTieredConfig from "@/components/billing/plans/PricingPlanTieredConfig.vue";
import {createTierPriceSet, createTierRow} from "@/utils/billingTier";

/**
 * 阶梯档位输入框的两条口径：区间边界（token 数 / 张数 / 秒数）是计数，禁止小数；
 * 单价值必须能保留小数。两者共用同一张表，改一处很容易把另一处也锁成整数。
 */

function mountTiered() {
  const form = reactive({
    billingMode: "VIDEO",
    tierMode: "PER_REQUEST",
    cacheWriteMode: "standard",
    tiers: [createTierRow({from: 0, to: 10, unitPrices: createTierPriceSet(1)})]
  });
  const wrapper = mount(PricingPlanTieredConfig, {props: {form}});
  const inputs = wrapper.findAll(".el-input-number input");
  return {form, wrapper, inputs};
}

async function type(input: ReturnType<typeof mountTiered>["inputs"][number], value: string) {
  await input.setValue(value);
  await input.trigger("change");
  await input.trigger("blur");
  await nextTick();
}

describe("阶梯档位输入精度", () => {
  it("区间边界输入小数被规范为整数", async () => {
    const {form, inputs} = mountTiered();
    await type(inputs[0], "0.5");
    expect(form.tiers[0].from).toBe(1);
    await type(inputs[1], "10.4");
    expect(form.tiers[0].to).toBe(10);
  });

  it("档位单价仍保留小数", async () => {
    const {form, inputs} = mountTiered();
    // standard 缓存写口径下单价列顺序：input / output / cacheRead / cacheWrite
    await type(inputs[2], "0.00015");
    expect(form.tiers[0].unitPrices.input).toBe(0.00015);
  });

  it("加减控件在右侧", () => {
    const {wrapper} = mountTiered();
    const numbers = wrapper.findAll(".el-input-number");
    expect(numbers.length).toBeGreaterThan(0);
    expect(numbers.every((node) => node.classes().includes("is-controls-right"))).toBe(true);
  });
});

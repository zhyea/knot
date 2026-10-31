// @vitest-environment jsdom
import {describe, it, expect, afterEach} from "vitest";
import {enableAutoUnmount, mount} from "@vue/test-utils";
import {nextTick, reactive} from "vue";
import PricingPlanPeakOffPeakConfig from "@/components/billing/plans/PricingPlanPeakOffPeakConfig.vue";
import {createPeakPhaseRow, createWindow} from "@/utils/billingPeakOffPeak";
import type {Dict} from "@/types";

/**
 * `PricingPlanPeakOffPeakConfig`（高低峰方案编辑器）直测。
 *
 * <p>纯算术逻辑落在 {@code utils/billingPeakOffPeak}（已有独立测试），这里只钉两件
 * <b>组件层才有的</b>行为：</p>
 * <ol>
 *   <li><b>默认骨架</b>：父表单没带 peakPricing 时自行补一份（末项必须是「兜底」相位，
 *       否则 validatePeakPricing 会判成「未配置兜底」而整块方案不可保存）；</li>
 *   <li><b>缓存写方式的清理联动</b>：切到 ttl 必须清掉 standard 那组缓存写单价，
 *       切回 standard 必须清掉 5m/1h。残留值会被 toUnitPricesPayload 带出，
 *       变成「界面上看不见、但确实被拿去算钱」的单价——这是真实的资损口。</li>
 * </ol>
 */

enableAutoUnmount(afterEach);

function formWith(peakPricing?: unknown): {form: Dict} {
  return {
    form: reactive({
      billingMode: "TOKEN",
      cacheWriteMode: "standard",
      ...(peakPricing === undefined ? {} : {peakPricing})
    } as Dict)
  };
}

function phaseWithPrices(): unknown {
  return {
    timezone: "UTC+0",
    rateMode: "MULTIPLIER",
    phases: [
      createPeakPhaseRow({
        phase: "PEAK",
        // ⚠ 高峰倍率输入框是 :max="1"（模型是「高峰也打折」，绝不会高于基准价）：
        // 哨兵值必须落在合法区间内，否则挂载瞬间就被 el-input-number 钳到 1，
        // 测试就变成在断言钳位、而不是断言「用户填的值被保住」。
        multiplier: 0.9,
        weekdays: [1, 2, 3, 4, 5],
        windows: [createWindow("01:00", "04:00")],
        unitPrices: {
          input: 1,
          output: 2,
          cacheRead: 0.5,
          cacheWrite: 3,
          cacheWrite5m: 4,
          cacheWrite1h: 5
        }
      }),
      createPeakPhaseRow({phase: "OFF_PEAK", multiplier: 1, isDefault: true})
    ]
  };
}

describe("PricingPlanPeakOffPeakConfig 默认骨架", () => {
  it("父表单缺 peakPricing 时补默认骨架，且末项是兜底相位", () => {
    const {form} = formWith(undefined);
    mount(PricingPlanPeakOffPeakConfig, {props: {form}});

    const phases = (form.peakPricing as {phases: Array<Record<string, unknown>>}).phases;
    expect(phases).toHaveLength(2);
    expect(phases[phases.length - 1].isDefault).toBe(true);
    expect(phases[0].phase).toBe("PEAK");
  });

  it("已有可用 peakPricing 时原样保留，不覆盖用户填写", () => {
    const {form} = formWith(phaseWithPrices());
    mount(PricingPlanPeakOffPeakConfig, {props: {form}});

    // 默认骨架的高峰倍率是 1；这里用户填的是 0.9，被默认骨架覆盖就会掉回 1
    const pricing = form.peakPricing as {phases: Array<{multiplier: unknown; unitPrices: unknown}>};
    expect(pricing.phases).toHaveLength(2);
    expect(pricing.phases[0].multiplier).toBe(0.9);
    expect(pricing.phases[0].unitPrices).not.toBeNull();
  });
});

describe("PricingPlanPeakOffPeakConfig 缓存写方式联动", () => {
  it("切到 ttl：清掉 standard 的 cacheWrite，保留 5m / 1h", async () => {
    const {form} = formWith(phaseWithPrices());
    mount(PricingPlanPeakOffPeakConfig, {props: {form}});

    form.cacheWriteMode = "ttl";
    await nextTick();

    const prices = (form.peakPricing as {phases: Array<{unitPrices: Record<string, unknown>}>})
      .phases[0].unitPrices;
    expect(prices.cacheWrite).toBeNull();
    expect(prices.cacheWrite5m).toBe(4);
    expect(prices.cacheWrite1h).toBe(5);
  });

  it("切回 standard：清掉 5m / 1h，保留 cacheWrite", async () => {
    const {form} = formWith(phaseWithPrices());
    form.cacheWriteMode = "ttl";
    mount(PricingPlanPeakOffPeakConfig, {props: {form}});

    form.cacheWriteMode = "standard";
    await nextTick();

    const prices = (form.peakPricing as {phases: Array<{unitPrices: Record<string, unknown>}>})
      .phases[0].unitPrices;
    expect(prices.cacheWrite).toBe(3);
    expect(prices.cacheWrite5m).toBeNull();
    expect(prices.cacheWrite1h).toBeNull();
  });

  it("没有 unitPrices 的相位（如兜底倍率相位）不因联动报错", async () => {
    const {form} = formWith(phaseWithPrices());
    mount(PricingPlanPeakOffPeakConfig, {props: {form}});

    form.cacheWriteMode = "ttl";
    await nextTick();

    const fallback = (form.peakPricing as {phases: Array<{isDefault: boolean; unitPrices: unknown}>})
      .phases.find((row) => row.isDefault)!;
    expect(fallback.unitPrices).toBeNull();
  });
});

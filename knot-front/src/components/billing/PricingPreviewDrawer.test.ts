// @vitest-environment jsdom
import {describe, it, expect, vi, beforeEach, afterEach} from "vitest";
import {mount, flushPromises} from "@vue/test-utils";
import PricingPreviewDrawer from "@/components/billing/PricingPreviewDrawer.vue";

const mocks = vi.hoisted(() => ({
  previewBillingRule: vi.fn(),
  labelOf: vi.fn((_enumName: string, code: unknown, fallback?: string) =>
    fallback !== undefined ? fallback : String(code ?? ""))
}));

vi.mock("@/api/billing", () => ({previewBillingRule: mocks.previewBillingRule}));

vi.mock("@/composables/useEnumOptions", () => ({
  labelOf: mocks.labelOf,
  useEnumOptions: () => ({labelOf: mocks.labelOf})
}));

/**
 * 试算抽屉的价格种类文案。
 *
 * <p>图片按 token 计价时后端给五个模态键（与计费明细 type 同源），
 * 标签缺失会把 `cachedInputText` 这类原始键直接摆给方案配置者。</p>
 */
describe("PricingPreviewDrawer 价格种类文案", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("图片按 token 计价：五个模态单价各显示中文种类", async () => {
    mocks.previewBillingRule.mockResolvedValue({
      ruleId: 12,
      ruleCode: "IMG_GPT",
      billingMode: "IMAGE",
      pricingPlan: "FIXED",
      currency: "USD",
      unit: "1M_TOKENS",
      unitSize: 1000000,
      occurredAt: "2026-11-24T02:00:00Z",
      timezone: null,
      phase: null,
      reason: null,
      multiplier: null,
      prices: {
        inputText: 5,
        inputImage: 8,
        cachedInputText: 1.25,
        cachedInputImage: 2,
        outputImage: 30
      }
    } as never);

    const wrapper = mount(PricingPreviewDrawer, {
      props: {modelValue: false, ruleId: 12, ruleCode: "IMG_GPT"}
    });
    await wrapper.setProps({modelValue: true});
    await flushPromises();

    const probe = wrapper.findAll(".el-button").find((node) => node.text().includes("试算"));
    expect(probe, "找不到试算按钮").toBeTruthy();
    await probe!.trigger("click");
    await flushPromises();

    const text = wrapper.text();
    for (const label of ["文本输入", "图像输入", "文本缓存输入", "图像缓存输入", "图像输出"]) {
      expect(text, `缺少价格种类「${label}」`).toContain(label);
    }
    expect(text, "价格种类键不得裸露").not.toContain("cachedInputText");
  });
});

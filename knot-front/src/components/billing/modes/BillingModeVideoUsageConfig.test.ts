// @vitest-environment jsdom
import {describe, it, expect, afterEach} from "vitest";
import {enableAutoUnmount, flushPromises, mount} from "@vue/test-utils";
import {reactive} from "vue";
import BillingModeVideoUsageConfig from "@/components/billing/modes/BillingModeVideoUsageConfig.vue";
import type {Dict} from "@/types";

/**
 * 「输入侧分量默认不展示」的组件层契约（2026-11-10 robin 定：只收这一组，计费量口径保持常显）。
 *
 * <p>⭐ 风险不在收起本身，而在把已配置的收费项藏起来：值留在表单里会随保存落库，
 * 变成界面上找不到、计费却会命中的单价。所以有值必须自动展开 + 标注，且收起不等于清空。</p>
 */

enableAutoUnmount(afterEach);

function formWith(overrides: Dict = {}): Dict {
  return reactive({
    unit: "PER_SECOND",
    pricingPlan: "TIERED",
    videoQuantityMode: "OUTPUT",
    videoDimensionCombos: ["BASE"],
    videoResolutionRows: [],
    videoInputVideoPerSecond: null,
    videoInputImagePerImage: null,
    videoFreeInputImages: null,
    videoAudioSurcharge: null,
    ...overrides
  } as Dict);
}

const CHARGE_LABELS = ["输入视频 每秒", "输入图片 每张", "图片免费张数", "音频直出附加费"];

function mountUsage(form: Dict) {
  return mount(BillingModeVideoUsageConfig, {props: {form}});
}

describe("BillingModeVideoUsageConfig 输入侧分量默认收起", () => {
  it("新建规则默认不展示四个分量，计费量口径仍按组合显示", () => {
    const wrapper = mountUsage(formWith({videoDimensionCombos: ["BASE", "REFERENCE"]}));

    // 「量」是另一根轴：有含参考组合就必须照常显示，不跟着输入侧分量收起
    expect(wrapper.text()).toContain("合成量模式");
    // 入口必须可见，否则「默认不展示」等于「永远配不了」
    expect(wrapper.find(".video-usage-config__toggle").exists()).toBe(true);
    for (const label of CHARGE_LABELS) {
      expect(wrapper.text()).not.toContain(label);
    }
  });

  it("点击入口可展开与再收起", async () => {
    const wrapper = mountUsage(formWith());
    const toggle = wrapper.find(".video-usage-config__toggle");

    await toggle.trigger("click");
    for (const label of CHARGE_LABELS) {
      expect(wrapper.text()).toContain(label);
    }

    await toggle.trigger("click");
    for (const label of CHARGE_LABELS) {
      expect(wrapper.text()).not.toContain(label);
    }
  });

  it("已配置分量时默认展开并标注，收起不清空已配值", async () => {
    const form = formWith({videoInputImagePerImage: 0.2, videoAudioSurcharge: 15});
    const wrapper = mountUsage(form);

    expect(wrapper.find(".video-usage-config__badge").text()).toContain("已配置 2 项");
    expect(wrapper.text()).toContain("输入图片 每张");

    await wrapper.find(".video-usage-config__toggle").trigger("click");
    expect(wrapper.text()).not.toContain("输入图片 每张");
    // 折叠只是显示态：值仍在表单里，不能变成「界面上看不到却在计费」的反面（静默少收）
    expect(form.videoInputImagePerImage).toBe(0.2);
    expect(form.videoAudioSurcharge).toBe(15);
  });

  it("详情异步回填（挂载时无值）也要自动展开并标注", async () => {
    const form = formWith();
    const wrapper = mountUsage(form);
    expect(wrapper.text()).not.toContain("输入图片 每张");

    // 抽屉先挂载再异步回填 configJson，这是编辑存量规则的真实路径
    form.videoInputImagePerImage = 0.2;
    await flushPromises();

    expect(wrapper.find(".video-usage-config__badge").text()).toContain("已配置 1 项");
    expect(wrapper.text()).toContain("输入图片 每张");
  });
});

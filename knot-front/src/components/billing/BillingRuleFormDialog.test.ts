// @vitest-environment jsdom
import {describe, it, expect, vi, beforeEach, afterEach} from "vitest";
import {mount, flushPromises, type VueWrapper} from "@vue/test-utils";
import type {Component} from "vue";
import {ElDrawer, ElMessage} from "element-plus";
import BillingRuleFormDialog from "@/components/billing/BillingRuleFormDialog.vue";
import BillingModeTokenConfig from "@/components/billing/modes/BillingModeTokenConfig.vue";
import BillingModeVideoConfig from "@/components/billing/modes/BillingModeVideoConfig.vue";
import BillingModeCustomConfig from "@/components/billing/modes/BillingModeCustomConfig.vue";
import type {Dict} from "@/types";

/**
 * `BillingRuleFormDialog` 的存量行为固化（改进清单 §4.7：754 行 / 36 次提交 / **零测试**，
 * 先补测试再谈拆）。
 *
 * <p>**为什么先测它而不是 `RoutingRuleFormDrawer`**：本组件在金额链路上，且内部有
 * 「config_json 文本 ↔ 表单字段」双向同步与 `suppressJsonApplyBack` 遗留文本守卫——
 * 这类问题不会报错，只会静默把错误的单价写进库，比方法行数更值得先钉住。</p>
 *
 * <p>断言落点一律选**业务可见行为**：抽屉标题、提示文案、以及 `createBillingRule` /
 * `updateBillingRule` 收到的 payload。价格字段刻意不读 DOM——它们散落在 8 个模式子组件里，
 * 直接取子组件的 `form` prop（同一个 reactive 对象）来驱动更稳。</p>
 *
 * <p>Element Plus 用真实组件（要读 tabs 的切换与抽屉标题）；只有
 * EnumSelect / EnumControl / JsonCodeEditor 三个换成 stub：前两个会去打枚举接口，
 * 第三个是 CodeBlockView 的二次封装，两者都与本组件的逻辑无关。</p>
 */

const RULE_ID = 12;

const EnumControlStub = {
  name: "EnumControl",
  props: ["modelValue", "enumName", "includeCodes", "showCode"],
  template: "<i class='enum-stub'>{{ modelValue }}</i>"
};
const EnumSelectStub = {
  name: "EnumSelect",
  props: ["modelValue", "category", "codeOnly", "clearable", "filterable", "placeholder"],
  template: "<i class='enum-select-stub'/>"
};
const JsonEditorStub = {
  name: "JsonCodeEditor",
  props: ["modelValue", "readonly", "minHeight", "height", "maxHeight"],
  emits: ["update:modelValue"],
  template: `<textarea class="json-stub" :value="modelValue"
    @input="$emit('update:modelValue', $event.target.value)"/>`
};

vi.mock("@/api/billing", () => ({
  listBillingRules: vi.fn(),
  getBillingRule: vi.fn(),
  listModeCapabilities: vi.fn(),
  createBillingRule: vi.fn(),
  updateBillingRule: vi.fn(),
  updateBillingRuleStatus: vi.fn(),
  previewBillingRule: vi.fn(),
  deleteBillingRule: vi.fn()
}));

import {createBillingRule, getBillingRule, listModeCapabilities, updateBillingRule} from "@/api/billing";

/** 能力矩阵：单位与方案的可用集合完全由后端下发，前端不维护映射表 */
const CAPABILITIES = {
  billingModes: [
    {
      code: "TOKEN",
      defaultUnit: "1M_TOKENS",
      supportedUnits: ["1M_TOKENS", "1K_TOKENS", "PER_TOKEN"],
      supportedPricingPlans: ["FIXED", "TIERED", "PEAK_OFF_PEAK"]
    },
    {code: "VIDEO", defaultUnit: "PER_SECOND", supportedUnits: ["PER_SECOND"], supportedPricingPlans: ["FIXED"]},
    {code: "CUSTOM", defaultUnit: "CUSTOM", supportedUnits: ["CUSTOM"], supportedPricingPlans: ["FIXED"]},
    {code: "FREE", defaultUnit: "PER_REQUEST", supportedUnits: ["PER_REQUEST"], supportedPricingPlans: ["FIXED"]}
  ]
};

const TOKEN_CONFIG = {
  cacheWriteMode: "standard",
  basePrices: {input: 0.003, output: 0.006, cacheRead: 0.0003, cacheWrite: 0.0015, cacheWrite5m: 0, cacheWrite1h: 0}
};

/** 一份合法的阶梯配置（校验要求每档 6 项单价齐全、首档 from 必填） */
function tierConfig(): Dict {
  return {
    ...TOKEN_CONFIG,
    tier: [
      {
        condition: {from: 0, to: 1000000},
        unitPrices: {input: 0.003, output: 0.006, cacheRead: 0.0003, cacheWrite: 0.0015, cacheWrite5m: 0, cacheWrite1h: 0}
      }
    ]
  };
}

function detailRow(overrides: Dict = {}): Dict {
  return {
    id: RULE_ID,
    code: "TOKEN_GPT4O",
    modelFamilyCode: "openai",
    billingMode: "TOKEN",
    pricingPlan: "FIXED",
    currency: "USD",
    unit: "1M_TOKENS",
    configJson: JSON.stringify(TOKEN_CONFIG),
    enabled: true,
    remark: "备注",
    boundModelCount: 0,
    ...overrides
  };
}

/** 打开抽屉必须走 setProps：初始化挂在 watch(props.modelValue) 上，没有 immediate */
async function openDialog(props: Dict = {}): Promise<VueWrapper> {
  const wrapper = mount(BillingRuleFormDialog, {
    props: {modelValue: false, ruleId: null, codeOnly: false, ...props},
    global: {
      stubs: {
        EnumControl: EnumControlStub,
        EnumSelect: EnumSelectStub,
        JsonCodeEditor: JsonEditorStub
      }
    }
  });
  await wrapper.setProps({modelValue: true});
  await flushPromises();
  return wrapper;
}

/** 取表单状态：模式子组件拿到的 `form` prop 就是父组件的 reactive 对象 */
function formOf(wrapper: VueWrapper, component: Component = BillingModeTokenConfig): Dict {
  const found = wrapper.findComponent(component);
  if (!found.exists()) {
    throw new Error("模式子组件未渲染，取不到 form");
  }
  return found.props("form") as Dict;
}

function saveButton(wrapper: VueWrapper) {
  const save = wrapper
    .findAll(".el-drawer__footer .el-button")
    .find((button) => button.text().includes("保存"));
  if (!save) {
    throw new Error("找不到保存按钮");
  }
  return save;
}

async function save(wrapper: VueWrapper) {
  await saveButton(wrapper).trigger("click");
  await flushPromises();
}

async function switchTab(wrapper: VueWrapper, label: string) {
  const tab = wrapper.findAll(".el-tabs__item").find((node) => node.text().includes(label));
  if (!tab) {
    throw new Error(`找不到标签页：${label}`);
  }
  await tab.trigger("click");
  await flushPromises();
}

function activeTabLabel(wrapper: VueWrapper): string {
  const [active] = wrapper.findAll(".el-tabs__item.is-active");
  return active ? active.text() : "";
}

async function setJsonText(wrapper: VueWrapper, text: string) {
  await wrapper.find(".json-stub").setValue(text);
  await flushPromises();
}

function updatePayload(): Dict {
  const calls = vi.mocked(updateBillingRule).mock.calls;
  expect(calls.length).toBeGreaterThan(0);
  return calls[calls.length - 1][1] as Dict;
}

function configOf(payload: Dict): Dict {
  return JSON.parse(String(payload.configJson)) as Dict;
}

function warned(): string[] {
  return vi.mocked(ElMessage.warning).mock.calls.map((call) => String(call[0]));
}

function errored(): string[] {
  return vi.mocked(ElMessage.error).mock.calls.map((call) => String(call[0]));
}

describe("BillingRuleFormDialog 计费规则表单", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(listModeCapabilities).mockResolvedValue(CAPABILITIES as never);
    vi.mocked(getBillingRule).mockResolvedValue(detailRow() as never);
    vi.mocked(createBillingRule).mockResolvedValue({} as never);
    vi.mocked(updateBillingRule).mockResolvedValue({} as never);

    vi.spyOn(ElMessage, "warning").mockImplementation(() => ({}) as never);
    vi.spyOn(ElMessage, "success").mockImplementation(() => ({}) as never);
    vi.spyOn(ElMessage, "error").mockImplementation(() => ({}) as never);
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  // ==================== 打开与回填 ====================

  it("新建：标题为新建态，拉能力矩阵但不拉详情", async () => {
    const wrapper = await openDialog();

    expect(wrapper.findComponent(ElDrawer).props("title")).toBe("新建计费规则");
    expect(listModeCapabilities).toHaveBeenCalledTimes(1);
    expect(getBillingRule).not.toHaveBeenCalled();
    expect(activeTabLabel(wrapper)).toContain("表单编辑");
  });

  it("新建提交：默认 TOKEN / 固定价，配置由基础价格重建", async () => {
    const wrapper = await openDialog();
    await wrapper.find('input[placeholder="如 TOKEN_GPT4O"]').setValue("TOKEN_NEW");
    await save(wrapper);

    expect(createBillingRule).toHaveBeenCalledTimes(1);
    const payload = vi.mocked(createBillingRule).mock.calls[0][0] as Dict;
    expect(payload).toMatchObject({
      code: "TOKEN_NEW",
      modelFamilyCode: null,
      billingMode: "TOKEN",
      pricingPlan: "FIXED",
      currency: "USD",
      unit: "1M_TOKENS",
      enabled: true,
      remark: null
    });
    expect(configOf(payload)).toEqual(TOKEN_DEFAULTS());
  });

  it("编辑：按 id 拉详情并按 config_json 回显单价与启用状态", async () => {
    const wrapper = await openDialog({ruleId: RULE_ID});
    expect(wrapper.findComponent(ElDrawer).props("title")).toBe("编辑计费规则");
    expect(getBillingRule).toHaveBeenCalledWith(RULE_ID);

    await save(wrapper);
    const payload = updatePayload();
    expect(updateBillingRule).toHaveBeenCalledWith(RULE_ID, payload);
    expect(payload).toMatchObject({code: "TOKEN_GPT4O", unit: "1M_TOKENS", enabled: true, remark: "备注"});
    // 回显来的价格必须原样回到 payload——不能被 resetForm 的初值盖掉
    expect(configOf(payload).basePrices).toEqual(TOKEN_CONFIG.basePrices);
  });

  it("详情不存在或加载失败时关掉抽屉，不留下可误保存的空表单", async () => {
    vi.mocked(getBillingRule).mockResolvedValueOnce(null as never);
    const missing = await openDialog({ruleId: RULE_ID});
    expect(errored()).toContain("未找到该计费规则");
    expect(missing.emitted("update:modelValue")?.at(-1)).toEqual([false]);

    vi.mocked(getBillingRule).mockRejectedValueOnce(new Error("500"));
    const failed = await openDialog({ruleId: RULE_ID});
    expect(errored()).toContain("加载计费规则详情失败，请重试");
    expect(failed.emitted("update:modelValue")?.at(-1)).toEqual([false]);
  });

  // ==================== 能力矩阵门禁（静默重置风险） ====================

  it("能力矩阵晚于详情到达时，回显的进阶方案不得被兜底值改写", async () => {
    // `capabilitiesLoaded` 门禁只在**矩阵还没到**时起作用：那时 planCodes 只有兜底 ["FIXED"]，
    // 回显的 TIERED 会被当成「当前模式不支持」静默改回固定价，保存即丢配置。
    // 反过来，若矩阵先到（billingModes 已有数据），planCodes 已正确，去掉门禁也不会出错——
    // 所以这条用例刻意用延迟 resolve 制造「详情先到」的时序，否则测不到门禁。
    let release!: (value: unknown) => void;
    vi.mocked(listModeCapabilities).mockReturnValue(new Promise((resolve) => {
      release = resolve;
    }) as never);
    vi.mocked(getBillingRule).mockResolvedValue(
      detailRow({pricingPlan: "TIERED", configJson: JSON.stringify(tierConfig())}) as never
    );

    const wrapper = await openDialog({ruleId: RULE_ID});
    // 矩阵尚未返回：直接保存，验证回显没被改写
    await save(wrapper);
    expect(updatePayload().pricingPlan).toBe("TIERED");
    expect(configOf(updatePayload()).tier).toHaveLength(1);

    // 矩阵到达且支持该方案 → 仍然保持；若矩阵不支持则由 applyModeDefaults 统一收敛（见下一条用例）
    release(CAPABILITIES);
    await flushPromises();
    await save(wrapper);
    expect(updatePayload().pricingPlan).toBe("TIERED");
    expect(configOf(updatePayload()).tier[0].condition).toEqual({from: 0, to: 1000000});
  });

  it("能力矩阵未列出该方案时才回退固定价", async () => {
    vi.mocked(listModeCapabilities).mockResolvedValue({
      billingModes: [
        {code: "TOKEN", defaultUnit: "1M_TOKENS", supportedUnits: ["1M_TOKENS"], supportedPricingPlans: ["FIXED"]}
      ]
    } as never);
    vi.mocked(getBillingRule).mockResolvedValue(
      detailRow({pricingPlan: "TIERED", configJson: JSON.stringify({...TOKEN_CONFIG, tier: []})}) as never
    );

    const wrapper = await openDialog({ruleId: RULE_ID});
    await save(wrapper);

    const payload = updatePayload();
    expect(payload.pricingPlan).toBe("FIXED");
    expect(configOf(payload).tier).toBeUndefined();
  });

  it("单位按能力矩阵归一：不支持的回显单位收敛到该模式默认单位", async () => {
    vi.mocked(listModeCapabilities).mockResolvedValue({
      billingModes: [
        {code: "TOKEN", defaultUnit: "1M_TOKENS", supportedUnits: ["1M_TOKENS"], supportedPricingPlans: ["FIXED"]}
      ]
    } as never);
    vi.mocked(getBillingRule).mockResolvedValue(detailRow({unit: "PER_TOKEN"}) as never);

    const wrapper = await openDialog({ruleId: RULE_ID});
    await save(wrapper);

    expect(updatePayload().unit).toBe("1M_TOKENS");
  });

  // ==================== config_json 页 ↔ 表单双向同步 ====================

  it("切到 config_json 页展示由表单重建的配置文本", async () => {
    const wrapper = await openDialog({ruleId: RULE_ID});
    await switchTab(wrapper, "config_json");

    expect(activeTabLabel(wrapper)).toContain("config_json");
    const text = (wrapper.find(".json-stub").element as HTMLTextAreaElement).value;
    expect(JSON.parse(text)).toEqual(TOKEN_CONFIG);
  });

  it("停留在 config_json 页直接保存时，先把文本回写表单再统一构建 payload", async () => {
    const wrapper = await openDialog({ruleId: RULE_ID});
    await switchTab(wrapper, "config_json");
    await setJsonText(
      wrapper,
      JSON.stringify({cacheWriteMode: "standard", basePrices: {...TOKEN_CONFIG.basePrices, input: 0.5}})
    );

    await save(wrapper);

    const payload = updatePayload();
    expect(configOf(payload).basePrices.input).toBe(0.5);
    // 其余分项未被文本覆盖时保留原值
    expect(configOf(payload).basePrices.output).toBe(0.006);
  });

  it("config_json 不是合法 JSON 时拒绝保存，且留在 json 页让用户继续修", async () => {
    const wrapper = await openDialog({ruleId: RULE_ID});
    await switchTab(wrapper, "config_json");
    await setJsonText(wrapper, '{"basePrices":');

    await save(wrapper);

    expect(warned()).toContain("config_json 不是合法 JSON");
    expect(updateBillingRule).not.toHaveBeenCalled();
    expect(activeTabLabel(wrapper)).toContain("config_json");
  });

  // 曾经失效过（补测时发现、已修）：回写失败后把 activeTab 设回 "json"，
  // 但若与用户点击发生在**同一个 flush**，modelValue 先变 "form" 又变回 "json"、父组件只渲染一次，
  // el-tabs 收到的 prop 净变化为空 → `Tabs` 的 `watch(() => props.modelValue)`
  // （element-plus/es/components/tabs/src/tabs.mjs:115）依赖从未触发，内部 currentName 停在 "form"。
  // 结果不是崩溃而是**静默拒绝保存**：用户看着表单点保存，submit 却去回写那段过期非法文本。
  // 修法是 `nextTick` 后再重设 activeTab，让 prop 变化跨 flush 生效。
  it("json 文本非法时切回表单页，界面真正弹回 json 页", async () => {
    const wrapper = await openDialog({ruleId: RULE_ID});
    await switchTab(wrapper, "config_json");
    await setJsonText(wrapper, "{oops");

    await switchTab(wrapper, "表单编辑");

    expect(warned()).toContain("config_json 不是合法 JSON");
    expect(activeTabLabel(wrapper)).toContain("config_json");
    // 弹回时不得用表单重建文本——否则用户正在修的非法内容会被静默清空
    expect((wrapper.find(".json-stub").element as HTMLTextAreaElement).value).toBe("{oops");

    await save(wrapper);
    expect(updateBillingRule).not.toHaveBeenCalled();
  });

  it("新建时上次停在 json 页后关闭，重开不得把遗留文本回写进空表单", async () => {
    // 新建态才是 `suppressJsonApplyBack` 的唯一真作用面：编辑态重开时详情回显会把
    // applyConfig 写进去的值全部覆盖，遗留文本留不下痕迹（探针实测：去掉守卫后编辑态用例仍绿）。
    // 新建态没有回显，resetForm 给完默认值后紧接着就是那次多余的 tab 回写——
    // 少了守卫，9.99 会直接顶掉 0.002 并原样落库。
    const wrapper = await openDialog();
    await switchTab(wrapper, "config_json");
    await setJsonText(wrapper, JSON.stringify({cacheWriteMode: "standard", basePrices: {input: 9.99}}));

    await wrapper.setProps({modelValue: false});
    await flushPromises();
    await wrapper.setProps({modelValue: true});
    await flushPromises();

    expect(activeTabLabel(wrapper)).toContain("表单编辑");
    await wrapper.find('input[placeholder="如 TOKEN_GPT4O"]').setValue("TOKEN_NEW");
    await save(wrapper);

    expect(createBillingRule).toHaveBeenCalledTimes(1);
    expect(configOf(vi.mocked(createBillingRule).mock.calls[0][0] as Dict).basePrices.input).toBe(0.002);
  });

  // ==================== 规则编码绑定锁 ====================

  it("编码已被供应商模型绑定时只读并给出引用处数", async () => {
    vi.mocked(getBillingRule).mockResolvedValue(detailRow({boundModelCount: 3}) as never);

    const wrapper = await openDialog({ruleId: RULE_ID});
    const input = wrapper.find('input[placeholder="如 TOKEN_GPT4O"]');

    expect((input.element as HTMLInputElement).readOnly).toBe(true);
    expect(wrapper.text()).toContain("已被 3 个供应商模型使用，需先解绑才能修改");
  });

  it("未绑定的编码可编辑", async () => {
    const wrapper = await openDialog({ruleId: RULE_ID});
    const input = wrapper.find('input[placeholder="如 TOKEN_GPT4O"]');

    expect((input.element as HTMLInputElement).readOnly).toBe(false);
    expect(wrapper.text()).not.toContain("需先解绑才能修改");
  });

  it("被绑定的编码改码时拦在提交前", async () => {
    vi.mocked(getBillingRule).mockResolvedValue(detailRow({boundModelCount: 3}) as never);

    const wrapper = await openDialog({ruleId: RULE_ID});
    await wrapper.find('input[placeholder="如 TOKEN_GPT4O"]').setValue("TOKEN_RENAMED");
    await save(wrapper);

    expect(warned()).toContain("规则编码已被 3 个供应商模型使用，请先解绑后再修改");
    expect(updateBillingRule).not.toHaveBeenCalled();
  });

  it("改码判定用 trim 同口径——纯首尾空白不算改码", async () => {
    vi.mocked(getBillingRule).mockResolvedValue(detailRow({boundModelCount: 3}) as never);

    const wrapper = await openDialog({ruleId: RULE_ID});
    await wrapper.find('input[placeholder="如 TOKEN_GPT4O"]').setValue("  TOKEN_GPT4O  ");
    await save(wrapper);

    expect(updateBillingRule).toHaveBeenCalledTimes(1);
    expect(updatePayload().code).toBe("TOKEN_GPT4O");
  });

  it("规则编码为空时拒绝保存", async () => {
    const wrapper = await openDialog({ruleId: RULE_ID});
    await wrapper.find('input[placeholder="如 TOKEN_GPT4O"]').setValue("");

    await save(wrapper);

    expect(warned()).toContain("请填写规则编码");
    expect(updateBillingRule).not.toHaveBeenCalled();
  });

  // ==================== 提交 payload 口径 ====================

  it("缓存写方式来回切换时清零对方单价：ttl 落库只带 5m/1h，切回 standard 不带回旧值", async () => {
    // 注意：落库口径本身在 buildBaseConfig 里就按 cacheWriteMode 门控，
    // 光看 payload 测不出那个 watch——必须走「切过去再切回来」，才能看出
    // 表单里的另一族单价是否被真正清零（否则切回 standard 会把 ttl 期间的旧价带回来）。
    const wrapper = await openDialog({ruleId: RULE_ID});
    const form = formOf(wrapper);
    form.cacheWrite5mUnitPrice = 0.0009;
    form.cacheWrite1hUnitPrice = 0.0011;
    form.cacheWriteMode = "ttl";
    await flushPromises();

    await save(wrapper);
    expect(configOf(updatePayload()).basePrices).toMatchObject({
      cacheWrite: 0,
      cacheWrite5m: 0.0009,
      cacheWrite1h: 0.0011
    });

    form.cacheWriteMode = "standard";
    await flushPromises();
    await save(wrapper);
    expect(configOf(updatePayload()).basePrices).toMatchObject({
      cacheWrite: 0,
      cacheWrite5m: 0,
      cacheWrite1h: 0
    });
  });

  it("单价收敛到全站精度口径（8 位小数）", async () => {
    // roundPrice 的位数默认取 billingPrice.ts 的 UNIT_PRICE_PRECISION = 8，
    // 与后端 setScale(8, HALF_UP) 对齐（组件内注释已同步为 8 位）。
    const wrapper = await openDialog({ruleId: RULE_ID});
    formOf(wrapper).inputUnitPrice = 0.1234567891;
    await flushPromises();

    await save(wrapper);

    expect(configOf(updatePayload()).basePrices.input).toBe(0.12345679);
  });

  it("FREE 模式单位强制 1K_TOKENS，与能力矩阵给的默认单位无关", async () => {
    vi.mocked(getBillingRule).mockResolvedValue(
      detailRow({billingMode: "FREE", unit: "PER_REQUEST", configJson: ""}) as never
    );

    const wrapper = await openDialog({ruleId: RULE_ID});
    await save(wrapper);

    expect(updatePayload().unit).toBe("1K_TOKENS");
  });

  it("VIDEO 模式只写填过的分辨率单价", async () => {
    vi.mocked(getBillingRule).mockResolvedValue(
      detailRow({
        billingMode: "VIDEO",
        unit: "PER_SECOND",
        configJson: JSON.stringify({defaultUnitPrice: 0.1, resolutionPrices: {"720P": 0.2}})
      }) as never
    );

    const wrapper = await openDialog({ruleId: RULE_ID});
    expect(formOf(wrapper, BillingModeVideoConfig).videoPrice720p).toBe(0.2);
    await save(wrapper);

    expect(configOf(updatePayload())).toEqual({defaultUnitPrice: 0.1, resolutionPrices: {"720P": 0.2}});
  });

  it("CUSTOM 模式原样透传自定义配置文本，不做重建", async () => {
    vi.mocked(getBillingRule).mockResolvedValue(
      detailRow({billingMode: "CUSTOM", unit: "CUSTOM", configJson: '{"custom":{"weight":1.5}}'}) as never
    );

    const wrapper = await openDialog({ruleId: RULE_ID});
    expect(formOf(wrapper, BillingModeCustomConfig).customConfigJson).toBe('{"custom":{"weight":1.5}}');
    await save(wrapper);

    expect(updatePayload().configJson).toBe('{"custom":{"weight":1.5}}');
  });

  it("切到阶梯价时用基础价预填首个档位，档位缺失项以 0 落库", async () => {
    const wrapper = await openDialog({ruleId: RULE_ID});
    formOf(wrapper).pricingPlan = "TIERED";
    await flushPromises();

    await save(wrapper);

    const config = configOf(updatePayload());
    expect(config.tier).toHaveLength(1);
    expect(config.tier[0].condition).toEqual({from: 0});
    expect(config.tier[0].unitPrices).toEqual({
      input: 0.003,
      output: 0.006,
      cacheRead: 0.0003,
      cacheWrite: 0.0015,
      cacheWrite5m: 0,
      cacheWrite1h: 0
    });
  });

  it("切到高低峰时补默认骨架：首条高峰、末条兜底低峰", async () => {
    const wrapper = await openDialog({ruleId: RULE_ID});
    formOf(wrapper).pricingPlan = "PEAK_OFF_PEAK";
    await flushPromises();

    await save(wrapper);

    const pricing = configOf(updatePayload()).pricing;
    expect(pricing.rateMode).toBe("MULTIPLIER");
    expect(pricing.timezone).toBe("UTC+0");
    expect(pricing.phases).toHaveLength(2);
    expect(pricing.phases[0]).toMatchObject({phase: "PEAK", multiplier: 1});
    expect(pricing.phases[1]).toMatchObject({phase: "OFF_PEAK", multiplier: 0.8, condition: {type: "DEFAULT"}});
  });

  // ==================== 保存成功 ====================

  it("保存成功后关闭抽屉并抛出 saved", async () => {
    const wrapper = await openDialog({ruleId: RULE_ID});
    await save(wrapper);

    expect(vi.mocked(ElMessage.success).mock.calls.at(-1)?.[0]).toBe("已保存");
    expect(wrapper.emitted("update:modelValue")?.at(-1)).toEqual([false]);
    expect(wrapper.emitted("saved")).toBeTruthy();
  });
});

/** 新建态的默认配置：resetForm 的初值经 buildBaseConfig 投影 */
function TOKEN_DEFAULTS(): Dict {
  return {
    cacheWriteMode: "standard",
    basePrices: {input: 0.002, output: 0.002, cacheRead: 0, cacheWrite: 0, cacheWrite5m: 0, cacheWrite1h: 0}
  };
}

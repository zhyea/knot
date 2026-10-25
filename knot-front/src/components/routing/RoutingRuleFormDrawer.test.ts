// @vitest-environment jsdom
import {describe, it, expect, vi, beforeEach, afterEach} from "vitest";
import {enableAutoUnmount, mount, flushPromises, type VueWrapper} from "@vue/test-utils";
import {defineComponent, h, onMounted} from "vue";
import {ElDrawer, ElMessage} from "element-plus";
import RoutingRuleFormDrawer from "@/components/routing/RoutingRuleFormDrawer.vue";
import {useMissingOptionReporter} from "@/composables/useMissingOptionGuard";
import type {Dict} from "@/types";

/**
 * `RoutingRuleFormDrawer` 的存量行为固化（改进清单 §4.7 第五批：986 行 / 41 次提交 / **零测试**）。
 *
 * <p>与前面三个「声明多」的巨型组件不同，它有两处真正的状态机：
 * ① **路由目标列表的合并**——切目标类型只重算当前类型，另一类型的目标必须原样保留，
 * 主目标要自动指定并在被删后回落；② **摘除目标管理面**——只在编辑态渲染、网关不可达时静默降级。
 * 两处出错都不报错，只会静默丢用户已选的目标、把主目标指错行，或让保存变成一句看不懂的提示。</p>
 *
 * <p>断言落点：抽屉标题、提示文案、目标表格的渲染结果、以及 `createRoutingRule` /
 * `updateRoutingRule` 收到的 payload。`RemoteEntitySelect` 用透传 stub 替掉（它内部会打 options
 * 接口），三个策略区也用 stub（只透传 v-model，便于直接推策略值）。</p>
 */

const MISSING = "__missing__";

// el-select 的下拉 teleport 到 body，不 unmount 会残留到后续用例（曾导致选项断言拿到 98 条历史节点）
enableAutoUnmount(afterEach);

const RemoteSelectStub = defineComponent({
  name: "RemoteEntitySelect",
  props: ["modelValue", "loadFunction", "selectedOptions", "extraParams", "placeholder", "multiple", "clearable"],
  emits: ["update:modelValue"],
  setup(props) {
    // 模拟真实组件「下拉缺失值」上报：值等于 MISSING 时向守卫报一条。
    // 只在 mounted 上报——守卫内部是 ref<Map>，在 render 里 set 会触发自身重渲染。
    const reporter = useMissingOptionReporter();
    onMounted(() => {
      if (props.modelValue === MISSING) {
        reporter?.report("归属用户#1", [MISSING]);
      }
    });
    return () => h("i", {class: "remote-stub"});
  }
});

const TrafficStub = defineComponent({
  name: "TrafficPolicySection",
  props: ["rateLimit", "mode", "title", "description"],
  setup: () => () => h("i", {class: "traffic-stub"})
});
const RetryStub = defineComponent({
  name: "RetryPolicySection",
  props: ["retryPolicy", "title", "description"],
  setup: () => () => h("i", {class: "retry-stub"})
});
const EjectionStub = defineComponent({
  name: "EjectionPolicySection",
  props: ["ejectionPolicy", "title", "description"],
  setup: () => () => h("i", {class: "ejection-stub"})
});

vi.mock("@/api/routing", () => ({
  createRoutingRule: vi.fn(),
  updateRoutingRule: vi.fn(),
  updateRoutingRuleStatus: vi.fn(),
  checkRoutingRuleCode: vi.fn()
}));

vi.mock("@/api/gatewayOutage", () => ({
  listGatewayOutages: vi.fn(),
  releaseGatewayOutage: vi.fn(),
  ejectGatewayOutage: vi.fn()
}));

vi.mock("@/api/options", () => ({
  listAppOptions: vi.fn(),
  listLogicalModelOptions: vi.fn(),
  listModelOptions: vi.fn(),
  listModelPoolOptions: vi.fn(),
  listRoutingConsumerOptions: vi.fn(),
  listUserUsernameOptions: vi.fn()
}));

const ENUM_LABELS: Dict = {MODEL: "模型", MODEL_POOL: "模型池", PRIORITY: "优先级", LEAST_LOAD: "最少负载"};
vi.mock("@/composables/useEnumOptions", () => ({
  useEnumOptions: () => ({
    optionsOf: (name: string) =>
      name === "RouteTargetTypeEnum"
        ? [{value: "MODEL", label: "模型"}, {value: "MODEL_POOL", label: "模型池"}]
        : [{value: "PRIORITY", label: "优先级"}, {value: "LEAST_LOAD", label: "最少负载"}],
    labelOf: (_name: string, code: unknown, fallback?: string) => ENUM_LABELS[String(code)] ?? fallback ?? String(code)
  })
}));

import {checkRoutingRuleCode, createRoutingRule, updateRoutingRule} from "@/api/routing";
import {ejectGatewayOutage, listGatewayOutages, releaseGatewayOutage} from "@/api/gatewayOutage";
import {
  listAppOptions,
  listLogicalModelOptions,
  listModelOptions,
  listModelPoolOptions,
  listRoutingConsumerOptions,
  listUserUsernameOptions
} from "@/api/options";

const RULE_ID = 5;

const RULE = {
  id: RULE_ID,
  ruleCode: "R_GPT",
  name: "GPT 路由",
  appScenario: "知识库问答，客服对话",
  consumerCode: "cons-1",
  consumerName: "消费者一",
  appId: 12,
  appName: "应用一",
  username: "zhangsan",
  userRealName: "张三",
  logicalModelCode: "lm-gpt",
  logicalModelName: "GPT 统一模型",
  selectionStrategy: "PRIORITY",
  enabled: true,
  boundCount: 0,
  targets: [
    {targetType: "MODEL", targetCode: "m-1", targetName: "模型一", priority: 10, primary: true},
    {targetType: "MODEL", targetCode: "m-2", targetName: "模型二", priority: 20, primary: false}
  ],
  rateLimitPolicy: {rpm: 0, tpm: 0}
};

function ruleRow(overrides: Dict = {}): Dict {
  return {...RULE, ...overrides};
}

function outageRow(overrides: Dict = {}): Dict {
  return {
    ruleCode: "r_gpt",
    modelCode: "m-1",
    failures: 3,
    slows: 1,
    windowEndAt: Date.now(),
    ejectedUntil: 0,
    probeUntil: 0,
    cooldownMs: 60000,
    reason: "FAILURE",
    ...overrides
  };
}

/** 打开抽屉必须走 setProps：初始化挂在 watch([modelValue, rule]) 上，没有 immediate */
async function openDrawer(props: Dict = {}): Promise<VueWrapper> {
  const wrapper = mount(RoutingRuleFormDrawer, {
    props: {modelValue: false, rule: null, ...props},
    global: {
      stubs: {
        RemoteEntitySelect: RemoteSelectStub,
        TrafficPolicySection: TrafficStub,
        RetryPolicySection: RetryStub,
        EjectionPolicySection: EjectionStub
      }
    }
  });
  await wrapper.setProps({modelValue: true});
  await flushPromises();
  return wrapper;
}

/** 按表单项 label 精确定位其中的 input（label 归一后等值比较，避免「模型」误命中「统一模型」） */
function inputOf(wrapper: VueWrapper, label: string) {
  const normalize = (text: string) => text.replace(/[*\s:：]/g, "");
  const item = wrapper.findAll(".el-form-item").find((node) => {
    const el = node.find(".el-form-item__label");
    return el.exists() && normalize(el.text()) === normalize(label);
  });
  if (!item) {
    throw new Error(`找不到表单项：${label}`);
  }
  const input = item.find("input");
  if (!input.exists()) {
    throw new Error(`表单项 ${label} 内没有 input`);
  }
  return input;
}

/** 按 placeholder 定位某个 RemoteEntitySelect stub（template 里 5 个下拉各有独特 placeholder） */
function remoteOf(wrapper: VueWrapper, placeholder: string) {
  const found = wrapper
    .findAllComponents(RemoteSelectStub)
    .find((node) => String(node.props("placeholder") || "").includes(placeholder));
  if (!found) {
    throw new Error(`找不到下拉：${placeholder}`);
  }
  return found;
}

async function pickTargets(wrapper: VueWrapper, codes: string[]) {
  await remoteOf(wrapper, "路由目标").vm.$emit("update:modelValue", codes);
  await flushPromises();
}

async function switchTargetType(wrapper: VueWrapper, index: 0 | 1) {
  // jsdom 下点 <label> 不会激活内部 radio，直接 setValue 到原生 input 才可靠
  await wrapper.findAll(".el-radio-button__original-radio")[index].setValue(true);
  await flushPromises();
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

/** el-form-item 的 error 文案走 refDebounced(validateState, 100)，flushPromises 之后仍是空 */
async function settleFormItemError() {
  await new Promise((resolve) => setTimeout(resolve, 150));
  await flushPromises();
}

function warned(): string[] {
  return vi.mocked(ElMessage.warning).mock.calls.map((call) => String(call[0]));
}

function updateBody(): Dict {
  const calls = vi.mocked(updateRoutingRule).mock.calls;
  expect(calls.length).toBeGreaterThan(0);
  return calls[calls.length - 1][1] as Dict;
}

function createBody(): Dict {
  const calls = vi.mocked(createRoutingRule).mock.calls;
  expect(calls.length).toBeGreaterThan(0);
  return calls[calls.length - 1][0] as Dict;
}

/** 目标表格按渲染顺序取值：类型 / 编码 / 优先级 / 是否主目标 */
function targetRows(wrapper: VueWrapper): Dict[] {
  return wrapper.findAll(".bind-table .el-table__body tbody tr").map((tr) => {
    const cells = tr.findAll("td");
    return {
      type: cells[0].text(),
      code: cells[1].text(),
      priority: Number((cells[2].find("input").element as HTMLInputElement).value),
      primary: cells[3].find(".el-radio.is-checked").exists()
    };
  });
}

describe("RoutingRuleFormDrawer 路由规则表单", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(listAppOptions).mockResolvedValue({list: [{value: 12, label: "应用一"}]} as never);
    vi.mocked(listRoutingConsumerOptions).mockResolvedValue({list: [{value: "cons-1", label: "消费者一"}]} as never);
    vi.mocked(listLogicalModelOptions).mockResolvedValue({list: [{value: "lm-gpt", label: "GPT 统一模型"}]} as never);
    vi.mocked(listModelOptions).mockResolvedValue({
      list: [{value: "m-1", label: "模型一"}, {value: "m-2", label: "模型二"}, {value: "m-3", label: "模型三"}]
    } as never);
    vi.mocked(listModelPoolOptions).mockResolvedValue({list: [{value: "pool-1", label: "模型池一"}]} as never);
    vi.mocked(listUserUsernameOptions).mockResolvedValue({list: [{value: "zhangsan", label: "张三(zhangsan)"}]} as never);
    vi.mocked(checkRoutingRuleCode).mockResolvedValue({available: true} as never);
    vi.mocked(createRoutingRule).mockResolvedValue({} as never);
    vi.mocked(updateRoutingRule).mockResolvedValue({} as never);
    vi.mocked(listGatewayOutages).mockResolvedValue([] as never);
    vi.mocked(releaseGatewayOutage).mockResolvedValue({} as never);
    vi.mocked(ejectGatewayOutage).mockResolvedValue({} as never);

    vi.spyOn(ElMessage, "warning").mockImplementation(() => ({}) as never);
    vi.spyOn(ElMessage, "success").mockImplementation(() => ({}) as never);
    vi.spyOn(ElMessage, "error").mockImplementation(() => ({}) as never);
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  // ==================== 打开与回填 ====================

  it("新建：标题新建态，默认不启用，只预热应用与消费者候选、不拉摘除", async () => {
    const wrapper = await openDrawer();

    expect(wrapper.findComponent(ElDrawer).props("title")).toBe("新建路由规则");
    expect(listAppOptions).toHaveBeenCalledTimes(1);
    expect(listRoutingConsumerOptions).toHaveBeenCalledTimes(1);
    expect(listGatewayOutages).not.toHaveBeenCalled();
    expect(wrapper.find(".outage-table").exists()).toBe(false);
  });

  it("编辑：标题编辑态，编码原样保留大小写、表格按 primary 标出主目标", async () => {
    const wrapper = await openDrawer({rule: ruleRow()});

    expect(wrapper.findComponent(ElDrawer).props("title")).toBe("编辑路由规则");
    // 编码口径 = 只去空白（与后端 RoutingRuleService.normalizeRuleCode 一致），**不改大小写**
    expect((inputOf(wrapper, "规则编码").element as HTMLInputElement).value).toBe("R_GPT");
    expect(targetRows(wrapper)).toEqual([
      {type: "模型", code: "m-1", priority: 10, primary: true},
      {type: "模型", code: "m-2", priority: 20, primary: false}
    ]);

    await save(wrapper);
    // 场景标签按「，」拆成数组、提交时按同一分隔符拼回，往返无损
    expect(updateBody().appScenario).toBe("知识库问答，客服对话");
  });

  it("编辑态按 ruleCode 拉本规则的摘除目标", async () => {
    await openDrawer({rule: ruleRow()});
    expect(listGatewayOutages).toHaveBeenCalledWith({ruleCode: "R_GPT"});
  });

  it("关闭后用另一条规则重开：按新 ruleCode 重新拉取", async () => {
    // 摘除面板抽出后，刷新由父组件在「可见」时显式驱动——不能依赖抽屉 destroy-on-close
    // 把面板卸载重挂（该语义在 jsdom 里测不出来，等于拿一个测不到的前提换正确性）。
    const wrapper = await openDrawer({rule: ruleRow()});
    expect(listGatewayOutages).toHaveBeenLastCalledWith({ruleCode: "R_GPT"});

    await wrapper.setProps({modelValue: false});
    await flushPromises();
    await wrapper.setProps({rule: ruleRow({id: 6, ruleCode: "R_CLAUDE"}), modelValue: true});
    await flushPromises();

    expect(listGatewayOutages).toHaveBeenLastCalledWith({ruleCode: "R_CLAUDE"});
  });

  // ==================== 规则编码 ====================

  it("编码输入即归一：只去空白，保留大小写", async () => {
    // 曾经会 toLowerCase——与后端 trim-only 口径不一致，且会把 API 直写的大写编码静默改成小写。
    // 唯一性仍由 DB 的 uk_routing_rules_code（utf8mb4_unicode_ci）保证大小写不敏感。
    const wrapper = await openDrawer();
    await inputOf(wrapper, "规则编码").setValue("  Rule_ABC  ");
    await flushPromises();

    expect((inputOf(wrapper, "规则编码").element as HTMLInputElement).value).toBe("Rule_ABC");
  });

  it("编码为空时拦在提交前，不查重也不发请求", async () => {
    const wrapper = await openDrawer();
    await inputOf(wrapper, "名称").setValue("规则");
    await save(wrapper);
    await settleFormItemError();

    expect(wrapper.text()).toContain("请填写规则编码");
    expect(checkRoutingRuleCode).not.toHaveBeenCalled();
    expect(createRoutingRule).not.toHaveBeenCalled();
  });

  it("编码重复时报错且不提交；编辑态把自身 id 作为 excludeId，新建态传 null", async () => {
    vi.mocked(checkRoutingRuleCode).mockResolvedValue({available: false} as never);
    const wrapper = await openDrawer({rule: ruleRow()});
    await save(wrapper);

    await settleFormItemError();
    expect(checkRoutingRuleCode).toHaveBeenCalledWith("R_GPT", RULE_ID);
    expect(wrapper.text()).toContain("规则编码已存在，请更换");
    expect(updateRoutingRule).not.toHaveBeenCalled();

    vi.mocked(checkRoutingRuleCode).mockResolvedValue({available: true} as never);
    const created = await openDrawer();
    await inputOf(created, "名称").setValue("规则");
    await inputOf(created, "规则编码").setValue("new_rule");
    await save(created);
    expect(checkRoutingRuleCode).toHaveBeenLastCalledWith("new_rule", null);
  });

  it("编码已被引用时只读、给出引用提示，且跳过唯一性校验", async () => {
    const wrapper = await openDrawer({rule: ruleRow({boundCount: 2})});
    const input = inputOf(wrapper, "规则编码");

    expect((input.element as HTMLInputElement).readOnly).toBe(true);
    expect(wrapper.text()).toContain("已被 2 处引用，需先解除绑定才能修改编码");

    await save(wrapper);
    expect(checkRoutingRuleCode).not.toHaveBeenCalled();
    expect(updateRoutingRule).toHaveBeenCalledTimes(1);
  });

  it("查重接口异常时静默不提交（错误由全局拦截器提示，不再二次弹窗）", async () => {
    vi.mocked(checkRoutingRuleCode).mockRejectedValue(new Error("500"));
    const wrapper = await openDrawer({rule: ruleRow()});
    await save(wrapper);

    expect(updateRoutingRule).not.toHaveBeenCalled();
    expect(warned()).toEqual([]);
  });

  // ==================== 路由目标状态机（核心静默风险） ====================

  it("切到模型池只重算池目标，已选的模型目标与优先级原样保留", async () => {
    const wrapper = await openDrawer({rule: ruleRow()});
    await switchTargetType(wrapper, 1);
    await pickTargets(wrapper, ["pool-1"]);

    expect(targetRows(wrapper)).toEqual([
      {type: "模型", code: "m-1", priority: 10, primary: true},
      {type: "模型", code: "m-2", priority: 20, primary: false},
      {type: "模型池", code: "pool-1", priority: 100, primary: false}
    ]);

    await save(wrapper);
    expect(updateBody().targets).toEqual([
      {targetType: "MODEL", targetCode: "m-1", priority: 10, primary: true},
      {targetType: "MODEL", targetCode: "m-2", priority: 20, primary: false},
      {targetType: "MODEL_POOL", targetCode: "pool-1", priority: 100, primary: false}
    ]);
  });

  it("⚠ 先加池目标再改模型选择时，池目标被整体排到列表前面（顺序变化，主目标不变）", async () => {
    // 只有「真的改了当前类型的选择」才会走 onSelectedTargetsChange 的
    // `form.targets = [...otherTargets, ...currentTargets]`，此时另一类型整体前移。
    // 单纯切类型（不动选择）不会重建列表（见下一条用例）。
    // 主目标按 targetKey 记录，不受顺序影响；payload 的 targets 数组顺序会跟着变。
    const wrapper = await openDrawer({rule: ruleRow()});
    await switchTargetType(wrapper, 1);
    await pickTargets(wrapper, ["pool-1"]);
    await switchTargetType(wrapper, 0);
    await pickTargets(wrapper, ["m-1", "m-2", "m-3"]);

    expect(targetRows(wrapper).map((row) => row.code)).toEqual(["pool-1", "m-1", "m-2", "m-3"]);
    expect(targetRows(wrapper).filter((row) => row.primary).map((row) => row.code)).toEqual(["m-1"]);
  });

  it("切类型但不改选择时目标列表与主目标都不动", async () => {
    const wrapper = await openDrawer({rule: ruleRow()});
    await switchTargetType(wrapper, 1);
    await switchTargetType(wrapper, 0);

    expect(targetRows(wrapper)).toEqual([
      {type: "模型", code: "m-1", priority: 10, primary: true},
      {type: "模型", code: "m-2", priority: 20, primary: false}
    ]);
  });

  it("首次选择目标时自动把第一个设为主目标，新增行默认优先级 100", async () => {
    const wrapper = await openDrawer();
    await pickTargets(wrapper, ["m-2", "m-3"]);

    expect(targetRows(wrapper)).toEqual([
      {type: "模型", code: "m-2", priority: 100, primary: true},
      {type: "模型", code: "m-3", priority: 100, primary: false}
    ]);
  });

  it("改选择把原主目标移出后，主目标回落到当前类型的首个（另一类型目标保留）", async () => {
    const wrapper = await openDrawer({rule: ruleRow()});
    await switchTargetType(wrapper, 1);
    await pickTargets(wrapper, ["pool-1"]);
    await switchTargetType(wrapper, 0);
    // 把主目标 m-1 从选择里去掉，只剩 m-2
    await pickTargets(wrapper, ["m-2"]);

    expect(targetRows(wrapper)).toEqual([
      {type: "模型池", code: "pool-1", priority: 100, primary: false},
      {type: "模型", code: "m-2", priority: 20, primary: true}
    ]);
  });

  it("删除主目标后主目标回落到剩余首个", async () => {
    const wrapper = await openDrawer({rule: ruleRow()});
    // 每行操作列只有一个删除按钮，删第一行（当前主目标）
    await wrapper.findAll(".bind-table .el-button")[0].trigger("click");
    await flushPromises();

    expect(targetRows(wrapper)).toEqual([{type: "模型", code: "m-2", priority: 20, primary: true}]);

    await save(wrapper);
    expect(updateBody().targets).toEqual([
      {targetType: "MODEL", targetCode: "m-2", priority: 20, primary: true}
    ]);
  });

  it("启用态目标为空时拦在提交前", async () => {
    const wrapper = await openDrawer();
    await inputOf(wrapper, "名称").setValue("规则");
    await inputOf(wrapper, "规则编码").setValue("r_new");
    await remoteOf(wrapper, "请选择应用").vm.$emit("update:modelValue", 12);
    await remoteOf(wrapper, "请选择消费者").vm.$emit("update:modelValue", "cons-1");
    await wrapper.find(".inline-switch .el-switch").trigger("click");
    await flushPromises();

    await save(wrapper);
    expect(warned()).toContain("启用规则前请完整配置路由目标");
    expect(createRoutingRule).not.toHaveBeenCalled();
  });

  // ==================== 提交 payload 口径 ====================

  it("停用的重试与摘除策略提交归一化结果，而不是 null", async () => {
    // 注释明确：传 null 会被运行时当成「未配置」而退回默认开启策略 —— 必须显式落库
    const wrapper = await openDrawer({rule: ruleRow()});
    wrapper.findComponent(RetryStub).vm.$emit("update:retry-policy", {enabled: false, maxAttempts: 1});
    wrapper.findComponent(EjectionStub).vm.$emit("update:ejection-policy", {enabled: false});
    await flushPromises();

    await save(wrapper);
    const body = updateBody();
    expect(body.retryPolicy.enabled).toBe(false);
    expect(body.retryPolicy.maxAttempts).toBe(1);
    expect(body.ejectionPolicy.enabled).toBe(false);
    expect(body.ejectionPolicy.windowSec).toBeGreaterThan(0);
  });

  it("限流留空（rpm/tpm 均 0）时提交 null，而不是全零策略", async () => {
    const wrapper = await openDrawer({rule: ruleRow()});
    await save(wrapper);
    expect(updateBody().rateLimitPolicy).toBeNull();

    const filled = await openDrawer({rule: ruleRow()});
    filled.findComponent(TrafficStub).vm.$emit("update:rate-limit", {rpm: 60, tpm: 0});
    await flushPromises();
    await save(filled);
    expect(updateBody().rateLimitPolicy).toEqual({rpm: 60, tpm: 0});
  });

  it("空字符串字段一律提交 null，名称去空白", async () => {
    const wrapper = await openDrawer({rule: ruleRow({username: "", logicalModelCode: null, appScenario: ""})});
    await inputOf(wrapper, "名称").setValue("  规则  ");
    await save(wrapper);

    const body = updateBody();
    expect(body.name).toBe("规则");
    expect(body.username).toBeNull();
    expect(body.logicalModelCode).toBeNull();
    expect(body.appScenario).toBeNull();
    expect(body.selectionStrategy).toBe("PRIORITY");
  });

  // ==================== 启用前置校验 ====================

  it("名称缺失时拦在提交前", async () => {
    const wrapper = await openDrawer({rule: ruleRow({name: "  "})});
    await save(wrapper);

    expect(warned()).toContain("请填写名称");
    expect(updateRoutingRule).not.toHaveBeenCalled();
  });

  it("启用态缺消费者 / 缺绑定应用时各自提示，且先报消费者", async () => {
    const wrapper = await openDrawer({rule: ruleRow({consumerCode: null, appId: null})});
    await save(wrapper);
    expect(warned()).toContain("启用规则前请选择消费者");
    expect(updateRoutingRule).not.toHaveBeenCalled();

    await remoteOf(wrapper, "请选择消费者").vm.$emit("update:modelValue", "cons-1");
    await flushPromises();
    await save(wrapper);
    expect(warned()).toContain("启用规则前请选择绑定应用");
    expect(updateRoutingRule).not.toHaveBeenCalled();
  });

  it("未启用时跳过消费者 / 应用 / 目标的完整性校验", async () => {
    const wrapper = await openDrawer({
      rule: ruleRow({enabled: false, consumerCode: null, appId: null, targets: []})
    });
    await save(wrapper);

    expect(updateRoutingRule).toHaveBeenCalledTimes(1);
    expect(updateBody().targets).toEqual([]);
  });

  it("下拉缺失值守卫生效时阻止提交并给出可读提示", async () => {
    const wrapper = await openDrawer({rule: ruleRow({username: MISSING})});
    await save(wrapper);

    expect(warned().some((text) => text.includes("存在无效的已选项"))).toBe(true);
    expect(updateRoutingRule).not.toHaveBeenCalled();
  });

  // ==================== 结果处理 ====================

  it("新建成功走 createRoutingRule，关闭抽屉并抛出 saved", async () => {
    const wrapper = await openDrawer();
    await inputOf(wrapper, "名称").setValue("新规则");
    await inputOf(wrapper, "规则编码").setValue("r_new");
    await save(wrapper);

    expect(createRoutingRule).toHaveBeenCalledTimes(1);
    expect(createBody().ruleCode).toBe("r_new");
    expect(wrapper.emitted("update:modelValue")?.at(-1)).toEqual([false]);
    expect(wrapper.emitted("saved")).toBeTruthy();
    expect(vi.mocked(ElMessage.success).mock.calls.at(-1)?.[0]).toBe("已创建");
  });

  it("编辑成功走 updateRoutingRule(id, body)", async () => {
    const wrapper = await openDrawer({rule: ruleRow()});
    await save(wrapper);

    expect(updateRoutingRule).toHaveBeenCalledTimes(1);
    expect(vi.mocked(updateRoutingRule).mock.calls[0][0]).toBe(RULE_ID);
    expect(vi.mocked(ElMessage.success).mock.calls.at(-1)?.[0]).toBe("已保存");
  });

  // ==================== 摘除目标管理面（B4） ====================

  it("摘除列表按 ejectedUntil 区分「摘除中 / 探活中」，冷却归零显示破折号", async () => {
    const now = Date.now();
    vi.mocked(listGatewayOutages).mockResolvedValue([
      // 摘除中：看 ejectedUntil（此行的 probeUntil 更早，若误取 min 会显示 30s）
      outageRow({modelCode: "m-1", ejectedUntil: now + 120_000, probeUntil: now + 30_000}),
      outageRow({modelCode: "m-2", ejectedUntil: now + 30_000, probeUntil: now + 120_000}),
      // 探活中：看 probeUntil（ejectedUntil 已过期；曾经取 min 恒 ≤ 0，这一列永远显示「—」）
      outageRow({modelCode: "m-3", ejectedUntil: 0, probeUntil: now + 90_000}),
      outageRow({modelCode: "m-4", ejectedUntil: 0, probeUntil: 0})
    ] as never);

    const wrapper = await openDrawer({rule: ruleRow()});
    const rows = wrapper.findAll(".outage-table .el-table__body tbody tr");
    const cooldownOf = (index: number) => rows[index].findAll("td")[4].text();

    expect(rows).toHaveLength(4);
    expect(rows[0].text()).toContain("摘除中");
    expect(cooldownOf(0)).toMatch(/2m0s/);
    expect(rows[1].text()).toContain("摘除中");
    expect(cooldownOf(1)).toMatch(/30s/);
    expect(rows[2].text()).toContain("探活中");
    expect(cooldownOf(2)).toMatch(/1m3\ds/);
    expect(rows[3].text()).toContain("探活中");
    expect(cooldownOf(3)).toBe("—");
  });

  it("网关不可达时摘除列表静默降级为空态，不影响表单编辑与保存", async () => {
    vi.mocked(listGatewayOutages).mockRejectedValue(new Error("gateway down"));
    const wrapper = await openDrawer({rule: ruleRow()});

    expect(wrapper.text()).toContain("当前无摘除目标");
    await save(wrapper);
    expect(updateRoutingRule).toHaveBeenCalledTimes(1);
  });

  it("恢复摘除目标后重新拉取列表", async () => {
    vi.mocked(listGatewayOutages).mockResolvedValue([
      outageRow({modelCode: "m-1", ejectedUntil: Date.now() + 60_000, probeUntil: Date.now() + 60_000})
    ] as never);
    const wrapper = await openDrawer({rule: ruleRow()});
    expect(listGatewayOutages).toHaveBeenCalledTimes(1);

    await wrapper.findAll(".outage-table .el-button")[0].trigger("click");
    await flushPromises();

    expect(releaseGatewayOutage).toHaveBeenCalledWith({ruleCode: "R_GPT", modelCode: "m-1"});
    expect(vi.mocked(ElMessage.success).mock.calls.at(-1)?.[0]).toBe("已恢复 m-1");
    expect(listGatewayOutages).toHaveBeenCalledTimes(2);
  });

  it("手动摘除：模型 code 为空时提示，不调接口", async () => {
    const wrapper = await openDrawer({rule: ruleRow()});
    await wrapper.findAll(".outage-eject-form .el-button")[0].trigger("click");
    await flushPromises();

    expect(warned()).toContain("请选择或输入模型 code");
    expect(ejectGatewayOutage).not.toHaveBeenCalled();
  });

  it("手动摘除候选只包含 MODEL 类型目标，不含模型池", async () => {
    const wrapper = await openDrawer({
      rule: ruleRow({
        targets: [
          {targetType: "MODEL", targetCode: "m-1", priority: 10, primary: true},
          {targetType: "MODEL_POOL", targetCode: "pool-1", priority: 20, primary: false}
        ]
      })
    });

    const select = wrapper.find(".outage-eject-form .el-select");
    await select.trigger("click");
    await flushPromises();

    const labels = Array.from(document.querySelectorAll(".el-select-dropdown__item")).map((node) => node.textContent);
    expect(labels).toContain("m-1");
    expect(labels).not.toContain("pool-1");
  });
});

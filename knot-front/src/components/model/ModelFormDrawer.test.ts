// @vitest-environment jsdom
import {describe, it, expect, vi, beforeEach, afterEach} from "vitest";
import {mount, flushPromises, type VueWrapper} from "@vue/test-utils";
import {ElDrawer, ElMessage} from "element-plus";
import ModelFormDrawer from "@/components/model/ModelFormDrawer.vue";

/**
 * `ModelFormDrawer` 的存量行为固化（改进清单 §4.7：1113 行 / 57 次提交 / **零测试**，
 * 拆不拆先不论，测试必须先补）。
 *
 * <p>本文件刻意 mock 掉「外部世界」——4 个 api 模块与 `useModelTypes`（协议映射表）——
 * 由测试持有协议列表，这样才能驱动「协议映射表未就绪 / 就绪」两种时序。
 * 子组件（EnumControl / RemoteEntitySelect / ProviderAccountTreeSelect / TrafficPolicySection）
 * 用透传 modelValue 的 stub 替掉，便于读回绑定值；Element Plus 本体用真实组件。</p>
 *
 * <p>断言落点刻意选**业务可见行为**：抽屉标题、错误提示、以及「提交出去的 payload」。
 * 尤其协议归一与请求适配器兜底——它们出错不会报错，只会静默写错库。</p>
 */

const h = vi.hoisted(() => ({
  /** `protocolsOf(modelType)` 的返回值：测试用它模拟「协议映射表就绪 / 未就绪」 */
  protocols: [] as string[],
  loadModelTypes: (() => Promise.resolve()) as () => Promise<unknown>
}));

vi.mock("vue-router", () => ({
  useRouter: () => ({resolve: () => ({href: "/model-management/usage-extractors"})})
}));

vi.mock("@/composables/useModelTypes", async () => {
  const {ref} = await import("vue");
  return {
    useModelTypes: () => ({
      loading: ref(false),
      loadOptions: () => h.loadModelTypes(),
      protocolsOf: () => h.protocols,
      defaultCode: ref("")
    })
  };
});

vi.mock("@/composables/useEnumOptions", () => ({
  useEnumOptions: () => ({labelOf: (_enumName: string, code: unknown) => (code == null ? "-" : String(code))})
}));

vi.mock("@/api/models", () => ({
  checkModelCode: vi.fn(),
  createModel: vi.fn(),
  getModel: vi.fn(),
  listRequestAdapters: vi.fn(),
  listUsageExtractors: vi.fn(),
  updateModel: vi.fn()
}));

vi.mock("@/api/billing", () => ({listBillingRules: vi.fn()}));

// 任务协议候选：选项接口在打开抽屉时会调一次，必须挡住，否则 jsdom 会真发 XHR
vi.mock("@/api/asyncTaskProfiles", () => ({listAsyncTaskProfileOptions: vi.fn()}));

vi.mock("@/api/options", () => ({
  listBillingRuleOptions: vi.fn(),
  listLogicalModelOptions: vi.fn()
}));

import {checkModelCode, createModel, getModel, listRequestAdapters, listUsageExtractors, updateModel} from "@/api/models";
import {listBillingRules} from "@/api/billing";
import {listAsyncTaskProfileOptions} from "@/api/asyncTaskProfiles";
import {listBillingRuleOptions, listLogicalModelOptions} from "@/api/options";

const MODEL_ID = 7;
const LOGICAL_CODE = "lm-gpt";
const BILLING_CODE = "br-token";

const LOGICAL_OPTION = {
  value: LOGICAL_CODE,
  label: "GPT 统一模型",
  meta: {modelType: "CHAT", modelFamily: "openai", status: 1}
};

const EXTRACTORS = [
  {code: "openai.usage", label: "OpenAI 用量", billingModes: ["TOKEN"], streamSupported: true},
  {code: "video.usage", label: "视频用量", billingModes: ["PER_SECOND"], streamSupported: false}
];

const ADAPTERS = [
  {code: "passthrough", label: "透传"},
  {code: "openai.chat", label: "OpenAI Chat"}
];

/**
 * 任务协议候选：`value` 是档案编码，`meta.protocol` 决定它出现在哪个协议的下拉里。
 * 与真实接口一致——只返回启用档案，流式协议的项由后端标 `disabled`。
 */
const ASYNC_PROFILES = [
  {
    value: "KLING_TASK",
    label: "KLING_TASK",
    disabled: false,
    meta: {
      protocol: "CHAT_COMPLETIONS",
      queryMethod: "GET",
      queryPath: "/v1/videos/{task_id}",
      pollIntervalMs: 5000,
      timeoutMs: 1800000
    }
  },
  {
    value: "RESPONSES_TASK",
    label: "RESPONSES_TASK",
    disabled: true,
    meta: {
      protocol: "RESPONSES",
      queryMethod: "GET",
      queryPath: "/v1/responses/{task_id}",
      pollIntervalMs: 3000,
      timeoutMs: 600000
    }
  }
];

const EnumControlStub = {
  name: "EnumControl",
  props: ["modelValue"],
  template: "<i class='enum-stub'>{{ modelValue }}</i>"
};
const RemoteSelectStub = {
  name: "RemoteEntitySelect",
  props: ["modelValue", "loadFunction", "extraParams", "disabled", "placeholder"],
  template: "<i class='remote-stub'/>"
};
const AccountTreeStub = {
  name: "ProviderAccountTreeSelect",
  props: ["modelValue"],
  emits: ["change", "update:modelValue"],
  template: "<i class='account-stub'/>"
};
const TrafficStub = {
  name: "TrafficPolicySection",
  props: ["rateLimit", "mode", "title", "description"],
  template: "<i class='traffic-stub'/>"
};

function editModel(overrides: Record<string, unknown> = {}) {
  return {
    id: MODEL_ID,
    modelCode: "gpt-4o",
    upstreamModel: "gpt-4o",
    basePath: "/ep-1",
    remark: "备注",
    providerAccountCode: "acct-1",
    logicalModelCode: LOGICAL_CODE,
    billingRuleCode: BILLING_CODE,
    version: "1.0.0",
    enabled: true,
    rateLimitPolicy: {rpm: 0, tpm: 0},
    apiBindings: [
      {
        id: 11,
        protocol: "CHAT_COMPLETIONS",
        asyncProfileCode: null,
        apiPath: "/v1/chat/completions",
        requestAdapter: "OPENAI_CHAT",
        usageExtractor: "openai.usage",
        streamUsageExtractor: "",
        enabled: true,
        remark: ""
      }
    ],
    boundCount: 0,
    ...overrides
  };
}

/** 打开抽屉必须走 setProps：初始化挂在 watch([modelValue, model]) 上，没有 immediate */
async function openDrawer(props: Record<string, unknown> = {}): Promise<VueWrapper> {
  const wrapper = mount(ModelFormDrawer, {
    props: {modelValue: false, model: null, ...props},
    global: {
      stubs: {
        EnumControl: EnumControlStub,
        RemoteEntitySelect: RemoteSelectStub,
        ProviderAccountTreeSelect: AccountTreeStub,
        TrafficPolicySection: TrafficStub
      }
    }
  });
  await wrapper.setProps({modelValue: true});
  await flushPromises();
  return wrapper;
}

/** 读回每个 API 绑定当前的协议值（按渲染顺序） */
function protocolsOf(wrapper: VueWrapper): string[] {
  return wrapper.findAll(".enum-stub").map((node) => node.text());
}

function saveButton(wrapper: VueWrapper) {
  const buttons = wrapper.findAll(".el-drawer__footer .el-button");
  const save = buttons.find((b) => b.text().includes("保存"));
  if (!save) {
    throw new Error("找不到保存按钮");
  }
  return save;
}

function warned(): string[] {
  return vi.mocked(ElMessage.warning).mock.calls.map((call) => String(call[0]));
}

describe("ModelFormDrawer 供应商模型表单", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    h.protocols = ["CHAT_COMPLETIONS", "RESPONSES"];
    h.loadModelTypes = vi.fn().mockResolvedValue(undefined);

    vi.mocked(listLogicalModelOptions).mockResolvedValue({list: [LOGICAL_OPTION]} as never);
    vi.mocked(listBillingRuleOptions).mockResolvedValue({list: [{code: BILLING_CODE, label: "Token 计费"}]} as never);
    vi.mocked(listBillingRules).mockResolvedValue({
      list: [{code: BILLING_CODE, billingMode: "TOKEN", pricingPlan: "FIXED", providerName: "OpenAI"}]
    } as never);
    vi.mocked(listUsageExtractors).mockResolvedValue(EXTRACTORS as never);
    vi.mocked(listRequestAdapters).mockResolvedValue(ADAPTERS as never);
    vi.mocked(listAsyncTaskProfileOptions).mockResolvedValue(ASYNC_PROFILES as never);
    vi.mocked(getModel).mockResolvedValue(null);
    vi.mocked(checkModelCode).mockResolvedValue({available: true} as never);
    vi.mocked(createModel).mockResolvedValue({});
    vi.mocked(updateModel).mockResolvedValue({});

    vi.spyOn(ElMessage, "warning").mockImplementation(() => ({}) as never);
    vi.spyOn(ElMessage, "success").mockImplementation(() => ({}) as never);
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  // ==================== 打开与回填 ====================

  it("新建：标题为新建态，加载五类候选且不拉详情", async () => {
    const wrapper = await openDrawer();

    expect(wrapper.findComponent(ElDrawer).props("title")).toBe("新建供应商模型");
    expect(h.loadModelTypes).toHaveBeenCalledTimes(1);
    expect(listLogicalModelOptions).toHaveBeenCalledTimes(1);
    expect(listUsageExtractors).toHaveBeenCalledTimes(1);
    expect(listRequestAdapters).toHaveBeenCalledTimes(1);
    expect(listAsyncTaskProfileOptions).toHaveBeenCalledTimes(1);
    expect(getModel).not.toHaveBeenCalled();
    // 新建默认版本：v + 年(yyyy)月日时
    expect((wrapper.find('input[placeholder^="如 v"]').element as HTMLInputElement).value).toMatch(/^v\d{10}$/);
  });

  it("编辑：标题为编辑态，按 id 拉详情并按 code 精确回显计费规则", async () => {
    const wrapper = await openDrawer({model: editModel()});

    expect(wrapper.findComponent(ElDrawer).props("title")).toBe("编辑供应商模型");
    expect(getModel).toHaveBeenCalledWith(MODEL_ID);
    // 走 code 精确匹配，不是 keyword 模糊搜索
    expect(listBillingRules).toHaveBeenCalledWith(expect.objectContaining({code: BILLING_CODE, pageSize: 1}));
    expect(wrapper.text()).toContain(BILLING_CODE);
  });

  it("编辑：已绑计费规则被模糊命中但不含目标码时，表格回落为提示而非误展示", async () => {
    vi.mocked(listBillingRules).mockResolvedValue({
      list: [{code: "br-token-v2", billingMode: "TOKEN"}]
    } as never);

    const wrapper = await openDrawer({model: editModel()});

    expect(wrapper.text()).not.toContain("br-token-v2");
    expect(wrapper.text()).toContain("已绑定的计费规则不存在或已删除，请重新选择");
  });

  it("编码改码锁：编辑且已被引用时只读并给出引用处数", async () => {
    const locked = await openDrawer({model: editModel({boundCount: 3})});
    const lockedInput = locked.find('input[placeholder="请输入供应商模型编码"]');
    expect((lockedInput.element as HTMLInputElement).readOnly).toBe(true);
    expect(locked.text()).toContain("已被 3 处引用");

    const free = await openDrawer({model: editModel({boundCount: 0})});
    const freeInput = free.find('input[placeholder="请输入供应商模型编码"]');
    expect((freeInput.element as HTMLInputElement).readOnly).toBe(false);
    expect(free.text()).not.toContain("已被 3 处引用");
  });

  // ==================== 协议归一（两处静默风险） ====================

  it("协议映射表未就绪时不得改写已保存协议", async () => {
    // 映射表为空 = /api/models/types 还没回来；此时清空或改写成默认协议都会静默丢用户配置
    h.protocols = [];

    const wrapper = await openDrawer({
      model: editModel({
        apiBindings: [
          {id: 11, protocol: "RESPONSES", usageExtractor: "openai.usage", enabled: true},
          {id: 12, protocol: "MESSAGES", usageExtractor: "openai.usage", enabled: true}
        ]
      })
    });

    expect(protocolsOf(wrapper)).toEqual(["RESPONSES", "MESSAGES"]);
  });

  it("协议映射就绪后：不被当前模型类型允许的协议被收敛，重复协议顺延到下一个可用", async () => {
    h.protocols = ["CHAT_COMPLETIONS", "RESPONSES"];

    const wrapper = await openDrawer({
      model: editModel({
        apiBindings: [
          // EMBEDDINGS 不在允许列表 → 收敛成首个可用
          {id: 11, protocol: "EMBEDDINGS", usageExtractor: "openai.usage", enabled: true},
          // 与上一行撞车 → 顺延到下一个未被占用的
          {id: 12, protocol: "CHAT_COMPLETIONS", usageExtractor: "openai.usage", enabled: true}
        ]
      })
    });

    expect(protocolsOf(wrapper)).toEqual(["CHAT_COMPLETIONS", "RESPONSES"]);
  });

  // ==================== 提交校验（四条拒绝 + 缺值） ====================

  it("必填缺失时拦在提交前，不发请求", async () => {
    const wrapper = await openDrawer({
      model: editModel({upstreamModel: "", billingRuleCode: null})
    });

    await saveButton(wrapper).trigger("click");
    await flushPromises();

    expect(warned()).toContain("请填写上游模型");
    expect(updateModel).not.toHaveBeenCalled();
  });

  it("统一模型已停用（status=0）时拒绝提交——0 是合法值，不能用 truthy 判断", async () => {
    vi.mocked(listLogicalModelOptions).mockResolvedValue({
      list: [{...LOGICAL_OPTION, meta: {...LOGICAL_OPTION.meta, status: 0}}]
    } as never);

    const wrapper = await openDrawer({model: editModel()});
    await saveButton(wrapper).trigger("click");
    await flushPromises();

    expect(warned()).toContain("只能绑定已启用的统一模型");
    expect(updateModel).not.toHaveBeenCalled();
  });

  it("接口协议与模型类型不匹配时拒绝提交", async () => {
    h.protocols = ["CHAT_COMPLETIONS"];
    const wrapper = await openDrawer({
      model: editModel({
        apiBindings: [{id: 11, protocol: "RESPONSES", usageExtractor: "openai.usage", enabled: true}]
      })
    });
    // 打开时已被收敛成合法协议，这里手工改回非法值模拟「映射表就绪前先选了别的」
    const enumControl = wrapper.findComponent(EnumControlStub);
    await enumControl.vm.$emit("update:modelValue", "EMBEDDINGS");
    await flushPromises();

    await saveButton(wrapper).trigger("click");
    await flushPromises();

    expect(warned()).toContain("接口协议与模型类型不匹配：EMBEDDINGS");
    expect(updateModel).not.toHaveBeenCalled();
  });

  it("接口协议重复时拒绝提交", async () => {
    h.protocols = ["CHAT_COMPLETIONS", "RESPONSES"];
    const wrapper = await openDrawer({
      model: editModel({
        apiBindings: [
          {id: 11, protocol: "CHAT_COMPLETIONS", usageExtractor: "openai.usage", enabled: true},
          {id: 12, protocol: "RESPONSES", usageExtractor: "openai.usage", enabled: true}
        ]
      })
    });
    // 把第二条改成与第一条相同
    const second = wrapper.findAllComponents(EnumControlStub)[1];
    await second.vm.$emit("update:modelValue", "CHAT_COMPLETIONS");
    await flushPromises();

    await saveButton(wrapper).trigger("click");
    await flushPromises();

    expect(warned()).toContain("接口协议不能重复");
    expect(updateModel).not.toHaveBeenCalled();
  });

  it("Usage 解析器为空时拒绝提交", async () => {
    const wrapper = await openDrawer({
      model: editModel({
        apiBindings: [{id: 11, protocol: "CHAT_COMPLETIONS", usageExtractor: "", enabled: true}]
      })
    });

    await saveButton(wrapper).trigger("click");
    await flushPromises();

    expect(warned()).toContain("Usage 解析器必填：CHAT_COMPLETIONS");
    expect(updateModel).not.toHaveBeenCalled();
  });

  // ==================== 提交 payload ====================

  it("编辑提交：空值归一成 null、字段 trim、适配器补下拉首项", async () => {
    const wrapper = await openDrawer({
      model: editModel({
        basePath: "  /ep-1  ",
        remark: "   ",
        apiBindings: [
          {
            id: 11,
            protocol: "CHAT_COMPLETIONS",
            apiPath: "  ",
            requestAdapter: "",
            usageExtractor: "openai.usage",
            streamUsageExtractor: "  ",
            enabled: true,
            remark: "  "
          }
        ]
      })
    });

    await saveButton(wrapper).trigger("click");
    await flushPromises();

    expect(updateModel).toHaveBeenCalledTimes(1);
    const [id, payload] = vi.mocked(updateModel).mock.calls[0];
    expect(id).toBe(MODEL_ID);
    expect(payload).toMatchObject({
      modelCode: "gpt-4o",
      upstreamModel: "gpt-4o",
      basePath: "/ep-1",
      remark: null,
      enabled: true,
      rateLimitPolicy: null
    });
    expect(payload.apiBindings).toEqual([
      {
        id: 11,
        protocol: "CHAT_COMPLETIONS",
        asyncProfileCode: null,
        apiPath: null,
        requestAdapter: "PASSTHROUGH",
        usageExtractor: "openai.usage",
        streamUsageExtractor: null,
        enabled: true,
        remark: null
      }
    ]);
  });

  it("适配器候选未加载时不写死默认值——留空交给后端兜底", async () => {
    vi.mocked(listRequestAdapters).mockResolvedValue([] as never);

    const wrapper = await openDrawer({
      model: editModel({
        apiBindings: [{id: 11, protocol: "CHAT_COMPLETIONS", requestAdapter: "", usageExtractor: "openai.usage", enabled: true}]
      })
    });

    await saveButton(wrapper).trigger("click");
    await flushPromises();

    const [, payload] = vi.mocked(updateModel).mock.calls[0];
    expect(payload.apiBindings[0].requestAdapter).toBeNull();
  });

  it("新建提交走 createModel，并带上新建态默认值", async () => {
    const wrapper = await openDrawer();
    await wrapper.find('input[placeholder="请输入供应商模型编码"]').setValue("gpt-4o-mini");
    await wrapper.find('input[placeholder^="向上游请求时使用的 model"]').setValue("gpt-4o-mini");
    // 编码失焦会用编码兜底上游模型，这里显式触发一次唯一性校验
    await wrapper.find('input[placeholder="请输入供应商模型编码"]').trigger("blur");
    await flushPromises();

    // 统一模型 / 账户 → 触发联动清空计费规则，故计费规则最后再推
    const remoteSelects = wrapper.findAllComponents(RemoteSelectStub);
    await remoteSelects[0].vm.$emit("update:modelValue", LOGICAL_CODE);
    await wrapper.findComponent(AccountTreeStub).vm.$emit("update:modelValue", "acct-1");
    await flushPromises();
    await remoteSelects[remoteSelects.length - 1].vm.$emit("update:modelValue", BILLING_CODE);
    await flushPromises();

    await saveButton(wrapper).trigger("click");
    await flushPromises();

    expect(checkModelCode).toHaveBeenCalledWith("gpt-4o-mini", null);
    expect(createModel).toHaveBeenCalledTimes(1);
    const payload = vi.mocked(createModel).mock.calls[0][0];
    expect(payload).toMatchObject({modelCode: "gpt-4o-mini", upstreamModel: "gpt-4o-mini", enabled: false});
    expect(updateModel).not.toHaveBeenCalled();
  });

  it("保存成功后关闭抽屉并抛出 saved", async () => {
    const wrapper = await openDrawer({model: editModel()});
    await saveButton(wrapper).trigger("click");
    await flushPromises();

    expect(wrapper.emitted("update:modelValue")?.at(-1)).toEqual([false]);
    expect(wrapper.emitted("saved")).toBeTruthy();
  });

  it("后端因编码冲突报错时回填到编码字段，而不是只弹一句提示", async () => {
    vi.mocked(updateModel).mockRejectedValue(new Error("模型编码“gpt-4o”已存在，请更换后重试"));

    const wrapper = await openDrawer({model: editModel()});
    await saveButton(wrapper).trigger("click");
    await flushPromises();
    // el-form-item 的 error 文案走 refDebounced(validateState, 100)，需等防抖窗口过去才落 DOM
    await new Promise((resolve) => setTimeout(resolve, 150));
    await flushPromises();

    expect(wrapper.text()).toContain("模型编码“gpt-4o”已存在，请更换后重试");
    expect(wrapper.emitted("saved")).toBeFalsy();
  });

  // ==================== 联动 ====================

  it("切换供应商账户或统一模型时清空已绑计费规则（模型族会变）", async () => {
    const wrapper = await openDrawer({model: editModel()});
    expect(wrapper.text()).toContain(BILLING_CODE);

    const selects = wrapper.findAllComponents(RemoteSelectStub);
    // [0] = 统一模型，[1] = 计费规则
    await selects[0].vm.$emit("update:modelValue", "lm-claude");
    await flushPromises();

    expect(wrapper.text()).toContain("尚未绑定计费规则，请从上方下拉选择");
  });

  it("计费规则候选按统一模型派生的模型族过滤", async () => {
    const wrapper = await openDrawer({model: editModel()});
    const billingSelect = wrapper.findAllComponents(RemoteSelectStub)[1];

    expect(billingSelect.props("extraParams")).toEqual({modelFamilyCode: "openai"});
    expect(billingSelect.props("disabled")).toBe(false);
  });

  // ==================== 任务协议（异步档案绑定） ====================

  it("任务协议原样回填并随绑定一起提交（空值归一成 null）", async () => {
    const wrapper = await openDrawer({
      model: editModel({
        apiBindings: [
          {
            id: 11,
            protocol: "CHAT_COMPLETIONS",
            asyncProfileCode: "KLING_TASK",
            requestAdapter: "OPENAI_CHAT",
            usageExtractor: "openai.usage",
            enabled: true
          }
        ]
      })
    });

    // 只读口径提示：不点开档案页也能确认「怎么轮询」
    expect(wrapper.text()).toContain("/v1/videos/{task_id}");
    expect(wrapper.text()).toContain("轮询间隔 5 秒");

    await saveButton(wrapper).trigger("click");
    await flushPromises();

    const [, payload] = vi.mocked(updateModel).mock.calls[0];
    expect(payload.apiBindings[0].asyncProfileCode).toBe("KLING_TASK");
  });

  it("切换接口协议时清空不匹配的任务协议并提示，不静默丢配置", async () => {
    const wrapper = await openDrawer({
      model: editModel({
        apiBindings: [
          {
            id: 11,
            protocol: "CHAT_COMPLETIONS",
            asyncProfileCode: "KLING_TASK",
            requestAdapter: "OPENAI_CHAT",
            usageExtractor: "openai.usage",
            enabled: true
          }
        ]
      })
    });

    // KLING_TASK 属于 CHAT_COMPLETIONS；切到 RESPONSES 后它不再适用
    await wrapper.findComponent(EnumControlStub).vm.$emit("update:modelValue", "RESPONSES");
    await flushPromises();

    expect(warned().some((text) => text.includes("KLING_TASK"))).toBe(true);
    expect(wrapper.text()).not.toContain("/v1/videos/{task_id}");

    await saveButton(wrapper).trigger("click");
    await flushPromises();

    const [, payload] = vi.mocked(updateModel).mock.calls[0];
    expect(payload.apiBindings[0].asyncProfileCode).toBeNull();
  });

  it("已绑定档案被停用/删除时拦在提交前，并指出是哪一条", async () => {
    vi.mocked(listAsyncTaskProfileOptions).mockResolvedValue([] as never);

    const wrapper = await openDrawer({
      model: editModel({
        apiBindings: [
          {
            id: 11,
            protocol: "CHAT_COMPLETIONS",
            asyncProfileCode: "GONE_TASK",
            requestAdapter: "OPENAI_CHAT",
            usageExtractor: "openai.usage",
            enabled: true
          }
        ]
      })
    });

    // 候选为空时不做归一剔除（无从判断），只提示；清空与否由人决定
    expect(wrapper.text()).toContain("已停用或不存在");

    await saveButton(wrapper).trigger("click");
    await flushPromises();

    expect(warned()).toContain("任务协议已停用或不存在：GONE_TASK（CHAT_COMPLETIONS）");
    expect(updateModel).not.toHaveBeenCalled();
  });
});

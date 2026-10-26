<template>
  <el-drawer
    v-model="visible"
    :title="isEdit ? '编辑计费规则' : isCopy ? '复制计费规则' : '新建计费规则'"
    direction="rtl"
    size="960px"
    class="drawer-with-scrollbar"
    destroy-on-close
  >
    <el-scrollbar v-loading="detailLoading" max-height="calc(100vh - 140px)">
      <el-tabs v-model="activeTab" type="border-card">
        <el-tab-pane label="表单编辑" name="form">
          <el-form :model="form" label-width="120px" class="billing-rule-form">
            <div class="slot-body form-section">
              <div class="section-head">
                <h3>基础信息</h3>
                <el-form-item label="启用" class="inline-switch">
                  <el-switch v-model="form.enabled"/>
                </el-form-item>
              </div>
              <el-row :gutter="16">
                <el-col :span="12">
                  <el-form-item label="规则编码" required>
                    <el-input
                      v-model="form.code"
                      placeholder="如 TOKEN_GPT4O"
                      maxlength="64"
                      :readonly="codeBlockedByBinding"
                    />
                    <div v-if="codeBlockedByBinding" class="form-tip">
                      已被 {{ boundModelCount }} 个供应商模型使用，需先解绑才能修改
                    </div>
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="模型族">
                    <EnumSelect
                      v-model="form.modelFamilyCode"
                      category="model_family"
                      :code-only="codeOnly"
                      clearable
                      filterable
                      placeholder="不选则作为默认规则"
                      style="width: 100%"
                    />
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="币种">
                    <EnumControl v-model="form.currency" enum-name="CurrencyCodeEnum"/>
                  </el-form-item>
                </el-col>
              </el-row>
            </div>
            <div class="space-line"/>

            <div class="slot-body form-section">
              <div class="section-head">
                <h3>计费策略</h3>
              </div>
              <el-row :gutter="16">
                <el-col :span="12">
                  <el-form-item label="计费模式" class="billing-strategy-label">
                    <EnumControl
                      v-model="form.billingMode"
                      enum-name="BillingModeEnum"
                    />
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="进阶方案" class="billing-strategy-label">
                    <EnumControl
                      v-model="form.pricingPlan"
                      enum-name="PricingPlanEnum"
                      :include-codes="planCodes"
                      show-code
                    />
                  </el-form-item>
                </el-col>
                <el-col v-if="form.billingMode !== BillingMode.FREE" :span="12">
                  <el-form-item label="计费单位" class="billing-strategy-label">
                    <EnumControl
                      v-model="form.unit"
                      enum-name="BillingUnitEnum"
                      :include-codes="unitCodes"
                      show-code
                    />
                  </el-form-item>
                </el-col>
                <el-col v-if="form.billingMode === BillingMode.TOKEN" :span="24">
                  <el-form-item label="缓存写方式" class="cache-write-label">
                    <el-radio-group v-model="form.cacheWriteMode">
                      <el-radio-button value="standard">缓存写</el-radio-button>
                      <el-radio-button value="ttl">缓存写(5m)/(1h)</el-radio-button>
                    </el-radio-group>
                  </el-form-item>
                </el-col>
              </el-row>
              <!-- 第一层：模式组件（用量与基础价格）；第二层：方案组件（进阶定价明细） -->
              <div class="billing-base-card">
                <div class="billing-base-card__title">基础计费规则</div>
                <component :is="modeComponent" :form="form"/>
              </div>
              <component :is="planComponent" :form="form"/>
            </div>

            <div class="space-line"/>

            <div class="slot-body form-section">
              <div class="section-head">
                <h3>备注</h3>
              </div>
              <el-form-item label-width="0">
                <el-input v-model="form.remark" type="textarea" :rows="4" maxlength="500" show-word-limit/>
              </el-form-item>
            </div>
          </el-form>
        </el-tab-pane>
        <el-tab-pane label="config_json" name="json">
          <JsonCodeEditor
            v-model="configJsonText"
            min-height="320px"
            height="calc(100vh - 320px)"
            max-height="620px"
          />
        </el-tab-pane>
      </el-tabs>
    </el-scrollbar>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import {type PropType, computed, nextTick, reactive, ref, watch} from "vue";
import type {Component} from "vue";
import {ElMessage} from "element-plus";
import type {Dict, Row} from "@/types";
import {BillingMode, BillingUnit, PricingPlan} from "@/constants/status";
import EnumSelect from "../common/EnumSelect.vue";
import EnumControl from "../common/EnumControl.vue";
import JsonCodeEditor from "../common/JsonCodeEditor.vue";
import BillingModeAudioConfig from "./modes/BillingModeAudioConfig.vue";
import BillingModeCustomConfig from "./modes/BillingModeCustomConfig.vue";
import BillingModeEmbeddingConfig from "./modes/BillingModeEmbeddingConfig.vue";
import BillingModeFreeConfig from "./modes/BillingModeFreeConfig.vue";
import BillingModeImageConfig from "./modes/BillingModeImageConfig.vue";
import BillingModeRequestConfig from "./modes/BillingModeRequestConfig.vue";
import BillingModeTokenConfig from "./modes/BillingModeTokenConfig.vue";
import BillingModeVideoConfig from "./modes/BillingModeVideoConfig.vue";
import PricingPlanFixedConfig from "./plans/PricingPlanFixedConfig.vue";
import PricingPlanTieredConfig from "./plans/PricingPlanTieredConfig.vue";
import PricingPlanPeakOffPeakConfig from "./plans/PricingPlanPeakOffPeakConfig.vue";
import PricingPlanVendorMiniMaxConfig from "./plans/PricingPlanVendorMiniMaxConfig.vue";
import {createBillingRule, updateBillingRule, listModeCapabilities, getBillingRule} from "@/api/billing";
import {formatJsonText, isValidJsonText, parseJsonObject, roundPrice, stringifyJson} from "@/utils/format";
import {
  basePricesOf,
  createTierPriceSet,
  createTierRow,
  parseTierRows,
  toNumberOrNull,
  toTierPayload,
  validateTierRows
} from "@/utils/billingTier";
import type {TierRow} from "@/utils/billingTier";
import {
  createDefaultPeakPricing,
  parsePeakPricing,
  toPeakPayload,
  validatePeakPricing
} from "@/utils/billingPeakOffPeak";
import type {PeakPricing} from "@/utils/billingPeakOffPeak";

const props = defineProps({
  modelValue: {type: Boolean, default: false},
  /** 编辑目标的规则 id；null 表示新建。全量记录由抽屉打开时向后端获取，不走列表行数据 */
  ruleId: {type: Number as PropType<number | null>, default: null},
  /** 克隆源规则 id：非空时按 id 取全量记录预填表单（编码留空），保存为新建规则 */
  copyFromId: {type: Number as PropType<number | null>, default: null},
  /** 模型族下拉仅展示 itemCode */
  codeOnly: {type: Boolean, default: false}
});

const emit = defineEmits(["update:modelValue", "saved"]);

// 两层组件：模式组件决定“量”（基础价格），方案组件决定“价”（进阶定价明细）。
// 模式能力与方案支持矩阵由后端 GET /api/billing/mode-capabilities 下发，前端不维护映射表。
const componentsByMode: Record<string, Component> = {
  TOKEN: BillingModeTokenConfig,
  REQUEST: BillingModeRequestConfig,
  IMAGE: BillingModeImageConfig,
  AUDIO: BillingModeAudioConfig,
  VIDEO: BillingModeVideoConfig,
  EMBEDDING: BillingModeEmbeddingConfig,
  FREE: BillingModeFreeConfig,
  CUSTOM: BillingModeCustomConfig
};

const componentsByPlan: Record<string, Component> = {
  FIXED: PricingPlanFixedConfig,
  TIERED: PricingPlanTieredConfig,
  PEAK_OFF_PEAK: PricingPlanPeakOffPeakConfig,
  VENDOR_MINIMAX: PricingPlanVendorMiniMaxConfig
};

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit("update:modelValue", value)
});

const saving = ref(false);
const billingModes = ref<Row[]>([]);
/** 能力矩阵是否已加载：加载前不校验 unit/plan（否则编辑回显会被误重置为 FIXED） */
const capabilitiesLoaded = ref(false);
const detailLoading = ref(false);
/** 编辑时从后端详情接口取到的全量记录 */
const detail = ref<Dict | null>(null);

const activeModeCapability = computed(() =>
  billingModes.value.find((item) => item.code === form.billingMode) || null
);
const unitCodes = computed(() => activeModeCapability.value?.supportedUnits || []);
/** 当前模式支持的进阶方案；能力矩阵由后端下发，PEAK_OFF_PEAK 首期只对 TOKEN 开放 */
const planCodes = computed(() => activeModeCapability.value?.supportedPricingPlans || ["FIXED"]);

interface BillingRuleFormState {
  id: number | string | null;
  /** 规则业务码，新建时手工填写（去空白归一由后端处理，不强制大写），编辑时可改（被绑定时后端拒绝） */
  code: string;
  /** 模型族 code（ks_enum_configs.category=model_family 的 item_code）；空串表示默认规则，覆盖所有族 */
  modelFamilyCode: string;
  billingMode: string;
  /** 进阶定价方案：FIXED / TIERED / PEAK_OFF_PEAK */
  pricingPlan: string;
  currency: string;
  unit: string;
  /** 简单模式（REQUEST/IMAGE/AUDIO/VIDEO/EMBEDDING）单价，写入 configJson.defaultUnitPrice */
  unitPrice: number;
  /** TOKEN 模式分项单价，写入 configJson.basePrices */
  inputUnitPrice: number;
  outputUnitPrice: number;
  cacheReadUnitPrice: number;
  cacheWriteUnitPrice: number;
  cacheWrite5mUnitPrice: number;
  cacheWrite1hUnitPrice: number;
  cacheWriteMode: "standard" | "ttl";
  /**
   * 分辨率档位行：档位名可自由填写（厂商档位不可枚举，MiniMax 用 768P、Vidu 用 540P）。
   * 序列化时 price -> {@code resolutionPrices[档位名]}，
   * referencePrice -> {@code resolutionPrices[档位名:REFERENCE]}。
   */
  videoResolutionRows: Dict[];
  /** 视频合成量口径：OUTPUT / SUM / OUTPUT_PLUS_MAX */
  videoQuantityMode: string;
  videoInputVideoPerSecond: number | null;
  videoInputImagePerImage: number | null;
  videoFreeInputImages: number | null;
  videoAudioSurcharge: number | null;
  /**
   * 附加费里表单未托管的条目（如 REFERENCE / ALWAYS 触发），原样保留并回写。
   *
   * <p>表单只编辑「音频直出」一条，而后端 {@code surcharges} 是数组且参与计价——
   * 重建数组时丢掉未托管条目，等于管理员打开规则点一次保存就静默删掉别人的配置。</p>
   */
  videoExtraSurcharges: Dict[];
  imageResolution: string;
  imageQuality: string;
  /** 阶梯档位（pricingPlan=TIERED），保存时由 {@link toTierPayload} 并入 configJson.tier */
  tiers: TierRow[];
  /**
   * 阶梯累计方式（pricingPlan=TIERED）：PER_REQUEST 整笔落一档 / PROGRESSIVE 逐单位累加。
   * 后者是 Vidu Q2「首秒阶梯」那种分段函数，与「总量落一档」是两套算法。
   */
  tierMode: string;
  /** 高低峰配置（pricingPlan=PEAK_OFF_PEAK），保存时由 {@link toPeakPayload} 并入 configJson.pricing */
  peakPricing: PeakPricing | null;
  customConfigJson: string;
  enabled: boolean;
  remark: string;
}

const form = reactive<BillingRuleFormState>({
  id: null,
  code: "",
  modelFamilyCode: "",
  billingMode: "TOKEN",
  pricingPlan: "FIXED",
  currency: "USD",
  unit: BillingUnit.ONE_M_TOKENS,
  unitPrice: 0.002,
  inputUnitPrice: 0.002,
  outputUnitPrice: 0.002,
  cacheReadUnitPrice: 0,
  cacheWriteUnitPrice: 0,
  cacheWrite5mUnitPrice: 0,
  cacheWrite1hUnitPrice: 0,
  cacheWriteMode: "standard",
  videoResolutionRows: [],
  videoQuantityMode: "OUTPUT",
  videoInputVideoPerSecond: null,
  videoInputImagePerImage: null,
  videoFreeInputImages: null,
  videoAudioSurcharge: null,
  videoExtraSurcharges: [],
  imageResolution: "",
  imageQuality: "",
  tiers: [],
  tierMode: "PER_REQUEST",
  peakPricing: null,
  customConfigJson: "",
  enabled: true,
  remark: ""
});

const isEdit = computed(() => props.ruleId != null);
/** 克隆模式：按 copyFromId 预填源规则，保存为新建规则（ruleId 为 null，故 isEdit 为 false） */
const isCopy = computed(() => props.copyFromId != null);

/** 抽屉内 Tab：form=表单编辑，json=config_json 原文查看/编辑 */
const activeTab = ref<"form" | "json">("form");
const configJsonText = ref("");
/** 重新打开抽屉时置位：跳过上一次停留 json 页遗留文本的回写，避免历史内容污染新表单 */
let suppressJsonApplyBack = false;
/** 回写失败被弹回 json 页时置位：跳过「用表单重建文本」，保住用户正在修的非法内容 */
let suppressJsonRebuild = false;
/** 只读派生：绑定了该编码的供应商模型数（详情接口带出） */
const boundModelCount = computed(() => Number(detail.value?.boundModelCount ?? 0));
/** 编辑且编码已被使用时不允许改码：绑定存的是 code，改了存量模型会掉绑 */
const codeBlockedByBinding = computed(() => isEdit.value && boundModelCount.value > 0);
/** 是否真的改了编码（后端仅去空白不做大小写归一，这里同口径比较） */
const codeChanged = computed(() =>
  String(detail.value?.code || "").trim() !== resolveRuleCode()
);
const modeComponent = computed(() => componentsByMode[form.billingMode] || BillingModeTokenConfig);
const planComponent = computed(() => componentsByPlan[form.pricingPlan] || PricingPlanFixedConfig);

/** 编辑时按 id 从后端取全量记录（列表是轻量 VO，configJson 不在列表数据里） */
async function loadDetail() {
  if (props.ruleId == null) {
    return;
  }
  detailLoading.value = true;
  try {
    const row = await getBillingRule(props.ruleId);
    if (!row) {
      ElMessage.error("未找到该计费规则");
      visible.value = false;
      return;
    }
    detail.value = row as Dict;
    fillFormFromDetail(row as Dict);
  } catch {
    // 详情拿不到就关掉抽屉：留着空表单可能被误保存
    ElMessage.error("加载计费规则详情失败，请重试");
    visible.value = false;
  } finally {
    detailLoading.value = false;
  }
}

function fillFormFromDetail(row: Dict) {
  form.id = row.id;
  form.code = row.code || "";
  form.modelFamilyCode = row.modelFamilyCode ?? "";
  form.billingMode = normalizeMode(row.billingMode || "TOKEN");
  form.pricingPlan = String(row.pricingPlan || "FIXED").trim().toUpperCase();
  form.currency = row.currency || "USD";
  form.unit = row.unit || modeDefaults(form.billingMode).unit;
  form.customConfigJson = form.billingMode === BillingMode.CUSTOM ? row.configJson || "" : "";
  form.enabled = row.enabled !== false;
  form.remark = row.remark || "";
  applyConfig(parseJsonObject(row.configJson));
}

watch(
  () => form.billingMode,
  (mode) => applyModeDefaults(mode)
);

// 能力矩阵加载前的回显阶段不校验 unit/plan，避免编辑打开时保存过的方案被误重置为 FIXED
watch(
  () => form.cacheWriteMode,
  (mode) => {
    if (mode === "standard") {
      form.cacheWrite5mUnitPrice = 0;
      form.cacheWrite1hUnitPrice = 0;
    } else {
      form.cacheWriteUnitPrice = 0;
    }
  }
);

// 计费项精度：输入框不做补零显示，确认后收敛到全站单价精度（`roundPrice` → `UNIT_PRICE_PRECISION`
// = 8 位，见 utils/billingPrice.ts：与后端 setScale(8, HALF_UP) 对齐，额度计数按 1e-8 缩放）
const PRICE_FIELDS = [
  "unitPrice",
  "inputUnitPrice",
  "outputUnitPrice",
  "cacheReadUnitPrice",
  "cacheWriteUnitPrice",
  "cacheWrite5mUnitPrice",
  "cacheWrite1hUnitPrice",
  "videoInputVideoPerSecond",
  "videoInputImagePerImage",
  "videoAudioSurcharge"
] as const;
for (const field of PRICE_FIELDS) {
  watch(
    () => form[field],
    (value) => {
      const rounded = roundPrice(value);
      if (rounded !== undefined && rounded !== value) {
        form[field] = rounded;
      }
    }
  );
}

// 切换模式时若当前方案不被支持则回退固定价；方案切换保留基础价格，仅清理方案专属配置由后端校验兜底
watch(
  () => form.pricingPlan,
  (plan) => {
    if (!capabilitiesLoaded.value) {
      return;
    }
    if (plan && !planCodes.value.includes(plan)) {
      form.pricingPlan = "FIXED";
      return;
    }
    // 首次切到阶梯价时给一个起始档位，避免空白表格无从下手
    if (plan === PricingPlan.TIERED && !form.tiers.length) {
      form.tiers.push(
        createTierRow({
          from: 0,
          unitPrices: basePricesOf(form, form.billingMode) || createTierPriceSet(null)
        })
      );
    }
    // 首次切到高低峰时补默认骨架（工作日高峰 + 兜底低峰）
    if (plan === PricingPlan.PEAK_OFF_PEAK && !form.peakPricing) {
      form.peakPricing = createDefaultPeakPricing();
    }
    // 首次切到 MiniMax 时补官方默认：图片每张 0.20（H3/H3-Highspeed 价）、前 5 张免费
    if (plan === PricingPlan.VENDOR_MINIMAX) {
      if (form.videoInputImagePerImage == null) {
        form.videoInputImagePerImage = 0.2;
      }
      if (form.videoFreeInputImages == null) {
        form.videoFreeInputImages = 5;
      }
    }
  }
);

watch(
  () => props.modelValue,
  (value) => {
    if (!value) {
      return;
    }
    // 上一次停留在 json 页时关闭了抽屉：重开时不走遗留文本回写
    if (activeTab.value === "json") {
      suppressJsonApplyBack = true;
    }
    capabilitiesLoaded.value = false;
    activeTab.value = "form";
    detail.value = null;
    resetForm();
    if (props.copyFromId != null) {
      loadCopySource();
    } else {
      loadDetailAndCapabilities();
    }
  }
);

/** 拉详情与能力矩阵（并行），就绪后统一做一次模式默认值校验 */
async function loadDetailAndCapabilities() {
  await Promise.all([loadModeCapabilities(), loadDetail()]);
  capabilitiesLoaded.value = true;
  applyModeDefaults(form.billingMode);
}

/**
 * 克隆源规则：按 id 取全量记录并预填表单，再清空 id 与编码，使其成为一条待保存的新建规则。
 * detail 保持 null，避免触发「编码被绑定」只读与改码校验（克隆走新建分支）。
 */
async function loadCopySource() {
  detailLoading.value = true;
  try {
    const row = await getBillingRule(props.copyFromId!);
    if (!row) {
      ElMessage.error("未找到源计费规则");
      visible.value = false;
      return;
    }
    fillFormFromDetail(row as Dict);
    form.id = null;
    form.code = "";
  } catch {
    ElMessage.error("加载源计费规则失败，请重试");
    visible.value = false;
  } finally {
    detailLoading.value = false;
    // 能力矩阵仍需加载：约束下拉选项并兜底方案回退
    await loadModeCapabilities();
    capabilitiesLoaded.value = true;
    applyModeDefaults(form.billingMode);
  }
}

/** 切到 json 页展示配置文本（与 JsonCodeEditor「格式化」按钮同口径）；切回表单页时回写编辑内容 */
watch(activeTab, (tab, prev) => {
  if (tab === "json") {
    if (suppressJsonRebuild) {
      // 一次性：从「回写失败被弹回」这条路返回时，不能重建文本，
      // 否则会把用户正在修的非法 JSON 冲掉（弹回即等于「帮你清空」）
      suppressJsonRebuild = false;
      return;
    }
    const built = buildConfigJson();
    configJsonText.value = built ? formatJsonText(built) : "";
    return;
  }
  if (suppressJsonApplyBack) {
    suppressJsonApplyBack = false;
    return;
  }
  if (prev === "json" && !applyJsonTextToForm()) {
    // 回写失败（非法 JSON 等）：留在 json 页让用户继续修。
    // ⚠ 必须跨一次 flush 再重设：与用户点击的 prop 变化同批设置会被父组件的一次性渲染「抵消」
    // （modelValue 先变 form 又变回 json、净变化为空），el-tabs 内部 currentName 不跟随
    // （element-plus/es/components/tabs/src/tabs.mjs:115 的 watch 依赖从未触发），
    // 结果是状态停在 json 页、界面停在表单页，保存时静默拒绝并弹一句与当前界面无关的警告。
    suppressJsonRebuild = true;
    nextTick(() => {
      activeTab.value = "json";
    });
  }
});

/** config_json 页文本回写表单；失败返回 false。回写前先格式化，保证加载口径一致 */
function applyJsonTextToForm(): boolean {
  const text = configJsonText.value.trim();
  if (!text) {
    if (form.billingMode === BillingMode.CUSTOM) {
      form.customConfigJson = "";
      return true;
    }
    ElMessage.warning("config_json 不能为空");
    return false;
  }
  if (!isValidJsonText(text)) {
    ElMessage.warning("config_json 不是合法 JSON");
    return false;
  }
  const pretty = formatJsonText(text);
  configJsonText.value = pretty;
  if (form.billingMode === BillingMode.CUSTOM) {
    form.customConfigJson = pretty;
    return true;
  }
  applyConfig(parseJsonObject(pretty));
  return true;
}

function resetForm() {
  form.id = null;
  form.code = "";
  form.modelFamilyCode = "";
  form.billingMode = "TOKEN";
  form.pricingPlan = "FIXED";
  form.currency = "USD";
  form.unit = BillingUnit.ONE_M_TOKENS;
  form.unitPrice = 0.002;
  form.inputUnitPrice = 0.002;
  form.outputUnitPrice = 0.002;
  form.cacheReadUnitPrice = 0;
  form.cacheWriteUnitPrice = 0;
  form.cacheWrite5mUnitPrice = 0;
  form.cacheWrite1hUnitPrice = 0;
  form.cacheWriteMode = "standard";
  form.videoResolutionRows = [];
  form.videoQuantityMode = "OUTPUT";
  form.videoInputVideoPerSecond = null;
  form.videoInputImagePerImage = null;
  form.videoFreeInputImages = null;
  form.videoAudioSurcharge = null;
  form.videoExtraSurcharges = [];
  form.imageResolution = "";
  form.imageQuality = "";
  form.tiers = [];
  form.tierMode = "PER_REQUEST";
  form.peakPricing = null;
  form.customConfigJson = "";
  form.enabled = true;
  form.remark = "";
}

/**
 * resolutionPrices -> 行列表：{@code 档位名} 与 {@code 档位名:REFERENCE} 归到同一行。
 *
 * <p>不做档位名白名单过滤——MiniMax 的 768P、Vidu 的 540P 都不在常用档位里，
 * 过滤掉就等于丢配置。同一档位出现多种大小写写法时合并到最后一次出现的值。</p>
 */
function parseResolutionRows(raw: unknown): Dict[] {
  if (!raw || typeof raw !== "object") {
    return [];
  }
  const source = raw as Dict;
  const REFERENCE_SUFFIX = ":REFERENCE";
  const INPUT_VIDEO_PREFIX = "INPUT_VIDEO:";
  const rows = new Map<string, Dict>();
  for (const key of Object.keys(source)) {
    const name = key.trim().toUpperCase();
    const price = toNumberOrNull(source[key]);
    if (price == null) {
      continue;
    }
    // MiniMax 输入视频档位价：INPUT_VIDEO:档位 -> 同一行的 inputPrice（与输出档位共用一行）
    if (name.startsWith(INPUT_VIDEO_PREFIX)) {
      const base = name.slice(INPUT_VIDEO_PREFIX.length);
      if (!base) {
        continue;
      }
      const row = rows.get(base) || {key: base, price: null, referencePrice: null, inputPrice: null};
      row.inputPrice = price;
      rows.set(base, row);
      continue;
    }
    const isReference = name.endsWith(REFERENCE_SUFFIX);
    const base = isReference ? name.slice(0, -REFERENCE_SUFFIX.length) : name;
    if (!base) {
      continue;
    }
    const row = rows.get(base) || {key: base, price: null, referencePrice: null, inputPrice: null};
    if (isReference) {
      row.referencePrice = price;
    } else {
      row.price = price;
    }
    rows.set(base, row);
  }
  return [...rows.values()];
}

/** config 结构映射回表单字段（编辑回显与 config_json 页回写共用同一份映射） */
function applyConfig(config: Dict) {
  const basePrices = (config.basePrices && typeof config.basePrices === "object") ? config.basePrices : {};
  form.unitPrice = Number(config.defaultUnitPrice ?? 0.002);
  form.inputUnitPrice = Number(basePrices.input ?? config.defaultUnitPrice ?? 0.002);
  form.outputUnitPrice = Number(basePrices.output ?? config.defaultUnitPrice ?? 0.002);
  form.cacheReadUnitPrice = Number(basePrices.cacheRead ?? 0);
  form.cacheWriteUnitPrice = Number(basePrices.cacheWrite ?? 0);
  form.cacheWrite5mUnitPrice = Number(basePrices.cacheWrite5m ?? 0);
  form.cacheWrite1hUnitPrice = Number(basePrices.cacheWrite1h ?? 0);
  // cacheWriteMode 直接取落库值；未写入的旧配置按 standard 显式化（后端 null 语义即 standard），
  // 不做分价字段推断——本次保存后即为显式值
  form.cacheWriteMode = config.cacheWriteMode === "ttl" ? "ttl" : "standard";
  // 分辨率档位：键空间完全由配置决定（厂商档位不可枚举，不做白名单过滤）。
  // {档位名} 与 {档位名}:REFERENCE 共用一张表，回显时按后缀拆到同一行的不同列。
  form.videoResolutionRows = parseResolutionRows(config.resolutionPrices);
  const video = (config.video && typeof config.video === "object") ? config.video : {};
  form.videoQuantityMode = String(video.quantityMode || "OUTPUT").trim().toUpperCase() || "OUTPUT";
  form.videoInputVideoPerSecond = toNumberOrNull(video.inputVideoPerSecond);
  form.videoInputImagePerImage = toNumberOrNull(video.inputImagePerImage);
  form.videoFreeInputImages = toNumberOrNull(video.freeInputImages);
  const surcharges = Array.isArray(config.surcharges) ? config.surcharges : [];
  const audioSurcharge = surcharges.find((item: Dict) => String(item?.trigger || "").toUpperCase() === "AUDIO");
  form.videoAudioSurcharge = toNumberOrNull(audioSurcharge?.amount);
  // 其余触发条件（REFERENCE / ALWAYS）表单不编辑，原样带回去，避免保存时被重建数组吞掉
  form.videoExtraSurcharges = surcharges.filter((item: Dict) => item !== audioSurcharge);
  form.imageResolution = config.imageResolution || "";
  form.imageQuality = config.imageQuality || "";
  form.tiers = parseTierRows(config.tier);
  form.tierMode = String(config.tierMode || "PER_REQUEST").trim().toUpperCase() === "PROGRESSIVE"
    ? "PROGRESSIVE"
    : "PER_REQUEST";
  // 高低峰回显：存过的用原配置，没有（或结构缺失）则给一份默认骨架
  form.peakPricing = parsePeakPricing(config.pricing) || createDefaultPeakPricing();
}

/** 当前模式的默认单位；能力未加载完成时退回新建表单的初值 */
function modeDefaults(mode: string): { unit: string } {
  const matched = billingModes.value.find((item) => item.code === mode);
  return {
    unit: matched?.defaultUnit || BillingUnit.ONE_M_TOKENS
  };
}

function applyModeDefaults(mode: string) {
  if (!capabilitiesLoaded.value) {
    return;
  }
  const defaults = modeDefaults(mode);
  if (!unitCodes.value.includes(form.unit)) {
    form.unit = defaults.unit;
  }
  // 模式切换后方案能力矩阵变化：不支持的方案回退固定价
  if (!planCodes.value.includes(form.pricingPlan)) {
    form.pricingPlan = "FIXED";
  }
}

async function loadModeCapabilities() {
  const data = await listModeCapabilities();
  billingModes.value = Array.isArray(data?.billingModes) ? data.billingModes : [];
}

function validateJson(value: unknown, label: string): boolean {
  if (!isValidJsonText(value)) {
    ElMessage.warning(`${label}不是合法 JSON`);
    return false;
  }
  return true;
}

function normalizeMode(mode: unknown): string {
  // 取值由后端枚举 BillingModeEnum 下拉约束，这里只做格式归一与空值兜底
  return String(mode || "").trim().toUpperCase() || "TOKEN";
}

/** 基础价格部分（模式组件产出）；进阶方案明细在此基础上合并 */
function buildBaseConfig(): Dict | null {
  if (form.billingMode === BillingMode.TOKEN) {
    return {
      cacheWriteMode: form.cacheWriteMode,
      basePrices: {
        input: form.inputUnitPrice,
        output: form.outputUnitPrice,
        cacheRead: form.cacheReadUnitPrice,
        cacheWrite: form.cacheWriteMode === "standard" ? form.cacheWriteUnitPrice : 0,
        cacheWrite5m: form.cacheWriteMode === "ttl" ? form.cacheWrite5mUnitPrice : 0,
        cacheWrite1h: form.cacheWriteMode === "ttl" ? form.cacheWrite1hUnitPrice : 0
      }
    };
  }
  if (form.billingMode === BillingMode.IMAGE) {
    return {
      defaultUnitPrice: form.unitPrice,
      imageResolution: form.imageResolution?.trim() || null,
      imageQuality: form.imageQuality?.trim() || null
    };
  }
  if (form.billingMode === BillingMode.VIDEO) {
    const isMiniMax = form.pricingPlan === PricingPlan.VENDOR_MINIMAX;
    const resolutionPrices: Dict = {};
    for (const row of form.videoResolutionRows || []) {
      const key = String(row?.key || "").trim().toUpperCase();
      if (!key) {
        continue;
      }
      const price = toNumberOrNull(row.price);
      if (price != null) {
        resolutionPrices[key] = price;
      }
      if (isMiniMax) {
        // MiniMax 输入视频档位价：只要用户显式填了就落库（不再按「等于输出价」隐式去重——
        // MiniMax 官方价表输出价与输入价本就相等，去重会让用户刚填的值在 config_json 里凭空消失，
        // 重开抽屉也回显不出来）。留空则不下发 INPUT_VIDEO 键，由后端回落到同档位输出价。
        const inputPrice = toNumberOrNull(row.inputPrice);
        if (inputPrice != null) {
          resolutionPrices[`INPUT_VIDEO:${key}`] = inputPrice;
        }
      } else {
        const referencePrice = toNumberOrNull(row.referencePrice);
        if (referencePrice != null) {
          resolutionPrices[`${key}:REFERENCE`] = referencePrice;
        }
      }
    }
    // video 段只在有实质配置时才落库：默认的「仅输出秒数」不需要写进 JSON
    // （写了会污染老配置的 config_json，也让「默认行为」在库里多一份可漂移的副本）
    const video: Dict = {};
    // MiniMax 输入秒是独立分量，绝不折叠进输出量：不落 quantityMode
    if (!isMiniMax && form.videoQuantityMode && form.videoQuantityMode !== "OUTPUT") {
      video.quantityMode = form.videoQuantityMode;
    }
    // 非 MiniMax 的输入视频价走标量字段；MiniMax 用分辨率表里的 INPUT_VIDEO:档位
    if (!isMiniMax && form.videoInputVideoPerSecond != null) {
      video.inputVideoPerSecond = form.videoInputVideoPerSecond;
    }
    if (form.videoInputImagePerImage != null) {
      video.inputImagePerImage = form.videoInputImagePerImage;
    }
    if (form.videoFreeInputImages != null) {
      video.freeInputImages = form.videoFreeInputImages;
    }
    // MiniMax 音频输入免费，不落任何附加费；其余视频方案保留表单托管 + 未托管条目
    const surcharges: Dict[] = isMiniMax ? [] : [...(form.videoExtraSurcharges || [])];
    if (!isMiniMax && form.videoAudioSurcharge != null) {
      surcharges.push({code: "AUDIO", trigger: "AUDIO", amount: form.videoAudioSurcharge});
    }
    return {
      defaultUnitPrice: form.unitPrice,
      ...(Object.keys(resolutionPrices).length ? {resolutionPrices} : {}),
      ...(Object.keys(video).length ? {video} : {}),
      ...(surcharges.length ? {surcharges} : {})
    };
  }
  if (form.billingMode === BillingMode.EMBEDDING) {
    return {defaultUnitPrice: form.inputUnitPrice};
  }
  if (form.billingMode === BillingMode.REQUEST || form.billingMode === BillingMode.AUDIO) {
    return {defaultUnitPrice: form.unitPrice};
  }
  return null;
}

function buildConfigJson() {
  if (form.billingMode === BillingMode.CUSTOM) {
    return form.customConfigJson?.trim() || null;
  }
  const base = buildBaseConfig();
  if (form.pricingPlan === PricingPlan.TIERED) {
    // 档位缺失项以 0 落库，满足后端「每档 6 项单价齐全」约束；保存前统一 2 空格格式化
    // tierMode 仅在 TIERED 下写入——非 TIERED 配了它会被后端 validate 拒绝
    const mode = String(form.tierMode || "PER_REQUEST").trim().toUpperCase();
    return stringifyJson({
      ...(base || {}),
      tierMode: mode === "PROGRESSIVE" ? "PROGRESSIVE" : "PER_REQUEST",
      tier: toTierPayload(form.tiers)
    }, 2);
  }
  if (form.pricingPlan === PricingPlan.PEAK_OFF_PEAK) {
    // 方案层只输出相位与倍率，价格本体来自上方基础价
    return stringifyJson({
      ...(base || {}),
      pricing: toPeakPayload(form.peakPricing)
    }, 2);
  }
  return base ? stringifyJson(base, 2) : null;
}

/** 规则编码由人工填写（后端做去空白与唯一性校验，不强制大写） */
function resolveRuleCode(): string {
  return form.code.trim();
}

function buildPayload() {
  return {
    code: resolveRuleCode(),
    modelFamilyCode: form.modelFamilyCode ? form.modelFamilyCode : null,
    billingMode: form.billingMode,
    pricingPlan: form.pricingPlan,
    currency: form.currency,
    unit: form.billingMode === BillingMode.FREE ? BillingUnit.ONE_K_TOKENS : form.unit,
    configJson: buildConfigJson(),
    enabled: form.enabled,
    remark: form.remark?.trim() || null
  };
}

async function submit() {
  // 停留在 json 页直接保存时，先把编辑内容回写表单再走统一构建
  if (activeTab.value === "json" && !applyJsonTextToForm()) {
    return;
  }
  if (!resolveRuleCode()) {
    ElMessage.warning("请填写规则编码");
    return;
  }
  // 保存前确认编码未被使用：被绑定的编码改了会让存量模型掉绑（后端亦有同名硬校验）
  if (codeBlockedByBinding.value && codeChanged.value) {
    ElMessage.warning(`规则编码已被 ${boundModelCount.value} 个供应商模型使用，请先解绑后再修改`);
    return;
  }
  const tierIssues = form.pricingPlan === PricingPlan.TIERED ? validateTierRows(form.tiers) : [];
  if (tierIssues.length) {
    ElMessage.warning(tierIssues[0].message);
    return;
  }
  const peakIssues = form.pricingPlan === PricingPlan.PEAK_OFF_PEAK ? validatePeakPricing(form.peakPricing, form.billingMode, form.cacheWriteMode) : [];
  if (peakIssues.length) {
    ElMessage.warning(peakIssues[0].message);
    return;
  }
  // MiniMax 的识别分量是「输入图片按张计费」：缺图片单价与后端 validate 同口径，前置拦截给出明确提示
  if (form.pricingPlan === PricingPlan.VENDOR_MINIMAX && form.videoInputImagePerImage == null) {
    ElMessage.warning("MiniMax 方案请填写「输入图片 每张」单价");
    return;
  }
  if (form.billingMode === BillingMode.CUSTOM && !validateJson(form.customConfigJson, "自定义配置")) {
    return;
  }
  saving.value = true;
  try {
    const payload = buildPayload();
    if (isEdit.value) {
      await updateBillingRule(form.id!, payload);
    } else {
      await createBillingRule(payload);
    }
    ElMessage.success("已保存");
    visible.value = false;
    emit("saved");
  } finally {
    saving.value = false;
  }
}
</script>

<style scoped>
.billing-rule-form {
  padding-bottom: 8px;
}

.form-section {
  border-color: #e4e7ed;
}

.billing-base-card {
  margin-top: 12px;
  padding: 12px 14px 4px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  background: var(--el-fill-color-blank);
}

.billing-base-card__title {
  margin-bottom: 12px;
  color: var(--el-text-color-primary);
  font-size: var(--el-font-size-base, 13px);
  font-weight: 600;
}

:deep(.billing-usage-field .el-form-item__label) {
  background: #e4f5ed;
  padding: 0 8px;
}

:deep(.billing-strategy-label .el-form-item__label) {
  background: #f7dce8;
  padding: 0 8px;
}

:deep(.cache-write-label .el-form-item__label) {
  background: #fff9c4;
  padding: 0 8px;
}

.inline-switch {
  margin-bottom: 0;
}
</style>

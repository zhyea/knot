/**
 * 视频分辨率档位行的共享契约（表单层）。
 *
 * <p>一行档位最终落成 {@code config_json.resolutionPrices} 里的多个键，键名与行字段的对应关系
 * 是**唯一契约**——保存（buildBaseConfig）与回显（parseResolutionRows）必须共用它，
 * 各方案组件也只按这套字段填自己那几列：</p>
 *
 * <pre>
 * 档位                 -> price                无声价 / 通用单价
 * 档位:AUDIO           -> audioPrice           有声价（维度分档键）
 * 档位:REFERENCE       -> referencePrice       含参考输入价（维度分档键）
 * 档位:REFERENCE:AUDIO -> referenceAudioPrice  含参考+有声价（维度分档键最具体档）
 * INPUT_VIDEO:档位     -> inputPrice           MiniMax 输入视频价
 * </pre>
 *
 * <p>⭐ 为什么要有这个模块：行的创建分散在「视频模式组件」「维度分档方案组件」「config_json 回显」
 * 三处，各自写字面量会让新增字段只补到其中一两处——2026-10-26 加 {@code inputPrice} 时就漏了一处，
 * 结果是 MiniMax 输入视频价保存后回显不出来。字段集与工厂函数收在一处，新增维度只改这个文件。</p>
 */
export type ResolutionRow = {
  /** 行唯一键：仅前端用于 v-for key，不落库 */
  uid?: string;
  /** 档位名（720P / 768P / 2K / 4K…），可自由输入 */
  key: string;
  price: number | null;
  audioPrice: number | null;
  referencePrice: number | null;
  referenceAudioPrice: number | null;
  inputPrice: number | null;
};

/** 价格列字段名（不含 key/uid）：后缀键只能映射到这五个之一 */
export type ResolutionPriceField = "price" | "audioPrice" | "referencePrice" | "referenceAudioPrice" | "inputPrice";

/**
 * 后缀键 -> 行字段的映射，**按后缀长度降序**排列。
 *
 * <p>顺序不能乱：{@code :REFERENCE:AUDIO} 必须先于 {@code :REFERENCE} 命中，
 * 否则会被截成「档位:AUDIO」，把「含参考+有声」价错配到「有声」列。</p>
 */
export const RESOLUTION_SUFFIX_FIELDS: {suffix: string; field: ResolutionPriceField}[] = [
  {suffix: ":REFERENCE:AUDIO", field: "referenceAudioPrice"},
  {suffix: ":REFERENCE", field: "referencePrice"},
  {suffix: ":AUDIO", field: "audioPrice"}
];

/** 建一行空档位：所有价格列一律 null，由各方案组件填自己那几列 */
export function createResolutionRow(key = "", uid?: string): ResolutionRow {
  return {
    ...(uid ? {uid} : {}),
    key,
    price: null,
    audioPrice: null,
    referencePrice: null,
    referenceAudioPrice: null,
    inputPrice: null
  };
}

// ============================================================================
// 计费组合：决定档位表显示哪些价格列（不是决定「有哪些档位」）
// ============================================================================

/**
 * 计费组合码：{@code BASE}（基础价）恒在，其余是「请求侧带不带这个维度」的组合。
 *
 * <p>与档位是两根完全不同的轴：档位（720P / 1080P…）是逐行自由新增的；组合是**先定下来、
 * 一旦有档位就锁死**的维度配置。组合决定列集与落键后缀，档位决定每行填多少价。</p>
 *
 * <p>⚠ 代码里的 {@code BASE} 就是界面上的「基础价」，它<b>不是「无声专用价」</b>：
 * {@code VideoUsageFacts} 判「有无声」靠请求体的 {@code generate_audio} / {@code audio} 等开关，
 * <b>字段缺失即判为无</b>；默认带声音的模型（大多数视频模型）客户端不传这个键，
 * 命中就是 {@code BASE}。所以它是<b>主力价 + 回落链终点</b>，叫「无声」会误导配置者
 * 把便宜的无声音价填进主力列（2026-10-30 更名）。</p>
 */
export type ResolutionComboCode = "BASE" | "AUDIO" | "REFERENCE" | "REFERENCE_AUDIO";

export interface ResolutionCombo {
  code: ResolutionComboCode;
  /** 复选框 / 列标题文案 */
  label: string;
  /** 对应行字段 */
  field: ResolutionPriceField;
  /** 拼进 resolutionPrices 键的后缀（BASE 为空串） */
  suffix: string;
  /** 该列留空时的占位提示 */
  placeholder: string;
}

/**
 * 四种组合，**顺序即列顺序**；BASE（基础价）恒在首位且不可取消。
 *
 * <p>后端 {@code BillingConfig.SUPPORTED_DIMENSION_SUFFIXES} 只放行这四种后缀，
 * 且取价链按「含参考+有声 → 含参考 → 有声 → 基础价」逐级去尾回落——BASE 是回落链的终点，
 * 没有它，未声明音频开关的请求将无价可取。</p>
 */
export const RESOLUTION_COMBOS: ResolutionCombo[] = [
  {code: "BASE", label: "基础价", field: "price", suffix: "", placeholder: "留空用默认单价"},
  {code: "AUDIO", label: "有声", field: "audioPrice", suffix: ":AUDIO", placeholder: "留空同基础价"},
  {code: "REFERENCE", label: "含参考", field: "referencePrice", suffix: ":REFERENCE", placeholder: "留空同基础价"},
  {
    code: "REFERENCE_AUDIO",
    label: "含参考 + 有声",
    field: "referenceAudioPrice",
    suffix: ":REFERENCE:AUDIO",
    placeholder: "留空逐级回落"
  }
];

/**
 * 新建规则的默认组合：**只有基础价**。
 *
 * <p>⭐ 不再预设成四列全开（2026-10-30 改）：多数模型的价格表只有一个价，
 * 预设出「有声 / 含参考」的空列会让配置者以为这两维必须填，也会诱导把有声价填错列。
 * 只有把某个维度**单独卖更贵**时才勾上对应开关，列才出现。</p>
 */
export const DEFAULT_COMBOS: ResolutionComboCode[] = ["BASE"];

/**
 * 存量配置缺 {@code video.dimensionCombos} 字段时的回显口径：**全选**。
 *
 * <p>⚠ 这个常量不可改、不可与 {@link DEFAULT_COMBOS} 合并：老配置库里是一张四列价表，
 * 若把「无字段」重新解释成「仅基础价」，有声价 / 参考价就会变成后端仍命中、
 * 界面上却看不见的静默收费（改配置时更容易把它们一并抹掉）。</p>
 */
export const LEGACY_COMBOS: ResolutionComboCode[] = RESOLUTION_COMBOS.map((combo) => combo.code);

/** 组合码 -> 定义（未知码返回 undefined，供回显容错） */
export function comboOf(code: string): ResolutionCombo | undefined {
  const normalized = String(code || "").trim().toUpperCase();
  return RESOLUTION_COMBOS.find((combo) => combo.code === normalized);
}

/**
 * 归一组合码集合：过滤未知码、去重、**恒定补齐 BASE**、按 {@link RESOLUTION_COMBOS} 顺序输出。
 *
 * <p>补 BASE 不是防御性兜底而是语义要求：无声价是逐级回落链的终点，
 * 没有 BASE 的价表在「无声请求」上无价可取（等价于该请求免费出账）。</p>
 */
export function normalizeCombos(codes: unknown): ResolutionComboCode[] {
  const picked = new Set<ResolutionComboCode>(["BASE"]);
  if (Array.isArray(codes)) {
    for (const raw of codes) {
      const matched = comboOf(String(raw ?? ""));
      if (matched) {
        picked.add(matched.code);
      }
    }
  }
  return RESOLUTION_COMBOS.filter((combo) => picked.has(combo.code)).map((combo) => combo.code);
}

/** 是否全选：全选是默认值，不必写进 config_json（回显无字段即按全选处理） */
export function isAllCombos(codes: ResolutionComboCode[]): boolean {
  return normalizeCombos(codes).length === RESOLUTION_COMBOS.length;
}

// ============================================================================
// 组合轴：把四码的笛卡尔积换成「两个独立开关」
// ============================================================================

/**
 * 组合轴。界面上一轴一个开关，**默认都关**（只基础价）：
 * 关 = 该维度与基础价同价，开 = 该维度单独定价。
 *
 * <p>⭐ 这样表达才贴合真实语义：不是「哪些档位要出现」，而是「哪个维度要卖更贵」。
 * 四码仍是落库与列渲染的契约（后端只认这四种后缀），轴只是它的界面投影。</p>
 */
export type ResolutionComboAxis = "AUDIO" | "REFERENCE";

/**
 * 该轴当前是否已单独定价。{@code REFERENCE_AUDIO} 同时属于两轴。
 *
 * <p>⚠ 存量配置存在非完整状态（如只有 {@code REFERENCE_AUDIO} 没有 {@code AUDIO}：
 * 只在「含参考」时给有声加价）。这种状态界面上无法逐码表达，故按「该轴已开」显示；
 * 用户一旦改动任一开关，就按完整笛卡尔积归一——不主动改动则原样保留、原样保存。</p>
 */
export function hasComboAxis(codes: unknown, axis: ResolutionComboAxis): boolean {
  const normalized = normalizeCombos(codes);
  return axis === "AUDIO"
    ? normalized.includes("AUDIO") || normalized.includes("REFERENCE_AUDIO")
    : normalized.includes("REFERENCE") || normalized.includes("REFERENCE_AUDIO");
}

/**
 * 是否任一声价档已单列（{@code :AUDIO} / {@code :REFERENCE:AUDIO}）。
 *
 * <p>⭐ 音频直出附加费（{@code surcharges[trigger=AUDIO]}）与它是**同一件事的两种表达**，
 * 后端两处都加（附加费在分量求和后 add），同时配就是双收。判据只认这两个键。</p>
 */
export function hasAudioPriceTier(codes: unknown): boolean {
  const normalized = normalizeCombos(codes);
  return normalized.includes("AUDIO") || normalized.includes("REFERENCE_AUDIO");
}

/**
 * 切换一轴后重算组合：开则补齐该轴（含参考时连带 REFERENCE_AUDIO），关则该轴两码一起去掉。
 *
 * <p>两轴都开必然补 {@code REFERENCE_AUDIO}：取价链按「最具体优先」命中，
 * 「有声 + 含参考」的请求不会把两个维度相加，只会命中 {@code :REFERENCE:AUDIO}
 * （缺它则回落到 {@code :REFERENCE}，有声那一档白配）。</p>
 */
export function withComboAxis(codes: unknown, axis: ResolutionComboAxis, on: boolean): ResolutionComboCode[] {
  const audio = axis === "AUDIO" ? on : hasComboAxis(codes, "AUDIO");
  const reference = axis === "REFERENCE" ? on : hasComboAxis(codes, "REFERENCE");
  const picked: ResolutionComboCode[] = ["BASE"];
  if (audio) {
    picked.push("AUDIO");
  }
  if (reference) {
    picked.push("REFERENCE");
  }
  if (audio && reference) {
    picked.push("REFERENCE_AUDIO");
  }
  return normalizeCombos(picked);
}

/**
 * 组合变更后的最小表单切片：结构化类型而不是表单类型，避免 utils 反向依赖业务表单。
 */
export interface CombosFormSlice {
  videoDimensionCombos: ResolutionComboCode[];
  videoQuantityMode: string;
  videoAudioSurcharge: number | null;
  videoResolutionRows: ResolutionRow[];
}

/**
 * 组合变更的统一落地（两个宿主共用：视频模式组件与维度分档方案组件）。
 *
 * <p>三步，缺任一步都会留下「界面上看不见、计费却会命中」的配置：</p>
 * <ol>
 *   <li>归一后写回表单（恒补 BASE）；</li>
 *   <li>清掉被取消组合在**所有行**上的价格——只让列消失、价留在行里，保存就写进
 *       {@code resolutionPrices}，成为看不见的收费项；</li>
 *   <li>无「含参考」组合时把合成量口径回落 {@code OUTPUT}——否则已隐藏的下拉会以残留值落库。</li>
 * </ol>
 *
 * <p>勾上有声单列时<strong>清空音频直出附加费</strong>：两者是同一件事，并存即双收。
 * 清空会通过返回值告诉宿主去提示，不做无声改动。</p>
 */
export function applyCombosChange(form: CombosFormSlice, codes: unknown): {clearedAudioSurcharge: boolean} {
  const normalized = normalizeCombos(codes);
  form.videoDimensionCombos = normalized;
  if (!normalized.includes("REFERENCE") && !normalized.includes("REFERENCE_AUDIO")) {
    form.videoQuantityMode = "OUTPUT";
  }
  for (const combo of RESOLUTION_COMBOS) {
    if (!normalized.includes(combo.code)) {
      for (const row of form.videoResolutionRows) {
        row[combo.field] = null;
      }
    }
  }
  if (hasAudioPriceTier(normalized) && form.videoAudioSurcharge != null) {
    form.videoAudioSurcharge = null;
    return {clearedAudioSurcharge: true};
  }
  return {clearedAudioSurcharge: false};
}

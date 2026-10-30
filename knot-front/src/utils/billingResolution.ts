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

/**
 * 常用档位快捷选项：**只是下拉建议，不是合法值白名单**。
 *
 * <p>厂商档位不可枚举——MiniMax 用 768P、Vidu 用 540P、豆包用 480p/720p/1080p，
 * 还有各种自造名。硬编码成固定档位会让新档位永远配不上价，故档位名可自由输入
 * （el-select allow-create）。</p>
 */
export const COMMON_RESOLUTION_OPTIONS = ["480P", "540P", "720P", "768P", "1080P", "2K", "4K"];

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
 * 计费组合码：{@code BASE}（无声）恒在，其余是「请求侧带不带这个维度」的组合。
 *
 * <p>与档位是两根完全不同的轴：档位（720P / 1080P…）是逐行自由新增的；组合是**先定下来、
 * 一旦有档位就锁死**的维度配置。组合决定列集与落键后缀，档位决定每行填多少价。</p>
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
 * 四种组合，**顺序即列顺序**；BASE（无声）恒在首位且不可取消。
 *
 * <p>后端 {@code BillingConfig.SUPPORTED_DIMENSION_SUFFIXES} 只放行这四种后缀，
 * 且取价链按「含参考+有声 → 含参考 → 有声 → 无声」逐级去尾回落——BASE 是回落链的终点，
 * 没有它，「无声请求」将无价可取。</p>
 */
export const RESOLUTION_COMBOS: ResolutionCombo[] = [
  {code: "BASE", label: "无声", field: "price", suffix: "", placeholder: ""},
  {code: "AUDIO", label: "有声", field: "audioPrice", suffix: ":AUDIO", placeholder: "留空同无声"},
  {code: "REFERENCE", label: "含参考", field: "referencePrice", suffix: ":REFERENCE", placeholder: "留空同无声"},
  {
    code: "REFERENCE_AUDIO",
    label: "含参考 + 有声",
    field: "referenceAudioPrice",
    suffix: ":REFERENCE:AUDIO",
    placeholder: "留空逐级回落"
  }
];

/** 默认全选：与「档位表默认展示全部组合」的旧行为一致（全选时不落库，回显按默认全选处理） */
export const DEFAULT_COMBOS: ResolutionComboCode[] = RESOLUTION_COMBOS.map((combo) => combo.code);

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

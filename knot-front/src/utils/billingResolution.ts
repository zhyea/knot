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

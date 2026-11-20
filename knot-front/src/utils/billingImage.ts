import type {Dict} from "@/types";
import {toNumberOrNull} from "@/utils/billingTier";

/**
 * 图片计费的像素档位：解析、序列化与校验。
 *
 * <p>与后端 {@code BillingConfig.ImageConfig} 对齐：落库形状为
 * {@code image.tiers = [{ maxPixels, price }]}，{@code maxPixels} 含本档、缺省表示上不封顶；
 * 落档判据是「第一个上界不小于实际像素的档」，不是按档位名匹配。</p>
 *
 * <p>⭐ 为什么用像素上界而不是分辨率标签：图片请求可声明 {@code size=1024x1024}，也可能只说 {@code 2K}，
 * 上游更常回传实际宽高。像素总量是唯一各侧都对得上的事实，阈值分档能直接表达厂商价表里的
 * 「P ≤ 2,601,123.84 一档价、再往上另一档价」。</p>
 */

/** 单个像素档位：上界（含）与该档每张单价 */
export interface ImageTierRow {
  /** 仅用于前端 v-for 稳定 key，不参与序列化 */
  uid?: string;
  /** 像素上界（含）；null 表示上不封顶，只允许出现在最后一行 */
  maxPixels: number | null;
  price: number | null;
}

/** 建一行空档位：上界与单价都留空，由用户填 */
export function createImageTierRow(uid?: string): ImageTierRow {
  return {
    ...(uid ? {uid} : {}),
    maxPixels: null,
    price: null
  };
}

/**
 * config 里的 {@code image.tiers} -> 行列表。
 *
 * <p>不做排序与去重：档位顺序是用户声明的一部分（上不封顶档靠位置表达），
 * 打乱它会让保存后的配置与界面所见不一致。</p>
 */
export function parseImageTierRows(raw: unknown): ImageTierRow[] {
  if (!Array.isArray(raw)) {
    return [];
  }
  return raw
    .filter((item): item is Dict => !!item && typeof item === "object")
    .map((item) => ({
      maxPixels: toNumberOrNull(item.maxPixels),
      price: toNumberOrNull(item.price)
    }));
}

/**
 * 行列表 -> {@code image.tiers}。只保留填了单价的行；上界留空即上不封顶档。
 *
 * <p>单价为空的行整行丢弃（而不是按 0 落库）：0 元档会让该区间免费出账，
 * 与「这行还没填完」是两种完全不同的语义。</p>
 */
export function toImageTierPayload(rows: ImageTierRow[]): Dict[] {
  const payload: Dict[] = [];
  for (const row of rows || []) {
    const price = toNumberOrNull(row?.price);
    if (price == null) {
      continue;
    }
    const maxPixels = toNumberOrNull(row?.maxPixels);
    payload.push(maxPixels == null ? {price} : {maxPixels, price});
  }
  return payload;
}

/**
 * 表单校验：与后端 {@code validateImage} 同口径，返回可直接展示的问题文案。
 *
 * <p>上界必须逐行递增、上不封顶档只能在最后一行——后端按行序找第一个覆盖档，
 * 顺序错了会让大图命中低档价，编译期与页面都看不出来。</p>
 */
export function validateImageTierRows(rows: ImageTierRow[]): string[] {
  const issues: string[] = [];
  const effective = (rows || []).filter((row) =>
    toNumberOrNull(row?.price) != null || toNumberOrNull(row?.maxPixels) != null
  );
  if (!effective.length) {
    return ["维度分档价请至少填写一个像素档位及其单价"];
  }
  let previous: number | null = null;
  effective.forEach((row, index) => {
    const position = index + 1;
    const price = toNumberOrNull(row.price);
    if (price == null) {
      issues.push(`第 ${position} 行缺少每张单价`);
    } else if (price < 0) {
      issues.push(`第 ${position} 行单价不能为负`);
    }
    const maxPixels = toNumberOrNull(row.maxPixels);
    if (maxPixels == null) {
      if (index !== effective.length - 1) {
        issues.push("上不封顶档（像素上界留空）只能放在最后一行");
      }
      return;
    }
    if (!Number.isInteger(maxPixels) || maxPixels <= 0) {
      issues.push(`第 ${position} 行像素上界必须是正整数`);
      return;
    }
    if (previous != null && maxPixels <= previous) {
      issues.push("像素上界必须逐行递增");
    }
    previous = maxPixels;
  });
  return issues;
}

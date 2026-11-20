import {describe, expect, it} from "vitest";
import {
  createImageTierRow,
  parseImageTierRows,
  toImageTierPayload,
  validateImageTierRows
} from "@/utils/billingImage";

/**
 * 图片像素档位的前端契约：回显保序、序列化丢空行、校验与后端同口径。
 *
 * <p>这三件事任一走偏都只在金额上体现：行序错了会让大图命中低档价，
 * 空行按 0 落库会让整段区间免费，上界不递增则在后端保存期才被拒。</p>
 */
describe("billingImage 像素档位", () => {
  it("解析保留声明顺序，上界缺省即上不封顶", () => {
    const rows = parseImageTierRows([
      {maxPixels: 2601123, price: 0.02},
      {maxPixels: "4624220", price: "0.035"},
      {price: 0.05}
    ]);

    expect(rows).toEqual([
      {maxPixels: 2601123, price: 0.02},
      {maxPixels: 4624220, price: 0.035},
      {maxPixels: null, price: 0.05}
    ]);
  });

  it("解析容错：非数组与非对象元素返回空 / 被跳过", () => {
    expect(parseImageTierRows(null)).toEqual([]);
    expect(parseImageTierRows("x")).toEqual([]);
    expect(parseImageTierRows([null, 1, {price: 0.1}])).toEqual([{maxPixels: null, price: 0.1}]);
  });

  it("序列化丢弃没有单价的行，上界留空只写 price", () => {
    const payload = toImageTierPayload([
      {...createImageTierRow(), maxPixels: 2601123, price: 0.02},
      {...createImageTierRow()},
      {...createImageTierRow(), price: 0.05}
    ]);

    expect(payload).toEqual([{maxPixels: 2601123, price: 0.02}, {price: 0.05}]);
  });

  it("校验：上界必须逐行递增", () => {
    const issues = validateImageTierRows([
      {...createImageTierRow(), maxPixels: 4624220, price: 0.035},
      {...createImageTierRow(), maxPixels: 2601123, price: 0.02}
    ]);

    expect(issues).toContain("像素上界必须逐行递增");
  });

  it("校验：上不封顶档只能放在最后一行", () => {
    const issues = validateImageTierRows([
      {...createImageTierRow(), price: 0.05},
      {...createImageTierRow(), maxPixels: 2601123, price: 0.02}
    ]);

    expect(issues).toContain("上不封顶档（像素上界留空）只能放在最后一行");
  });

  it("校验：空表、缺单价、非正整数上界都拒绝", () => {
    expect(validateImageTierRows([])).toEqual(["维度分档价请至少填写一个像素档位及其单价"]);

    const missingPrice = validateImageTierRows([{...createImageTierRow(), maxPixels: 1024}]);
    expect(missingPrice).toContain("第 1 行缺少每张单价");

    const badBound = validateImageTierRows([
      {...createImageTierRow(), maxPixels: 1024.5, price: 0.01}
    ]);
    expect(badBound).toContain("第 1 行像素上界必须是正整数");
  });

  it("校验通过：递增 + 末尾开放档", () => {
    const issues = validateImageTierRows([
      {...createImageTierRow(), maxPixels: 2601123, price: 0.02},
      {...createImageTierRow(), maxPixels: 4624220, price: 0.035},
      {...createImageTierRow(), price: 0.05}
    ]);

    expect(issues).toEqual([]);
  });
});

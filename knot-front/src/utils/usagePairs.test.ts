import {describe, expect, it} from "vitest";
import {buildUsagePairs} from "@/utils/usagePairs";

/**
 * 用量对照的对齐口径。
 *
 * <p>上游 usage 是扁平键、`knot_extend.usage` 是 `input/output/attributes` 三段嵌套。
 * 按裸键并集对齐会把整段对象塞进一格，与上游字段错行——图片场景会直接掩盖
 * 「上游 1 张 / 网关 1 张」这个唯一能核对的事实。</p>
 */
describe("buildUsagePairs", () => {
  it("图片场景：扁平上游键对齐到 output / attributes 段，不出重复行", () => {
    const rows = buildUsagePairs(
      {image_count: 1, width: 1024, height: 1024},
      {output: {image_count: 1}, attributes: {width: 1024, height: 1024, pixels: 1048576}}
    );

    expect(rows.map((row) => row.key)).toEqual(["height", "image_count", "width", "attributes.pixels"]);
    expect(rows.find((row) => row.key === "image_count")).toEqual({
      key: "image_count",
      upstream: "1",
      produced: "1",
    });
    expect(rows.find((row) => row.key === "width")).toEqual({
      key: "width",
      upstream: "1024",
      produced: "1024",
    });
    expect(rows.filter((row) => row.key.includes("image_count"))).toHaveLength(1);
  });

  it("两侧同名键直接对齐，优先 output 段", () => {
    const rows = buildUsagePairs(
      {total_tokens: 1500},
      {input: {total_tokens: 100}, output: {total_tokens: 1500}}
    );

    expect(rows[0]).toEqual({key: "total_tokens", upstream: "1500", produced: "1500"});
    expect(rows.map((row) => row.key)).toEqual(["total_tokens", "input.total_tokens"]);
  });

  it("多段同后缀候选时不猜，未匹配的 produced 键单独成行", () => {
    const rows = buildUsagePairs(
      {image_count: 2},
      {input: {image_count: 1}, output: {image_count: 2}, attributes: {mode: "std"}}
    );

    expect(rows[0]).toEqual({key: "image_count", upstream: "2", produced: "2"});
    expect(rows.map((row) => row.key)).toEqual(["image_count", "attributes.mode", "input.image_count"]);
    expect(rows[1]).toEqual({key: "attributes.mode", upstream: "—", produced: "std"});
  });

  it("单侧缺失留占位符，不把 undefined 渲染成文字", () => {
    const rows = buildUsagePairs({prompt_tokens: 1000}, {});

    expect(rows).toEqual([{key: "prompt_tokens", upstream: "1000", produced: "—"}]);
  });

  it("两侧都空时不出行", () => {
    expect(buildUsagePairs({}, {})).toEqual([]);
    expect(buildUsagePairs(null, undefined)).toEqual([]);
  });

  it("数组按叶子原样序列化，不展开成点路径", () => {
    const rows = buildUsagePairs({images: [{url: "a.png"}]}, {});

    expect(rows[0].key).toBe("images");
    expect(rows[0].upstream).toBe('[{"url":"a.png"}]');
  });
});

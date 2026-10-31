// @vitest-environment jsdom
import {describe, it, expect, afterEach} from "vitest";
import {enableAutoUnmount, mount} from "@vue/test-utils";
import RoutingBillingVerdictPanel from "@/components/routing/RoutingBillingVerdictPanel.vue";
import type {Dict} from "@/types";

/**
 * `RoutingBillingVerdictPanel`（计费校验双盲面板）直测。
 *
 * <p>它只吃一个 `result` 快照 + 三个状态位，自身不发请求、不做判定——所以这里钉的是
 * <b>状态分支的优先级</b>与<b>空值展示口径</b>这两件容易被后续改动推翻的事：</p>
 * <ul>
 *   <li>skipped / loading / error / result 是互斥链路，顺序写错会让「请求失败」被
 *       「加载中」遮住，用户永远等不到错误；</li>
 *   <li>金额一律走 {@code displayText}（占位符 “—”），不能退回 {@code || "-"}：
 *       后者会把金额 0 显示成占位符，看起来像「没算出来」。</li>
 * </ul>
 */

enableAutoUnmount(afterEach);

function mountPanel(props: Dict = {}) {
  return mount(RoutingBillingVerdictPanel, {
    props: {result: null, loading: false, skipped: false, error: "", ...props}
  });
}

/** 取「标签 → 值」的 kv 行文本（面板里 kv 是 .bv-kv__item 加上两个 span） */
function kvText(wrapper: ReturnType<typeof mountPanel>, label: string): string {
  const item = wrapper.findAll(".bv-kv__item").find((node) => node.text().includes(label));
  expect(item, "未找到 kv 行：" + label).toBeTruthy();
  return item!.text();
}

describe("RoutingBillingVerdictPanel 状态分支", () => {
  it("链路短路：提示未发起裁判，且不渲染任何判定区", () => {
    const wrapper = mountPanel({skipped: true});
    expect(wrapper.text()).toContain("本次响应无消费详情，未发起裁判");
    expect(wrapper.find(".bv-zone").exists()).toBe(false);
  });

  it("skipped 优先于 loading：已短路就不再显示「正在判定」", () => {
    const wrapper = mountPanel({skipped: true, loading: true});
    expect(wrapper.text()).toContain("未发起裁判");
    expect(wrapper.find(".bv-loading").exists()).toBe(false);
  });

  it("loading 优先于 error：请求进行中不提前报错", () => {
    const wrapper = mountPanel({loading: true, error: "boom"});
    expect(wrapper.find(".bv-loading").exists()).toBe(true);
    expect(wrapper.text()).not.toContain("计费校验请求失败");
  });

  it("请求失败：渲染错误文案", () => {
    const wrapper = mountPanel({error: "网关超时"});
    expect(wrapper.text()).toContain("计费校验请求失败");
    expect(wrapper.text()).toContain("网关超时");
  });

  it("无结果且无状态位：只留标题，不出现空判定区", () => {
    const wrapper = mountPanel();
    expect(wrapper.find(".bv-zone").exists()).toBe(false);
    expect(wrapper.text()).toContain("计费校验");
  });
});

describe("RoutingBillingVerdictPanel 空值口径", () => {
  it("被告总额缺失时显示统一占位符 “—”，不是连字符", () => {
    const wrapper = mountPanel({result: {producedBilling: {totalCost: null}}});
    const text = kvText(wrapper, "被告总额");
    expect(text).toContain("—");
    expect(text).not.toContain("-");
  });

  it("被告总额为 0 时如实显示 0（不能被占位符吞掉）", () => {
    const wrapper = mountPanel({result: {producedBilling: {totalCost: 0}}});
    expect(kvText(wrapper, "被告总额")).toContain("0");
  });

  it("上游未返回 cost 时给出专门文案，不退化成占位符", () => {
    const wrapper = mountPanel({result: {upstreamCost: null}});
    expect(kvText(wrapper, "上游 cost")).toContain("上游未返回");
  });

  it("影子链未完成时单独告警，不把整块判定渲染成失败", () => {
    const wrapper = mountPanel({result: {shadowError: "上游未回 knot_extend"}});
    expect(wrapper.text()).toContain("影子链未完成");
    expect(wrapper.text()).toContain("上游未回 knot_extend");
  });
});

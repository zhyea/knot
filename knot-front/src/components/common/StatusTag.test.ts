// @vitest-environment jsdom
import { describe, it, expect } from "vitest";
import { mount } from "@vue/test-utils";
import StatusTag from "@/components/common/StatusTag.vue";

/**
 * StatusTag 是全局状态展示咽喉组件：接收 `active: Boolean` 渲染「是/否」。
 * 业务上启用状态只有 1/0，0 是合法值。本测试锁定 Boolean 强制边界——
 * 父组件若直接 `:active="row.status"` 传原始数值，0 必须被强制为 false（否），
 * 1 必须为 true（是），绝不能因 truthy 误判把禁用(0)显示成启用。
 */
describe("StatusTag 状态边界", () => {
  it("active=true 渲染「是」+ success", () => {
    const wrapper = mount(StatusTag, { props: { active: true } });
    expect(wrapper.text()).toBe("是");
    expect(wrapper.find(".el-tag").classes()).toContain("el-tag--success");
  });

  it("active=false 渲染「否」+ info", () => {
    const wrapper = mount(StatusTag, { props: { active: false } });
    expect(wrapper.text()).toBe("否");
    expect(wrapper.find(".el-tag").classes()).toContain("el-tag--info");
  });

  // 父组件传原始状态码 1 —— 必须显式强制为 true（是）
  it("active=1（原始状态码）强制为 true → 是", () => {
    const wrapper = mount(StatusTag, { props: { active: 1 as unknown as boolean } });
    expect(wrapper.text()).toBe("是");
    expect(wrapper.find(".el-tag").classes()).toContain("el-tag--success");
  });

  // 核心防回归：父组件传原始状态码 0 —— 必须强制为 false（否），
  // 若有人把 prop 改成 `v-if="active"` 或 `active ? ... : ...` 的 truthy 写法，
  // 0 会被当 falsy，但 Boolean(0) 强制后仍是 false，行为正确；本断言钉死此契约。
  it("active=0（禁用状态码）强制为 false → 否", () => {
    const wrapper = mount(StatusTag, { props: { active: 0 as unknown as boolean } });
    expect(wrapper.text()).toBe("否");
    expect(wrapper.find(".el-tag").classes()).toContain("el-tag--info");
  });

  // 注：Vue 对 Boolean prop 的空串属性按 HTML boolean 语义强制为 true（非业务场景，
  // status 永远是数值 0/1），故不在此覆盖，避免与业务边界混淆。

  it("active=undefined 强制为 false → 否", () => {
    const wrapper = mount(StatusTag, { props: {} });
    expect(wrapper.text()).toBe("否");
    expect(wrapper.find(".el-tag").classes()).toContain("el-tag--info");
  });
});

// @vitest-environment jsdom
import {describe, expect, it} from "vitest";
import {mount} from "@vue/test-utils";
import JsonCodeEditor from "@/components/common/JsonCodeEditor.vue";

describe("JsonCodeEditor 自动换行", () => {
  it("默认不换行", () => {
    const wrapper = mount(JsonCodeEditor, {props: {modelValue: "{\"name\":\"knot\"}"}});

    expect(wrapper.find("textarea").attributes("wrap")).toBe("off");
    expect(wrapper.find(".json-editor__body").classes()).not.toContain("json-editor__body--wrap");
  });

  it("点击按钮后可开启并关闭自动换行", async () => {
    const wrapper = mount(JsonCodeEditor, {props: {modelValue: "{\"name\":\"knot\"}"}});
    const button = wrapper.get('button[aria-label="自动换行"]');

    expect(button.find(".json-editor__wrap-icon").exists()).toBe(true);
    expect(button.text()).toBe("");

    await button.trigger("click");

    expect(wrapper.find("textarea").attributes("wrap")).toBe("soft");
    expect(wrapper.find(".json-editor__body").classes()).toContain("json-editor__body--wrap");
    expect(button.attributes("aria-pressed")).toBe("true");

    await wrapper.get('button[aria-label="关闭自动换行"]').trigger("click");

    expect(wrapper.find("textarea").attributes("wrap")).toBe("off");
    expect(wrapper.find(".json-editor__body").classes()).not.toContain("json-editor__body--wrap");
    expect(button.attributes("aria-pressed")).toBe("false");
  });

  it("只读模式不展示换行按钮", () => {
    const wrapper = mount(JsonCodeEditor, {props: {modelValue: "{}", readonly: true}});

    expect(wrapper.find('button[aria-label="自动换行"]').exists()).toBe(false);
  });
});

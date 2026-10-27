// @vitest-environment jsdom
import {describe, it, expect, afterEach} from "vitest";
import {mount} from "@vue/test-utils";
import {nextTick} from "vue";
import TreeSelect from "@/components/common/TreeSelect.vue";

/**
 * 默认展开契约：robin 2026-10-23 反馈「打开后子节点没有全部展开」。
 * 覆盖两条加载路径：静态 options 直传、异步 options（先空数组后到位——
 * 对应 ProviderAccountTreeSelect 的 onMounted 翻页聚合路径）。
 */

const TREE = [
  {
    value: "openai",
    label: "OpenAI",
    children: [
      {value: "openai-main", label: "openai-main"},
      {value: "openai-backup", label: "openai-backup"}
    ]
  },
  {
    value: "azure",
    label: "Azure",
    children: [{value: "azure-east", label: "azure-east"}]
  }
];

/**
 * 展开态（父容器 v-show 未置 display:none）的子级行 label。
 * ⚠ v-show 挂在 `.tree-select__children` 容器上，子行自身没有 display:none——
 * 必须查最近的 children 祖先，直接查子行 style.display 会永远判为「可见」。
 * popover teleported 到 body，故查全局 DOM。
 */
function visibleChildLabels(): string[] {
  return Array.from(document.querySelectorAll<HTMLElement>(".tree-select__row--child"))
    .filter((el) => el.closest(".tree-select__children")?.style.display !== "none")
    .map((el) => el.textContent?.trim() ?? "");
}

/** 打开下拉：popover 内容懒渲染，点击 reference 触发 trigger=click */
async function openDropdown(wrapper: ReturnType<typeof mount>): Promise<void> {
  await wrapper.find(".tree-select").trigger("click");
  await nextTick();
  await nextTick();
}

/** 原生派发点击（popover teleported 到 body，VTU 的 wrapper.find 够不到） */
function clickEl(selector: string): void {
  const el = document.querySelector<HTMLElement>(selector);
  el?.dispatchEvent(new MouseEvent("click", {bubbles: true}));
}

/** 收集本用例创建的实例，afterEach 统一 unmount（见上述隔离说明） */
const mounted: ReturnType<typeof mount>[] = [];

function mountTree(props: Record<string, unknown>): ReturnType<typeof mount> {
  const wrapper = mount(TreeSelect, {props});
  mounted.push(wrapper);
  return wrapper;
}

afterEach(() => {
  // popover teleport 到 body：仅清 innerHTML 不 unmount 会让上一个实例的渲染内容
  // 残留在全局 DOM 里，后续用例的 querySelectorAll 会查到别人的行（假失败）。
  mounted.splice(0).forEach((w) => w.unmount());
  document.body.innerHTML = "";
});

describe("TreeSelect 默认展开", () => {
  it("静态 options：打开后全部子节点可见", async () => {
    const wrapper = mountTree({options: TREE});
    await openDropdown(wrapper);
    expect(visibleChildLabels()).toEqual(["openai-main", "openai-backup", "azure-east"]);
  });

  it("异步 options（先空后到）：子节点同样默认可见", async () => {
    const wrapper = mountTree({options: []});
    await openDropdown(wrapper);
    await wrapper.setProps({options: TREE});
    await nextTick();
    await nextTick();
    expect(visibleChildLabels()).toEqual(["openai-main", "openai-backup", "azure-east"]);
  });
});

describe("TreeSelect 交互契约", () => {
  it("leafOnly：点击父行只折叠/展开，不产生选中", async () => {
    const wrapper = mountTree({options: TREE});
    await openDropdown(wrapper);
    clickEl(".tree-select__row--parent");
    await nextTick();
    expect(wrapper.emitted("update:modelValue")).toBeFalsy();
    expect(visibleChildLabels()).toEqual(["azure-east"]);
  });

  it("父行折叠后可再展开（toggleExpand 双向）", async () => {
    const wrapper = mountTree({options: TREE});
    await openDropdown(wrapper);
    clickEl(".tree-select__row--parent");
    await nextTick();
    expect(visibleChildLabels()).toEqual(["azure-east"]);
    clickEl(".tree-select__row--parent");
    await nextTick();
    expect(visibleChildLabels()).toEqual(["openai-main", "openai-backup", "azure-east"]);
  });

  it("点击子行 emit update:modelValue + change(value, node)", async () => {
    const wrapper = mountTree({options: TREE});
    await openDropdown(wrapper);
    clickEl(".tree-select__row--child");
    await nextTick();
    expect(wrapper.emitted("update:modelValue")![0]).toEqual(["openai-main"]);
    const changeArgs = wrapper.emitted("change")![0];
    expect(changeArgs[0]).toBe("openai-main");
    expect((changeArgs[1] as {value: string}).value).toBe("openai-main");
  });

  it("defaultExpandAll=false 时默认全折叠", async () => {
    const wrapper = mountTree({options: TREE, defaultExpandAll: false});
    await openDropdown(wrapper);
    expect(visibleChildLabels()).toEqual([]);
    clickEl(".tree-select__row--parent");
    await nextTick();
    expect(visibleChildLabels()).toEqual(["openai-main", "openai-backup"]);
  });
});

describe("TreeSelect 面板宽度", () => {
  it("弹层由内容自适应，不被 el-popover 默认 150px 固定", async () => {
    const wrapper = mountTree({options: TREE});
    await openDropdown(wrapper);
    const popper = document.querySelector<HTMLElement>(".tree-select__popper");
    expect(popper).not.toBeNull();
    expect(popper!.style.width).toBe("auto");
  });
});

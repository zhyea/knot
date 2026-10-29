// @vitest-environment jsdom
import {describe, it, expect} from "vitest";
import {reactive} from "vue";
import {flushPromises, mount, type VueWrapper} from "@vue/test-utils";
import {ElSelect} from "element-plus";
import ResolutionTierPicker from "@/components/billing/ResolutionTierPicker.vue";
import {COMMON_RESOLUTION_OPTIONS, createResolutionRow} from "@/utils/billingResolution";
import type {ResolutionRow} from "@/utils/billingResolution";

/**
 * 档位选择（两步式第一步）的行为固化。
 *
 * <p>为什么单独测这个组件而不是只测抽屉：两步式的全部风险都在「**选择状态与行集合是不是一份数据**」——
 * 一旦另存 {@code selectedKeys} 副本，就会出现「界面上取消勾选了、保存时价还在库里计费」这种
 * 静默收费事故，而这类事故在抽屉的端到端测试里只表现为 payload 少一个键，很容易被当成断言写错了。</p>
 *
 * <p>断言一律落在传入的 {@code rows} 数组上（它就是 {@code form.videoResolutionRows}，保存侧的真源），
 * 不读 DOM 里的 tag 文本——tag 是 Element Plus 内部渲染，与落库无关。</p>
 */
function mountPicker(rows: ResolutionRow[], tip = ""): VueWrapper {
  return mount(ResolutionTierPicker, {props: {rows, tip}});
}

/**
 * 勾选档位：直接对 el-select emit，等价于用户在多选下拉里选中若干项
 * （含 allow-create 输入的自定义档位，jsdom 下走 DOM 路径要处理 teleport 到 body 的下拉，
 * 而 emit 走的是组件契约，两者对业务逻辑等价）。
 */
async function selectKeys(wrapper: VueWrapper, keys: string[]): Promise<void> {
  await wrapper.findComponent(ElSelect).vm.$emit("update:modelValue", keys);
  await flushPromises();
}

async function clickButton(wrapper: VueWrapper, text: string): Promise<void> {
  const button = wrapper.findAll("button").find((node) => node.text().includes(text));
  if (!button) {
    throw new Error(`找不到按钮：${text}`);
  }
  await button.trigger("click");
  await flushPromises();
}

function keysOf(rows: ResolutionRow[]): string[] {
  return rows.map((row) => row.key);
}

describe("ResolutionTierPicker 档位选择", () => {
  it("档位名可自由输入：多选 + 可创建 + 可搜索", () => {
    // 厂商档位不可枚举（MiniMax 用 768P、Vidu 用 540P），固定下拉会让新档位永远配不上价
    const wrapper = mountPicker([]);

    expect(wrapper.findComponent(ElSelect).props()).toMatchObject({
      multiple: true,
      filterable: true,
      allowCreate: true
    });
  });

  it("勾选即建行，键名统一大写无空白（与落库键同口径）", async () => {
    const rows: ResolutionRow[] = [];
    const wrapper = mountPicker(rows);

    await selectKeys(wrapper, ["720P", " 480p "]);

    expect(keysOf(rows)).toEqual(["720P", "480P"]);
  });

  it("重复选择同一档位只建一行", async () => {
    const rows: ResolutionRow[] = [];
    const wrapper = mountPicker(rows);

    await selectKeys(wrapper, ["720P", "720P"]);

    expect(keysOf(rows)).toEqual(["720P"]);
  });

  it("行顺序跟随选择顺序（表格按此顺序展示）", async () => {
    const rows = [createResolutionRow("720P"), createResolutionRow("1080P")];
    const wrapper = mountPicker(rows);

    await selectKeys(wrapper, ["1080P", "720P", "4K"]);

    expect(keysOf(rows)).toEqual(["1080P", "720P", "4K"]);
  });

  it("取消勾选即删除该档位行", async () => {
    const rows = [createResolutionRow("720P"), createResolutionRow("1080P")];
    const wrapper = mountPicker(rows);

    await selectKeys(wrapper, ["720P"]);

    expect(keysOf(rows)).toEqual(["720P"]);
  });

  it("重新勾选是全新空行：旧单价不留在隐藏状态里", async () => {
    // ⭐ 这条是两步式的前提：未选中的档位不该以隐藏形式留在 config_json——
    // 那是一份界面上看不见、计费时却会命中的价，等于埋一颗静默收费的雷
    const rows = [createResolutionRow("1080P")];
    rows[0].price = 0.5;
    rows[0].referencePrice = 0.4;
    const wrapper = mountPicker(rows);

    await selectKeys(wrapper, []);
    await selectKeys(wrapper, ["1080P"]);

    expect(rows[0].price).toBeNull();
    expect(rows[0].referencePrice).toBeNull();
  });

  it("遗留的空名行会被一次勾选清掉（僵尸行不再参与保存判断）", async () => {
    // 旧交互靠「新增档位」按钮造空行，保存时靠 buildBaseConfig 跳过；两步式下这个来源被根除
    const rows = [createResolutionRow("720P"), createResolutionRow("")];
    const wrapper = mountPicker(rows);

    await selectKeys(wrapper, ["720P"]);

    expect(keysOf(rows)).toEqual(["720P"]);
  });

  it("全选常用档位：并入当前选择且不覆盖已填价格", async () => {
    const rows = [createResolutionRow("768P")];
    rows[0].price = 0.3;
    const wrapper = mountPicker(rows);

    await clickButton(wrapper, "全选常用档位");

    expect(keysOf(rows)).toEqual(["768P", ...COMMON_RESOLUTION_OPTIONS.filter((item) => item !== "768P")]);
    expect(rows[0].price).toBe(0.3);
  });

  it("清空移除全部档位", async () => {
    const rows = [createResolutionRow("720P"), createResolutionRow("4K")];
    const wrapper = mountPicker(rows);

    await clickButton(wrapper, "清空");

    expect(rows).toHaveLength(0);
  });

  it("未选任何档位时清空按钮不可用", async () => {
    const wrapper = mountPicker([]);
    expect(wrapper.findAll("button").find((node) => node.text().includes("清空"))?.attributes("disabled"))
      .toBeDefined();

    await selectKeys(wrapper, ["720P"]);
    expect(wrapper.findAll("button").find((node) => node.text().includes("清空"))?.attributes("disabled"))
      .toBeUndefined();
  });

  it("选择状态从行派生，不另存副本", async () => {
    // 用 reactive 数组：真实场景里它就是 form.videoResolutionRows，外部改动必须能反向驱动选择器
    const rows = reactive<ResolutionRow[]>([createResolutionRow("720P")]);
    const wrapper = mountPicker(rows);
    expect(wrapper.findComponent(ElSelect).props("modelValue")).toEqual(["720P"]);

    // 反向也成立：行被外部改动（如 config_json 页回写）时，选择器跟着变
    rows.push(createResolutionRow("4K"));
    await flushPromises();

    expect(wrapper.findComponent(ElSelect).props("modelValue")).toEqual(["720P", "4K"]);
  });

  it("提示文案渲染取消勾选的代价", () => {
    const wrapper = mountPicker([], "取消勾选即删除该档位（含已填单价）");

    expect(wrapper.text()).toContain("取消勾选即删除该档位（含已填单价）");
  });
});

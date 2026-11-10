// @vitest-environment jsdom
import {describe, it, expect, vi, beforeEach, afterEach} from "vitest";
import {readFileSync} from "node:fs";
import {resolve} from "node:path";
import {nextTick} from "vue";
import {mount, flushPromises} from "@vue/test-utils";
import {ElMessage, ElSwitch} from "element-plus";
import type {Row} from "@/types";
import RoutingRuleListPanel from "@/components/routing/RoutingRuleListPanel.vue";

// 隔离网络：列表行启用开关最终调这个 API
vi.mock("@/api/routing", () => ({
  updateRoutingRuleStatus: vi.fn().mockResolvedValue(undefined)
}));

import {updateRoutingRuleStatus} from "@/api/routing";

/**
 * 「启用规则必须绑定应用」在列表开关这个第二入口的守门。
 *
 * 抽屉的 submit 校验只管表单保存；这里的开关直接调 /status 接口，
 * 旧实现没有任何前置检查，用户只会看到开关打开后被后端 400 静默弹回。
 * 后端仍是唯一权威校验，前端预检只负责给出可执行提示、不发无效请求。
 */
function row(id: number, enabled: boolean, appId: number | null, appName: string | null = "应用一"): Row {
  return {
    id, enabled, appId, appName,
    ruleCode: `R_${id}`, targets: [], consumerName: "消费者一",
    appScenario: null, username: null, userRealName: null,
    logicalModelName: null, logicalModelCode: null,
    updatedAt: "", createdAt: ""
  } as Row;
}

async function mountList(rows: Row[]) {
  const wrapper = mount(RoutingRuleListPanel, {
    props: {rows},
    global: {stubs: {ListPagination: true, RowActions: true}}
  });
  // el-table 在 jsdom 下需要两次 nextTick 才把 body 行渲染出来
  await nextTick();
  await nextTick();
  return wrapper;
}

// jsdom 环境下 import.meta.url 不是 file scheme，用 vitest 的 cwd（knot-front）定位源码
const source = readFileSync(
  resolve(process.cwd(), "src/components/routing/RoutingRuleListPanel.vue"),
  "utf8"
);

describe("RoutingRuleListPanel 启用开关前置守卫", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(ElMessage, "warning").mockImplementation(() => ({}) as never);
    vi.spyOn(ElMessage, "success").mockImplementation(() => ({}) as never);
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("未绑定应用的停用规则：启用被拦，不调状态接口且提示可执行的下一步", async () => {
    const rows = [row(1, false, null)];
    const wrapper = await mountList(rows);

    await wrapper.findAllComponents(ElSwitch)[0].vm.$emit("change", true);
    await flushPromises();
    await nextTick();

    expect(updateRoutingRuleStatus).not.toHaveBeenCalled();
    expect(vi.mocked(ElMessage.warning)).toHaveBeenCalledWith("启用规则前请先绑定应用");
    expect(rows[0].enabled).toBe(false);
    expect(wrapper.emitted("changed")).toBeFalsy();
  });

  it("已绑定应用的停用规则：启用正常走状态接口", async () => {
    const rows = [row(2, false, 12)];
    const wrapper = await mountList(rows);

    await wrapper.findAllComponents(ElSwitch)[0].vm.$emit("change", true);
    await flushPromises();

    expect(updateRoutingRuleStatus).toHaveBeenCalledWith(2, true);
    expect(vi.mocked(ElMessage.warning)).not.toHaveBeenCalled();
    expect(wrapper.emitted("changed")).toBeTruthy();
  });

  it("停用一条已启用的脏数据规则不受绑定应用约束", async () => {
    const rows = [row(3, true, null)];
    const wrapper = await mountList(rows);

    await wrapper.findAllComponents(ElSwitch)[0].vm.$emit("change", false);
    await flushPromises();

    expect(updateRoutingRuleStatus).toHaveBeenCalledWith(3, false);
    expect(vi.mocked(ElMessage.warning)).not.toHaveBeenCalled();
  });

  it("应用名空值走统一占位出口 displayText", () => {
    expect(source).toContain("{{ displayText(row.appName) }}");
    expect(source).not.toContain('prop="appName"');
  });
});

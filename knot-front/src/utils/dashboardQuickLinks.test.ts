import {describe, it, expect} from "vitest";
import type {Component} from "vue";
import {collectQuickLinks, QUICK_LINK_LIMIT} from "@/utils/dashboardQuickLinks";
import type {Row} from "@/types";

/**
 * 图标解析用字符串替身：本模块不关心拿到的是哪个组件，只关心**传给它的图标名**，
 * 所以断言落在「resolveIcon 收到什么名字」上，比断言组件本身稳定。
 */
function resolveIcon(iconName: string): Component {
  return iconName as unknown as Component;
}
const iconNameOf = (icon: Component) => icon as unknown as string;

describe("collectQuickLinks", () => {
  it("只取叶子：有 children 的分组自己不进列表", () => {
    const modules: Row[] = [
      {
        icon: "Grid",
        menus: [
          {menuName: "分组A", icon: "Bell", routePath: "/group-a", children: []},
          {
            menuName: "分组B",
            routePath: "/group-b",
            children: [{menuName: "页面1", routePath: "/p1", children: []}]
          }
        ]
      }
    ];
    const links = collectQuickLinks(modules, resolveIcon);
    // 分组A 是「有 children 字段但为空数组」→ 按叶子处理，保留
    // 分组B 有实际子节点 → 自己不进列表
    expect(links.map((link) => link.path)).toEqual(["/group-a", "/p1"]);
  });

  it("图标继承：自身优先，缺省时用父分组的", () => {
    const modules: Row[] = [
      {
        icon: "Grid",
        menus: [
          {
            menuName: "分组",
            children: [
              {menuName: "跟着父图标", routePath: "/inherit", children: []},
              {menuName: "自己配了", icon: "Tools", routePath: "/own", children: []}
            ]
          }
        ]
      }
    ];
    const links = collectQuickLinks(modules, resolveIcon);
    expect(links.map((link) => iconNameOf(link.icon))).toEqual(["Grid", "Tools"]);
  });

  it("按 routePath 去重：同一页面挂在两个分组下只出现一次", () => {
    const modules: Row[] = [
      {icon: "Grid", menus: [{menuName: "A", routePath: "/same", children: []}]},
      {icon: "Grid", menus: [{menuName: "B", routePath: "/same", children: []}]}
    ];
    const links = collectQuickLinks(modules, resolveIcon);
    expect(links).toHaveLength(1);
    expect(links[0].label).toBe("A");
  });

  it("没有 routePath 的叶子跳过：点进去是空白页", () => {
    const modules: Row[] = [{icon: "Grid", menus: [{menuName: "无路由", children: []}]}];
    expect(collectQuickLinks(modules, resolveIcon)).toEqual([]);
  });

  it("截到前 6 条", () => {
    const menus = Array.from({length: 10}, (_, i) => ({menuName: `P${i}`, routePath: `/p${i}`, children: []}));
    const links = collectQuickLinks([{icon: "Grid", menus}], resolveIcon);
    expect(links).toHaveLength(QUICK_LINK_LIMIT);
    expect(links.map((link) => link.path)).toEqual(["/p0", "/p1", "/p2", "/p3", "/p4", "/p5"]);
  });

  it("空输入不抛错", () => {
    expect(collectQuickLinks([], resolveIcon)).toEqual([]);
    expect(collectQuickLinks([{icon: "Grid"}], resolveIcon)).toEqual([]);
  });
});

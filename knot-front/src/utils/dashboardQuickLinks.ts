import type {Component} from "vue";
import type {Row} from "@/types";

export interface QuickLink {
  path: string;
  label: string;
  icon: Component;
}

/** 快捷入口上限：再多项就退化成「又一个菜单页」 */
export const QUICK_LINK_LIMIT = 6;

/**
 * 从授权菜单树里收集快捷入口。
 *
 * <p>三条口径：① **只取叶子**——有 children 的节点是分组，它自己的 routePath 通常是空，
 * 点进去也是空白页；② 按 `routePath` **去重**——同一页面挂在不同分组下很常见；
 * ③ 截前 N 条，顺序即菜单顺序（不做「按使用频率排序」，那要靠埋点，别在这里猜）。</p>
 *
 * @param modules 当前用户有权访问的模块（含嵌套菜单）
 * @param resolveIcon 图标名 → 组件。由调用方注入，本模块不依赖 Element Plus
 * @param limit 上限，默认 {@link QUICK_LINK_LIMIT}
 */
export function collectQuickLinks(
  modules: Row[],
  resolveIcon: (iconName: string) => Component,
  limit: number = QUICK_LINK_LIMIT
): QuickLink[] {
  const links: QuickLink[] = [];

  function collect(menus: Row[], parentIcon: string): void {
    for (const menu of menus || []) {
      const children = Array.isArray(menu.children) ? menu.children : [];
      if (children.length) {
        // 分组节点把图标下传：子菜单多半不单独配图标
        collect(children, menu.icon || parentIcon);
        continue;
      }
      if (!menu.routePath) {
        continue;
      }
      links.push({
        path: menu.routePath,
        label: menu.menuName,
        icon: resolveIcon(menu.icon || parentIcon)
      });
    }
  }

  for (const module of modules || []) {
    collect(module.menus, module.icon);
  }

  const seen = new Set<string>();
  const unique: QuickLink[] = [];
  for (const link of links) {
    if (seen.has(link.path)) {
      continue;
    }
    seen.add(link.path);
    unique.push(link);
  }
  return unique.slice(0, limit);
}

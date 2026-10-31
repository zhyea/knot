import type {Component} from "vue";
import {
  Bell,
  Connection,
  Cpu,
  Grid,
  Lock,
  Money,
  OfficeBuilding,
  Odometer,
  Setting,
  Share,
  Tools,
  User
} from "@element-plus/icons-vue";

/**
 * 菜单图标名 → Element Plus 组件。
 *
 * <p>⚠ 兜底是 {@code Setting} 而不是空：图标名可能是后端新加的、也可能是
 * 别的图标库的名字（{@code Box} / {@code Coin} / {@code Document} 就是这种情况，
 * 这里给它们挑了形近的替代）。缺图标不该让整行菜单错位。</p>
 */
const ICON_MAP: Record<string, Component> = {
  Bell,
  Box: Cpu,
  Coin: Money,
  Connection,
  Cpu,
  Document: Bell,
  Grid,
  Lock,
  OfficeBuilding,
  Odometer,
  Setting,
  Share,
  Tools,
  User
};

export function resolveDashboardIcon(iconName: string): Component {
  return ICON_MAP[iconName] || Setting;
}

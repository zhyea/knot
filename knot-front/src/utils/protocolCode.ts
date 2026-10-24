/**
 * 协议码基础归一（改进清单 3.10）：去首尾空白 + 转大写。
 *
 * <p>此前 `ModelFormDrawer` 与 `useDebugCapabilities` 各有一份 trim+upper 实现，
 * `RoutingRuleTestDrawer` 的同名函数则是 canonical 别名归一的薄包装——三份同名不同义。
 * 本 util 只承担<b>基础归一</b>；别名归一（供应商别名 → canonicalCode）依赖后端能力接口，
 * 仍由 `useDebugCapabilities().canonicalOf` 负责，两者不可互相替代。</p>
 *
 * <p>用 `??` 而非 `||`：协议码是字符串枚举，`0` / `false` 不应被当作空值吞掉。</p>
 */
export function normalizeProtocolCode(value: unknown): string {
  return String(value ?? "").trim().toUpperCase();
}

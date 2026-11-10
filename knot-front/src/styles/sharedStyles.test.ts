import {describe, expect, it} from "vitest";
import {readFileSync} from "node:fs";
import {resolve} from "node:path";

/**
 * 共享 CSS 收口守门（2026-11-10 第五批）。
 *
 * <p>背景：`app.css` 已有 ⑮-1~⑮-3 的收口决策，本批再收两组「逐字相同且全局化等价」
 * 的 scoped 规则。CSS 改动不会让类型检查或组件测试变红，只能靠源码断言钉住。</p>
 *
 * <p>更重要的是反向的一半：**刻意不合并**的两组也必须留在局部，否则就是真实视觉回归 ——
 * `.section-head p` 的 4 处 `margin:5px 0 0` 是 ⑮-3 保留的间距变体（全局基础是 margin:0，
 * 另 6 个文件依赖它）；`@media(max-width:900px)` 的 3 处 `.section-head{display:block}` 是
 * 对全局 flex 容器的响应式覆写（另 7 个文件在小屏下仍应保持 flex）。</p>
 */

const src = resolve(process.cwd(), "src");
const appCss = () => readFileSync(resolve(src, "styles/app.css"), "utf8");
const vue = (rel: string) => readFileSync(resolve(src, rel), "utf8");

/** 只取 <style scoped> 块内容，避免把模板/脚本里的同名字符串算进来 */
function scoped(rel: string): string {
  return [...vue(rel).matchAll(/<style scoped>([\s\S]*?)<\/style>/g)]
    .map((m) => m[1])
    .join("\n");
}

const DELETED_ROW_FILES = [
  "components/model/LogicalModelList.vue",
  "components/model/ModelListPanel.vue",
  "components/model/ModelPoolListPanel.vue"
];

const AUTH_TITLE_FILES = [
  "components/system/auth/AuthorizationPermissionGrantPanel.vue",
  "components/system/auth/AuthorizationResourcePanel.vue",
  "components/system/auth/AuthorizationRolePanel.vue"
];

const SECTION_HEAD_P_FILES = [
  "components/model/LogicalModelFormDrawer.vue",
  "components/model/ModelPoolFormDrawer.vue",
  "components/routing/RoutingRuleFormDrawer.vue",
  "components/routing/RoutingRuleOutagePanel.vue"
];

const SECTION_HEAD_MEDIA_FILES = [
  "components/model/ModelFormDrawer.vue",
  "components/routing/RoutingRuleFormDrawer.vue",
  "components/routing/RoutingRuleOutagePanel.vue"
];

const DELETED_ROW_RULE = ":deep(.el-table__row.row-deleted > td) {";
const AUTH_TITLE_RULE = ".auth-panel__title {";

describe("已收口到全局的两组规则：app.css 唯一定义", () => {
  it("删除态行样式恰有一份，声明体与旧 scoped 版一致", () => {
    const css = appCss();
    expect(css.match(/\.el-table__row\.row-deleted > td \{/g)).toHaveLength(1);
    const body = css.match(/\.el-table__row\.row-deleted > td \{([\s\S]*?)\}/)?.[1] ?? "";
    expect(body).toContain("background: #fef0f0 !important");
    expect(body).toContain("color: #c45656");
  });

  it("授权面板标题恰有一份，声明体与旧 scoped 版一致", () => {
    const css = appCss();
    expect(css.match(/\.auth-panel__title \{/g)).toHaveLength(1);
    const body = css.match(/\.auth-panel__title \{([\s\S]*?)\}/)?.[1] ?? "";
    expect(body).toContain("font-size: var(--el-font-size-base, 13px)");
    expect(body).toContain("color: #303133");
    expect(body).toContain("font-weight: 600");
  });
});

describe("组件内不再重复定义已收口的规则", () => {
  it.each(DELETED_ROW_FILES)("%s 不再定义删除态行样式", (rel) => {
    expect(scoped(rel)).not.toContain(DELETED_ROW_RULE);
  });

  it.each(AUTH_TITLE_FILES)("%s 不再定义授权面板标题", (rel) => {
    expect(scoped(rel)).not.toContain(AUTH_TITLE_RULE);
  });

  it("ModelPoolListPanel 的 .bind-list__text 删除线仍留在局部（局部增强不并入）", () => {
    expect(scoped("components/model/ModelPoolListPanel.vue"))
      .toContain(":deep(.el-table__row.row-deleted > td .bind-list__text)");
  });
});

describe("刻意不合并的两组变体：必须继续留在 scoped", () => {
  it("全局 .section-head p 基础口径仍是 margin: 0（⑮-3）", () => {
    const body = appCss().match(/\.section-head p \{([\s\S]*?)\}/)?.[1] ?? "";
    expect(body).toContain("margin: 0");
  });

  it.each(SECTION_HEAD_P_FILES)("%s 保留 margin:5px 0 0 局部覆写", (rel) => {
    expect(scoped(rel)).toMatch(/\.section-head p \{\s*margin: 5px 0 0;\s*\}/);
  });

  it.each(SECTION_HEAD_MEDIA_FILES)("%s 保留 @media 内的 display:block 覆写", (rel) => {
    const css = scoped(rel);
    expect(css).toContain("@media (max-width: 900px)");
    expect(css).toMatch(/\.section-head \{\s*display: block;\s*\}/);
  });
});

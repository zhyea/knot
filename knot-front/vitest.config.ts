import { defineConfig } from "vitest/config";
import vue from "@vitejs/plugin-vue";
import AutoImport from "unplugin-auto-import/vite";
import Components from "unplugin-vue-components/vite";
import { ElementPlusResolver } from "unplugin-vue-components/resolvers";
import { fileURLToPath, URL } from "node:url";

// knot-front 的测试运行器配置。
//
// 组件挂载测试需要编译 .vue SFC，并解析 SFC 里直接使用的 <el-*> 组件与
// ElMessage 等程序化 API（业务代码靠 unplugin 自动注册，未在 SFC 内显式 import）。
// 因此这里复刻 vite.config.ts 的 vue / AutoImport / Components 三个插件。
//
// 关键差异：**dts 一律 false**。vite.config 里这两个插件会写 components.d.ts /
// auto-imports.d.ts，测试 transform 时若也写会破坏类型声明文件（vite build 侧已有
// emptyOutDir 改写 dts 的已知副作用，测试侧必须彻底不写）。dts:false 不影响运行期
// 注入——自动导入在 transform 阶段生效，声明文件只是给 tsc/编辑器用的。
//
// 需要 DOM 的测试文件用 `// @vitest-environment jsdom` 单文件覆盖（默认仍是 node，
// 纯逻辑测试无需 DOM，跑得更快更稳）。jsdom 缺的浏览器 API 由 ./vitest.setup.ts 补齐。
export default defineConfig({
  plugins: [
    vue(),
    AutoImport({ dts: false, resolvers: [ElementPlusResolver()] }),
    Components({ dts: false, resolvers: [ElementPlusResolver()] })
  ],
  resolve: {
    alias: {
      "@": fileURLToPath(new URL("./src", import.meta.url))
    }
  },
  test: {
    environment: "node",
    include: ["src/**/*.test.ts"],
    globals: false,
    setupFiles: ["./vitest.setup.ts"],
    // element-plus 默认被 externalize，Node 直接加载其 theme-chalk/*.css 会报
    // "Unknown file extension .css"。内联后交给 Vite 处理 CSS 导入即可。
    server: {
      deps: {
        inline: [/element-plus/, /@element-plus\//]
      }
    }
  }
});

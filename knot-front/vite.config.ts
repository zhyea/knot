import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";
import AutoImport from "unplugin-auto-import/vite";
import Components from "unplugin-vue-components/vite";
import { ElementPlusResolver } from "unplugin-vue-components/resolvers";
import { fileURLToPath, URL } from "node:url";

export default defineConfig({
  plugins: [
    vue(),
    AutoImport({
      dts: "auto-imports.d.ts",
      resolvers: [ElementPlusResolver()]
    }),
    Components({
      dts: "components.d.ts",
      resolvers: [ElementPlusResolver()]
    })
  ],
  resolve: {
    alias: {
      "@": fileURLToPath(new URL("./src", import.meta.url))
    }
  },
  server: {
    port: 5173,
    proxy: {
      "/api": {
        target: "http://127.0.0.1:8080",
        changeOrigin: true,
        // 管理端流式测试走 SSE，开发代理必须逐块透传，否则前端看不到增量片段
        configure: (proxy) => {
          proxy.on("proxyRes", (proxyRes) => {
            if (String(proxyRes.headers["content-type"] || "").includes("text/event-stream")) {
              proxyRes.headers["x-accel-buffering"] = "no";
            }
          });
        }
      }
    }
  }
});

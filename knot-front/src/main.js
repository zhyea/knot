import { createApp } from "vue";
import "element-plus/dist/index.css";
import "./styles/themes.css";
import "./styles/app.css";
import App from "./App.vue";
import router from "./router";
import { initTheme } from "./composables/useTheme";

initTheme();
createApp(App).use(router).mount("#app");

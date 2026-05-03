import { createApp } from "vue";
import "element-plus/dist/index.css";
import "./styles/app.css";
import App from "./App.vue";
import router from "./router";

createApp(App).use(router).mount("#app");

import { ref, watch } from "vue";

const STORAGE_KEY = "knot-theme";

/** 所有可用主题 */
export const THEMES = [
  { key: "blue", label: "默认蓝" },
  { key: "green", label: "极客绿" },
  { key: "orange", label: "活力橙" },
  { key: "purple", label: "典雅紫" },
  { key: "red", label: "中国红" }
];

const current = ref(loadStored());

function loadStored() {
  try {
    return localStorage.getItem(STORAGE_KEY) || "blue";
  } catch {
    return "blue";
  }
}

function applyTheme(key) {
  document.documentElement.setAttribute("data-theme", key);
  try {
    localStorage.setItem(STORAGE_KEY, key);
  } catch {
    // ignore
  }
}

/** 初始化主题（应用启动时调用一次） */
export function initTheme() {
  applyTheme(current.value);
}

/** 切换主题 */
export function setTheme(key) {
  current.value = key;
}

/** 监听变化自动应用 */
watch(current, (key) => {
  applyTheme(key);
});

/** 当前主题 key（响应式） */
export function useTheme() {
  return { current, setTheme };
}

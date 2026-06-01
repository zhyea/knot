import { ref, watch } from "vue";
import { getMySettings, saveMySettings } from "../api/userSettings";

const STORAGE_KEY = "knot-theme";
const TOKEN_KEY = "knot_token";
const THEME_SETTING_KEY = "theme";

/** 所有可用主题 */
export const THEMES = [
  { key: "blue", label: "海湾蓝" },
  { key: "green", label: "松针绿" },
  { key: "orange", label: "咖啡橙" },
  { key: "purple", label: "雾紫灰" },
  { key: "red", label: "日出红" },
  { key: "teal", label: "青釉青" },
  { key: "gold", label: "麦穗金" },
  { key: "slate", label: "石墨灰" },
  { key: "mist", label: "青瓷蓝灰" }
];

const current = ref(loadStored());
let remoteLoaded = false;
let remoteLoadingPromise = null;

function loadStored() {
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    return THEMES.some((theme) => theme.key === stored) ? stored : "blue";
  } catch {
    return "blue";
  }
}

function applyTheme(key) {
  const themeKey = THEMES.some((theme) => theme.key === key) ? key : "blue";
  document.documentElement.setAttribute("data-theme", themeKey);
  document.body?.setAttribute("data-theme", themeKey);
  try {
    localStorage.setItem(STORAGE_KEY, themeKey);
  } catch {
    // ignore
  }
}

function hasToken() {
  try {
    return !!localStorage.getItem(TOKEN_KEY);
  } catch {
    return false;
  }
}

/** 初始化主题（应用启动时调用一次） */
export function initTheme() {
  applyTheme(current.value);
}

/** 登录态下从服务端加载主题设置，失败时保留本地主题 */
export async function loadThemePreference() {
  if (!hasToken()) {
    return current.value;
  }
  if (remoteLoadingPromise) {
    return remoteLoadingPromise;
  }
  remoteLoadingPromise = doLoadThemePreference();
  try {
    return await remoteLoadingPromise;
  } finally {
    remoteLoadingPromise = null;
  }
}

async function doLoadThemePreference() {
  try {
    const settings = await getMySettings({ silentError: true, skipIdleTouch: true });
    const remoteTheme = settings?.[THEME_SETTING_KEY];
    remoteLoaded = true;
    if (THEMES.some((theme) => theme.key === remoteTheme)) {
      current.value = remoteTheme;
      applyTheme(remoteTheme);
    }
  } catch {
    // localStorage remains the offline/default preference.
  }
  return current.value;
}

/** 切换主题 */
export async function setTheme(key) {
  const themeKey = THEMES.some((theme) => theme.key === key) ? key : "blue";
  current.value = themeKey;
  applyTheme(themeKey);
  if (hasToken()) {
    try {
      await saveMySettings({ [THEME_SETTING_KEY]: themeKey }, { silentError: true, skipIdleTouch: true });
      remoteLoaded = true;
    } catch {
      // The local theme has already been applied; retry on the next explicit change.
    }
  }
}

/** 监听变化自动应用 */
watch(current, (key) => {
  applyTheme(key);
});

/** 当前主题 key（响应式） */
export function useTheme() {
  if (!remoteLoaded) {
    loadThemePreference();
  }
  return { current, setTheme, loadThemePreference };
}

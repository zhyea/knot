import { ref, watch } from "vue";
import { getMySettings, saveMySettings } from "../api/userSettings";
import { getStorageItem, hasStorageItem, setStorageItem } from "../utils/storage";

const STORAGE_KEY = "knot-theme";
const TOKEN_KEY = "knot_token";
const THEME_SETTING_KEY = "theme";

export const THEMES = [
  { key: "blue", labelKey: "theme.blue" },
  { key: "green", labelKey: "theme.green" },
  { key: "orange", labelKey: "theme.orange" },
  { key: "purple", labelKey: "theme.purple" },
  { key: "red", labelKey: "theme.red" },
  { key: "teal", labelKey: "theme.teal" },
  { key: "gold", labelKey: "theme.gold" },
  { key: "slate", labelKey: "theme.slate" },
  { key: "mist", labelKey: "theme.mist" }
];

const current = ref(loadStored());
let remoteLoaded = false;
let remoteLoadingPromise = null;

function loadStored() {
  const stored = getStorageItem(STORAGE_KEY);
  return THEMES.some((theme) => theme.key === stored) ? stored : "blue";
}

function applyTheme(key) {
  const themeKey = THEMES.some((theme) => theme.key === key) ? key : "blue";
  document.documentElement.setAttribute("data-theme", themeKey);
  document.body?.setAttribute("data-theme", themeKey);
  setStorageItem(STORAGE_KEY, themeKey);
}

function hasToken() {
  return hasStorageItem(TOKEN_KEY);
}

export function initTheme() {
  applyTheme(current.value);
}

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
    // Keep local preference if remote loading fails.
  }
  return current.value;
}

export async function setTheme(key) {
  const themeKey = THEMES.some((theme) => theme.key === key) ? key : "blue";
  current.value = themeKey;
  applyTheme(themeKey);
  if (hasToken()) {
    try {
      await saveMySettings({ [THEME_SETTING_KEY]: themeKey }, { silentError: true, skipIdleTouch: true });
      remoteLoaded = true;
    } catch {
      // Keep local preference if remote saving fails.
    }
  }
}

watch(current, (key) => {
  applyTheme(key);
});

export function useTheme() {
  if (!remoteLoaded) {
    loadThemePreference();
  }
  return { current, setTheme, loadThemePreference };
}

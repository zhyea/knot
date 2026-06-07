import { computed, ref } from "vue";
import zhCn from "element-plus/es/locale/lang/zh-cn";
import zhTw from "element-plus/es/locale/lang/zh-tw";
import en from "element-plus/es/locale/lang/en";
import fr from "element-plus/es/locale/lang/fr";
import { getMySettings, saveMySettings } from "../api/userSettings";
import { DEFAULT_LOCALE, messages } from "../i18n/messages";
import { getStorageItem, hasStorageItem, setStorageItem } from "../utils/storage";

const STORAGE_KEY = "knot-locale";
const TOKEN_KEY = "knot_token";
const LOCALE_SETTING_KEY = "locale";

const elementLocales = {
  "zh-CN": zhCn,
  "zh-TW": zhTw,
  "en-US": en,
  "fr-FR": fr
};

export const LOCALES = [
  { code: "zh-CN", labelKey: "locale.zh-CN" },
  { code: "zh-TW", labelKey: "locale.zh-TW" },
  { code: "en-US", labelKey: "locale.en-US" },
  { code: "fr-FR", labelKey: "locale.fr-FR" }
];

const current = ref(loadStored());
const elementLocale = computed(() => elementLocales[current.value] || elementLocales[DEFAULT_LOCALE]);
let remoteLoaded = false;
let remoteLoadingPromise = null;

function loadStored() {
  const stored = getStorageItem(STORAGE_KEY);
  return messages[stored] ? stored : DEFAULT_LOCALE;
}

function resolveMessage(locale, key) {
  return key.split(".").reduce((result, segment) => result?.[segment], messages[locale]);
}

function interpolate(template, params = {}) {
  if (typeof template !== "string") {
    return template;
  }
  return template.replace(/\{(\w+)\}/g, (_, name) => params[name] ?? "");
}

function applyLocale(locale) {
  const nextLocale = messages[locale] ? locale : DEFAULT_LOCALE;
  current.value = nextLocale;
  setStorageItem(STORAGE_KEY, nextLocale);
  document.documentElement.setAttribute("lang", nextLocale);
}

function hasToken() {
  return hasStorageItem(TOKEN_KEY);
}

export function translate(key, params = {}, locale = current.value) {
  const message = resolveMessage(locale, key) ?? resolveMessage(DEFAULT_LOCALE, key) ?? key;
  return interpolate(message, params);
}

export function initLocale() {
  applyLocale(current.value);
}

export async function loadLocalePreference() {
  if (!hasToken()) {
    return current.value;
  }
  if (remoteLoadingPromise) {
    return remoteLoadingPromise;
  }
  remoteLoadingPromise = doLoadLocalePreference();
  try {
    return await remoteLoadingPromise;
  } finally {
    remoteLoadingPromise = null;
  }
}

async function doLoadLocalePreference() {
  try {
    const settings = await getMySettings({ silentError: true, skipIdleTouch: true });
    const remoteLocale = settings?.[LOCALE_SETTING_KEY];
    remoteLoaded = true;
    if (messages[remoteLocale]) {
      applyLocale(remoteLocale);
    }
  } catch {
    // Keep local preference if remote loading fails.
  }
  return current.value;
}

export async function setLocale(locale) {
  const nextLocale = messages[locale] ? locale : DEFAULT_LOCALE;
  applyLocale(nextLocale);
  if (hasToken()) {
    try {
      await saveMySettings({ [LOCALE_SETTING_KEY]: nextLocale }, { silentError: true, skipIdleTouch: true });
      remoteLoaded = true;
    } catch {
      // Keep local preference if remote saving fails.
    }
  }
}

export function useLocale() {
  if (!remoteLoaded) {
    loadLocalePreference();
  }
  return {
    current,
    locales: LOCALES,
    elementLocale,
    setLocale,
    loadLocalePreference,
    t: (key, params) => translate(key, params)
  };
}

import {computed, ref, type Ref} from "vue";
import zhCn from "element-plus/es/locale/lang/zh-cn";
import zhTw from "element-plus/es/locale/lang/zh-tw";
import en from "element-plus/es/locale/lang/en";
import fr from "element-plus/es/locale/lang/fr";
import {getMySettings, saveMySettings} from "@/api/userSettings";
import {DEFAULT_LOCALE, messages} from "@/i18n/messages";
import {getStorageItem, hasStorageItem, setStorageItem} from "@/utils/storage";
import type {Dict} from "@/types";

export type LocaleCode = keyof typeof messages;

export interface LocaleOption {
  code: LocaleCode;
  labelKey: string;
}

/** `t()` 的插值参数，如 `t("x.hello", { name: "robin" })` */
export type TranslateParams = Record<string, string | number | boolean>;

const STORAGE_KEY = "knot-locale";
const TOKEN_KEY = "knot_token";
const LOCALE_SETTING_KEY = "locale";

const elementLocales = {
  "zh-CN": zhCn,
  "zh-TW": zhTw,
  "en-US": en,
  "fr-FR": fr
};

export const LOCALES: LocaleOption[] = [
  {code: "zh-CN", labelKey: "locale.zh-CN"},
  {code: "zh-TW", labelKey: "locale.zh-TW"},
  {code: "en-US", labelKey: "locale.en-US"},
  {code: "fr-FR", labelKey: "locale.fr-FR"}
];

const current: Ref<LocaleCode> = ref(loadStored()) as Ref<LocaleCode>;
const elementLocale = computed(() => elementLocales[current.value] || elementLocales[DEFAULT_LOCALE]);
let remoteLoaded = false;
let remoteLoadingPromise: Promise<LocaleCode> | null = null;

function loadStored(): LocaleCode {
  const stored = getStorageItem(STORAGE_KEY);
  return messages[stored as LocaleCode] ? (stored as LocaleCode) : DEFAULT_LOCALE;
}

function resolveMessage(locale: LocaleCode, key: string): unknown {
  return key.split(".").reduce<Dict | undefined>(
    (result, segment) => result?.[segment],
    messages[locale] as Dict
  );
}

function interpolate(template: unknown, params: TranslateParams = {}): string {
  if (typeof template !== "string") {
    return String(template);
  }
  return template.replace(/\{(\w+)}/g, (_, name) => String(params[name] ?? ""));
}

function applyLocale(locale: LocaleCode): void {
  const nextLocale = messages[locale] ? locale : DEFAULT_LOCALE;
  current.value = nextLocale;
  setStorageItem(STORAGE_KEY, nextLocale);
  document.documentElement.setAttribute("lang", nextLocale);
}

function hasToken() {
  return hasStorageItem(TOKEN_KEY);
}

export function translate(
  key: string,
  params: TranslateParams = {},
  locale: LocaleCode = current.value
): string {
  const message =
    (resolveMessage(locale, key) as string) ?? (resolveMessage(DEFAULT_LOCALE, key) as string) ?? key;
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

async function doLoadLocalePreference(): Promise<LocaleCode> {
  try {
    const settings = await getMySettings({silentError: true, skipIdleTouch: true});
    const remoteLocale = settings?.[LOCALE_SETTING_KEY];
    remoteLoaded = true;
    if (messages[remoteLocale as LocaleCode]) {
      applyLocale(remoteLocale as LocaleCode);
    }
  } catch {
    // Keep local preference if remote loading fails.
  }
  return current.value;
}

export async function setLocale(locale: LocaleCode): Promise<void> {
  const nextLocale = messages[locale] ? locale : DEFAULT_LOCALE;
  applyLocale(nextLocale);
  if (hasToken()) {
    try {
      await saveMySettings({[LOCALE_SETTING_KEY]: nextLocale}, {silentError: true, skipIdleTouch: true});
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
    t: (key: string, params?: TranslateParams): string => translate(key, params)
  };
}

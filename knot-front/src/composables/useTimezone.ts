import {computed, ref, type Ref} from "vue";
import {getMySettings, saveMySettings} from "@/api/userSettings";
import {resolveEnumLabel, useEnums} from "@/composables/useEnums";
import {getStorageItem, hasStorageItem, setStorageItem} from "@/utils/storage";

/**
 * 用户时区偏好
 *
 * 取值统一用 UTC 偏移（`UTC+08:00` 这类 code），选项由 DB 枚举表维护：
 * `ks_enum_configs` 分类 `timezone`，经 `GET /api/system/enums/items/timezone` 读取
 * （`useEnums` + `EnumSelect` 同一数据源，与代码枚举 `/api/common/enums` 无关）。
 * 选择结果以 `setting_key='timezone'` 落 `ks_user_settings`，与 locale / theme 同机制。
 *
 * 该偏好当前仅用于展示与存储，不参与业务逻辑分支。
 */
const STORAGE_KEY = "knot-timezone";
const TOKEN_KEY = "knot_token";
export const TIMEZONE_SETTING_KEY = "timezone";
export const TIMEZONE_ENUM_CATEGORY = "timezone";
/** 未设置时的默认时区（UTC 偏移 code） */
export const DEFAULT_TIMEZONE = "UTC+08:00";

const current: Ref<string> = ref(loadStored());
let remoteLoaded = false;
let remoteLoadingPromise: Promise<string> | null = null;

function loadStored(): string {
  return getStorageItem(STORAGE_KEY) || DEFAULT_TIMEZONE;
}

function hasToken() {
  return hasStorageItem(TOKEN_KEY);
}

// 选项共享缓存（useEnums 内部按分类缓存，这里同一 ref 供页面复用）
const {options, loading: optionsLoading, loadOptions} = useEnums(TIMEZONE_ENUM_CATEGORY);

/** 当前时区的展示文案（命中枚举则取 item_label，否则回显 code） */
export const currentTimezoneLabel = computed(() => resolveEnumLabel(options.value, current.value, current.value));

export async function loadTimezonePreference(): Promise<string> {
  if (!hasToken()) {
    return current.value;
  }
  if (remoteLoadingPromise) {
    return remoteLoadingPromise;
  }
  remoteLoadingPromise = doLoadTimezonePreference();
  try {
    return await remoteLoadingPromise;
  } finally {
    remoteLoadingPromise = null;
  }
}

async function doLoadTimezonePreference(): Promise<string> {
  try {
    const settings = await getMySettings({silentError: true, skipIdleTouch: true});
    const remoteTimezone = settings?.[TIMEZONE_SETTING_KEY];
    remoteLoaded = true;
    if (typeof remoteTimezone === "string" && remoteTimezone) {
      current.value = remoteTimezone;
      setStorageItem(STORAGE_KEY, remoteTimezone);
    }
  } catch {
    // Keep local preference if remote loading fails.
  }
  return current.value;
}

export async function setTimezone(timezone: string): Promise<void> {
  if (!timezone) {
    return;
  }
  current.value = timezone;
  setStorageItem(STORAGE_KEY, timezone);
  if (hasToken()) {
    try {
      await saveMySettings({[TIMEZONE_SETTING_KEY]: timezone}, {silentError: true, skipIdleTouch: true});
      remoteLoaded = true;
    } catch {
      // Keep local preference if remote saving fails.
    }
  }
}

export function useTimezone() {
  if (!remoteLoaded) {
    loadTimezonePreference();
  }
  return {
    current,
    label: currentTimezoneLabel,
    options,
    loading: optionsLoading,
    loadOptions,
    setTimezone,
    loadTimezonePreference
  };
}

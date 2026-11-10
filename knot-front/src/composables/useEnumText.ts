import {useEnumOptions} from "@/composables/useEnumOptions";
import {EMPTY_PLACEHOLDER} from "@/utils/format";

export interface EnumTextOptions {
  /** 枚举未命中时的兜底文案（按 code 计算）；缺省由 labelOf 回显 code */
  fallback?: (code: unknown) => string;
  /** 声明后先对空值短路；不声明则交给 labelOf 处理。⚠ 不做 truthy 判断，0 是合法值 */
  emptyText?: string;
}

/**
 * 「code -> 枚举 label」展示函数的唯一工厂。
 *
 * <p>此前 11 处组件各写一份 `function modeLabel(code) { return enumLabelOf(KEY, code, ...); }`，
 * 差异只在枚举键与兜底文案。抽成工厂后仍由 `useEnumOptions()` 触发 /api/enum-options 加载
 * —— 直接用模块级 `labelOf` 会让缓存未预热时状态列退化成落库码。</p>
 *
 * <p>只收口展示，不碰提交与编排；与 `useEnums`（DB 枚举）是两套来源，不混用。</p>
 */
export function useEnumText(enumName: string, options: EnumTextOptions = {}) {
  const {labelOf} = useEnumOptions();

  return (code: unknown): string => {
    if (options.emptyText !== undefined && (code === null || code === undefined || code === "")) {
      return options.emptyText;
    }
    return labelOf(enumName, code, options.fallback ? options.fallback(code) : undefined);
  };
}

/** 模型类型展示：空值走全站唯一占位符，未命中回显 code */
export function useModelTypeText() {
  return useEnumText("ModelTypeEnum", {
    emptyText: EMPTY_PLACEHOLDER,
    fallback: (code) => String(code)
  });
}

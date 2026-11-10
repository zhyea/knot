import {resolveEnumLabel} from "@/composables/useEnums";
import {useModelTypeText} from "@/composables/useEnumText";
import {EMPTY_PLACEHOLDER} from "@/utils/format";
import type {Row} from "@/types";

export interface LogicalModelPresentationOptions {
  /** DB 枚举 useEnums 的 options 是 Row[]（itemCode / itemLabel），不是 SelectOption[] */
  familyOptions: () => Row[];
}

/**
 * 统一模型表格视图与卡片视图共用的展示口径。
 *
 * <p>模型类型走代码枚举 `ModelTypeEnum`（{@link useModelTypeText} 是全站唯一出口，
 * 内部会经 `useEnumOptions()` 触发 /api/enum-options 加载）；模型族走 DB 枚举
 * `useEnums("model_family")`。两套来源不能混用（见 useEnumOptions.ts:23）。</p>
 *
 * <p>此前模型类型由父组件把 `optionsOf("ModelTypeEnum")` 当 props 逐层传下来，
 * 与 `useModelTypeText` 是同一事实的两套实现。2026-11-10 去掉 props 透传后，
 * 只保留 DB 枚举这一路外部数据。</p>
 */
export function useLogicalModelPresentation(options: LogicalModelPresentationOptions) {
  const modelTypeLabel = useModelTypeText();

  function familyLabel(code: unknown): string {
    if (!code) return EMPTY_PLACEHOLDER;
    return resolveEnumLabel(options.familyOptions(), code, String(code));
  }

  return {modelTypeLabel, familyLabel};
}

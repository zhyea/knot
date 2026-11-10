import {resolveEnumLabel} from "@/composables/useEnums";
import {EMPTY_PLACEHOLDER} from "@/utils/format";
import type {Row, SelectOption} from "@/types";

export interface LogicalModelPresentationOptions {
  /** getter 而非数组快照：父组件换 options / 枚举异步回填后，展示要能跟上 */
  modelTypeOptions: () => SelectOption[];
  /** DB 枚举 useEnums 的 options 是 Row[]（itemCode / itemLabel），不是 SelectOption[] */
  familyOptions: () => Row[];
}

/**
 * 统一模型表格视图与卡片视图共用的展示口径。
 *
 * <p>模型类型来自父组件传入的 options（代码枚举 ModelTypeEnum），模型族来自 DB 枚举
 * `useEnums("model_family")` —— 两套来源不能混用（见 useEnumOptions.ts:23）。</p>
 */
export function useLogicalModelPresentation(options: LogicalModelPresentationOptions) {
  function modelTypeLabel(code: unknown): string {
    if (!code) return EMPTY_PLACEHOLDER;
    const item = options.modelTypeOptions().find((option) => option.value === code);
    return item?.label || String(code);
  }

  function familyLabel(code: unknown): string {
    if (!code) return EMPTY_PLACEHOLDER;
    return resolveEnumLabel(options.familyOptions(), code, String(code));
  }

  return {modelTypeLabel, familyLabel};
}

import {AsyncTaskStatus} from "@/constants/status";
import {useEnumOptions} from "@/composables/useEnumOptions";

export type AsyncTaskTagType = "success" | "warning" | "danger" | "info";

/**
 * 异步任务的展示口径：列表与详情抽屉共用一份，避免状态色与 label 各写一遍。
 *
 * <p>为什么是 composable 而不是纯 utils：`useEnumOptions()` 负责触发
 * /api/enum-options 加载，模块级 `labelOf` 只读缓存。若把 label 抽成不带 hook 的
 * 纯函数，调用方容易忘记触发加载，状态列会退化成落库码。</p>
 */
export function useAsyncTaskPresentation() {
  const {labelOf} = useEnumOptions();

  /** label 走后端枚举，不在前端造第二份词表（见 constants/status.ts 注释） */
  function statusLabel(status: unknown): string {
    return labelOf("AsyncTaskStatusEnum", status);
  }

  /**
   * 状态 → 标签色。用 `===` 与常量比较：status 是数字，0 是合法值，
   * 任何 truthy 判断都会把 PENDING 当成「无状态」。
   */
  function statusTagType(status: unknown): AsyncTaskTagType {
    if (status === AsyncTaskStatus.SUCCEEDED) {
      return "success";
    }
    if (status === AsyncTaskStatus.POLLING) {
      return "warning";
    }
    if (status === AsyncTaskStatus.FAILED) {
      return "danger";
    }
    return "info";
  }

  return {statusLabel, statusTagType};
}

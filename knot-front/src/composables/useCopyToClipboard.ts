import {ElMessage} from "element-plus";

export interface CopyFeedbackOptions {
  /** 成功提示文案，默认「已复制」；API Key 等场景需要更具体的提示 */
  successMessage?: string;
}

/**
 * 复制文本到剪贴板，统一成功/失败反馈。
 * - text 为空时：若 caller 提供非空 emptyMessage 则警告，否则静默返回 false（保留各组件原有差异）。
 * - 成功文案在 hook 层配置，避免为一次文案差异给 `copy()` 增加第三个位置参数。
 */
export function useCopyToClipboard(options: CopyFeedbackOptions = {}) {
  const successMessage = options.successMessage || "已复制";

  async function copy(text: string | null | undefined, emptyMessage?: string): Promise<boolean> {
    if (!text) {
      if (emptyMessage) {
        ElMessage.warning(emptyMessage);
      }
      return false;
    }
    try {
      await navigator.clipboard.writeText(text);
      ElMessage.success(successMessage);
      return true;
    } catch {
      ElMessage.error("复制失败");
      return false;
    }
  }

  return {copy};
}

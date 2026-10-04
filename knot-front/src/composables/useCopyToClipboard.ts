import {ElMessage} from "element-plus";

/**
 * 复制文本到剪贴板，统一成功/失败反馈。
 * - text 为空时：若 caller 提供非空 emptyMessage 则警告，否则静默返回 false（保留各组件原有差异）。
 */
export function useCopyToClipboard() {
  async function copy(text: string, emptyMessage?: string): Promise<boolean> {
    if (!text) {
      if (emptyMessage) {
        ElMessage.warning(emptyMessage);
      }
      return false;
    }
    try {
      await navigator.clipboard.writeText(text);
      ElMessage.success("已复制");
      return true;
    } catch {
      ElMessage.error("复制失败");
      return false;
    }
  }

  return {copy};
}

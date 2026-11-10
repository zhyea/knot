import {ElMessage} from "element-plus";
import {applyCombosChange} from "@/utils/billingResolution";
import type {CombosFormSlice, ResolutionComboCode} from "@/utils/billingResolution";

/**
 * 视频档位组合轴变更的提示口径：视频模式配置与维度分档方案组件此前各写一份。
 *
 * <p>只收口提示，组合清理仍走 `applyCombosChange` 唯一出口。</p>
 */
export function useResolutionComboNotice(form: CombosFormSlice) {
  function onCombosChange(codes: ResolutionComboCode[]) {
    const {clearedAudioSurcharge} = applyCombosChange(form, codes);
    if (clearedAudioSurcharge) {
      ElMessage.warning("已清除「音频直出附加费」：有声已按单价计费，同一项不重复收");
    }
  }

  return {onCombosChange};
}

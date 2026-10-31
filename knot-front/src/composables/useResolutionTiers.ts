import {computed} from "vue";
import {useEnumOptions} from "@/composables/useEnumOptions";

/**
 * 视频/图片分辨率档位的**建议列表**，唯一来源是后端 {@code ResolutionTierEnum}
 * （经 GET /api/enum-options 下发）。
 *
 * <p>⭐ 前端不再自己维护「常用档位」数组：此前 utils/billingResolution 里有一份
 * {@code ["480P","540P","720P","768P","1080P","2K","4K"]}，与后端枚举逐字相同——
 * 两处各写一遍，加一档就要改两个仓库文件，漏改则下拉里选不到、而计费侧认得。</p>
 *
 * <p>⚠⚠ <b>这只是建议，不是合法值白名单</b>（robin 拍板「目录 + 建议，不限制输入」）。
 * 厂商档位客观不可枚举——MiniMax 写 768P、Vidu 写 540P、还有各种自造名。所以用它的
 * 控件必须是 {@code filterable + allow-create}，用户填枚举外的档位同样要能保存。</p>
 */
const ENUM_NAME = "ResolutionTierEnum";

export function useResolutionTiers() {
  const {optionsOf} = useEnumOptions();

  /** 档位名列表（按后端枚举声明顺序；枚举未加载完时为空数组，加载完自动响应） */
  const tierOptions = computed<string[]>(() =>
    optionsOf(ENUM_NAME).map((item) => String(item.value))
  );

  return {tierOptions};
}

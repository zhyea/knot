package org.chobit.knot.gateway.service;

import org.chobit.knot.gateway.entity.EnumConfigEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * 将外部模型（OpenRouter 等）的模型标识归一到合法的 {@code model_family} 枚举码。
 *
 * <p>统一模型的 {@code model_family} 字段必须取 {@code ks_enum_configs(category='model_family')}
 * 的 {@code item_code}（如 gpt / claude / deepseek），才能与计费规则按族匹配。
 * 外部目录里只有「供应商前缀 + 模型名」（如 openai/gpt-4o），没有族码，因此这里对模型名/
 * 模型段做枚举码最长子串匹配；未知供应商返回 null，交由用户在编辑表单补填。</p>
 */
@Component
public class ModelFamilyResolver {

    private static final String CATEGORY = "model_family";

    private final EnumConfigService enumConfigService;

    public ModelFamilyResolver(EnumConfigService enumConfigService) {
        this.enumConfigService = enumConfigService;
    }

    /**
     * 解析模型族枚举码；无匹配时返回 null。
     */
    public String resolve(String modelId, String modelName) {
        return matchFamily(modelId, modelName, loadCodes());
    }

    /**
     * 纯函数：在给定枚举码列表中，对「模型段（去掉供应商前缀）+ 模型名」做最长子串匹配。
     * 优先匹配更长的码，避免短码误命中；无匹配返回 null。
     */
    public static String matchFamily(String modelId, String modelName, List<String> familyCodes) {
        if (familyCodes == null || familyCodes.isEmpty()) {
            return null;
        }
        String modelPart = modelId != null && modelId.indexOf('/') > 0
                ? modelId.substring(modelId.indexOf('/') + 1)
                : modelId;
        String haystack = ((modelPart == null ? "" : modelPart) + " "
                + (modelName == null ? "" : modelName)).toLowerCase(Locale.ROOT);
        String best = null;
        String bestCode = null;
        for (String code : familyCodes) {
            String c = code.toLowerCase(Locale.ROOT);
            if (haystack.contains(c) && (best == null || c.length() > best.length())) {
                best = c;
                bestCode = code;
            }
        }
        return bestCode;
    }

    private List<String> loadCodes() {
        try {
            return enumConfigService.listByCategory(CATEGORY).stream()
                    .map(EnumConfigEntity::getItemCode)
                    .filter(c -> c != null && !c.isBlank())
                    .toList();
        } catch (Exception ignored) {
            return List.of();
        }
    }
}

package org.chobit.knot.gateway.constants.enums;

import org.chobit.knot.gateway.error.BusinessException;
import org.chobit.knot.gateway.error.ErrorCode;

import java.util.Arrays;

/**
 * 模型池选择策略：已从 ks_enum_configs（model_pool_selection_strategy 字典）迁出，
 * 通过 /api/common/enums 以 ModelPoolSelectionStrategyEnum 键下发前端。
 * 三种策略在模型池选模处均有实际分支，见 ModelPoolSelection。
 */
public enum ModelPoolSelectionStrategyEnum implements EnumOption {
    WEIGHTED("WEIGHTED", "权重"),
    PRIORITY("PRIORITY", "优先级"),
    RANDOM("RANDOM", "随机");

    private final String code;
    private final String label;

    ModelPoolSelectionStrategyEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    @Override
    public String code() {
        return code;
    }

    /** 前端展示名（原 ks_enum_configs model_pool_selection_strategy 字典标签） */
    @Override
    public String label() {
        return label;
    }

    /**
     * Builds the target value from the source input. Executes the public operation.
     */
    public static ModelPoolSelectionStrategyEnum fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String normalized = code.trim().toUpperCase();
        return Arrays.stream(values())
                .filter(item -> item.code.equals(normalized))
                .findFirst()
                .orElse(null);
    }

    /**
     * 解析策略，未知或空白回退到 WEIGHTED；仅用于存量数据兼容读取，写入口必须用 requireCode。
     */
    public static ModelPoolSelectionStrategyEnum fromCodeOrDefault(String code) {
        ModelPoolSelectionStrategyEnum strategy = fromCode(code);
        return strategy == null ? WEIGHTED : strategy;
    }

    /**
     * 校验策略编码并返回规范大写形式，非法值直接拒绝。
     */
    public static String requireCode(String code, String errorMessage) {
        ModelPoolSelectionStrategyEnum strategy = fromCode(code);
        if (strategy == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, errorMessage);
        }
        return strategy.code();
    }
}

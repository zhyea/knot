package org.chobit.knot.gateway.constants.enums;

import org.chobit.knot.gateway.error.BusinessException;
import org.chobit.knot.gateway.error.ErrorCode;

import java.util.Arrays;

/**
 * 统一模型可见性：已从 ks_enum_configs（logical_model_visibility 字典）迁出，
 * 通过 /api/common/enums 以 LogicalModelVisibilityEnum 键下发前端。
 */
public enum LogicalModelVisibilityEnum implements EnumOption {
    PUBLIC("PUBLIC", "公开"),
    INTERNAL("INTERNAL", "内部"),
    PRIVATE("PRIVATE", "私有");

    private final String code;
    private final String label;

    LogicalModelVisibilityEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    @Override
    public String code() {
        return code;
    }

    /** 前端展示名（原 ks_enum_configs logical_model_visibility 字典标签） */
    @Override
    public String label() {
        return label;
    }

    /** 新建统一模型时的默认可见性 */
    public static LogicalModelVisibilityEnum defaultVisibility() {
        return PUBLIC;
    }

    /**
     * Builds the target value from the source input. Executes the public operation.
     */
    public static LogicalModelVisibilityEnum fromCode(String code) {
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
     * 校验可见性编码并返回规范大写形式，非法值直接拒绝。
     */
    public static String requireCode(String code, String errorMessage) {
        LogicalModelVisibilityEnum visibility = fromCode(code);
        if (visibility == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, errorMessage);
        }
        return visibility.code();
    }
}

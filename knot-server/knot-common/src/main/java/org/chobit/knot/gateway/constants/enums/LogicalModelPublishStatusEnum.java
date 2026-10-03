package org.chobit.knot.gateway.constants.enums;

import org.chobit.knot.gateway.error.BusinessException;
import org.chobit.knot.gateway.error.ErrorCode;

import java.util.Arrays;

/**
 * 统一模型发布状态：已从 ks_enum_configs（logical_model_publish_status 字典）迁出，
 * 通过 /api/common/enums 以 LogicalModelPublishStatusEnum 键下发前端。
 *
 * <p>发布状态只描述发布生命周期，不控制可用性：可用性由 enabled 决定，
 * 二者互不影响，避免「已发布但被停用」这类状态互相污染。</p>
 */
public enum LogicalModelPublishStatusEnum implements EnumOption {
    DRAFT("DRAFT", "草稿"),
    PUBLISHED("PUBLISHED", "已发布"),
    ARCHIVED("ARCHIVED", "已下架");

    private final String code;
    private final String label;

    LogicalModelPublishStatusEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    @Override
    public String code() {
        return code;
    }

    /** 前端展示名（原 ks_enum_configs logical_model_publish_status 字典标签） */
    @Override
    public String label() {
        return label;
    }

    /** 新建统一模型时的默认发布状态 */
    public static LogicalModelPublishStatusEnum defaultStatus() {
        return DRAFT;
    }

    /**
     * Builds the target value from the source input. Executes the public operation.
     */
    public static LogicalModelPublishStatusEnum fromCode(String code) {
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
     * 校验发布状态编码并返回规范大写形式，非法值直接拒绝。
     */
    public static String requireCode(String code, String errorMessage) {
        LogicalModelPublishStatusEnum status = fromCode(code);
        if (status == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, errorMessage);
        }
        return status.code();
    }
}

package org.chobit.knot.gateway.constants.enums;

import org.chobit.knot.gateway.error.BusinessException;
import org.chobit.knot.gateway.error.ErrorCode;

import java.util.List;

/**
 * 统一模型发布状态：已从 ks_enum_configs（logical_model_publish_status 字典）迁出，
 * 通过 /api/common/enums 以 LogicalModelPublishStatusEnum 键下发前端。
 *
 * <p>发布状态只描述发布生命周期，不控制可用性：可用性由 enabled 决定，
 * 二者互不影响，避免「已发布但被停用」这类状态互相污染。</p>
 */
public enum LogicalModelPublishStatusEnum implements NumericEnumOption {
    DRAFT(1, "草稿"),
    PUBLISHED(2, "已发布"),
    ARCHIVED(3, "已下架");

    private final int code;
    private final String label;

    LogicalModelPublishStatusEnum(int code, String label) {
        this.code = code;
        this.label = label;
    }

    @Override
    public int code() {
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

    public static LogicalModelPublishStatusEnum fromCode(Integer code) {
        return NumericEnumOption.fromCode(values(), code);
    }

    /** 全部状态 code，供查询下拉使用 */
    public static List<Integer> codes() {
        return NumericEnumOption.codes(values());
    }

    /**
     * 校验发布状态编码并返回其数字值，非法值直接拒绝。
     */
    public static int requireCode(Integer code, String errorMessage) {
        LogicalModelPublishStatusEnum status = fromCode(code);
        if (status == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, errorMessage);
        }
        return status.code();
    }
}

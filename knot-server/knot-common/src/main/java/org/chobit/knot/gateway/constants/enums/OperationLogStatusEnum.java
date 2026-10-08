package org.chobit.knot.gateway.constants.enums;

import org.chobit.knot.gateway.error.BusinessException;
import org.chobit.knot.gateway.error.ErrorCode;

import java.util.List;

/**
 * 操作日志执行结果：从 ks_enum_configs 的通用 status 字典中拆分出来的领域枚举，
 * 通过 /api/common/enums 以 OperationLogStatusEnum 键下发前端。
 *
 * <p>执行结果是运行时状态，与启用/停用的实体生命周期是不同领域的状态，不能混用。</p>
 */
public enum OperationLogStatusEnum implements NumericEnumOption {
    SUCCESS(1, "成功"),
    FAILURE(2, "失败");

    private final int code;
    private final String label;

    OperationLogStatusEnum(int code, String label) {
        this.code = code;
        this.label = label;
    }

    @Override
    public int code() {
        return code;
    }

    /** 前端展示名（原 ks_enum_configs status 字典标签） */
    @Override
    public String label() {
        return label;
    }

    public static OperationLogStatusEnum fromCode(Integer code) {
        return NumericEnumOption.fromCode(values(), code);
    }

    /** 全部状态 code，供查询下拉使用 */
    public static List<Integer> codes() {
        return NumericEnumOption.codes(values());
    }

    /**
     * 校验状态编码并返回其数字值，非法值直接拒绝。
     */
    public static int requireCode(Integer code, String errorMessage) {
        OperationLogStatusEnum status = fromCode(code);
        if (status == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, errorMessage);
        }
        return status.code();
    }
}

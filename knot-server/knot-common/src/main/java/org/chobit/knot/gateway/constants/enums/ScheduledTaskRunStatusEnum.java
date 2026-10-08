package org.chobit.knot.gateway.constants.enums;

import org.chobit.knot.gateway.error.BusinessException;
import org.chobit.knot.gateway.error.ErrorCode;

import java.util.List;

/**
 * 定时任务运行状态：从 ks_enum_configs 的通用 status 字典中拆分出来的领域枚举，
 * 通过 /api/common/enums 以 ScheduledTaskRunStatusEnum 键下发前端。
 *
 * <p>RUNNING 是一次执行中的瞬时状态，SUCCESS / FAILURE 是终态。</p>
 */
public enum ScheduledTaskRunStatusEnum implements NumericEnumOption {
    RUNNING(1, "运行中"),
    SUCCESS(2, "成功"),
    FAILURE(3, "失败");

    private final int code;
    private final String label;

    ScheduledTaskRunStatusEnum(int code, String label) {
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

    public static ScheduledTaskRunStatusEnum fromCode(Integer code) {
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
        ScheduledTaskRunStatusEnum status = fromCode(code);
        if (status == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, errorMessage);
        }
        return status.code();
    }
}

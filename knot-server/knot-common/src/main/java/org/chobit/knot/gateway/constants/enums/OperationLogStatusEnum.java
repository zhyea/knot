package org.chobit.knot.gateway.constants.enums;

import org.chobit.knot.gateway.error.BusinessException;
import org.chobit.knot.gateway.error.ErrorCode;

import java.util.Arrays;
import java.util.List;

/**
 * 操作日志执行结果：从 ks_enum_configs 的通用 status 字典中拆分出来的领域枚举，
 * 通过 /api/common/enums 以 OperationLogStatusEnum 键下发前端。
 *
 * <p>与 {@link EntityStatusEnum}（启用/停用等实体生命周期）是不同领域的状态，不能混用。</p>
 */
public enum OperationLogStatusEnum implements EnumOption {
    SUCCESS("SUCCESS", "成功"),
    FAILURE("FAILURE", "失败");

    private final String code;
    private final String label;

    OperationLogStatusEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    @Override
    public String code() {
        return code;
    }

    /** 前端展示名（原 ks_enum_configs status 字典标签） */
    @Override
    public String label() {
        return label;
    }

    /**
     * Builds the target value from the source input. Executes the public operation.
     */
    public static OperationLogStatusEnum fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String normalized = code.trim().toUpperCase();
        return Arrays.stream(values())
                .filter(item -> item.code.equals(normalized))
                .findFirst()
                .orElse(null);
    }

    /** 全部状态 code，供查询下拉使用 */
    public static List<String> codes() {
        return Arrays.stream(values()).map(OperationLogStatusEnum::code).toList();
    }

    /**
     * 校验状态编码并返回规范大写形式，非法值直接拒绝。
     */
    public static String requireCode(String code, String errorMessage) {
        OperationLogStatusEnum status = fromCode(code);
        if (status == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, errorMessage);
        }
        return status.code();
    }
}

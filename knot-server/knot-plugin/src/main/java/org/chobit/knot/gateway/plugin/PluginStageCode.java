package org.chobit.knot.gateway.plugin;

import org.chobit.knot.gateway.constants.enums.EnumOption;
import org.chobit.knot.gateway.error.BusinessException;
import org.chobit.knot.gateway.error.ErrorCode;

/**
 * 插件执行阶段：已从 ks_enum_configs（plugin_stage_code 字典）迁出，
 * 通过 /api/common/enums 以 PluginStageCode 键下发前端。
 */
public enum PluginStageCode implements EnumOption {
    GATEWAY_REQUEST("GATEWAY_REQUEST", "网关请求阶段"),
    GATEWAY_RESPONSE("GATEWAY_RESPONSE", "网关响应阶段"),
    GATEWAY_ERROR("GATEWAY_ERROR", "网关异常阶段"),
    UPSTREAM_REQUEST("UPSTREAM_REQUEST", "上游请求阶段"),
    UPSTREAM_RESPONSE("UPSTREAM_RESPONSE", "上游响应阶段"),
    UPSTREAM_ERROR("UPSTREAM_ERROR", "上游异常阶段");

    private final String code;
    private final String label;

    PluginStageCode(String code, String label) {
        this.code = code;
        this.label = label;
    }

    @Override
    public String code() {
        return code;
    }

    /** 前端展示名（原 ks_enum_configs plugin_stage_code 字典标签） */
    @Override
    public String label() {
        return label;
    }

    /**
     * Builds the target value from the source input. Executes the public operation.
     */
    public static PluginStageCode fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String normalized = code.trim();
        for (PluginStageCode value : values()) {
            if (value.code.equalsIgnoreCase(normalized)) {
                return value;
            }
        }
        return null;
    }

    /**
     * 校验执行阶段编码并返回规范大写形式，非法值直接拒绝。
     */
    public static String requireCode(String code, String errorMessage) {
        PluginStageCode stage = fromCode(code);
        if (stage == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, errorMessage);
        }
        return stage.code();
    }
}

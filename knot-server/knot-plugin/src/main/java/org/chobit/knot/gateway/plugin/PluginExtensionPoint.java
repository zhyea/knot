package org.chobit.knot.gateway.plugin;

import org.chobit.knot.gateway.constants.enums.EnumOption;
import org.chobit.knot.gateway.error.BusinessException;
import org.chobit.knot.gateway.error.ErrorCode;

import java.util.Arrays;

/**
 * 插件扩展点：已从 ks_enum_configs（plugin_extension_point 字典）迁出，
 * 通过 /api/common/enums 以 PluginExtensionPoint 键下发前端。
 * 扩展点 code 与枚举名一致，落库与分发都使用该 code。
 */
public enum PluginExtensionPoint implements EnumOption {
    GATEWAY_EXCHANGE("GATEWAY_EXCHANGE", "网关请求处理链路"),
    UPSTREAM_EXCHANGE("UPSTREAM_EXCHANGE", "上游请求处理链路");

    private final String code;
    private final String label;

    PluginExtensionPoint(String code, String label) {
        this.code = code;
        this.label = label;
    }

    @Override
    public String code() {
        return code;
    }

    /** 前端展示名（原 ks_enum_configs plugin_extension_point 字典标签） */
    @Override
    public String label() {
        return label;
    }

    /**
     * Builds the target value from the source input. Executes the public operation.
     */
    public static PluginExtensionPoint fromCode(String code) {
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
     * 校验扩展点编码并返回规范大写形式，非法值直接拒绝。
     */
    public static String requireCode(String code, String errorMessage) {
        PluginExtensionPoint point = fromCode(code);
        if (point == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, errorMessage);
        }
        return point.code();
    }
}

package org.chobit.knot.gateway.plugin;

import org.chobit.knot.gateway.constants.enums.EnumOption;
import org.chobit.knot.gateway.error.BusinessException;
import org.chobit.knot.gateway.error.ErrorCode;

import java.util.Arrays;

/**
 * 插件作用范围：已从 ks_enum_configs（plugin_scope_type 字典）迁出，
 * 通过 /api/common/enums 以 PluginScopeType 键下发前端。
 * 作用范围决定插件绑定在哪一类对象上，code 与枚举名一致。
 */
public enum PluginScopeType implements EnumOption {
    GLOBAL("GLOBAL", "全局"),
    APP("APP", "应用"),
    RULE("RULE", "路由规则"),
    PROVIDER("PROVIDER", "供应商账户"),
    MODEL("MODEL", "模型"),
    POOL("POOL", "模型池");

    private final String code;
    private final String label;

    PluginScopeType(String code, String label) {
        this.code = code;
        this.label = label;
    }

    @Override
    public String code() {
        return code;
    }

    /** 前端展示名（原 ks_enum_configs plugin_scope_type 字典标签） */
    @Override
    public String label() {
        return label;
    }

    /**
     * Builds the target value from the source input. Executes the public operation.
     */
    public static PluginScopeType fromCode(String code) {
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
     * 校验作用范围编码并返回规范大写形式，非法值直接拒绝。
     */
    public static String requireCode(String code, String errorMessage) {
        PluginScopeType scope = fromCode(code);
        if (scope == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, errorMessage);
        }
        return scope.code();
    }
}

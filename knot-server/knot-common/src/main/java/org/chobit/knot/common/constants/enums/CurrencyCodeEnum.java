package org.chobit.knot.common.constants.enums;

import org.chobit.knot.common.error.BusinessException;
import org.chobit.knot.common.error.ErrorCode;

import java.util.Arrays;

/**
 * 计费币种：已从 ks_enum_configs（billing_currency 字典）迁出，
 * 通过 /api/enum-options 以 CurrencyCodeEnum 键下发前端。
 */
public enum CurrencyCodeEnum implements EnumOption {
    USD("USD", "USD"),
    CNY("CNY", "CNY");

    private final String code;
    private final String label;

    CurrencyCodeEnum(String code, String label) {
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

    /** 前端展示名（原 ks_enum_configs billing_currency 字典标签） */
    @Override
    public String label() {
        return label;
    }

    /** 默认币种：未显式指定时使用 */
    public static CurrencyCodeEnum defaultCurrency() {
        return USD;
    }

    /**
     * Builds the target value from the source input. Executes the public operation.
     */
    public static CurrencyCodeEnum fromCode(String code) {
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
     * 校验币种编码并返回规范大写形式，非法值直接拒绝。
     */
    public static String requireCode(String code, String errorMessage) {
        CurrencyCodeEnum currency = fromCode(code);
        if (currency == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, errorMessage);
        }
        return currency.code();
    }
}

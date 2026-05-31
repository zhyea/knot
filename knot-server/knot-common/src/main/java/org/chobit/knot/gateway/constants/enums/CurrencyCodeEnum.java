package org.chobit.knot.gateway.constants.enums;

public enum CurrencyCodeEnum {
    USD("USD");

    private final String code;

    CurrencyCodeEnum(String code) {
        this.code = code;
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    public String code() {
        return code;
    }
}

package org.chobit.knot.gateway.constants.enums;

public enum BillingItemTypeEnum {
    INPUT_TOKEN("INPUT_TOKEN");

    private final String code;

    BillingItemTypeEnum(String code) {
        this.code = code;
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    public String code() {
        return code;
    }
}

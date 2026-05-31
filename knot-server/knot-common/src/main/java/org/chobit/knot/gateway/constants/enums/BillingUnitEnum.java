package org.chobit.knot.gateway.constants.enums;

import java.util.Arrays;

public enum BillingUnitEnum {
    ONE_K_TOKENS("1K_TOKENS"),
    ONE_M_TOKENS("1M_TOKENS"),
    PER_TOKEN("PER_TOKEN"),
    PER_REQUEST("PER_REQUEST"),
    PER_IMAGE("PER_IMAGE"),
    PER_MINUTE("PER_MINUTE"),
    PER_SECOND("PER_SECOND"),
    CUSTOM("CUSTOM");

    private final String code;

    BillingUnitEnum(String code) {
        this.code = code;
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    public String code() {
        return code;
    }

    /**
     * Builds the target value from the source input. Executes the public operation.
     */
    public static BillingUnitEnum fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String normalized = code.trim().toUpperCase();
        return Arrays.stream(values())
                .filter(item -> item.code.equals(normalized))
                .findFirst()
                .orElse(null);
    }
}

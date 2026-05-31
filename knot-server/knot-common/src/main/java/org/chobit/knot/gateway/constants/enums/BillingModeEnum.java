package org.chobit.knot.gateway.constants.enums;

import java.util.Arrays;

public enum BillingModeEnum {
    TOKEN("TOKEN"),
    REQUEST("REQUEST"),
    IMAGE("IMAGE"),
    AUDIO("AUDIO"),
    VIDEO("VIDEO"),
    EMBEDDING("EMBEDDING"),
    TIERED("TIERED"),
    FREE("FREE"),
    CUSTOM("CUSTOM");

    private final String code;

    BillingModeEnum(String code) {
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
    public static BillingModeEnum fromCode(String code) {
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

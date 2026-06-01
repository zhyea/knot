package org.chobit.knot.gateway.constants.enums;

import java.util.Arrays;

public enum ModelPoolSelectionStrategyEnum {
    WEIGHTED("WEIGHTED"),
    PRIORITY("PRIORITY"),
    RANDOM("RANDOM");

    private final String code;

    ModelPoolSelectionStrategyEnum(String code) {
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
    public static ModelPoolSelectionStrategyEnum fromCodeOrDefault(String code) {
        if (code == null || code.isBlank()) {
            return WEIGHTED;
        }
        String normalized = code.trim().toUpperCase();
        return Arrays.stream(values())
                .filter(item -> item.code.equals(normalized))
                .findFirst()
                .orElse(WEIGHTED);
    }
}

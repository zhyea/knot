package org.chobit.knot.gateway.constants.enums;

public enum ModelTypeEnum {
    CHAT("CHAT"),
    EMBEDDING("EMBEDDING"),
    IMAGE("IMAGE"),
    AUDIO("AUDIO"),
    VIDEO("VIDEO"),
    TEXT("TEXT"),
    REASONING("REASONING");

    private final String code;

    ModelTypeEnum(String code) {
        this.code = code;
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    public String code() {
        return code;
    }
}

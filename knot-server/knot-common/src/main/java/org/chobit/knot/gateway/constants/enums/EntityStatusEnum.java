package org.chobit.knot.gateway.constants.enums;

public enum EntityStatusEnum {
    ENABLED("ENABLED"),
    DISABLED("DISABLED"),
    ACTIVE("ACTIVE"),
    INACTIVE("INACTIVE");

    private final String code;

    EntityStatusEnum(String code) {
        this.code = code;
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    public String code() {
        return code;
    }
}

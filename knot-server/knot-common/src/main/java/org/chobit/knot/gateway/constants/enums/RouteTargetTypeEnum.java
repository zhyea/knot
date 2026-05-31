package org.chobit.knot.gateway.constants.enums;

public enum RouteTargetTypeEnum {
    MODEL("MODEL"),
    MODEL_POOL("MODEL_POOL");

    private final String code;

    RouteTargetTypeEnum(String code) {
        this.code = code;
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    public String code() {
        return code;
    }
}

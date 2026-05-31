package org.chobit.knot.gateway.constants.enums;

public enum TrafficResourceTypeEnum {
    APP("APP"),
    MODEL("MODEL"),
    PROVIDER("PROVIDER"),
    ROUTING_RULE("ROUTING_RULE"),
    ROUTING_CONSUMER("ROUTING_CONSUMER");

    private final String code;

    TrafficResourceTypeEnum(String code) {
        this.code = code;
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    public String code() {
        return code;
    }
}

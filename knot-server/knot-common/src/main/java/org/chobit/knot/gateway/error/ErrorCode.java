package org.chobit.knot.gateway.error;

public enum ErrorCode {
    NOT_FOUND("GW-SYSTEM-404", "resource not found"),
    VALIDATION_ERROR("GW-SYSTEM-400", "validation failed"),
    CONFLICT("GW-SYSTEM-409", "resource conflict"),
    UNAUTHORIZED("GW-SYSTEM-401", "unauthorized");

    private final String code;
    private final String message;

    ErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String code() {
        return code;
    }

    public String message() {
        return message;
    }
}

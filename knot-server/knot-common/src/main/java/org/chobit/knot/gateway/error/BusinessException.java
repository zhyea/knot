package org.chobit.knot.gateway.error;

public class BusinessException extends RuntimeException {
    private final String code;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.message());
        this.code = errorCode.code();
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.code();
    }

    public String getCode() {
        return code;
    }
}

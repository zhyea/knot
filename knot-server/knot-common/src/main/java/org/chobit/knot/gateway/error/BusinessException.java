package org.chobit.knot.gateway.error;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {
    private final String code;

    /**
     * Constructs a new instance.
     */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.message());
        this.code = errorCode.code();
    }

    /**
     * Constructs a new instance.
     */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.code();
    }
}

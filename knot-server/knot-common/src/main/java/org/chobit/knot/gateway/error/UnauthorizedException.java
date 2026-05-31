package org.chobit.knot.gateway.error;

/** 鏈櫥褰曟垨 token 鏃犳晥鏃舵姏鍑猴紝鍏ㄥ眬寮傚父澶勭悊鍣ㄥ簲杩斿洖 HTTP 401銆?*/
public class UnauthorizedException extends BusinessException {

    /**
     * Constructs a new instance.
     */
    public UnauthorizedException(String message) {
        super(ErrorCode.UNAUTHORIZED, message);
    }
}

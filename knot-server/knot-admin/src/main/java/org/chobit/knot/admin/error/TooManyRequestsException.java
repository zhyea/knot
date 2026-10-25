package org.chobit.knot.admin.error;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 请求频率超限（防爆破 / 防刷）时抛出，由全局异常处理器统一返回 HTTP 429。
 */
@ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
public class TooManyRequestsException extends BusinessException {

    /**
     * Constructs a new instance.
     */
    public TooManyRequestsException(String message) {
        super(ErrorCode.TOO_MANY_REQUESTS, message);
    }
}

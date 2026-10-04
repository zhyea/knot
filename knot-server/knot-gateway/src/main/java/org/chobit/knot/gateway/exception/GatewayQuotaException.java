package org.chobit.knot.gateway.exception;

import org.chobit.knot.gateway.constants.enums.GatewayErrorTypeEnum;

/**
 * 额度耗尽：日 / 月请求数或累计 token 达到策略上限。
 */
public class GatewayQuotaException extends GatewayRequestException {

    /**
     * Constructs a quota exceeded error.
     */
    public GatewayQuotaException(String message, String code) {
        super(message, GatewayErrorTypeEnum.QUOTA_ERROR.code(), code);
    }
}

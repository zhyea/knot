package org.chobit.knot.plugin.moderation;

import org.chobit.knot.common.constants.enums.GatewayErrorTypeEnum;
import org.chobit.knot.common.error.GatewayBadRequestException;

/**
 * 敏感词命中拒绝信号：由 {@link ModerationGuard} 在同步插件内联抛出，打断链路。
 *
 * 继承 {@link GatewayBadRequestException}，由网关 {@code GatewayExceptionHandler} 转 HTTP 400
 * （{@code errorType=MODERATION_ERROR}）。对外只带分类 code，不返回命中词与词库原文。
 */
public class ModerationRejectException extends GatewayBadRequestException {

    private final String categoryCode;

    public ModerationRejectException(String message, String categoryCode) {
        super(message, GatewayErrorTypeEnum.MODERATION_ERROR.code(), categoryCode);
        this.categoryCode = categoryCode;
    }

    /**
     * 命中的敏感词分类 code（唯一对外暴露的命中信息）。
     */
    public String categoryCode() {
        return categoryCode;
    }
}

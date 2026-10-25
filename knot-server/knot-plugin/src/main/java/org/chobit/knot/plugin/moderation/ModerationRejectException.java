package org.chobit.knot.plugin.moderation;

import org.chobit.knot.gateway.constants.enums.GatewayErrorTypeEnum;
import org.chobit.knot.gateway.error.GatewayBadRequestException;

/**
 * 敏感词命中拒绝信号：由 {@link ModerationGuard} 在同步插件内联抛出，打断链路。
 *
 * <p>继承 common 的 400 父异常 {@link GatewayBadRequestException}：插件无法反向依赖网关异常家族，
 * 改为抛这个跨模块父异常，由网关 {@code GatewayExceptionHandler} 捕获后统一转 HTTP 400
 * （{@code errorType=MODERATION_ERROR}）。对外只带分类 code，不返回命中词与词库原文（防词库探测）。</p>
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

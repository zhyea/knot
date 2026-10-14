package org.chobit.knot.gateway.plugin.moderation;

/**
 * 敏感词命中拒绝信号：由 {@link ModerationGuard} 在同步插件内联抛出，打断链路。
 *
 * <p>类型定义在 knot-plugin 而非 knot-gateway：插件实现类在本模块，无法反向依赖网关的
 * {@code GatewayRequestException} 家族；由网关侧的 {@code GatewayExceptionHandler} 捕获后
 * 转成 HTTP 400 并补上 {@code GatewayErrorTypeEnum.MODERATION_ERROR} 的 type，
 * 从而<b>不必把整套网关异常体系下沉</b>。</p>
 *
 * <p>对外只带分类 code：不返回命中词与词库原文（防词库探测）。</p>
 */
public class ModerationRejectException extends RuntimeException {

    private final String categoryCode;

    /**
     * Constructs a moderation reject exception.
     */
    public ModerationRejectException(String message, String categoryCode) {
        super(message);
        this.categoryCode = categoryCode;
    }

    /**
     * 命中的敏感词分类 code（唯一对外暴露的命中信息）。
     */
    public String categoryCode() {
        return categoryCode;
    }
}

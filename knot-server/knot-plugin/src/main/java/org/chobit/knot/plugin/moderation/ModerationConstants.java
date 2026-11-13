package org.chobit.knot.plugin.moderation;

/**
 * 敏感词审核的扫描常量：只装 {@link ModerationGuard} 扫描逻辑用到的阈值；
 * 词库时序参数与编译缓存行为分别内联在 {@code ModerationWordResource} 与 {@link ModerationWordCache}。
 */
public final class ModerationConstants {

    /**
     * 单段文本参与检测的长度上限（字符数）：超长文本直接跳过，不参与检测。
     */
    public static final int MAX_TEXT_LENGTH = 65536;

    /**
     * 触发处置动作的最低严重度：1 表示全部命中均触发动作；需要「只拦高危」时改本常量（不走配置）。
     */
    public static final int SEVERITY_THRESHOLD = 1;

    private ModerationConstants() {
    }
}

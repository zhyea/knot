package org.chobit.knot.gateway.plugin.moderation;

/**
 * 敏感词审核的<b>扫描</b>常量：只装 {@link ModerationGuard} 扫描逻辑用到的阈值，随守卫留在 knot-plugin。
 *
 * <p>词库缓存的时序参数不在此——刷新间隔在 {@link PluginProperties.Moderation#getRefreshInterval()}，
 * 编译缓存的其余行为随 {@link ModerationWordCache} 内联（常量分散到具体类）。</p>
 */
public final class ModerationConstants {

    /**
     * 单段文本参与检测的长度上限（字符数）：超长文本直接跳过，避免扫描耗时失控。
     */
    public static final int MAX_TEXT_LENGTH = 65536;

    /**
     * 触发处置动作的最低严重度：1 表示全部命中均触发动作。
     *
     * <p>需要"只拦高危"的灰度时改这个常量（不走配置）。</p>
     */
    public static final int SEVERITY_THRESHOLD = 1;

    private ModerationConstants() {
    }
}

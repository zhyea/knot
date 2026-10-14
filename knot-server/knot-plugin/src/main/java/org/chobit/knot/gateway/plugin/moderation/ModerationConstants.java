package org.chobit.knot.gateway.plugin.moderation;

/**
 * 敏感词审核常量：按设计文档 §5.2「配置最小化」口径，只有 enabled/action/scope 三项进配置，
 * 其余维度一律走代码常量，避免"配置即冗余"。
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

    /**
     * 词库缓存 TTL：与 {@code GatewayDataService} 的 10 分钟口径一致。
     *
     * <p>轮询版本号是主失效路径（见 {@link ModerationSnapshotCache}），TTL 只是兜底。</p>
     */
    public static final java.time.Duration WORD_CACHE_TTL = java.time.Duration.ofMinutes(10);

    /**
     * 版本号轮询间隔：命中请求路径上最多每这么久查一次 {@code MAX(updated_at)}。
     *
     * <p>轮询而非广播的理由：admin 与 gateway 是两个进程，缓存各自独立；
     * 版本号即失效机制（设计文档 §6）天然多节点一致，且不需要网关暴露管理端点、
     * 不需要 Redis。代价是词库变更最迟这么久生效。</p>
     */
    public static final long VERSION_CHECK_INTERVAL_MILLIS = 5_000L;

    private ModerationConstants() {
    }
}

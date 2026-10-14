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
     * 不需要 Redis。</p>
     *
     * <p>本间隔只是「最新行编辑」这类版本号敏感改动的<b>快速路径</b>；
     * 删除/停用<b>非最新行</b>时该行退出过滤集、{@code MAX(updated_at)} 不变 → 版本号不变 →
     * 这条路径无法感知（版本信号结构性盲区，2026-10-14 复盘）。盲区的确定性修复由
     * {@link #SCHEDULED_REFRESH_INTERVAL} 定时直读词表覆盖。</p>
     */
    public static final long VERSION_CHECK_INTERVAL_MILLIS = 5_000L;

    /**
     * 定时刷新间隔：绕过 {@code MAX(updated_at)} 版本盲区的确定性兜底。
     *
     * <p>版本号轮询只对「最大 updated_at 那一行」的改动敏感；删除/停用非最新行时 max 不变、
     * 版本号不变、定时刷新外的路径不失效，最长要等 {@link #WORD_CACHE_TTL}。定时刷新任务每这么久
     * 直接调 {@code loadTerms()} 直读当前真实词表（不依赖版本号比对），覆盖<b>所有</b>写类型
     * （新增/编辑/删除/停用/启用），是盲区修复的主路径（robin 拍板，2026-10-14）。</p>
     *
     * <p>默认值 10s；经 {@code knot.moderation.refresh-interval} 可全局覆盖。</p>
     */
    public static final java.time.Duration SCHEDULED_REFRESH_INTERVAL = java.time.Duration.ofSeconds(10);

    /**
     * 快照 {@code refreshAfterWrite}：访问到期后的异步重载，定时刷新之外的第二道兜底。
     *
     * <p>与 {@link #SCHEDULED_REFRESH_INTERVAL} 叠加：定时刷新保证最坏 ~10s 生效，
     * 即便调度线程异常，单条请求在访问到期（1min）后也会触发一次异步重载，避免旧词库用一辈子。</p>
     */
    public static final java.time.Duration REFRESH_AFTER_WRITE = java.time.Duration.ofMinutes(1);

    private ModerationConstants() {
    }
}

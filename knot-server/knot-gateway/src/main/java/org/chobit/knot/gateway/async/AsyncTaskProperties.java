package org.chobit.knot.gateway.async;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 异步任务轮询配置（{@code knot.gateway.async-task.*}）。
 *
 * <p>⚠ 这里只放「扫描节拍」级别的旋钮：<b>单个任务多久查一次由行上的 {@code next_poll_at} 决定</b>，
 * 而后者来自档案的 {@code poll_interval_ms} 与退避参数。于是调轮询节奏不需要重启，
 * 不同任务也能有不同的退避节奏。</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "knot.gateway.async-task")
public class AsyncTaskProperties {

    /** 总开关；关闭时不扫描（已提交的任务保留在表里，开启后继续） */
    private boolean enabled = true;

    /**
     * 扫描节拍（cron，默认每分钟整点一次）：只决定「多久扫一次表」，
     * <b>单个任务多久查一次由行上的 {@code next_poll_at} 决定</b>。
     *
     * <p>⚠ cron 不像 {@code fixedDelay} 那样「上一轮跑完再计时」，同一方法可能被并发触发；
     * 轮询器内部用一次性守卫跳过重叠的那一拍（见 {@code AsyncTaskPoller#tick}）。</p>
     *
     * <p>⭐ <b>节拍是一道下限，不是装饰</b>：档案里写 {@code poll_interval_ms = 5000} 的任务，
     * 实际最早也要等到下一个整分才会被查。默认 1 分钟意味着——</p>
     * <ul>
     *   <li>单次查询间隔 ≥ 60s（原来的秒级节拍下是 5s），三段式视频通常 2~4 次查询就够，
     *       上游调用量按分钟级任务的量级算完全不需要更密；</li>
     *   <li>吞吐上限 = {@code batch-size} / 分钟（默认 100/min ≈ 1.7/s），不再是 100/s；</li>
     *   <li>{@code max_attempts}（默认 360）在这个节拍下几乎永不生效，
     *       实际由档案的 {@code timeout_ms} 先到——两者「先到先生效」的规则没变，只是常数换了。</li>
     * </ul>
     *
     * <p>要回密直接改 cron（例如 {@code *&#47;10 * * * * *}），不必改代码。
     * 但注意单拍是全等的：{@code blockLast} 会等这一批（{@code concurrency} 路并发）跑完才进下一拍，
     * 所以节拍只是「多久起一次批」，真正的吞吐由 {@code batch-size} × {@code concurrency} 决定。</p>
     */
    private String tickCron = "0 * * * * *";

    /** 单批抢占上限：控制一次 tick 的吞吐 */
    private int batchSize = 100;

    /**
     * 单批内并发处理上限。
     *
     * <p>并发是「同时发多少个上游查询」，不是「同时占多少线程」：任务一律提交到
     * {@code boundedElastic}，池满时排队，不会为此额外起线程。</p>
     */
    private int concurrency = 8;

    /**
     * 单次上游查询的超时（毫秒）。
     *
     * <p>⭐ <b>必须显著小于 {@link #claimTtlMs}</b>，否则占位会在查询还没回来时过期，
     * 别的节点把同一行重新领走：同一个 {@code task_query_seq} 被发两次（数仓按
     * {@code (task_code, task_phase, task_query_seq)} 去重，会把两次真实的上游调用压成一条），
     * {@code attempts} 也会被两个节点各加一次导致退避跳档。
     * 启动期由 {@code AsyncTaskPoller#init} 校验，不满足直接拒绝启动——这种误配的表现是
     * 「偶发少算上游调用数」，事后按日志几乎查不出来，只能靠启动即炸。</p>
     *
     * <p>为什么不复用 {@code knot.gateway.upstream.response-timeout}（默认 120s）：
     * 那是给生成型请求的（等模型吐字），而查一次任务状态是个亚秒级 GET。
     * 复用的直接后果就是超时（120s）比占位（60s）还长，<b>默认配置本身就是错的</b>。</p>
     */
    private long queryTimeoutMs = 30_000L;

    /**
     * 占位有效期（毫秒）：节点宕机后多久可被其它节点回收。
     *
     * <p>⭐ 它是<b>一次处理的时间预算上限</b>，必须覆盖
     * 「查询（{@link #queryTimeoutMs}）+ 视频转储 + 落终态（JDBC + 发事件）」。
     * 默认 180s = 查询 30s + 转储与收尾 150s。压得太小会让健康节点在别人还没处理完时
     * 把同一行抢走，代价是重复的上游查询与重复的外部调用（结果仍由终态 CAS 保证唯一，
     * 但上游成本与事件口径会失真）。</p>
     *
     * <p>压得太大的代价是「节点崩溃后孤儿行的回收变慢」——对分钟级的视频任务无所谓，
     * 所以宁可留足余量。</p>
     */
    private long claimTtlMs = 180_000L;

    /** 单批超时回收上限 */
    private int expireBatchSize = 200;

    /** 终态行保留天数，超过由归档任务清理 */
    private int retentionDays = 30;

    /**
     * 归档扫描节拍（cron，默认每小时整点）：终态行清理是后台家务，不需要与轮询同频。
     *
     * <p>同样有重叠守卫——表大时单批删除可能超过一拍。</p>
     */
    private String cleanupCron = "0 0 * * * *";

    /** 单批清理上限：大表删除分批，避免长事务 */
    private int cleanupBatchSize = 500;

    /** 节点标识（占位号前缀，便于排查是谁抢到了任务）；为空时取本机主机名 */
    private String nodeId;
}

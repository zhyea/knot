package org.chobit.knot.gateway.async;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 异步任务轮询配置（{@code knot.gateway.async-task.*}），只放「扫描节拍」级别的旋钮。
 * 单个任务多久查一次由行上的 {@code next_poll_at} 决定，后者来自档案的 {@code poll_interval_ms} 与退避参数。
 * 字段默认值统一取自 {@link AsyncTaskDefaults}（档案级与扫描级默认值的唯一出处），本类不写裸字面量。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "knot.gateway.async-task")
public class AsyncTaskProperties {

    /** 总开关；关闭时不扫描（已提交的任务保留在表里，开启后继续） */
    private boolean enabled = AsyncTaskDefaults.ENABLED;

    /**
     * 扫描节拍（cron，默认每分钟整点一次）：只决定「多久扫一次表」；单个任务多久查一次由行上的 {@code next_poll_at} 决定。
     * cron 可能被并发触发，轮询器用一次性守卫跳过重叠拍（见 {@code AsyncTaskPoller#tick}）；节拍是查询间隔的下限，单次间隔 ≥ 60s。
     * 吞吐上限 = {@code batch-size} / 分钟（默认 100/min ≈ 1.7/s）；{@code max_attempts}（默认 360）实际由档案 {@code timeout_minutes} 先到。
     * 单拍全等：{@code blockLast} 等本批（{@code concurrency} 路并发）跑完才进下一拍，吞吐由 {@code batch-size} × {@code concurrency} 决定；要回密直接改 cron。
     */
    private String tickCron = AsyncTaskDefaults.TICK_CRON;

    /** 单批抢占上限：控制一次 tick 的吞吐 */
    private int batchSize = AsyncTaskDefaults.BATCH_SIZE;

    /**
     * 单批内并发处理上限：并发指「同时发多少个上游查询」，不是「同时占多少线程」。
     * 任务一律提交到 {@code boundedElastic}，池满时排队。
     */
    private int concurrency = AsyncTaskDefaults.CONCURRENCY;

    /**
     * 单次上游查询的超时（毫秒），必须显著小于 {@link #claimTtlMs}；启动期由 {@code AsyncTaskPoller#init} 校验，不满足直接拒绝启动。
     * 不复用 {@code knot.gateway.upstream.response-timeout}（默认 120s，面向生成型请求）。
     */
    private long queryTimeoutMs = AsyncTaskDefaults.QUERY_TIMEOUT_MS;

    /**
     * 占位有效期（毫秒）：节点宕机后多久可被其它节点回收。
     * 一次处理的时间预算上限，必须覆盖「查询（{@link #queryTimeoutMs}）+ 视频转储 + 落终态（JDBC + 发事件）」；默认 180s = 查询 30s + 转储与收尾 150s。
     */
    private long claimTtlMs = AsyncTaskDefaults.CLAIM_TTL_MS;

    /** 单批超时回收上限 */
    private int expireBatchSize = AsyncTaskDefaults.EXPIRE_BATCH_SIZE;

    /** 终态行保留天数，超过由归档任务清理 */
    private int retentionDays = AsyncTaskDefaults.RETENTION_DAYS;

    /**
     * 归档扫描节拍（cron，默认每小时整点）：终态行清理是后台家务，与轮询不同频。
     * 同样有重叠守卫。
     */
    private String cleanupCron = AsyncTaskDefaults.CLEANUP_CRON;

    /** 单批清理上限：大表删除分批，避免长事务 */
    private int cleanupBatchSize = AsyncTaskDefaults.CLEANUP_BATCH_SIZE;

    // ───────────────────────── 交付级：回调怎么送 ─────────────────────────

    /**
     * 回调总开关，与 {@link #enabled} 分开。
     * 关掉只是不投递：已武装的待回调行仍留在表里（{@code delivery_status=1}），重新打开后继续投递。
     */
    private boolean deliveryEnabled = AsyncTaskDefaults.DELIVERY_ENABLED;

    /**
     * 交付扫描节拍（cron，默认每分钟整点）。
     * 与 {@link #tickCron} 同形：cron 只决定「多久扫一次待投递表」，单条任务的下一次投递时刻由行上的 {@code next_delivery_at}（退避）决定。
     */
    private String deliveryCron = AsyncTaskDefaults.DELIVERY_CRON;

    /** 单批抢占待投递上限 */
    private int deliveryBatchSize = AsyncTaskDefaults.DELIVERY_BATCH_SIZE;

    /** 交付最大尝试次数（含首次）；到上限落 {@code delivery_status=FAILED}（不代表任务失败） */
    private int deliveryMaxAttempts = AsyncTaskDefaults.DELIVERY_MAX_ATTEMPTS;

    /** 交付退避基数（毫秒） */
    private int deliveryBackoffBaseMs = AsyncTaskDefaults.DELIVERY_BACKOFF_BASE_MS;

    /** 交付退避封顶（毫秒） */
    private int deliveryBackoffMaxMs = AsyncTaskDefaults.DELIVERY_BACKOFF_MAX_MS;

    /** 单次回调 HTTP 超时（毫秒）。⚠ 必须严格小于 {@link #deliveryClaimTtlMs} */
    private long deliveryTimeoutMs = AsyncTaskDefaults.DELIVERY_TIMEOUT_MS;

    /**
     * 交付占位有效期（毫秒）：必须严格大于 {@link #deliveryTimeoutMs}。
     */
    private long deliveryClaimTtlMs = AsyncTaskDefaults.DELIVERY_CLAIM_TTL_MS;

    /**
     * 回调地址主机白名单（后缀匹配，空 = 不限制主机）。
     * 无论白名单是否为空，回环 / 私网 / 链路本地 IP 一律拦截（SSRF 守卫）。
     */
    private List<String> callbackAllowedHosts = List.of();

    /** 节点标识（占位号前缀，便于排查是谁抢到了任务）；为空时取本机主机名 */
    private String nodeId;
}

package org.chobit.knot.gateway.async;

/**
 * 异步任务的默认值（唯一出处）：档案级（单个任务怎么查）与扫描级（多久扫一次表）两层。
 * {@link #CLAIM_TTL_MS}（180000 毫秒）与 {@link #TASK_TIMEOUT_MINUTES}（30 分钟）单位不同；同名常量按语义拆开，禁止按数值合并。
 * {@link #TICK_CRON} / {@link #CLEANUP_CRON} 在 {@link AsyncTaskScheduledPoller} 的 {@code @Scheduled} 占位符中另有一份拷贝。
 * 改档案级默认值需同步迁移脚本（DDL 列默认值）。
 */
public final class AsyncTaskDefaults {

    private AsyncTaskDefaults() {
    }

    // ───────────────────────── 档案级：单个任务怎么查 ─────────────────────────

    /** 档案没配 {@code query_method} 时的查询方法 */
    static final String QUERY_METHOD = "GET";

    /** 档案没配 {@code poll_interval_ms}：首次轮询的基准间隔（毫秒） */
    static final int POLL_INTERVAL_MS = 5_000;

    /** 档案没配 {@code poll_backoff_base_ms}：退避基数（毫秒），与 {@code BackoffCalculator} 同口径 */
    static final int POLL_BACKOFF_BASE_MS = 1_000;

    /** 档案没配 {@code poll_backoff_max_ms}：退避**封顶**（毫秒）。⚠ 与 {@link #QUERY_TIMEOUT_MS} 同值不同义 */
    static final int POLL_BACKOFF_MAX_MS = 30_000;

    /** 档案没配 {@code max_attempts}：轮询次数上限（在分钟级节拍下通常先被 {@link #TASK_TIMEOUT_MINUTES} 兜住） */
    static final int MAX_ATTEMPTS = 360;

    /** 档案没配 {@code timeout_minutes}：knot 侧任务寿命（分钟），到期转 {@code EXPIRED} */
    static final long TASK_TIMEOUT_MINUTES = 30L;

    // ───────────────────────── 扫描级：多久扫一次表 ─────────────────────────

    /** 总开关默认开；关掉只是不扫描，已提交的任务留在表里等开启 */
    public static final boolean ENABLED = true;

    /** 轮询扫描节拍（cron）：每分钟整点一次。⚠ 与 {@code @Scheduled} 里的兜底字面量是两份，见类注释 */
    public static final String TICK_CRON = "0 * * * * *";

    /** 单批抢占上限：控制一次 tick 的吞吐 */
    public static final int BATCH_SIZE = 100;

    /** 单批内并发上游查询数（并发是「同时发几个查询」，不是占多少线程） */
    public static final int CONCURRENCY = 8;

    /** 单次查询上游的超时（毫秒）。⚠ 与 {@link #POLL_BACKOFF_MAX_MS} 同值不同义 */
    public static final long QUERY_TIMEOUT_MS = 30_000L;

    /** 占位有效期（毫秒）：一轮处理的时间预算上限，必须严格大于 {@link #QUERY_TIMEOUT_MS} */
    public static final long CLAIM_TTL_MS = 180_000L;

    /** 单批超时回收上限 */
    public static final int EXPIRE_BATCH_SIZE = 200;

    /** 终态行保留天数，超过由归档任务清理 */
    public static final int RETENTION_DAYS = 30;

    /** 归档扫描节拍（cron）：每小时整点。⚠ 与 {@code @Scheduled} 里的兜底字面量是两份 */
    public static final String CLEANUP_CRON = "0 0 * * * *";

    /** 单批清理上限：大表删除分批，避免长事务 */
    public static final int CLEANUP_BATCH_SIZE = 500;

    // ───────────────────────── 交付级：回调怎么送 ─────────────────────────

    /** 回调总开关；关掉只是不投递，已武装的待回调行留在表里等开启 */
    public static final boolean DELIVERY_ENABLED = true;

    /** 交付扫描节拍（cron）：每分钟整点。⚠ 与 {@code @Scheduled} 里的兜底字面量是两份 */
    public static final String DELIVERY_CRON = "0 * * * * *";

    /** 单批抢占待投递上限 */
    public static final int DELIVERY_BATCH_SIZE = 100;

    /**
     * 交付最大尝试次数（含首次，3 次）。到上限后落 {@code delivery_status=FAILED}，
     * 但不代表任务失败，客户仍可用任务句柄轮询到真实结果。
     */
    public static final int DELIVERY_MAX_ATTEMPTS = 3;

    /** 交付退避基数（毫秒）：与轮询同公式 {@code min(cap, base × 2^(n-1))} */
    public static final int DELIVERY_BACKOFF_BASE_MS = 5_000;

    /** 交付退避封顶（毫秒）。⚠ 与 {@link #DELIVERY_TIMEOUT_MS} 同数量级但不同义 */
    public static final int DELIVERY_BACKOFF_MAX_MS = 300_000;

    /** 单次回调 HTTP 超时（毫秒）；必须显著小于 {@link #DELIVERY_CLAIM_TTL_MS} */
    public static final long DELIVERY_TIMEOUT_MS = 10_000L;

    /** 交付占位有效期（毫秒）：必须严格大于 {@link #DELIVERY_TIMEOUT_MS} */
    public static final long DELIVERY_CLAIM_TTL_MS = 60_000L;
}

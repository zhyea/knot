package org.chobit.knot.gateway.async;

/**
 * 异步任务的默认值（唯一出处）：档案级（单个任务怎么查）与扫描级（多久扫一次表）两层。
 *
 * <p><b>为什么要抽出来</b>：这些数原本散在 {@link AsyncTaskSpec#of} 的兜底实参与
 * {@link AsyncTaskProperties} 的字段初始化里，读代码时得在两类之间来回跳；更要紧的是<b>改错没有编译期信号</b>
 * ——例如把退避上限调大、忘了档案侧还有一份，同一个任务在不同节点会按不同节奏查。</p>
 *
 * <p>⭐⭐ <b>同名常量必须按「一个事实」拆开，禁止按数值合并</b>。这里有两处数值巧合，都是陷阱：</p>
 * <ul>
 *   <li>{@link #POLL_BACKOFF_MAX_MS} 与 {@link #QUERY_TIMEOUT_MS} 都是 30000——
 *       一个是「两次查询间隔的封顶」，一个是「一次 HTTP 查询等多久」。合并后调退避上限会顺带改掉查询超时，
 *       而查询超时又牵连占位预算链（{@code claim-ttl-ms}），表现是「偶发少算上游调用数」。</li>
 *   <li>{@link #CLAIM_TTL_MS}（180 000，3 分钟）与 {@link #TASK_TIMEOUT_MS}（1 800 000，30 分钟）——
 *       少写一个零就差十倍，而两者都叫「超时」。前者是节点宕机后的占位回收窗口，后者是 knot 侧任务寿命。</li>
 * </ul>
 *
 * <p>⚠ <b>cron 的两处写法合并不了</b>：{@link AsyncTaskPoller} 的 {@code @Scheduled} 为了支持 yml 覆盖，
 * 写的是 {@code ${key:默认值}} 占位符，而占位符里只能是字面量。于是 {@link #TICK_CRON} /
 * {@link #CLEANUP_CRON} 与本类之间天然存在第二份拷贝，由 {@code AsyncTaskPropertiesTest} 比对守门。</p>
 *
 * <p>⚠ <b>还有第三份在 DDL 里</b>：{@code kb_async_task_profiles} 的列默认值
 * （{@code poll_interval_ms DEFAULT 5000} 等）。档案行取到的是列值，本类的兜底只在列值缺失/非正时才生效，
 * 所以<b>改这里的档案级默认值要同步迁移脚本</b>，否则「代码说 5s、库里存 5s、新档案却按别的数落库」。</p>
 *
 * <p>⚠ 测试里<b>故意</b>再写一遍字面量（见 {@code AsyncTaskSpecTest} / {@code AsyncTaskPropertiesTest}）：
 * 那是探针，不是重复。若测试改成引用本类常量，改常量就不会有任何一处变红，等于把护栏拆了。</p>
 */
final class AsyncTaskDefaults {

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

    /** 档案没配 {@code max_attempts}：轮询次数上限（在分钟级节拍下通常先被 {@link #TASK_TIMEOUT_MS} 兜住） */
    static final int MAX_ATTEMPTS = 360;

    /** 档案没配 {@code timeout_ms}：knot 侧任务寿命（毫秒，30 分钟），到期转 {@code EXPIRED} */
    static final long TASK_TIMEOUT_MS = 1_800_000L;

    // ───────────────────────── 扫描级：多久扫一次表 ─────────────────────────

    /** 总开关默认开；关掉只是不扫描，已提交的任务留在表里等开启 */
    static final boolean ENABLED = true;

    /** 轮询扫描节拍（cron）：每分钟整点一次。⚠ 与 {@code @Scheduled} 里的兜底字面量是两份，见类注释 */
    static final String TICK_CRON = "0 * * * * *";

    /** 单批抢占上限：控制一次 tick 的吞吐 */
    static final int BATCH_SIZE = 100;

    /** 单批内并发上游查询数（并发是「同时发几个查询」，不是占多少线程） */
    static final int CONCURRENCY = 8;

    /** 单次查询上游的超时（毫秒）。⚠ 与 {@link #POLL_BACKOFF_MAX_MS} 同值不同义 */
    static final long QUERY_TIMEOUT_MS = 30_000L;

    /** 占位有效期（毫秒）：一轮处理的时间预算上限，必须严格大于 {@link #QUERY_TIMEOUT_MS} */
    static final long CLAIM_TTL_MS = 180_000L;

    /** 单批超时回收上限 */
    static final int EXPIRE_BATCH_SIZE = 200;

    /** 终态行保留天数，超过由归档任务清理 */
    static final int RETENTION_DAYS = 30;

    /** 归档扫描节拍（cron）：每小时整点。⚠ 与 {@code @Scheduled} 里的兜底字面量是两份 */
    static final String CLEANUP_CRON = "0 0 * * * *";

    /** 单批清理上限：大表删除分批，避免长事务 */
    static final int CLEANUP_BATCH_SIZE = 500;
}

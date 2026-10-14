package org.chobit.knot.gateway.plugin.refresh;

import java.time.Duration;

/**
 * 可定时刷新的资源：版本盲区修复的通用抽象（robin 2026-10-14）。
 *
 * <p>实现类只提供刷新动作 {@link #refresh()} 与刷新间隔 {@link #refreshInterval()}；
 * 调度由 {@link ScheduledRefreshManager} 统一托管，各资源无需各自持有线程池、各自写 start/shutdown。</p>
 *
 * <p>新增「可定时刷新的配置」= 实现本接口并注册为 Spring bean，管理器按各自的间隔自动调度，零额外接线
 * （如敏感词词库快照 {@code ModerationSnapshotCache}）。</p>
 *
 * <p>约定：{@link #refreshInterval()} 返回 {@code null} 或 {@code <=0} 视为「不调度」；
 * {@link #refresh()} 自身应 fail-open（吞掉异常），调度器对任意未捕获异常仅告警、不影响后续 tick 与兄弟任务。</p>
 */
public interface Refreshable {

    /**
     * 执行一次刷新（fail-open，异常自行容错）。
     */
    void refresh();

    /**
     * 刷新间隔；{@code null} 或 {@code <=0} 表示不调度。
     */
    Duration refreshInterval();
}

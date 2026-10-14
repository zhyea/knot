package org.chobit.knot.gateway.plugin.schedule;

import java.time.Duration;

/**
 * 可定时刷新的资源：版本盲区修复的通用抽象（robin 2026-10-14）。
 *
 * <p>实现类标注 {@code @Component} 注册为 Spring bean，刷新动作由实现类自身以
 * {@code @Scheduled} 注解驱动（间隔取自实现类配置），无需各自持有线程池；
 * 需要按 IOC 收集全部实现时按 {@code List<Refreshable>} 注入即可。</p>
 *
 * <p>约定：{@link #refreshInterval()} 返回 {@code null} 或 {@code <=0} 视为「不调度」；
 * {@link #refresh()} 自身应 fail-open（吞掉异常），不影响后续 tick。</p>
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

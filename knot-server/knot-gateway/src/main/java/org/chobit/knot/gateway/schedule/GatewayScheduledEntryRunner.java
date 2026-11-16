package org.chobit.knot.gateway.schedule;

import lombok.RequiredArgsConstructor;
import org.chobit.knot.gateway.async.AsyncTaskScheduledPoller;
import org.chobit.knot.gateway.config.ConfigRefreshProperties;
import org.chobit.knot.gateway.service.support.ConfigCacheRefreshRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 网关所有定时任务的统一入口：{@code @Scheduled} 只写在本类，方法体只做一行转发。
 *
 * <p>一个节拍一个方法。开关（{@code knot.gateway.*.enabled}）、拍点重叠守卫、单轮并发与批量
 * 都留在被委托的 Runner 里，本类不判业务条件——「什么时候触发」与「触发后做什么」分开，
 * 调节拍不必读业务代码，改业务也不会碰到调度配置。</p>
 *
 * <p>cron 占位符必须带兜底默认值（{@code ${key:default}}）：注解读不到 Properties bean 的字段，
 * 默认值只能写成字面量，因此与 {@code AsyncTaskProperties} / {@link ConfigRefreshProperties}
 * 各存一份，两份是否一致由测试比对。</p>
 */
@Component
@RequiredArgsConstructor
public class GatewayScheduledEntryRunner {

    private final AsyncTaskScheduledPoller asyncTaskPoller;
    private final ConfigCacheRefreshRunner configCacheRefreshRunner;

    /** 异步任务一轮扫描：先回收超时、再抢占待办、抢到的逐个交给编排层。 */
    @Scheduled(cron = "${knot.gateway.async-task.tick-cron:0 * * * * *}")
    public void asyncTaskTick() {
        asyncTaskPoller.tick();
    }

    /** 异步任务归档：删除保留窗口之前的终态行。 */
    @Scheduled(cron = "${knot.gateway.async-task.cleanup-cron:0 0 * * * *}")
    public void asyncTaskArchive() {
        asyncTaskPoller.archive();
    }

    /** 回调交付一拍：抢占到期待投递任务并并发投递。 */
    @Scheduled(cron = "${knot.gateway.async-task.delivery-cron:0 * * * * *}")
    public void asyncTaskDeliver() {
        asyncTaskPoller.deliver();
    }

    /** 配置缓存刷新一拍：比对配置表指纹，差异域整域失效。 */
    @Scheduled(cron = "${knot.gateway.config-refresh.tick-cron:" + ConfigRefreshProperties.DEFAULT_TICK_CRON + "}")
    public void configRefreshTick() {
        configCacheRefreshRunner.tick();
    }
}

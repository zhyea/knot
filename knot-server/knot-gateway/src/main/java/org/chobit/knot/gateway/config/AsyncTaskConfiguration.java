package org.chobit.knot.gateway.config;

import org.chobit.knot.gateway.archive.ArchiveStore;
import org.chobit.knot.gateway.async.*;
import org.chobit.knot.svc.service.AsyncTaskService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 启用 {@link AsyncTaskProperties} 绑定（同 {@code UsageExtractionConfiguration} 的模式），
 * 并按 gateway 的配置<b>装配</b>编排层 {@link AsyncTaskRunner}。
 *
 * <p>单独成一个空配置类而不把 {@code @EnableConfigurationProperties} 挂到
 * {@link AsyncTaskSchedulingConfiguration} 上：后者只解决「谁来发起这一轮」（调度器线程池），
 * 与「轮询节奏/批量/占位时长」这些业务旋钮无关，混在一起会让「调批量大小」看起来像在动调度层。</p>
 *
 * <p>⭐ <b>为什么 {@link AsyncTaskRunner} 的 bean 在这里 new 而不在类上加 {@code @Component}</b>：
 * 它需要 {@link AsyncTaskRunPolicy}，而那几个数来自 {@code knot.gateway.async-task.*}
 * ——那是<b>部署</b>口径（哪个进程跑、跑多大批量）。若让 Runner 自己读配置前缀，
 * 「哪些数从哪来」就散落在两个类里；在这里装配则一处可见，Runner 只声明
 * 「我需要批量、占位时长、保留窗口」。</p>
 *
 * <p>与之相对，svc 侧那两个只碰表的数据访问类（{@code AsyncTaskService} / {@code AsyncTaskProfileService}）是 {@code @Service}——
 * 它没有部署参数，也就没有需要在这里注入的理由。</p>
 */
@Configuration
@EnableConfigurationProperties(AsyncTaskProperties.class)
public class AsyncTaskConfiguration {

    /**
     * 装配编排层：策略参数取自本 app 的配置，端口实现由本 app 的 bean 提供。
     *
     * <p>⚠ 转储器用 {@link ObjectProvider} 取而不是直接注入：单测与最小装配下可以没有它
     * （{@link AsyncTaskRunner} 对 null 的语义是「直通」），但<b>生产环境一定有</b>
     * ——{@code PassthroughArchiveStore} 带 {@code matchIfMissing = true}。</p>
     */
    @Bean
    public AsyncTaskRunner asyncTaskRunner(AsyncTaskService taskService,
                                           AsyncTaskQueryPort queryPort,
                                           AsyncTaskSettlementPort settlementPort,
                                           ObjectProvider<ArchiveStore> archiveStoreProvider,
                                           AsyncTaskProperties properties) {
        return new AsyncTaskRunner(
                taskService,
                queryPort,
                settlementPort,
                archiveStoreProvider.getIfAvailable(),
                new AsyncTaskRunPolicy(
                        properties.getBatchSize(),
                        properties.getExpireBatchSize(),
                        properties.getClaimTtlMs(),
                        properties.getRetentionDays(),
                        properties.getCleanupBatchSize()));
    }
}

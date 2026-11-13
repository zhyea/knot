package org.chobit.knot.gateway.config;

import org.chobit.knot.gateway.archive.ArchiveStore;
import org.chobit.knot.gateway.async.*;
import org.chobit.knot.gateway.async.admin.AsyncTaskAdminProperties;
import org.chobit.knot.svc.service.AsyncTaskService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 启用 {@link AsyncTaskProperties} 绑定（同 {@code UsageExtractionConfiguration} 的模式），并按 gateway 的配置装配编排层 {@link AsyncTaskRunner}。
 * {@link AsyncTaskRunner} 的 bean 在此装配（不在类上加 {@code @Component}）：策略参数取自 {@code knot.gateway.async-task.*}，Runner 只声明「我需要批量、占位时长、保留窗口」。
 */
@Configuration
@EnableConfigurationProperties({AsyncTaskProperties.class, AsyncTaskAdminProperties.class})
public class AsyncTaskConfiguration {

    /**
     * 装配编排层：策略参数取自本 app 的配置，端口实现由本 app 的 bean 提供。
     * 转储器用 {@link ObjectProvider} 取（{@link AsyncTaskRunner} 对 {@code null} 的语义是「直通」）；生产环境由 {@code PassthroughArchiveStore}（{@code matchIfMissing = true}）兜底。
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

    /**
     * 装配回调交付编排：策略同样取自本 app 的配置，投递实现（{@code AsyncTaskCallbackClient}）是 {@code @Component}，按端口注入。
     * 与 {@link AsyncTaskRunner} 分成两个 bean：轮询与交付是两条独立的对外链路（「问上游」vs「推客户」）。
     */
    @Bean
    public AsyncTaskDeliveryRunner asyncTaskDeliveryRunner(AsyncTaskService taskService,
                                                           AsyncTaskCallbackPort callbackPort,
                                                           AsyncTaskProperties properties) {
        return new AsyncTaskDeliveryRunner(taskService, callbackPort,
                new AsyncTaskDeliveryPolicy(
                        properties.getDeliveryBatchSize(),
                        properties.getDeliveryClaimTtlMs(),
                        properties.getDeliveryMaxAttempts(),
                        properties.getDeliveryBackoffBaseMs(),
                        properties.getDeliveryBackoffMaxMs()));
    }
}

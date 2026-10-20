package org.chobit.knot.gateway.config;

import io.netty.channel.ChannelOption;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;

/**
 * 上游调用的 HTTP 客户端装配：reactor-netty 连接池 + 显式超时。
 *
 * <p>2026-10-20 由 Apache HttpClient 5 迁移而来（WebFlux 引入第一步）。迁移后：</p>
 * <ul>
 *     <li>等待上游响应<b>不再占用线程</b>：连接读写由 Netty event loop 承载，
 *         非流式在阻塞段调度器（{@code gatewayBlockingScheduler}）上 {@code block()} 只是等结果，
 *         不再占连接池线程；</li>
 *     <li>流式具备<b>反压</b>：客户端慢时通过 {@code request(n)} 自动减少从上游的读取，
 *         不再需要靠「线程卡在 flush 上」来传导拥塞；</li>
 *     <li>连接池按 host 分片，总连接数是唯一全局约束（HC5 的 per-route 上限无对应物，已移除）。</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GatewayUpstreamClientProperties.class)
public class UpstreamWebClientConfiguration {

    /**
     * 上游连接池。
     */
    @Bean(destroyMethod = "dispose")
    public ConnectionProvider upstreamConnectionProvider(GatewayUpstreamClientProperties properties) {
        return ConnectionProvider.builder("knot-upstream")
                .maxConnections(properties.getMaxTotalConnections())
                .pendingAcquireTimeout(unboundedAsNull(properties.getConnectionRequestTimeout()))
                .maxIdleTime(unboundedAsNull(properties.getMaxIdleTime()))
                .maxLifeTime(unboundedAsNull(properties.getTimeToLive()))
                .build();
    }

    /**
     * 上游 HttpClient。
     *
     * <p>{@code responseTimeout} 在 reactor-netty 里是「相邻两次网络读之间的最大静默时长」，
     * 与配里的 {@code readTimeout} 同义——不是整段响应的总时长（总时长由 executor 侧的
     * {@code responseTimeout} 覆盖到响应头，见
     * {@code AbstractUpstreamProtocolExecutor#withResponseTimeout}）。</p>
     */
    @Bean
    public HttpClient upstreamHttpClient(ConnectionProvider connectionProvider,
                                         GatewayUpstreamClientProperties properties) {
        return HttpClient.create(connectionProvider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) properties.getConnectTimeout().toMillis())
                .responseTimeout(unboundedAsNull(properties.getReadTimeout()))
                .compress(properties.isContentCompressionEnabled());
    }

    /**
     * 网关使用的 WebClient：沿用 Boot 自动配置的编解码器，只替换底层连接器。
     */
    @Bean
    public WebClient upstreamWebClient(WebClient.Builder builder, HttpClient httpClient) {
        return builder.clientConnector(new ReactorClientHttpConnector(httpClient)).build();
    }

    /**
     * 配置的 Duration 用 0 / 负数表示「不限制」，而 reactor-netty 用 {@code null} 表达同一语义；
     * 直接把 0 传进去会被解释成「立即超时」，必须在这里归一。
     */
    private static Duration unboundedAsNull(Duration value) {
        return value == null || value.isZero() || value.isNegative() ? null : value;
    }
}

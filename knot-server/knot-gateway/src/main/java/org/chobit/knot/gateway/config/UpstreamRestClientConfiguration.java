package org.chobit.knot.gateway.config;

import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * 上游调用的 HTTP 客户端装配：Apache HttpClient 5 连接池 + 显式超时。
 *
 * <p>关键点：</p>
 * <ul>
 *     <li>连接池：默认 {@code HttpURLConnection} 每请求新建 TCP + TLS，这里改为按路由复用。</li>
 *     <li>超时：建连超时、取连接超时、Socket 读超时三者都必须有界，否则上游挂起会永久占住 worker 线程。</li>
 *     <li>响应不落盘：连接池配合 {@code HttpClient.executeOpen} 才能真正流式读取上游响应体。</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GatewayUpstreamClientProperties.class)
public class UpstreamRestClientConfiguration {

    /**
     * 上游连接池。
     */
    @Bean(destroyMethod = "close")
    public PoolingHttpClientConnectionManager upstreamConnectionManager(GatewayUpstreamClientProperties properties) {
        ConnectionConfig connectionConfig = ConnectionConfig.custom()
                .setConnectTimeout(timeout(properties.getConnectTimeout()))
                .setSocketTimeout(timeout(properties.getReadTimeout()))
                .setValidateAfterInactivity(TimeValue.ofMilliseconds(properties.getValidateAfterInactivity().toMillis()))
                .setTimeToLive(timeout(properties.getTimeToLive()))
                .build();
        return PoolingHttpClientConnectionManagerBuilder.create()
                .setDefaultConnectionConfig(connectionConfig)
                .setMaxConnTotal(properties.getMaxTotalConnections())
                .setMaxConnPerRoute(properties.getMaxConnectionsPerRoute())
                .build();
    }

    /**
     * 上游 HttpClient。连接管理器由单独的 bean 托管，这里声明为共享，避免重复关闭。
     */
    @Bean(destroyMethod = "close")
    public CloseableHttpClient upstreamHttpClient(PoolingHttpClientConnectionManager connectionManager,
                                                  GatewayUpstreamClientProperties properties) {
        // 建连与读超时都在 ConnectionConfig 上设置，这里只管连接池排队与内容压缩
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(timeout(properties.getConnectionRequestTimeout()))
                .setContentCompressionEnabled(properties.isContentCompressionEnabled())
                .build();
        return HttpClientBuilder.create()
                .setConnectionManager(connectionManager)
                .setConnectionManagerShared(true)
                .setDefaultRequestConfig(requestConfig)
                .disableCookieManagement()
                .build();
    }

    /**
     * Spring 的请求工厂，桥接 RestClient 与 Apache HttpClient。
     */
    @Bean
    public HttpComponentsClientHttpRequestFactory upstreamClientHttpRequestFactory(CloseableHttpClient httpClient) {
        return new HttpComponentsClientHttpRequestFactory(httpClient);
    }

    /**
     * 网关使用的 RestClient：沿用 Boot 自动配置的编解码器，只替换请求工厂。
     */
    @Bean
    public RestClient restClient(RestClient.Builder builder,
                                 HttpComponentsClientHttpRequestFactory requestFactory) {
        return builder.requestFactory(requestFactory).build();
    }

    private static Timeout timeout(Duration duration) {
        return Timeout.ofMilliseconds(duration == null ? 0L : duration.toMillis());
    }
}

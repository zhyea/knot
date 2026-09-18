package org.chobit.knot.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 上游 HTTP 客户端配置：连接池与超时。
 *
 * <p>默认 RestClient 走 JDK {@code HttpURLConnection}，既不复用连接也不设超时；
 * 这里统一改为 Apache HttpClient 5 的池化连接，并显式给出三类超时。</p>
 */
@ConfigurationProperties(prefix = "knot.gateway.upstream")
public class GatewayUpstreamClientProperties {

    /**
     * TCP + TLS 建连超时；上游主机不可达时快速失败并交给 failover。
     */
    private Duration connectTimeout = Duration.ofSeconds(5);

    /**
     * 连接池耗尽时等待空闲连接的超时；超时即抛错，避免请求无限排队。
     */
    private Duration connectionRequestTimeout = Duration.ofSeconds(5);

    /**
     * Socket 读超时，即相邻两个数据包之间的最大静默时间（不是整个响应的总时长）。
     * 非流式请求：超时即判定上游挂死；流式请求：上游静默超过该值即断开。
     */
    private Duration readTimeout = Duration.ofSeconds(120);

    /**
     * 单条连接的最长存活时间，到点后不再复用，避免长期固定在同一条连接上。
     */
    private Duration timeToLive = Duration.ofMinutes(5);

    /**
     * 从池中取出的连接空闲超过该时长后，先做一次有效性校验再使用。
     */
    private Duration validateAfterInactivity = Duration.ofSeconds(10);

    /**
     * 连接池总连接数上限。
     */
    private int maxTotalConnections = 200;

    /**
     * 单个路由（协议 + 域名）的连接数上限。
     */
    private int maxConnectionsPerRoute = 50;

    /**
     * 是否启用流式转发：{@code stream=true} 的请求边收边发，不再整包缓冲。
     * 出问题时可置为 false 退回整包缓冲模式。
     */
    private boolean streamingEnabled = true;

    /**
     * 流式转发的读写缓冲区大小（字节）。
     */
    private int streamBufferSize = 8192;

    /**
     * 是否接受上游的 gzip/br 压缩响应。默认关闭：解压器会引入额外缓冲，
     * 且压缩过的 SSE 更容易被中间层攒包，与流式转发的低延迟目标冲突。
     */
    private boolean contentCompressionEnabled = false;

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getConnectionRequestTimeout() {
        return connectionRequestTimeout;
    }

    public void setConnectionRequestTimeout(Duration connectionRequestTimeout) {
        this.connectionRequestTimeout = connectionRequestTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    public Duration getTimeToLive() {
        return timeToLive;
    }

    public void setTimeToLive(Duration timeToLive) {
        this.timeToLive = timeToLive;
    }

    public Duration getValidateAfterInactivity() {
        return validateAfterInactivity;
    }

    public void setValidateAfterInactivity(Duration validateAfterInactivity) {
        this.validateAfterInactivity = validateAfterInactivity;
    }

    public int getMaxTotalConnections() {
        return maxTotalConnections;
    }

    public void setMaxTotalConnections(int maxTotalConnections) {
        this.maxTotalConnections = maxTotalConnections;
    }

    public int getMaxConnectionsPerRoute() {
        return maxConnectionsPerRoute;
    }

    public void setMaxConnectionsPerRoute(int maxConnectionsPerRoute) {
        this.maxConnectionsPerRoute = maxConnectionsPerRoute;
    }

    public boolean isStreamingEnabled() {
        return streamingEnabled;
    }

    public void setStreamingEnabled(boolean streamingEnabled) {
        this.streamingEnabled = streamingEnabled;
    }

    public int getStreamBufferSize() {
        return streamBufferSize;
    }

    public void setStreamBufferSize(int streamBufferSize) {
        this.streamBufferSize = streamBufferSize;
    }

    public boolean isContentCompressionEnabled() {
        return contentCompressionEnabled;
    }

    public void setContentCompressionEnabled(boolean contentCompressionEnabled) {
        this.contentCompressionEnabled = contentCompressionEnabled;
    }
}

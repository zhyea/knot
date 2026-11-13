package org.chobit.knot.gateway.config;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * 上游 HTTP 客户端配置：连接池与超时。
 * 客户端为 reactor-netty（{@code WebClient} 底层）：连接池按 host 分片复用，三类超时（建连 / 相邻读静默 / 整段响应）显式有界。
 * 所有数值经 {@code @Min} 约束下限；Duration 字段不允许为负，0 表示「不限制」，由 {@code UpstreamWebClientConfiguration} 归一成 reactor-netty 的 {@code null} 语义。
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "knot.gateway.upstream")
public class GatewayUpstreamClientProperties {

    /**
     * TCP + TLS 建连超时；上游主机不可达时快速失败并交给 failover。
     */
    private Duration connectTimeout = Duration.ofSeconds(5);

    /**
     * 连接池耗尽时等待空闲连接的超时；超时即抛错。
     */
    private Duration connectionRequestTimeout = Duration.ofSeconds(5);

    /**
     * 相邻两次网络读之间的最大静默时间（不是整个响应的总时长），对应 reactor-netty 的
     * {@code responseTimeout}。非流式请求：超时即判定上游挂死；流式请求：上游静默超过该值即断开。
     */
    private Duration readTimeout = Duration.ofSeconds(120);

    /**
     * 单条连接的最长存活时间，到点后不再复用。
     */
    private Duration timeToLive = Duration.ofMinutes(5);

    /**
     * 池中连接的最长空闲时间，超过即回收。
     */
    private Duration maxIdleTime = Duration.ofSeconds(10);

    /**
     * 总响应超时：从请求发起、到收到完整响应头的最长时间，是「整段交互」的总时限，而非相邻数据包的静默超时；由 executor 侧的 {@code Mono#timeout} 施加。
     * 与 {@link #readTimeout}（相邻数据包静默超时）互补。
     * 流式转发只阻塞等到响应头，响应体由 {@code ProxyStreamingFlux} 惰性读取（受 readTimeout 约束）；总响应超时只卡到「响应头到达」，非流式则覆盖完整响应体读取。
     * 设 0 关闭（仅依赖 readTimeout）。
     */
    private Duration responseTimeout = Duration.ofSeconds(120);

    /**
     * 连接池总连接数上限。
     * reactor-netty 连接池按 host 分片但共享全局 {@code maxConnections}，无 per-route 对应物。
     */
    @Min(1)
    private int maxTotalConnections = 200;

    /**
     * 流式转发的读写缓冲区大小（字节）。
     */
    @Min(1)
    private int streamBufferSize = 8192;

    /**
     * 网关阻塞段调度器的线程数。
     * 由 {@code Schedulers.newBoundedElastic(threads, ...)} 承载，任务永不回到调用线程。
     * 线程数决定「同时可执行的阻塞段上限」（路由解析 + 上游调用），默认 200，下限 {@code @Min(1)}。
     */
    @Min(1)
    private int asyncExecutorThreads = 200;

    /**
     * 单条网关请求的总预算（B2 全局请求 deadline）：从请求进入网关到响应就绪的最长时间。
     * 以 {@code Mono.timeout(...)} 作用于每条网关请求，到点即终止并返回 504；预算只覆盖「响应就绪」，不覆盖流式响应体的写出。
     * 客户端的断连由 Reactor 取消处理；默认 600s。
     */
    private Duration requestBudget = Duration.ofSeconds(600);

    /**
     * 是否接受上游的 gzip/br 压缩响应。默认关闭：解压器会引入额外缓冲，
     * 且压缩过的 SSE 更容易被中间层攒包，与流式转发的低延迟目标冲突。
     */
    private boolean contentCompressionEnabled = false;
}

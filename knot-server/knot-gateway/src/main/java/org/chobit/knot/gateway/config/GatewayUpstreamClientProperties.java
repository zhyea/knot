package org.chobit.knot.gateway.config;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * 上游 HTTP 客户端配置：连接池与超时。
 *
 * <p>客户端为 reactor-netty（{@code WebClient} 底层）：连接池按 host 分片复用，
 * 三类超时（建连 / 相邻读静默 / 整段响应）显式有界，避免上游挂起把承载资源吃干。</p>
 *
 * <p>所有数值均经 {@code @Min} 约束下限，避免被误配成 0/负数引发自我 DoS
 * （连接池/缓冲区为 0 会让请求无限排队或每请求新建连接）。Duration 类字段不允许为负，
 * 0 表示「不限制」——由 {@code UpstreamWebClientConfiguration} 归一成 reactor-netty 的
 * {@code null} 语义（直接传 0 会被解释成「立即超时」）。</p>
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
     * 连接池耗尽时等待空闲连接的超时；超时即抛错，避免请求无限排队。
     */
    private Duration connectionRequestTimeout = Duration.ofSeconds(5);

    /**
     * 相邻两次网络读之间的最大静默时间（不是整个响应的总时长），对应 reactor-netty 的
     * {@code responseTimeout}。非流式请求：超时即判定上游挂死；流式请求：上游静默超过该值即断开。
     */
    private Duration readTimeout = Duration.ofSeconds(120);

    /**
     * 单条连接的最长存活时间，到点后不再复用，避免长期固定在同一条连接上。
     */
    private Duration timeToLive = Duration.ofMinutes(5);

    /**
     * 池中连接的最长空闲时间，超过即回收。
     *
     * <p>2026-10-20：原 {@code validateAfterInactivity}（HC5 语义＝「空闲超过该值则复用前先校验」）
     * 在 reactor-netty 下没有对应物，就近归一为「空闲超时即回收」——二者目的都是
     * 不让陈旧连接留在池里被复用。</p>
     */
    private Duration maxIdleTime = Duration.ofSeconds(10);

    /**
     * 总响应超时：从请求发起、到收到<b>完整响应头</b>的最长时间，是「整段交互」的总时限，
     * 而非相邻数据包的静默超时。由 executor 侧的 {@code Mono#timeout} 施加。
     *
     * <p>与 {@link #readTimeout}（相邻数据包静默超时）互补：慢速滴流上游（如每 100s 只滴 1 字节）
     * 的滴流间隔 < readTimeout，单靠 readTimeout 拦不住，请求会被无限占住；总响应超时按
     * 整段时长兜底，到点即断、释放资源，堵住这个最坏场景。</p>
     *
     * <p>流式安全：流式转发只阻塞等到响应头，响应体由 {@code ProxyStreamingFlux} 惰性读取，
     * 受 readTimeout（静默）约束——总响应超时只卡到「响应头到达」，<b>不会</b>因长流整体超限被误杀。
     * 非流式（整包缓冲）则总响应超时覆盖完整响应体读取，正好用于拦慢速滴流。</p>
     *
     * <p>设 0 关闭（仅依赖 readTimeout）。若实测某合法长非流式响应被误伤，调大本值或置 0。</p>
     */
    private Duration responseTimeout = Duration.ofSeconds(120);

    /**
     * 连接池总连接数上限。
     *
     * <p>2026-10-20：原 {@code maxConnectionsPerRoute}（单路由上限）已移除——reactor-netty 的
     * 连接池按 host 分片但共享同一个全局 {@code maxConnections}，没有 per-route 对应物。
     * 保留一个无效果的开关只会造成误判，故不再提供。</p>
     */
    @Min(1)
    private int maxTotalConnections = 200;

    /**
     * 流式转发的读写缓冲区大小（字节）。
     */
    @Min(1)
    private int streamBufferSize = 8192;

    /**
     * 网关阻塞段调度器的线程数（原「B1 异步化承载池」的线程数，语义不变）。
     *
     * <p>2026-10-20（WebFlux 批2）：承载池由 {@code ThreadPoolTaskExecutor} 换成
     * {@code Schedulers.newBoundedElastic(threads, ...)}。原因是 Netty 容器下
     * {@code CallerRunsPolicy} 会把池满的任务<b>回落到 event loop 线程就地执行</b>，
     * 一次阻塞就卡死整个 I/O 线程；{@code boundedElastic} 保证任务永不回到调用线程。</p>
     *
     * <p>线程数仍决定「同时可执行的阻塞段上限」（路由解析 + 上游调用），
     * 默认 200 与历史承载池对齐。下限 {@code @Min(1)} 防止误配成 0 让请求无法被处理。</p>
     */
    @Min(1)
    private int asyncExecutorThreads = 200;

    /**
     * 单条网关请求的总预算（B2 全局请求 deadline）：从请求进入网关到响应就绪的最长时间。
     *
     * <p>以 {@code Mono.timeout(...)} 的形式作用于每条网关请求；到点即终止并返回 504。
     * ⚠ 2026-10-20（WebFlux 批2）：预算只覆盖「响应就绪」这一段，<b>不覆盖流式响应体的写出</b>
     * ——长回答是正常业务，套在流上会在到点时被硬掐断。客户端的断连由 Reactor 取消处理。</p>
     *
     * <p>默认 600s 与历史上 {@code spring.mvc.async.request-timeout} 的取值对齐；
     * 若实测合法长任务（大音频/视频生成）在响应就绪前就被截断，调大本值。</p>
     */
    private Duration requestBudget = Duration.ofSeconds(600);

    /**
     * 是否接受上游的 gzip/br 压缩响应。默认关闭：解压器会引入额外缓冲，
     * 且压缩过的 SSE 更容易被中间层攒包，与流式转发的低延迟目标冲突。
     */
    private boolean contentCompressionEnabled = false;
}

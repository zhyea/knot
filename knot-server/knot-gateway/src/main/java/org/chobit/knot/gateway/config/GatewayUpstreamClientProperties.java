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
 * <p>默认 RestClient 走 JDK {@code HttpURLConnection}，既不复用连接也不设超时；
 * 这里统一改为 Apache HttpClient 5 的池化连接，并显式给出三类超时。</p>
 *
 * <p>所有数值均经 {@code @Min} 约束下限，避免被误配成 0/负数引发自我 DoS
 * （连接池/缓冲区为 0 会让请求无限排队或每请求新建连接）。Duration 类字段不允许为负，
 * 0 表示「不限制」（由 HC5 解释为禁用该超时）。</p>
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
     * 总响应超时（HC5 {@code RequestConfig.setResponseTimeout}）：从请求发起、到收到
     * <b>完整响应头</b>的最长时间，是「整段交互」的总时限，而非相邻数据包的静默超时。
     *
     * <p>与 {@link #readTimeout}（相邻数据包静默超时）互补：慢速滴流上游（如每 100s 只滴 1 字节）
     * 的滴流间隔 < readTimeout，单靠 readTimeout 拦不住，worker 会被无限占住；总响应超时按
     * 整段时长兜底，到点即断、释放 worker，堵住这个最坏场景。</p>
     *
     * <p>流式安全：流式转发只在 {@code exchange(...)} 内读取响应头，响应体由
     * {@code ProxyStreamingBody} 在独立的 {@code StreamingResponseBody} 任务里惰性读取，
     * 受 readTimeout（静默）约束——总响应超时只卡到「响应头到达」，<b>不会</b>因长流整体超限被误杀。
     * 非流式（整包缓冲）则总响应超时覆盖完整响应体读取，正好用于拦慢速滴流。</p>
     *
     * <p>设 0 关闭（仅依赖 readTimeout）。若实测某合法长非流式响应被误伤，调大本值或置 0。</p>
     */
    private Duration responseTimeout = Duration.ofSeconds(120);

    /**
     * 连接池总连接数上限。
     */
    @Min(1)
    private int maxTotalConnections = 200;

    /**
     * 单个路由（协议 + 域名）的连接数上限。
     */
    @Min(1)
    private int maxConnectionsPerRoute = 50;

    /**
     * 流式转发的读写缓冲区大小（字节）。
     */
    @Min(1)
    private int streamBufferSize = 8192;

    /**
     * 网关异步请求处理线程池大小（B1 异步化的承载池）：控制器返回 {@code DeferredResult} 后，
     * 真正的上游代理链路——含失败重试的退避等待——在该专用线程池上执行，Tomcat worker 立即释放，
     * 避免坏上游的重试/退避把 Web 容器线程吃干。
     *
     * <p>线程数决定并发在途的上游调用上限；默认与 Tomcat 默认 worker 数（200）对齐。
     * 下限 {@code @Min(1)} 防止误配成 0 让请求无法被处理。若上游普遍偏慢或重试频繁，可调大。</p>
     */
    @Min(1)
    private int asyncExecutorThreads = 200;

    /**
     * 单条网关请求的总预算（B2 全局请求 deadline）：从请求进入网关到响应完全就绪的最长时间。
     *
     * <p>覆盖「非流式同步路径零兜底、仅流式 600s async 兜底」的历史不一致，作为<b>唯一真总超时</b>。
     * 以 {@code DeferredResult} 超时的形式作用于每条网关请求；到点即终止并返回 504，
     * 防止极端重试/挂死上游无限占住线程。</p>
     *
     * <p>默认 600s 与既有流式 {@code spring.mvc.async.request-timeout} 对齐，长流式响应不被误伤；
     * 若实测合法长任务（大音频/视频生成）被截断，调大本值。</p>
     */
    private Duration requestBudget = Duration.ofSeconds(600);

    /**
     * 是否接受上游的 gzip/br 压缩响应。默认关闭：解压器会引入额外缓冲，
     * 且压缩过的 SSE 更容易被中间层攒包，与流式转发的低延迟目标冲突。
     */
    private boolean contentCompressionEnabled = false;
}

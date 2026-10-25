package org.chobit.knot.svc.service.support;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 管理面内部转发的 {@link RestClient} 装配：连接超时 + 读取超时，别无其它。
 *
 * <p><b>存在的理由</b>（改进清单 4.3）：这段「new 工厂 → 设两个超时 → 塞进 builder」
 * 曾被原样抄在三处——{@code GatewayOutageRunner}、{@code RoutingRuleDebugRunner}、
 * {@code RestClientDecisionsTransport}。它们的超时<b>值</b>各不相同（分别对应摘除查询、
 * 流式调试、判官调用三种时延特征），但装配<b>形态</b>是同一件事；三份抄写意味着
 * 「加一个重试策略 / 加一个请求日志拦截器 / 换底层客户端」要改三遍且极易漏一处。</p>
 *
 * <p><b>为什么不做成 Spring Bean</b>：三者超时值不同，做成 Bean 就得为每种组合起名字，
 * 反而增加配置面；这里要收口的是<b>装配步骤</b>，不是实例。</p>
 *
 * <p><b>为什么不合并 {@code OpenRouterModelSyncProvider}</b>：它走 {@code java.net.http.HttpClient}
 * （JDK 同步客户端），与本类的 {@code RestClient} 异步语义、异常类型链、BodyHandler 机制都不同。
 * 硬统一属纯技术栈合并，收益（少一条依赖）低于风险（改异常包装与超时语义）；
 * 外部目录同步是低频链路，与网关转发无关，保持独立。</p>
 *
 * <p><b>为什么不写单测</b>：本方法零分支、零算术，只是把两个入参塞进工厂再返回 builder；
 * 测它只能断言「返回非 null」，零信息量。真正该测的是各 Runner 的请求构造与异常翻译，
 * 那些类有自己的测试。</p>
 */
public final class RestClientSupport {

    private RestClientSupport() {
    }

    /**
     * 造一个带超时的同步 {@link RestClient}。
     *
     * @param connectTimeoutMs 连接超时（毫秒），必须为正——{@code 0} 会被底层当作「无限等待」
     * @param readTimeoutMs    读取超时（毫秒），必须为正；流式场景下它表示相邻数据的最大间隔
     */
    public static RestClient withTimeouts(int connectTimeoutMs, int readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        return RestClient.builder()
                .requestFactory(factory)
                .build();
    }
}

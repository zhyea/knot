package org.chobit.knot.svc.service.support;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 管理面内部转发的 {@link RestClient} 装配：连接超时 + 读取超时，别无其它。
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

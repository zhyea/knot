package org.chobit.knot.gateway.runtime;

import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.MediaType;

/**
 * HTTP 请求/响应的最小读写入口：网关链路唯一允许碰「容器对象」的地方，链路中没有 ThreadLocal 依赖。
 * 实现方是 {@link ReactiveHttpExchange}（Netty / {@code ServerWebExchange}）。
 */
public interface GatewayHttpExchange {

    /**
     * 读请求头；缺失返回 {@code null}。
     */
    String requestHeader(String name);

    /**
     * 写响应头；响应已 commit 后由容器忽略。
     */
    void setResponseHeader(String name, String value);

    /**
     * 流式响应头：Content-Type + UTF-8 + no-cache + 禁用反代缓冲。
     */
    void prepareStreaming(MediaType contentType);

    /**
     * 响应侧的缓冲区工厂：流式转发需要按容器的方式分配 {@code DataBuffer}。
     */
    DataBufferFactory bufferFactory();

    /**
     * 非 web 场景（单测、内部调用）的空实现：读写一律静默忽略。
     */
    static GatewayHttpExchange noop() {
        return Noop.INSTANCE;
    }

    enum Noop implements GatewayHttpExchange {
        INSTANCE;

        @Override
        public String requestHeader(String name) {
            return null;
        }

        @Override
        public void setResponseHeader(String name, String value) {
            // 无响应对象可写
        }

        @Override
        public void prepareStreaming(MediaType contentType) {
            // 无响应对象可写
        }

        @Override
        public DataBufferFactory bufferFactory() {
            return DefaultDataBufferFactory.sharedInstance;
        }
    }
}

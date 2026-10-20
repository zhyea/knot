package org.chobit.knot.gateway.runtime;

import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.MediaType;

/**
 * HTTP 请求/响应的最小读写入口：网关链路唯一允许碰「容器对象」的地方。
 *
 * <p>2026-10-20（WebFlux 批1-B）引入：原先链路靠 {@code RequestContextHolder} 的线程本地属性
 * 到处隐式取 response —— 异步回调要手工重绑，换成 Reactor 线程模型后这套隐式传递必然失效
 * 且<b>不报错</b>（写头静默丢失）。改成显式传参后，链路里没有任何 ThreadLocal 依赖。</p>
 *
 * <p>批2 起实现方是 {@link ReactiveHttpExchange}（Netty / {@code ServerWebExchange}）。</p>
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

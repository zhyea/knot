package org.chobit.knot.gateway.async;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.chobit.knot.gateway.model.vo.response.AsyncTaskQueryResponse;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URI;
import java.time.Duration;

/**
 * 回调投递实现：向客户端的 {@code callback_url} 发一次 JSON POST。
 *
 * <p><b>只做一件事</b>：把 {@link AsyncTaskQueryResponse} 原样 POST 出去。
 * 不解析响应体、不判断业务语义——对端返回 2xx 即成功，其余一律抛异常交给编排层判定重试。</p>
 *
 * <p>⭐ <b>超时独立于上游查询</b>：回调是发给客户端的，慢客户端不该拖住轮询预算。
 * 超时取 {@code delivery-timeout-ms}，且必须小于 {@code delivery-claim-ttl-ms}
 * （启动期校验），否则占位会在请求返回前过期、同一笔结果被两个节点各投一次。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AsyncTaskCallbackClient implements AsyncTaskCallbackPort {

    private final WebClient webClient;
    private final AsyncTaskProperties properties;

    @Override
    public void deliver(String callbackUrl, AsyncTaskQueryResponse payload) {
        Duration timeout = Duration.ofMillis(Math.max(1_000L, properties.getDeliveryTimeoutMs()));
        // ⚠ 用 URI.create 而不是 uri(String)：后者会做 URI 模板编码，与本项目
        // 「不对 URL 做编解码」的口径一致（同一原则见 AsyncTaskQueryClient）
        webClient.post()
                .uri(URI.create(callbackUrl))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .map(body -> new IllegalStateException(
                                "async task callback failed: status=" + response.statusCode() + " body=" + body)))
                .bodyToMono(Void.class)
                .block(timeout);
    }
}

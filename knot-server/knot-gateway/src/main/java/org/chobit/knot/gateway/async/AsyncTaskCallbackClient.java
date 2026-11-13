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
 * 把 {@link AsyncTaskQueryResponse} 原样 POST 出去，不解析响应体、不判断业务语义；2xx 即成功，其余抛异常交编排层判定重试。
 * 超时取 {@code delivery-timeout-ms}，且必须小于 {@code delivery-claim-ttl-ms}（启动期校验）。
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

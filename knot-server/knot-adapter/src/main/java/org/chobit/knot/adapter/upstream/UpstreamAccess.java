package org.chobit.knot.adapter.upstream;

import org.springframework.http.HttpHeaders;

/**
 * 上游接入准备结果：生效的 base URL + 静态鉴权头，由 {@code UpstreamAuthApplier#prepare}
 * 每次请求产出一次，执行器只消费它。
 *
 * @param baseUrl 生效的上游 base URL（含 scheme，可为空表示配置缺失，由执行器统一报配置错误）
 * @param headers 静态鉴权头（只读快照）；需要签名覆盖 payload 的策略在此仍为空，
 *                签名头由执行器在请求体定稿后经 {@code sign} 追加
 */
public record UpstreamAccess(String baseUrl, HttpHeaders headers) {

    public UpstreamAccess {
        headers = headers == null ? HttpHeaders.EMPTY : HttpHeaders.readOnlyHttpHeaders(headers);
    }
}

package org.chobit.knot.gateway.adapter.upstream;

/**
 * 交给鉴权策略签名的请求摘要：只有「签名需要知道的三件事」。
 *
 * <p>刻意做成纯值类型且不引任何 HTTP 客户端类型：鉴权策略属于协议知识，
 * 不该被绑在 WebClient 上（与 {@code UpstreamAuthApplier#apply} 只碰 {@code HttpHeaders}
 * 是同一条边界）。</p>
 *
 * @param httpMethod  HTTP 方法（Bedrock 恒为 {@code POST}）
 * @param url        完整请求 URL（路径须已完成 RFC3986 编码，签名与实际发出必须是同一个串）
 * @param payloadHash 请求体字节的 SHA-256 十六进制（小写）
 */
public record UpstreamSignableRequest(String httpMethod, String url, String payloadHash) {
}

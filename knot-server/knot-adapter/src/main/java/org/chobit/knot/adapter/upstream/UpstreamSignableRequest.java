package org.chobit.knot.adapter.upstream;

import org.chobit.knot.adapter.support.aws.AwsSigV4Support;

/**
 * 交给鉴权策略签名的请求摘要：HTTP 方法、完整 URL、请求体哈希三项，
 * 纯值类型，不依赖任何 HTTP 客户端类型。
 *
 * @param httpMethod  HTTP 方法（Bedrock 恒为 {@code POST}）
 * @param url        完整请求 URL（路径须已完成 RFC3986 编码，签名与实际发出必须是同一个串）
 * @param payloadHash 请求体字节的 SHA-256 十六进制（小写）
 */
public record UpstreamSignableRequest(String httpMethod, String url, String payloadHash) {

    /**
     * 由原始请求体字节构造：哈希在这里算，调用方（协议执行器）只管把「将要发出的那份字节」交进来，
     * 以此保证「算哈希的字节」与「实际发出的字节」是同一份——SigV4 签名的第一雷区。
     */
    public static UpstreamSignableRequest of(String httpMethod, String url, byte[] payload) {
        return new UpstreamSignableRequest(httpMethod, url, AwsSigV4Support.payloadHash(payload));
    }
}

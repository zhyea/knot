package org.chobit.knot.gateway.adapter;

/**
 * 上游请求鉴权方案。由请求适配器通过 {@code authScheme()} 声明，
 * 实际鉴权头的注入统一交给 {@link UpstreamAuthApplier}，适配器自身不再处理鉴权。
 */
public enum AuthScheme {

    /** Authorization: Bearer <key>（OpenAI / Zhipu / Qwen 等兼容接口） */
    BEARER,

    /** x-api-key: <key> + 固定 anthropic-version（Anthropic 接口） */
    API_KEY,

    /** 不透传任何鉴权头（纯透传适配器） */
    NONE
}

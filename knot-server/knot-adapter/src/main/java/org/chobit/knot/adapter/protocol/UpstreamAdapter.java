package org.chobit.knot.adapter.protocol;

import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.adapter.upstream.UpstreamRequestContext;
import org.chobit.knot.common.constants.enums.ModelApiProtocolEnum;
import org.springframework.http.MediaType;

import java.util.Set;

/**
 * 上游适配器：把网关侧请求发往上游的双向适配角色——请求适配（{@link #resolvePath} 协议路径 /
 * {@link #buildRequestBody} 请求体转换 / {@link #resolveContentType} 内容类型）与响应适配
 * （{@link #handleResponse} 整包响应体转换 / {@link #streamAdapter} 流式逐行转换）由同一实现持有。
 * code 即 {@code kb_model_api_bindings.request_adapter} 的绑定值；透传实现名为 {@code PassthroughUpstreamAdapter}。
 */
public interface UpstreamAdapter {

    String code();

    String label();

    Set<ModelApiProtocolEnum> protocol();

    /**
     * 协议路径：优先绑定级 {@code api_path}，缺省走协议默认路径。
     * 不处理模型级 {@code base_path}——那是 URL 前缀，由执行器统一拼接。
     */
    default String resolvePath(UpstreamRequestContext context, String defaultPath) {
        String bindingPath = context.binding() == null ? null : context.binding().apiPath();
        return StringUtils.defaultIfBlank(StringUtils.trim(bindingPath), defaultPath);
    }

    /**
     * 该适配器能否承接流式请求。默认 {@code true}；AWS Bedrock 的流式是 EventStream 二进制帧
     * （不是 SSE），P1 返回 {@code false} 让执行器走整包路径。
     */
    default boolean streamSupported(UpstreamRequestContext context) {
        return true;
    }

    Object buildRequestBody(UpstreamRequestContext context);

    default MediaType resolveContentType(UpstreamRequestContext context) {
        return context.contentType();
    }

    /**
     * 响应适配：把上游响应体转换成网关侧（客户端）形状，默认原样透传。
     * 整包路径的用量提取以本方法产物为输入。
     */
    default String handleResponse(String responseBody, UpstreamRequestContext context) {
        return responseBody;
    }

    /**
     * 流式响应适配器：把上游 SSE 逐行转换成网关侧（客户端）形状，产物同时供客户端转发与用量提取。
     * 只服务流式路径（{@link #handleResponse} 只服务整包路径）；{@link #streamSupported} 为
     * {@code false} 的适配器不调用本方法。每次请求新建实例，默认返回 {@link StreamLineAdapter#PASSTHROUGH}。
     *
     * @param context 本次请求的上下文（可为 {@code null}，实现需自担空判断）
     */
    default StreamLineAdapter streamAdapter(UpstreamRequestContext context) {
        return StreamLineAdapter.PASSTHROUGH;
    }

}

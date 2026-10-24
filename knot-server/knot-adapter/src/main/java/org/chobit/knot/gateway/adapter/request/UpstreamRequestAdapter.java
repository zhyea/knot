package org.chobit.knot.gateway.adapter.request;

import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.gateway.adapter.upstream.UpstreamRequestContext;
import org.chobit.knot.gateway.constants.enums.ModelApiProtocolEnum;
import org.springframework.http.MediaType;

import java.util.Set;

public interface UpstreamRequestAdapter {

    String code();

    String label();

    Set<ModelApiProtocolEnum> protocol();

    /**
     * 协议路径：优先绑定级 {@code api_path}，缺省走协议默认路径。
     *
     * <p>不处理模型级 {@code base_path}——那是 URL 前缀，由执行器统一拼接，
     * 避免每个适配器重复实现同样的拼接逻辑。</p>
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

    default String handleResponse(String responseBody, UpstreamRequestContext context) {
        return responseBody;
    }

}

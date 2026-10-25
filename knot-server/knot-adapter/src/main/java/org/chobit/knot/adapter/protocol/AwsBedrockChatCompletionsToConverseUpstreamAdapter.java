package org.chobit.knot.adapter.protocol;

import org.chobit.knot.adapter.support.aws.AwsBedrockConverseSupport;
import org.chobit.knot.adapter.upstream.UpstreamRequestContext;
import org.chobit.knot.common.constants.enums.ModelApiProtocolEnum;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * AWS Bedrock Converse 适配器：OpenAI ChatCompletions → Converse（请求侧原形状即 OpenAI chat，
 * 归一为空操作；响应侧与 Converse 目标形状构造走 {@link AwsBedrockConverseSupport}）。
 *
 * <p><b>只服务 {@code CHAT_COMPLETIONS} 一种入协议</b>，且<b>只服务 {@code bedrock-runtime} 端点</b>：
 * mantle 端点不支持 Converse（只暴露 OpenAI 兼容与 Anthropic Messages），mantle 模型直接绑定
 * {@link PassthroughUpstreamAdapter}。</p>
 */
@Component
@Order(30)
public class AwsBedrockChatCompletionsToConverseUpstreamAdapter implements UpstreamAdapter {

    public static final String CODE = "AWS_BEDROCK_CHAT_COMPLETIONS_TO_CONVERSE";

    private static final Set<ModelApiProtocolEnum> PROTOCOLS = Set.of(ModelApiProtocolEnum.CHAT_COMPLETIONS);

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String label() {
        return "AWS Bedrock ChatCompletions 转 Converse";
    }

    @Override
    public Set<ModelApiProtocolEnum> protocol() {
        return PROTOCOLS;
    }

    @Override
    public String resolvePath(UpstreamRequestContext context, String defaultPath) {
        return AwsBedrockConverseSupport.resolvePath(context, defaultPath);
    }

    @Override
    public Object buildRequestBody(UpstreamRequestContext context) {
        return AwsBedrockConverseSupport.buildConverseBody(context.requestBody());
    }

    @Override
    public String handleResponse(String responseBody, UpstreamRequestContext context) {
        return AwsBedrockConverseSupport.handleConverseResponse(responseBody, context);
    }

    /**
     * P1 不支持流式：Converse 的流式是 AWS EventStream 二进制帧而非 SSE，需要独立的解码器（P2）。
     */
    @Override
    public boolean streamSupported(UpstreamRequestContext context) {
        return false;
    }
}

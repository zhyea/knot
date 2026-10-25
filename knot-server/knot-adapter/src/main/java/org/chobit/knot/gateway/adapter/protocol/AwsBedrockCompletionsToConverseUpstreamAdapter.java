package org.chobit.knot.gateway.adapter.protocol;

import org.chobit.knot.gateway.adapter.support.aws.AwsBedrockConverseSupport;
import org.chobit.knot.gateway.adapter.upstream.UpstreamRequestContext;
import org.chobit.knot.gateway.constants.AiPayloadFields;
import org.chobit.knot.gateway.constants.enums.ModelApiProtocolEnum;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AWS Bedrock Converse 适配器：OpenAI Completions → Converse。
 *
 * <p><b>只服务 {@code COMPLETIONS} 一种入协议</b>：{@code prompt} 折成单条 user 消息、
 * 移除 Completions 专有字段，再走 {@link AwsBedrockConverseSupport} 折成 Converse 请求。
 * 只服务 {@code bedrock-runtime} 端点。</p>
 */
@Component
@Order(30)
public class AwsBedrockCompletionsToConverseUpstreamAdapter implements UpstreamAdapter {

    public static final String CODE = "AWS_BEDROCK_COMPLETIONS_TO_CONVERSE";

    private static final Set<ModelApiProtocolEnum> PROTOCOLS = Set.of(ModelApiProtocolEnum.COMPLETIONS);

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String label() {
        return "AWS Bedrock Completions 转 Converse";
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
        return AwsBedrockConverseSupport.buildConverseBody(completionsToChatShape(context.requestBody()));
    }

    @Override
    public String handleResponse(String responseBody, UpstreamRequestContext context) {
        return AwsBedrockConverseSupport.handleConverseResponse(responseBody, context);
    }

    @Override
    public boolean streamSupported(UpstreamRequestContext context) {
        return false;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> completionsToChatShape(Map<String, Object> source) {
        Map<String, Object> body = new LinkedHashMap<>(source);
        Object prompt = source.get(AiPayloadFields.PROMPT);
        body.put(AiPayloadFields.MESSAGES, List.of(Map.of(AiPayloadFields.ROLE, AiPayloadFields.USER, AiPayloadFields.CONTENT, prompt == null ? "" : prompt)));
        body.remove(AiPayloadFields.PROMPT);
        body.remove("suffix");
        body.remove("n");
        body.remove("echo");
        body.remove("logprobs");
        body.remove("presence_penalty");
        body.remove("frequency_penalty");
        body.remove("logit_bias");
        return body;
    }
}

package org.chobit.knot.adapter.protocol;

import org.chobit.knot.adapter.support.aws.AwsBedrockConverseSupport;
import org.chobit.knot.adapter.upstream.UpstreamRequestContext;
import org.chobit.knot.common.constants.AiPayloadFields;
import org.chobit.knot.common.constants.enums.ModelApiProtocolEnum;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AWS Bedrock Converse 适配器：OpenAI Responses → Converse。
 *
 * <p><b>只服务 {@code RESPONSES} 一种入协议</b>：先把 Responses 形状（{@code input} /
 * {@code instructions}）归一成 OpenAI chat 形状，再走 {@link AwsBedrockConverseSupport}
 * 折成 Converse 请求。只服务 {@code bedrock-runtime} 端点。</p>
 */
@Component
@Order(30)
public class AwsBedrockResponsesToConverseUpstreamAdapter implements UpstreamAdapter {

    public static final String CODE = "AWS_BEDROCK_RESPONSES_TO_CONVERSE";

    private static final Set<ModelApiProtocolEnum> PROTOCOLS = Set.of(ModelApiProtocolEnum.RESPONSES);

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String label() {
        return "AWS Bedrock Responses 转 Converse";
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
        return AwsBedrockConverseSupport.buildConverseBody(responsesToChatShape(context.requestBody()));
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
    private Map<String, Object> responsesToChatShape(Map<String, Object> source) {
        Map<String, Object> body = new LinkedHashMap<>(source);
        Object input = source.get(AiPayloadFields.INPUT);
        if (input instanceof List<?>) {
            body.put(AiPayloadFields.MESSAGES, input);
        } else if (input != null) {
            body.put(AiPayloadFields.MESSAGES, List.of(Map.of(AiPayloadFields.ROLE, AiPayloadFields.USER, AiPayloadFields.CONTENT, input)));
        } else {
            body.put(AiPayloadFields.MESSAGES, List.of());
        }
        Object instructions = source.get(AiPayloadFields.INSTRUCTIONS);
        if (instructions != null) {
            body.put(AiPayloadFields.SYSTEM, instructions);
        }
        body.remove(AiPayloadFields.INPUT);
        body.remove(AiPayloadFields.INSTRUCTIONS);
        body.remove("previous_response_id");
        body.remove("text");
        body.remove("reasoning");
        body.remove("store");
        // max_output_tokens 不搬：Support.inferenceConfig 的别名吸收（max_completion_tokens / max_tokens /
        // max_output_tokens）已覆盖，搬了反而多一处口径
        return body;
    }
}

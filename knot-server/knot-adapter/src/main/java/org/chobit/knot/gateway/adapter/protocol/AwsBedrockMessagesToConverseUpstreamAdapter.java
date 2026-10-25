package org.chobit.knot.gateway.adapter.protocol;

import org.chobit.knot.gateway.adapter.support.aws.AwsBedrockConverseSupport;
import org.chobit.knot.gateway.adapter.upstream.UpstreamRequestContext;
import org.chobit.knot.gateway.constants.AiPayloadFields;
import org.chobit.knot.gateway.constants.enums.ModelApiProtocolEnum;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AWS Bedrock Converse 适配器：Anthropic Messages → Converse。
 *
 * <p><b>只服务 {@code MESSAGES} 一种入协议</b>：先把 Anthropic Messages 形状（system 消息提升、
 * content blocks 折文本）归一成 OpenAI chat 形状，再走 {@link AwsBedrockConverseSupport}
 * 折成 Converse 请求。只服务 {@code bedrock-runtime} 端点。</p>
 */
@Component
@Order(30)
public class AwsBedrockMessagesToConverseUpstreamAdapter implements UpstreamAdapter {

    public static final String CODE = "AWS_BEDROCK_MESSAGES_TO_CONVERSE";

    private static final Set<ModelApiProtocolEnum> PROTOCOLS = Set.of(ModelApiProtocolEnum.MESSAGES);

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String label() {
        return "AWS Bedrock Messages 转 Converse";
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
        return AwsBedrockConverseSupport.buildConverseBody(messagesToChatShape(context.requestBody()));
    }

    @Override
    public String handleResponse(String responseBody, UpstreamRequestContext context) {
        return AwsBedrockConverseSupport.handleConverseResponse(responseBody, context);
    }

    @Override
    public boolean streamSupported(UpstreamRequestContext context) {
        return false;
    }

    /**
     * Anthropic Messages 形状 → OpenAI chat 形状：system 角色消息提升为顶层 {@code system}，
     * content blocks 折成裸文本。{@code max_tokens} / {@code stop_sequences} 等字段名两协议一致，
     * 由 Support 的别名吸收处理，不搬。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> messagesToChatShape(Map<String, Object> source) {
        Map<String, Object> body = new LinkedHashMap<>(source);
        List<Map<String, Object>> messages = new ArrayList<>();
        Object system = source.get(AiPayloadFields.SYSTEM);
        if (system != null) {
            body.put(AiPayloadFields.SYSTEM, textOf(system));
        }
        Object sourceMessages = source.get(AiPayloadFields.MESSAGES);
        if (sourceMessages instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> raw)) {
                    continue;
                }
                Map<String, Object> message = (Map<String, Object>) raw;
                Map<String, Object> converted = new LinkedHashMap<>(message);
                converted.put(AiPayloadFields.CONTENT, textOf(message.get(AiPayloadFields.CONTENT)));
                messages.add(converted);
            }
        }
        body.put(AiPayloadFields.MESSAGES, messages);
        return body;
    }

    /** content blocks（{@code [{text: ...}]}）折成裸文本；已是字符串则原样。 */
    private static String textOf(Object content) {
        if (content == null) {
            return "";
        }
        if (content instanceof String value) {
            return value;
        }
        if (content instanceof List<?> list) {
            StringBuilder builder = new StringBuilder();
            for (Object item : list) {
                if (item instanceof Map<?, ?> raw) {
                    Object text = ((Map<String, Object>) raw).get("text");
                    if (text != null) {
                        builder.append(text);
                    }
                }
            }
            return builder.toString();
        }
        return String.valueOf(content);
    }
}

package org.chobit.knot.gateway.adapter.protocol;

import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.gateway.adapter.support.RequestFieldSupport;
import org.chobit.knot.gateway.adapter.support.anthropic.AnthropicMessagesRequestSupport;
import org.chobit.knot.gateway.adapter.support.anthropic.AnthropicMessagesResponseSupport;
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
 * OpenAI Responses → Anthropic Messages 适配器（泛化转换，不隶属单一供应商）。
 *
 * <p><b>只服务 {@code RESPONSES} 一种入协议</b>：请求侧 {@code input}（字符串或事件列表）折成
 * {@code messages}，{@code instructions} 提升为顶层 {@code system}，
 * {@code max_output_tokens→max_tokens}，移除 Responses 专有字段，{@code max_tokens} 缺省兜底 1024；
 * 响应侧把上游的 Messages 形状翻回 {@code response} 对象（output_text + status + usage，
 * 非 Messages 响应原样透传）。</p>
 */
@Component
@Order(10)
public class ResponsesToMessagesUpstreamAdapter implements UpstreamAdapter {

    public static final String CODE = "RESPONSES_TO_MESSAGES";

    private static final Set<ModelApiProtocolEnum> PROTOCOLS = Set.of(ModelApiProtocolEnum.RESPONSES);

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String label() {
        return "Responses 转 Messages";
    }

    @Override
    public Set<ModelApiProtocolEnum> protocol() {
        return PROTOCOLS;
    }

    @Override
    public String resolvePath(UpstreamRequestContext context, String defaultPath) {
        if (context.binding() != null && StringUtils.isNotBlank(context.binding().apiPath())) {
            return StringUtils.trim(context.binding().apiPath());
        }
        return ModelApiProtocolEnum.MESSAGES.defaultPath();
    }

    @Override
    public Object buildRequestBody(UpstreamRequestContext context) {
        return responsesToMessages(context.requestBody());
    }

    /** 响应侧：Messages → Responses {@code response} 对象（usage 走 Responses 明细位，错误体原样透传）。 */
    @Override
    public String handleResponse(String responseBody, UpstreamRequestContext context) {
        return AnthropicMessagesResponseSupport.messagesToResponses(responseBody, context);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> responsesToMessages(Map<String, Object> source) {
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
        RequestFieldSupport.move(body, AiPayloadFields.MAX_OUTPUT_TOKENS, AiPayloadFields.MAX_TOKENS);
        body.remove(AiPayloadFields.INPUT);
        body.remove(AiPayloadFields.INSTRUCTIONS);
        body.remove("previous_response_id");
        body.remove("text");
        body.remove("reasoning");
        body.remove("store");
        AnthropicMessagesRequestSupport.ensureMaxTokens(body);
        return body;
    }
}

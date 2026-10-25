package org.chobit.knot.gateway.adapter.protocol;

import org.chobit.knot.gateway.adapter.support.RequestFieldSupport;
import org.chobit.knot.gateway.adapter.support.anthropic.AnthropicMessagesRequestSupport;
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
 * OpenAI ChatCompletions → Anthropic Messages 适配器（泛化转换，不隶属单一供应商）。
 *
 * <p><b>只服务 {@code CHAT_COMPLETIONS} 一种入协议</b>：system 角色消息提升为顶层
 * {@code system}，{@code max_completion_tokens→max_tokens}、{@code stop→stop_sequences}，
 * 移除 Anthropic 不支持的字段，{@code max_tokens} 缺省兜底 1024。</p>
 */
@Component
@Order(10)
public class ChatCompletionsToMessagesUpstreamAdapter implements UpstreamAdapter {

    public static final String CODE = "CHAT_COMPLETIONS_TO_MESSAGES";

    private static final Set<ModelApiProtocolEnum> PROTOCOLS = Set.of(ModelApiProtocolEnum.CHAT_COMPLETIONS);

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String label() {
        return "ChatCompletions 转 Messages";
    }

    @Override
    public Set<ModelApiProtocolEnum> protocol() {
        return PROTOCOLS;
    }

    @Override
    public String resolvePath(UpstreamRequestContext context, String defaultPath) {
        if (context.binding() != null && org.apache.commons.lang3.StringUtils.isNotBlank(context.binding().apiPath())) {
            return org.apache.commons.lang3.StringUtils.trim(context.binding().apiPath());
        }
        return ModelApiProtocolEnum.MESSAGES.defaultPath();
    }

    @Override
    public Object buildRequestBody(UpstreamRequestContext context) {
        return chatCompletionsToMessages(context.requestBody());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> chatCompletionsToMessages(Map<String, Object> source) {
        Map<String, Object> body = new LinkedHashMap<>(source);
        List<Map<String, Object>> messages = new ArrayList<>();
        Object sourceMessages = source.get(AiPayloadFields.MESSAGES);
        if (sourceMessages instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> map)) {
                    continue;
                }
                Map<String, Object> message = (Map<String, Object>) map;
                Object role = message.get(AiPayloadFields.ROLE);
                if (AiPayloadFields.SYSTEM.equals(role)) {
                    body.putIfAbsent(AiPayloadFields.SYSTEM, message.get(AiPayloadFields.CONTENT));
                    continue;
                }
                messages.add(message);
            }
        }
        body.put(AiPayloadFields.MESSAGES, messages);
        RequestFieldSupport.move(body, AiPayloadFields.MAX_COMPLETION_TOKENS, AiPayloadFields.MAX_TOKENS);
        RequestFieldSupport.move(body, AiPayloadFields.STOP, AiPayloadFields.STOP_SEQUENCES);
        body.remove("n");
        body.remove("presence_penalty");
        body.remove("frequency_penalty");
        body.remove("logit_bias");
        body.remove("response_format");
        AnthropicMessagesRequestSupport.ensureMaxTokens(body);
        return body;
    }
}

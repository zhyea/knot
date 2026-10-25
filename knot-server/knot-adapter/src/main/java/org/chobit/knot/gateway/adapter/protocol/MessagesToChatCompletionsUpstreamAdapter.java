package org.chobit.knot.gateway.adapter.protocol;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.gateway.adapter.support.RequestFieldSupport;
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
 * Anthropic Messages → OpenAI ChatCompletions 适配器（泛化转换，不隶属单一供应商）。
 *
 * <p><b>只服务 {@code MESSAGES} 一种入协议</b>：OpenAI 系其余协议（ChatCompletions / Embeddings /
 * Image 等）的请求与响应均为原样透传，由 {@link PassthroughUpstreamAdapter} 承担，不在本类重复。</p>
 */
@Component
@Order(100)
@RequiredArgsConstructor
public class MessagesToChatCompletionsUpstreamAdapter implements UpstreamAdapter {

    public static final String CODE = "MESSAGES_TO_CHAT_COMPLETIONS";

    private static final Set<ModelApiProtocolEnum> PROTOCOLS = Set.of(ModelApiProtocolEnum.MESSAGES);

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String label() {
        return "Messages 转 ChatCompletions（OpenAI 兼容）";
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
        return ModelApiProtocolEnum.CHAT_COMPLETIONS.defaultPath();
    }

    @Override
    public Object buildRequestBody(UpstreamRequestContext context) {
        return anthropicMessagesToChatCompletions(context.requestBody());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> anthropicMessagesToChatCompletions(Map<String, Object> source) {
        Map<String, Object> body = new LinkedHashMap<>(source);
        List<Map<String, Object>> messages = new ArrayList<>();
        Object system = source.get(AiPayloadFields.SYSTEM);
        if (system != null) {
            messages.add(Map.of(AiPayloadFields.ROLE, AiPayloadFields.SYSTEM, AiPayloadFields.CONTENT, system));
        }
        Object sourceMessages = source.get(AiPayloadFields.MESSAGES);
        if (sourceMessages instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    messages.add((Map<String, Object>) map);
                }
            }
        }
        if (!messages.isEmpty()) {
            body.put(AiPayloadFields.MESSAGES, messages);
        }
        RequestFieldSupport.move(body, AiPayloadFields.STOP_SEQUENCES, AiPayloadFields.STOP);
        body.remove(AiPayloadFields.SYSTEM);
        body.remove("top_k");
        return body;
    }
}

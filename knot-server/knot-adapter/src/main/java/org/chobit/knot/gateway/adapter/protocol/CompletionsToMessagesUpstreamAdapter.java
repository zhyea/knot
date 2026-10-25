package org.chobit.knot.gateway.adapter.protocol;

import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.gateway.adapter.support.RequestFieldSupport;
import org.chobit.knot.gateway.adapter.support.anthropic.AnthropicMessagesRequestSupport;
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
 * OpenAI Completions → Anthropic Messages 适配器（泛化转换，不隶属单一供应商）。
 *
 * <p><b>只服务 {@code COMPLETIONS} 一种入协议</b>：{@code prompt} 折成单条 user 消息，
 * {@code stop→stop_sequences}，移除 Completions 专有字段，{@code max_tokens} 缺省兜底 1024。</p>
 */
@Component
@Order(10)
public class CompletionsToMessagesUpstreamAdapter implements UpstreamAdapter {

    public static final String CODE = "COMPLETIONS_TO_MESSAGES";

    private static final Set<ModelApiProtocolEnum> PROTOCOLS = Set.of(ModelApiProtocolEnum.COMPLETIONS);

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String label() {
        return "Completions 转 Messages";
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
        return completionsToMessages(context.requestBody());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> completionsToMessages(Map<String, Object> source) {
        Map<String, Object> body = new LinkedHashMap<>(source);
        Object prompt = source.get(AiPayloadFields.PROMPT);
        body.put(AiPayloadFields.MESSAGES, List.of(Map.of(AiPayloadFields.ROLE, AiPayloadFields.USER, AiPayloadFields.CONTENT, prompt == null ? "" : prompt)));
        RequestFieldSupport.move(body, AiPayloadFields.STOP, AiPayloadFields.STOP_SEQUENCES);
        body.remove(AiPayloadFields.PROMPT);
        body.remove("suffix");
        body.remove("n");
        body.remove("echo");
        body.remove("logprobs");
        body.remove("presence_penalty");
        body.remove("frequency_penalty");
        body.remove("logit_bias");
        AnthropicMessagesRequestSupport.ensureMaxTokens(body);
        return body;
    }
}

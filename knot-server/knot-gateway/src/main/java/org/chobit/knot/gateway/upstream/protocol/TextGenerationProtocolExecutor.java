package org.chobit.knot.gateway.upstream.protocol;

import org.chobit.knot.gateway.adapter.upstream.UpstreamRequestContext;
import org.chobit.knot.gateway.config.GatewayUpstreamClientProperties;
import org.chobit.knot.gateway.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.gateway.upstream.usage.UsageExtractorRegistry;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.EnumSet;
import java.util.Set;

@Component
@Order(10)
public class TextGenerationProtocolExecutor extends AbstractUpstreamProtocolExecutor {

    private static final Set<ModelApiProtocolEnum> PROTOCOLS = EnumSet.of(
            ModelApiProtocolEnum.CHAT_COMPLETIONS,
            ModelApiProtocolEnum.RESPONSES,
            ModelApiProtocolEnum.MESSAGES,
            ModelApiProtocolEnum.COMPLETIONS
    );

    /**
     * Constructs a new instance.
     */
    public TextGenerationProtocolExecutor(RestClient restClient,
                                          UsageExtractorRegistry usageExtractorRegistry,
                                          GatewayUpstreamClientProperties clientProperties) {
        super(restClient, usageExtractorRegistry, clientProperties);
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    @Override
    public boolean supports(ModelApiProtocolEnum protocol) {
        return protocol != null && PROTOCOLS.contains(protocol.canonical());
    }

    /**
     * 文本生成协议存在长耗时的 SSE 响应，必须边收边发。
     */
    @Override
    protected boolean supportsStreaming(UpstreamRequestContext context) {
        return true;
    }
}

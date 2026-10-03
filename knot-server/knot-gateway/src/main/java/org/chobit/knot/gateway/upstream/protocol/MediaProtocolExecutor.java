package org.chobit.knot.gateway.upstream.protocol;

import org.chobit.knot.gateway.adapter.auth.UpstreamAuthApplierCatalog;
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
@Order(30)
public class MediaProtocolExecutor extends AbstractUpstreamProtocolExecutor {

    private static final Set<ModelApiProtocolEnum> PROTOCOLS = EnumSet.of(
            ModelApiProtocolEnum.IMAGE_GENERATIONS,
            ModelApiProtocolEnum.IMAGE_EDITS,
            ModelApiProtocolEnum.IMAGE_VARIATIONS,
            ModelApiProtocolEnum.AUDIO_TRANSCRIPTIONS,
            ModelApiProtocolEnum.AUDIO_TRANSLATIONS,
            ModelApiProtocolEnum.AUDIO_SPEECH,
            ModelApiProtocolEnum.VIDEO_GENERATIONS
    );

    /**
     * Constructs a new instance.
     */
    public MediaProtocolExecutor(RestClient restClient,
                                 UsageExtractorRegistry usageExtractorRegistry,
                                 GatewayUpstreamClientProperties clientProperties,
                                 UpstreamAuthApplierCatalog authApplierCatalog) {
        super(restClient, usageExtractorRegistry, clientProperties, authApplierCatalog);
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    @Override
    public boolean supports(ModelApiProtocolEnum protocol) {
        return protocol != null && PROTOCOLS.contains(protocol.canonical());
    }

    /**
     * Audio Speech supports chunked audio/SSE responses when the request sets
     * {@code stream=true}; the remaining media protocols are buffered.
     */
    @Override
    protected boolean supportsStreaming(UpstreamRequestContext context) {
        return context != null
                && context.protocol() != null
                && ModelApiProtocolEnum.AUDIO_SPEECH == context.protocol().canonical();
    }
}

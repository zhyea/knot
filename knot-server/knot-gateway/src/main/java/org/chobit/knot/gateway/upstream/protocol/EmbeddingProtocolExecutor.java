package org.chobit.knot.gateway.upstream.protocol;

import org.chobit.knot.adapter.auth.UpstreamAuthApplierCatalog;
import org.chobit.knot.gateway.config.GatewayUpstreamClientProperties;
import org.chobit.knot.common.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.gateway.upstream.usage.UsageExtractorRegistry;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
@Order(20)
public class EmbeddingProtocolExecutor extends AbstractUpstreamProtocolExecutor {

    public EmbeddingProtocolExecutor(WebClient webClient,
                                     UsageExtractorRegistry usageExtractorRegistry,
                                     UpstreamAuthApplierCatalog authApplierCatalog,
                                     GatewayUpstreamClientProperties clientProperties) {
        super(webClient, usageExtractorRegistry, authApplierCatalog, clientProperties);
    }

    @Override
    public boolean supports(ModelApiProtocolEnum protocol) {
        return protocol != null && ModelApiProtocolEnum.EMBEDDINGS == protocol.canonical();
    }
}

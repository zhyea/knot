package org.chobit.knot.gateway.upstream.protocol;

import org.chobit.knot.gateway.adapter.auth.UpstreamAuthApplierCatalog;
import org.chobit.knot.gateway.config.GatewayUpstreamClientProperties;
import org.chobit.knot.gateway.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.gateway.upstream.usage.UsageExtractorRegistry;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
@Order(1000)
public class GenericProtocolExecutor extends AbstractUpstreamProtocolExecutor {

    /**
     * Constructs a new instance.
     */
    public GenericProtocolExecutor(WebClient webClient,
                                   UsageExtractorRegistry usageExtractorRegistry,
                                   UpstreamAuthApplierCatalog authApplierCatalog,
                                   GatewayUpstreamClientProperties clientProperties) {
        super(webClient, usageExtractorRegistry, authApplierCatalog, clientProperties);
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    @Override
    public boolean supports(ModelApiProtocolEnum protocol) {
        return true;
    }
}

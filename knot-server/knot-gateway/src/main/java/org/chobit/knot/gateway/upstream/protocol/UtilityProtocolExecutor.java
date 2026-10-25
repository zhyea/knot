package org.chobit.knot.gateway.upstream.protocol;

import org.chobit.knot.adapter.auth.UpstreamAuthApplierCatalog;
import org.chobit.knot.gateway.config.GatewayUpstreamClientProperties;
import org.chobit.knot.common.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.gateway.upstream.usage.UsageExtractorRegistry;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.EnumSet;
import java.util.Set;

@Component
@Order(40)
public class UtilityProtocolExecutor extends AbstractUpstreamProtocolExecutor {

    private static final Set<ModelApiProtocolEnum> PROTOCOLS = EnumSet.of(
            ModelApiProtocolEnum.RERANK,
            ModelApiProtocolEnum.MODERATIONS,
            ModelApiProtocolEnum.DECISIONS
    );

    /**
     * Constructs a new instance.
     */
    public UtilityProtocolExecutor(WebClient webClient,
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
        return protocol != null && PROTOCOLS.contains(protocol.canonical());
    }
}

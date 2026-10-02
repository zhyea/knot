package org.chobit.knot.gateway.upstream.protocol;

import org.chobit.knot.gateway.adapter.UpstreamAuthApplier;
import org.chobit.knot.gateway.config.GatewayUpstreamClientProperties;
import org.chobit.knot.gateway.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.gateway.upstream.usage.UsageExtractorRegistry;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.EnumSet;
import java.util.Set;

@Component
@Order(40)
public class UtilityProtocolExecutor extends AbstractUpstreamProtocolExecutor {

    private static final Set<ModelApiProtocolEnum> PROTOCOLS = EnumSet.of(
            ModelApiProtocolEnum.RERANK,
            ModelApiProtocolEnum.MODERATIONS
    );

    /**
     * Constructs a new instance.
     */
    public UtilityProtocolExecutor(RestClient restClient,
                                   UsageExtractorRegistry usageExtractorRegistry,
                                   GatewayUpstreamClientProperties clientProperties,
                                   UpstreamAuthApplier authApplier) {
        super(restClient, usageExtractorRegistry, clientProperties, authApplier);
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    @Override
    public boolean supports(ModelApiProtocolEnum protocol) {
        return protocol != null && PROTOCOLS.contains(protocol.canonical());
    }
}

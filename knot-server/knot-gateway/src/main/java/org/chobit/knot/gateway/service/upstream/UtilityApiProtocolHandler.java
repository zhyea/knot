package org.chobit.knot.gateway.service.upstream;

import org.chobit.knot.gateway.constants.ModelApiProtocol;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.EnumSet;
import java.util.Set;

@Component
@Order(40)
public class UtilityApiProtocolHandler extends AbstractApiProtocolHandler {

    private static final Set<ModelApiProtocol> PROTOCOLS = EnumSet.of(
            ModelApiProtocol.RERANK,
            ModelApiProtocol.MODERATIONS
    );

    public UtilityApiProtocolHandler(RestClient restClient) {
        super(restClient);
    }

    @Override
    public boolean supports(ModelApiProtocol protocol) {
        return protocol != null && PROTOCOLS.contains(protocol.canonical());
    }
}

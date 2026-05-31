package org.chobit.knot.gateway.service.upstream;

import org.chobit.knot.gateway.constants.enums.ModelApiProtocolEnum;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.EnumSet;
import java.util.Set;

@Component
@Order(40)
public class UtilityApiProtocolHandler extends AbstractApiProtocolHandler {

    private static final Set<ModelApiProtocolEnum> PROTOCOLS = EnumSet.of(
            ModelApiProtocolEnum.RERANK,
            ModelApiProtocolEnum.MODERATIONS
    );

    /**
     * Constructs a new instance.
     */
    public UtilityApiProtocolHandler(RestClient restClient) {
        super(restClient);
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    @Override
    public boolean supports(ModelApiProtocolEnum protocol) {
        return protocol != null && PROTOCOLS.contains(protocol.canonical());
    }
}

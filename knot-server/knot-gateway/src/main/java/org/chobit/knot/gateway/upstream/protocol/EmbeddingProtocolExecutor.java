package org.chobit.knot.gateway.upstream.protocol;

import org.chobit.knot.gateway.constants.enums.ModelApiProtocolEnum;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@Order(20)
public class EmbeddingProtocolExecutor extends AbstractUpstreamProtocolExecutor {

    /**
     * Constructs a new instance.
     */
    public EmbeddingProtocolExecutor(RestClient restClient) {
        super(restClient);
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    @Override
    public boolean supports(ModelApiProtocolEnum protocol) {
        return protocol != null && ModelApiProtocolEnum.EMBEDDINGS == protocol.canonical();
    }
}

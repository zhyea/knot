package org.chobit.knot.gateway.service.upstream;

import org.chobit.knot.gateway.constants.enums.ModelApiProtocolEnum;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@Order(20)
public class EmbeddingApiProtocolHandler extends AbstractApiProtocolHandler {

    /**
     * Constructs a new instance.
     */
    public EmbeddingApiProtocolHandler(RestClient restClient) {
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

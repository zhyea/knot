package org.chobit.knot.gateway.service.upstream;

import org.chobit.knot.gateway.constants.ModelApiProtocol;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@Order(20)
public class EmbeddingApiProtocolHandler extends AbstractApiProtocolHandler {

    public EmbeddingApiProtocolHandler(RestClient restClient) {
        super(restClient);
    }

    @Override
    public boolean supports(ModelApiProtocol protocol) {
        return protocol != null && ModelApiProtocol.EMBEDDINGS == protocol.canonical();
    }
}

package org.chobit.knot.gateway.service.upstream;

import org.chobit.knot.gateway.constants.ModelApiProtocol;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@Order(1000)
public class GenericApiProtocolHandler extends AbstractApiProtocolHandler {

    public GenericApiProtocolHandler(RestClient restClient) {
        super(restClient);
    }

    @Override
    public boolean supports(ModelApiProtocol protocol) {
        return true;
    }
}

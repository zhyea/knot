package org.chobit.knot.gateway.service.upstream;

import org.chobit.knot.gateway.constants.ModelApiProtocol;
import org.chobit.knot.gateway.service.ProxyService;

public interface ModelApiProtocolHandler {

    boolean supports(ModelApiProtocol protocol);

    ProxyService.ProxyResult execute(UpstreamRequestContext context, UpstreamProviderAdapter adapter);
}

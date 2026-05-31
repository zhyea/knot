package org.chobit.knot.gateway.service.upstream;

import org.chobit.knot.gateway.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.gateway.model.ProxyResult;

public interface ModelApiProtocolHandler {

    boolean supports(ModelApiProtocolEnum protocol);

    ProxyResult execute(UpstreamRequestContext context, UpstreamProviderAdapter adapter);
}

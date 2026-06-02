package org.chobit.knot.gateway.upstream.protocol;

import org.chobit.knot.gateway.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.gateway.model.ProxyResult;
import org.chobit.knot.gateway.upstream.UpstreamRequestContext;
import org.chobit.knot.gateway.upstream.provider.UpstreamProviderAdapter;

public interface UpstreamProtocolExecutor {

    boolean supports(ModelApiProtocolEnum protocol);

    ProxyResult execute(UpstreamRequestContext context, UpstreamProviderAdapter adapter);
}

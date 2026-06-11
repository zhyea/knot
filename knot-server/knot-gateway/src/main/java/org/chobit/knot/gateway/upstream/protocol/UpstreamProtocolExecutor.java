package org.chobit.knot.gateway.upstream.protocol;

import org.chobit.knot.gateway.adapter.request.UpstreamRequestAdapter;
import org.chobit.knot.gateway.adapter.upstream.UpstreamRequestContext;
import org.chobit.knot.gateway.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.gateway.model.ProxyResult;

public interface UpstreamProtocolExecutor {

    boolean supports(ModelApiProtocolEnum protocol);

    ProxyResult execute(UpstreamRequestContext context, UpstreamRequestAdapter adapter);
}

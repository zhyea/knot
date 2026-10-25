package org.chobit.knot.gateway.upstream.protocol;

import org.chobit.knot.adapter.protocol.UpstreamAdapter;
import org.chobit.knot.adapter.upstream.UpstreamRequestContext;
import org.chobit.knot.common.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.gateway.model.ProxyResult;

public interface UpstreamProtocolExecutor {

    boolean supports(ModelApiProtocolEnum protocol);

    ProxyResult execute(UpstreamRequestContext context, UpstreamAdapter adapter);
}

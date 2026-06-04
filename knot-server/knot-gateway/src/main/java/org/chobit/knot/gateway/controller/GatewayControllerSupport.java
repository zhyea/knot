package org.chobit.knot.gateway.controller;

import org.chobit.knot.gateway.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.gateway.runtime.GatewayRequestHandler;
import org.chobit.knot.gateway.vo.request.GatewayModelRequest;

public abstract class GatewayControllerSupport {

    private final GatewayRequestHandler requestHandler;

    protected GatewayControllerSupport(GatewayRequestHandler requestHandler) {
        this.requestHandler = requestHandler;
    }

    /**
     * Delegates a typed gateway API request to the shared request template.
     */
    protected Object handleRequest(String authorization,
                                   String rule,
                                   String traceparent,
                                   GatewayModelRequest request,
                                   ModelApiProtocolEnum protocol) {
        return requestHandler.handle(authorization, rule, traceparent, request, protocol);
    }
}

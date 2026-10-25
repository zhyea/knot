package org.chobit.knot.gateway.model.vo.request;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ModerationRequest extends GatewayModelRequest {

    public Object input;
}

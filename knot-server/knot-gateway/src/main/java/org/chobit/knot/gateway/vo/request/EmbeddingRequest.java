package org.chobit.knot.gateway.vo.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class EmbeddingRequest extends GatewayModelRequest {

    public Object input;

    @JsonProperty("encoding_format")
    public String encodingFormat;

    public Integer dimensions;

    public String user;
}

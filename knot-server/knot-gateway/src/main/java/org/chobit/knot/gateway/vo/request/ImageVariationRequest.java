package org.chobit.knot.gateway.vo.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ImageVariationRequest extends GatewayModelRequest {

    public Object image;

    public Integer n;

    public String size;

    @JsonProperty("response_format")
    public String responseFormat;

    public String user;
}

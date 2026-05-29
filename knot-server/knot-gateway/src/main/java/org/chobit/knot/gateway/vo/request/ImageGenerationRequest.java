package org.chobit.knot.gateway.vo.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ImageGenerationRequest extends GatewayModelRequest {

    public String prompt;

    public Integer n;

    public String size;

    public String quality;

    public String style;

    @JsonProperty("response_format")
    public String responseFormat;

    public String user;
}

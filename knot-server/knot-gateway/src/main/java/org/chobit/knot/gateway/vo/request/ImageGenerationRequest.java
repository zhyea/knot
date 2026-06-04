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

    public String background;

    public String moderation;

    @JsonProperty("output_format")
    public String outputFormat;

    @JsonProperty("output_compression")
    public Integer outputCompression;

    @JsonProperty("response_format")
    public String responseFormat;

    public String user;
}

package org.chobit.knot.gateway.vo.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class AudioTranslationRequest extends GatewayModelRequest {

    public Object file;

    public String prompt;

    @JsonProperty("response_format")
    public String responseFormat;

    public Double temperature;
}

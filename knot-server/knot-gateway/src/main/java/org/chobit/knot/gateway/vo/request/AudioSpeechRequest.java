package org.chobit.knot.gateway.vo.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class AudioSpeechRequest extends GatewayModelRequest {

    public String input;

    public String voice;

    @JsonProperty("response_format")
    public String responseFormat;

    public Double speed;
}

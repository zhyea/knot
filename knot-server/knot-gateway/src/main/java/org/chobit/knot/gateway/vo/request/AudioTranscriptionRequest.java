package org.chobit.knot.gateway.vo.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class AudioTranscriptionRequest extends GatewayModelRequest {

    public Object file;

    public String language;

    public String prompt;

    @JsonProperty("response_format")
    public String responseFormat;

    public Double temperature;

    @JsonProperty("timestamp_granularities")
    public List<String> timestampGranularities;
}

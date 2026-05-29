package org.chobit.knot.gateway.vo.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class CompletionRequest extends GatewayModelRequest {

    public Object prompt;

    public String suffix;

    @JsonProperty("max_tokens")
    public Integer maxTokens;

    public Double temperature;

    @JsonProperty("top_p")
    public Double topP;

    public Integer n;

    public Object stop;

    @JsonProperty("presence_penalty")
    public Double presencePenalty;

    @JsonProperty("frequency_penalty")
    public Double frequencyPenalty;

    @JsonProperty("logit_bias")
    public Map<String, Object> logitBias;

    public Boolean logprobs;

    public Boolean echo;

    public String user;
}

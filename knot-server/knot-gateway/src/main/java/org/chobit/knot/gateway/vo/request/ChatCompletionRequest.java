package org.chobit.knot.gateway.vo.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatCompletionRequest extends GatewayModelRequest {

    public List<Map<String, Object>> messages;

    public Double temperature;

    @JsonProperty("top_p")
    public Double topP;

    public Integer n;

    public Object stop;

    @JsonProperty("max_tokens")
    public Integer maxTokens;

    @JsonProperty("max_completion_tokens")
    public Integer maxCompletionTokens;

    @JsonProperty("presence_penalty")
    public Double presencePenalty;

    @JsonProperty("frequency_penalty")
    public Double frequencyPenalty;

    @JsonProperty("logit_bias")
    public Map<String, Object> logitBias;

    public Boolean logprobs;

    @JsonProperty("top_logprobs")
    public Integer topLogprobs;

    public List<Map<String, Object>> tools;

    @JsonProperty("tool_choice")
    public Object toolChoice;

    @JsonProperty("parallel_tool_calls")
    public Boolean parallelToolCalls;

    @JsonProperty("response_format")
    public Object responseFormat;

    public String user;

    public Object metadata;
}

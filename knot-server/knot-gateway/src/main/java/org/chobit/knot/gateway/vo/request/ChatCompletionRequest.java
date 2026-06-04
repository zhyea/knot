package org.chobit.knot.gateway.vo.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatCompletionRequest extends GatewayModelRequest {

    public List<Map<String, Object>> messages;

    public Object audio;

    public Double temperature;

    @JsonProperty("top_p")
    public Double topP;

    public List<String> modalities;

    public Integer n;

    public Object prediction;

    public Object stop;

    @JsonProperty("max_tokens")
    public Integer maxTokens;

    @JsonProperty("max_completion_tokens")
    public Integer maxCompletionTokens;

    @JsonProperty("reasoning_effort")
    public String reasoningEffort;

    @JsonProperty("presence_penalty")
    public Double presencePenalty;

    @JsonProperty("frequency_penalty")
    public Double frequencyPenalty;

    @JsonProperty("logit_bias")
    public Map<String, Object> logitBias;

    public Boolean logprobs;

    @JsonProperty("top_logprobs")
    public Integer topLogprobs;

    @JsonProperty("function_call")
    public Object functionCall;

    public List<Map<String, Object>> functions;

    public List<Map<String, Object>> tools;

    @JsonProperty("tool_choice")
    public Object toolChoice;

    @JsonProperty("parallel_tool_calls")
    public Boolean parallelToolCalls;

    @JsonProperty("response_format")
    public Object responseFormat;

    public Integer seed;

    @JsonProperty("service_tier")
    public String serviceTier;

    @JsonProperty("stream_options")
    public Object streamOptions;

    public Boolean store;

    public String user;

    public Object metadata;
}

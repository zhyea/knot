package org.chobit.knot.gateway.vo.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class MessageRequest extends GatewayModelRequest {

    @JsonProperty("max_tokens")
    public Integer maxTokens;

    public List<Map<String, Object>> messages;

    public Object system;

    public List<Map<String, Object>> tools;

    @JsonProperty("tool_choice")
    public Object toolChoice;

    public Double temperature;

    @JsonProperty("top_p")
    public Double topP;

    @JsonProperty("top_k")
    public Integer topK;

    @JsonProperty("stop_sequences")
    public List<String> stopSequences;

    public Object metadata;
}

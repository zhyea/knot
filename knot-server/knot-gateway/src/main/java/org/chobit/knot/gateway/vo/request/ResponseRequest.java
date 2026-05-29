package org.chobit.knot.gateway.vo.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResponseRequest extends GatewayModelRequest {

    public Object input;

    public String instructions;

    @JsonProperty("previous_response_id")
    public String previousResponseId;

    public List<Map<String, Object>> tools;

    @JsonProperty("tool_choice")
    public Object toolChoice;

    public Object text;

    public Object reasoning;

    @JsonProperty("max_output_tokens")
    public Integer maxOutputTokens;

    public Double temperature;

    @JsonProperty("top_p")
    public Double topP;

    public Boolean store;

    public Object metadata;

    public String user;
}

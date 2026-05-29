package org.chobit.knot.gateway.vo.request;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class GatewayModelRequest {

    public String model;

    public Boolean stream;

    private final Map<String, Object> extra = new LinkedHashMap<>();

    @JsonAnySetter
    public void putExtra(String name, Object value) {
        extra.put(name, value);
    }

    @JsonAnyGetter
    public Map<String, Object> getExtra() {
        return extra;
    }

    public Map<String, Object> toMap(ObjectMapper objectMapper) {
        return objectMapper.convertValue(this, new TypeReference<>() {
        });
    }
}

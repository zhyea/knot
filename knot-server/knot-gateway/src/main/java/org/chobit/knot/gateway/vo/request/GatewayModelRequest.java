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

    /**
     * Executes the public operation. Executes the public operation.
     */
    public String model;

    public Boolean stream;

    private final Map<String, Object> extra = new LinkedHashMap<>();

    @JsonAnySetter
    /**
     * Stores the supplied value.
     */
    public void putExtra(String name, Object value) {
        extra.put(name, value);
    }

    /**
     * Returns the requested value. Executes the public operation.
     */
    @JsonAnyGetter
    public Map<String, Object> getExtra() {
        return extra;
    }


}

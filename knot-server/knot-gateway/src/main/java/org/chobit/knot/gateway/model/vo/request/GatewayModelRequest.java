package org.chobit.knot.gateway.model.vo.request;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.LinkedHashMap;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class GatewayModelRequest {

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

    @JsonAnyGetter
    public Map<String, Object> getExtra() {
        return extra;
    }


}

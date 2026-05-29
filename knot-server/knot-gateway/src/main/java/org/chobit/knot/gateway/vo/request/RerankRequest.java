package org.chobit.knot.gateway.vo.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class RerankRequest extends GatewayModelRequest {

    public Object query;

    public Object documents;

    @JsonProperty("top_n")
    public Integer topN;

    @JsonProperty("return_documents")
    public Boolean returnDocuments;
}

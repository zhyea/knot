package org.chobit.knot.gateway.vo.request;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class VideoGenerationRequest extends GatewayModelRequest {

    public String prompt;

    public Object image;

    public Integer duration;

    public String size;

    public String quality;

    public Integer n;

    public String user;
}

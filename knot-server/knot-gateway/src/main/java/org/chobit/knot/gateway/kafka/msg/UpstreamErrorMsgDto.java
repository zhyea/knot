package org.chobit.knot.gateway.kafka.msg;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * attempt 级的上游异常三件套。
 *
 * @param httpStatus   上游响应状态码；未发出请求或拿不到时为 {@code null}
 * @param errorCode    网关侧业务码（{@code ProxyErrorCodeEnum}）
 * @param retryAfterMs 上游 {@code Retry-After} 换算的毫秒数
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record UpstreamErrorMsgDto(Integer httpStatus,
                                  String errorCode,
                                  Long retryAfterMs) {
}

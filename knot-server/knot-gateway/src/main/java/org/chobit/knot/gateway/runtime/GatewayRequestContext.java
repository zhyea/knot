package org.chobit.knot.gateway.runtime;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.gateway.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.gateway.model.GatewayRoutingInfo;
import org.chobit.knot.gateway.model.ResolvedRouting;

import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@Accessors(fluent = true)
public class GatewayRequestContext {

    private final String authorization;
    private final String apiKey;
    private final String ruleCode;
    private final GatewayTraceContext traceContext;
    private final ModelApiProtocolEnum protocol;
    /**
     * 粘性路由键（请求头 {@code X-Knot-Sticky-Key} 原值，未传为 null）。
     * 只在模型池开启粘性路由时参与池内首选的选择，其余场景为空不影响链路。
     */
    private final String stickyKey;
    /**
     * 请求进入网关模板处理的时刻（{@link System#currentTimeMillis()}），构造时固定。
     * 首 token 响应时间（响应头 {@code X-Knot-First-Token-Time}）的测量起点：
     * 端到端口径，含鉴权、路由解析、插件、频控与上游首字节，重试/failover 耗时亦计入。
     */
    private final long receivedAtMillis;
    private final Map<String, Object> attributes = new LinkedHashMap<>();

    @Setter
    private ResolvedRouting routing;

    public GatewayRequestContext(String authorization,
                                 String apiKey,
                                 String ruleCode,
                                 GatewayTraceContext traceContext,
                                 ModelApiProtocolEnum protocol,
                                 String stickyKey) {
        this.authorization = authorization;
        this.apiKey = apiKey;
        this.ruleCode = ruleCode;
        this.traceContext = traceContext;
        this.protocol = protocol;
        this.stickyKey = stickyKey;
        this.receivedAtMillis = System.currentTimeMillis();
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    public GatewayRoutingInfo routingInfo() {
        return routing == null ? null : routing.routingInfo();
    }

    /**
     * Returns the normalized traceparent propagated by the gateway.
     */
    public String traceparent() {
        return traceContext.traceparent();
    }

    /**
     * Returns the request trace id used in logs and downstream propagation.
     */
    public String traceId() {
        return traceContext.traceId();
    }

    /**
     * Returns the gateway span id generated for this request.
     */
    public String spanId() {
        return traceContext.spanId();
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    public void putAttribute(String key, Object value) {
        String normalizedKey = StringUtils.trimToNull(key);
        if (normalizedKey != null) {
            attributes.put(normalizedKey, value);
        }
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    @SuppressWarnings("unchecked")
    public <T> T attribute(String key) {
        return (T) attributes.get(key);
    }
}

package org.chobit.knot.gateway.runtime;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.common.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.gateway.model.GatewayRoutingContext;
import org.chobit.knot.gateway.model.ResolvedRouting;

import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@Accessors(fluent = true)
public class GatewayRequestContext {

    private final String authorization;
    /**
     * API key（Bearer 提取结果）。构造后才写入；头校验失败时请求上下文依然存在，此时本字段为 {@code null}。
     */
    @Setter
    private String apiKey;
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
                                 String ruleCode,
                                 GatewayTraceContext traceContext,
                                 ModelApiProtocolEnum protocol,
                                 String stickyKey) {
        this.authorization = authorization;
        this.ruleCode = ruleCode;
        this.traceContext = traceContext;
        this.protocol = protocol;
        this.stickyKey = stickyKey;
        this.receivedAtMillis = System.currentTimeMillis();
    }

    public GatewayRoutingContext routingContext() {
        return routing == null ? null : routing.routingContext();
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
     * 网关自生成的请求标识（32 位小写 hex）：上游请求头 {@code X-Request-Id} 与数仓消息 {@code request_id} 的唯一来源。
     * 与 {@code upstream_request_id}（上游返回的 id）不同，二者共同构成一次请求的双向对账键。
     */
    public String requestId() {
        return traceContext.requestId();
    }

    /**
     * Returns the gateway span id generated for this request.
     */
    public String spanId() {
        return traceContext.spanId();
    }

    public void putAttribute(String key, Object value) {
        String normalizedKey = StringUtils.trimToNull(key);
        if (normalizedKey != null) {
            attributes.put(normalizedKey, value);
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T attribute(String key) {
        return (T) attributes.get(key);
    }
}

package org.chobit.knot.gateway.config;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A 组（P6）落地验证：
 * <ul>
 *     <li>A1 默认值：总响应超时 responseTimeout = 120s。</li>
 *     <li>A3 数值边界：连接池/缓冲区整数字段带 @Min(1)，误配 0 必须被 Validator 拒绝。</li>
 * </ul>
 */
class GatewayUpstreamClientPropertiesTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void defaults_includeResponseTimeout120s() {
        GatewayUpstreamClientProperties properties = new GatewayUpstreamClientProperties();
        assertEquals(Duration.ofSeconds(120), properties.getResponseTimeout());
        assertEquals(Duration.ofSeconds(5), properties.getConnectTimeout());
        assertEquals(Duration.ofSeconds(120), properties.getReadTimeout());
        assertEquals(200, properties.getMaxTotalConnections());
        // 2026-10-20：per-route 上限随 HC5 一起退役，reactor-netty 只有全局 maxConnections
        assertFalse(hasProperty(properties, "maxConnectionsPerRoute"),
                "per-route 上限已移除，不该再出现在配置类上");
        assertEquals(Duration.ofSeconds(10), properties.getMaxIdleTime());
        assertEquals(8192, properties.getStreamBufferSize());
        // B1 异步化承载池默认与 Tomcat 默认 worker 数对齐
        assertEquals(200, properties.getAsyncExecutorThreads());
        // B2 全局请求预算默认 600s，与既有流式 async 兜底对齐
        assertEquals(Duration.ofSeconds(600), properties.getRequestBudget());
    }

    @Test
    void zeroConnectionPoolOrBuffer_rejectedByMinConstraint() {
        GatewayUpstreamClientProperties properties = new GatewayUpstreamClientProperties();
        properties.setMaxTotalConnections(0);
        properties.setStreamBufferSize(0);
        properties.setAsyncExecutorThreads(0);

        Set<ConstraintViolation<GatewayUpstreamClientProperties>> violations = validator.validate(properties);
        assertFalse(violations.isEmpty(), "maxTotalConnections/streamBufferSize/asyncExecutorThreads=0 必须触发 @Min 校验");
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("maxTotalConnections")));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("streamBufferSize")));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("asyncExecutorThreads")));
    }

    @Test
    void validPositiveValues_passValidation() {
        GatewayUpstreamClientProperties properties = new GatewayUpstreamClientProperties();
        properties.setMaxTotalConnections(500);
        properties.setStreamBufferSize(16384);
        assertTrue(validator.validate(properties).isEmpty(), "合法正值不应触发校验");
    }

    private static boolean hasProperty(Object bean, String propertyName) {
        return java.util.Arrays.stream(org.springframework.beans.BeanUtils
                        .getPropertyDescriptors(bean.getClass()))
                .anyMatch(descriptor -> propertyName.equals(descriptor.getName()));
    }
}

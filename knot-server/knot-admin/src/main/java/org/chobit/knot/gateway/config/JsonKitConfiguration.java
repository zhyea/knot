package org.chobit.knot.gateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.chobit.knot.gateway.util.JsonKit;
import org.springframework.context.annotation.Configuration;

/**
 * 启动时将 Spring Boot 的 {@link ObjectMapper} 同步到 {@link JsonKit}，
 * 保证工具类序列化与 MVC 请求/响应体一致。
 */
@Configuration(proxyBeanMethods = false)
public class JsonKitConfiguration {

    public JsonKitConfiguration(ObjectMapper objectMapper) {
        JsonKit.init(objectMapper);
    }
}

package org.chobit.knot.gateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.chobit.knot.gateway.util.JsonKit;
import org.springframework.context.annotation.Configuration;

/**
 * 鍚姩鏃跺皢 Spring Boot 鐨?{@link ObjectMapper} 鍚屾鍒?{@link JsonKit}锛?
 * 淇濊瘉宸ュ叿绫诲簭鍒楀寲涓?MVC 璇锋眰/鍝嶅簲浣撲竴鑷淬€?
 */
@Configuration(proxyBeanMethods = false)
public class JsonKitConfiguration {

    /**
     * Constructs a new instance.
     */
    public JsonKitConfiguration(ObjectMapper objectMapper) {
        JsonKit.init(objectMapper);
    }
}

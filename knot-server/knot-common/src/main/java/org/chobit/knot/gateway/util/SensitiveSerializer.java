package org.chobit.knot.gateway.util;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

/**
 * 敏感字段脱敏序列化器。配合 {@link Sensitive} 注解使用，仅作用于审计快照的专用 mapper。
 *
 * <p>脱敏规则与历史实现保持一致：长度不超过 10 的原样返回；否则保留前 7 位与后 4 位，
 * 中间以 {@code ...} 替换。非 {@link String} 类型交由默认序列化器写出。
 */
public class SensitiveSerializer extends JsonSerializer<Object> {

    public static final String MASK_MIDDLE = "...";

    @Override
    public void serialize(Object value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value == null) {
            gen.writeNull();
            return;
        }
        if (value instanceof String s) {
            gen.writeString(mask(s));
            return;
        }
        serializers.defaultSerializeValue(value, gen);
    }

    /**
     * 与 {@code RoutingConsumerService.maskSecretKey} 同源的脱敏算法，集中在此作为单一实现。
     */
    public static String mask(String value) {
        if (value == null || value.length() <= 10) {
            return value;
        }
        return value.substring(0, 7) + MASK_MIDDLE + value.substring(value.length() - 4);
    }
}

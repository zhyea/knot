package org.chobit.knot.common.util;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;

import java.util.List;

/**
 * 审计快照专用 Jackson 模块：通过 {@link BeanSerializerModifier#changeProperties} 把敏感属性
 * 替换为 {@link SensitiveSerializer}。判定有两种：带 {@link Sensitive} 注解的属性，或名称命中
 * 兜底清单（Lombok {@code @Data} 等经 getter 序列化、字段注解不可达，如 {@code UserEntity.passwordHash}）。
 * 仅在 {@link JsonKit} 的审计专用 mapper 上注册，不影响普通 MVC 序列化。
 */
public class MaskingModule extends SimpleModule {

    /**
     * 敏感字段名兜底清单。新增敏感字段时，优先用 {@link Sensitive} 注解；
     * 当实体由 Lombok 生成 getter 导致字段注解不可达时，再补到此清单。
     */
    private static final java.util.Set<String> SENSITIVE_NAMES =
            java.util.Set.of("secretKey", "passwordHash");

    public MaskingModule() {
        super("MaskingModule");
        setSerializerModifier(new BeanSerializerModifier() {
            @Override
            public List<BeanPropertyWriter> changeProperties(SerializationConfig config,
                                                             BeanDescription beanDesc,
                                                             List<BeanPropertyWriter> beanProperties) {
                for (BeanPropertyWriter writer : beanProperties) {
                    boolean sensitive = writer.getAnnotation(Sensitive.class) != null
                            || SENSITIVE_NAMES.contains(writer.getName());
                    if (sensitive) {
                        writer.assignSerializer(new SensitiveSerializer());
                    }
                }
                return beanProperties;
            }
        });
    }
}

package org.chobit.knot.gateway.util;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;

import java.util.List;

/**
 * 审计快照专用 Jackson 模块。通过 {@link BeanSerializerModifier#changeProperties} 将敏感属性
 * 替换为 {@link SensitiveSerializer}，实现字段级脱敏。
 *
 * <p>判定两种方式，确保不同实体形态都能覆盖：
 * <ul>
 *   <li>带 {@link Sensitive} 注解的属性（record 组件、手写 getter 等注解可达场景）；</li>
 *   <li>名称命中兜底清单（Lombok {@code @Data} 等经由 getter 序列化、字段注解未必被 Jackson
 *       识别的场景，例如 {@code UserEntity.passwordHash}）。</li>
 * </ul>
 *
 * <p>仅在 {@link JsonKit} 的审计专用 mapper 上注册，绝不影响普通 MVC 序列化。
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

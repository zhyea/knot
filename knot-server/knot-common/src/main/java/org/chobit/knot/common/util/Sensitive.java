package org.chobit.knot.common.util;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记字段为敏感信息：仅在 {@link MaskingModule} 注册的审计专用 mapper 上生效，
 * 普通 MVC 序列化不读取本注解。
 * 被标记的字段在审计快照（{@code JsonKit.toMaskedMap}）序列化时脱敏。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
public @interface Sensitive {
}

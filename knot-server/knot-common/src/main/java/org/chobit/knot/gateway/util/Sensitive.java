package org.chobit.knot.gateway.util;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记字段为敏感信息。仅在 {@link MaskingModule} 注册的审计专用 mapper 上生效，
 * 普通 MVC 序列化（Spring 默认 {@code ObjectMapper}）不会读取本注解，故不影响正常接口返回。
 *
 * <p>被标记的字段在审计快照（{@code JsonKit.toMaskedMap}）序列化时会被脱敏，避免明文凭据、
 * 密钥、密码哈希落入操作日志。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
public @interface Sensitive {
}

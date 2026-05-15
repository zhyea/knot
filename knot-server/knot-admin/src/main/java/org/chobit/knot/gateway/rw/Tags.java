package org.chobit.knot.gateway.rw;

import org.springframework.web.bind.annotation.ResponseBody;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 可选：为单个接口写入 {@link org.chobit.knot.gateway.ApiResponse#tags()}（多个值以逗号拼接）。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Documented
@ResponseBody
public @interface Tags {

    String[] value() default {};
}

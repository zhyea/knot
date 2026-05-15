package org.chobit.knot.gateway.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解
 * 用于标记需要记录操作日志的方法
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperationLog {
    
    /**
     * 模块名称，如 user、enum、provider、model等
     */
    String module();
    
    /**
     * 操作类型，如 CREATE、UPDATE、DELETE、LOGIN等
     */
    String operation();
    
    /**
     * 实体类型，如 User、EnumConfig等（支持SpEL表达式）
     */
    String entityType() default "";
    
    /**
     * 实体ID（支持SpEL表达式，如 #id）
     */
    String entityId() default "";
    
    /**
     * 实体名称或标识（支持SpEL表达式，如 #request.username）
     */
    String entityName() default "";
    
    /**
     * 操作描述（支持SpEL表达式）
     */
    String description() default "";
    
    /**
     * 方法成功返回后解析实体 ID 的 SpEL，可使用 #result 及方法参数名（如 #id、#request）
     */
    String entityIdAfter() default "";

    /**
     * 方法成功返回后解析实体名称的 SpEL，可使用 #result 及方法参数名
     */
    String entityNameAfter() default "";

    /**
     * 是否记录旧值（需要在方法参数中包含 id）
     */
    boolean recordOldValue() default false;
    
    /**
     * 是否记录新值（记录返回结果）
     */
    boolean recordNewValue() default false;
}

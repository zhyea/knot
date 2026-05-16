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
     * 是否在执行业务方法前记录旧值（需配合 {@link #oldValueSpel()}，在 {@code proceed} 之前求值）
     */
    boolean recordOldValue() default false;

    /**
     * 求旧值的 SpEL，仅在方法参数等上下文中求值（无 {@code #result}），可引用 Spring Bean，例如
     * {@code @enumConfigService.getById(#id)}
     */
    String oldValueSpel() default "";

    /**
     * 是否在成功后记录新值（需配合 {@link #newValueSpel()} 或直接序列化 {@code #result}）
     */
    boolean recordNewValue() default false;

    /**
     * 求新值的 SpEL，在业务方法成功返回后求值，可使用 {@code #result} 与方法参数名。为空时若
     * {@link #recordNewValue()} 为 true，则序列化方法返回值。
     */
    String newValueSpel() default "";
}

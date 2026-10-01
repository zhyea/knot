package org.chobit.knot.gateway.vo.common;

/**
 * 编码可用性检查结果：所有 {@code /check-code} 端点共用的响应结构。
 * 序列化后与原先的 {@code Map.of("available", boolean)} 完全一致（{"available": true}），
 * 只是把弱 DTO 换成具名类型，字段名由 Java 组件决定，不再依赖字符串键。
 */
public record CodeAvailability(boolean available) {
}

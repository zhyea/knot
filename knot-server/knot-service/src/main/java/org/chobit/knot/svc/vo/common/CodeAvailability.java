package org.chobit.knot.svc.vo.common;

/**
 * 编码可用性检查结果：所有 {@code /check-code} 端点共用的响应结构，序列化为单个布尔字段 {@code available}。
 * 字段名由 Java 组件决定，不依赖字符串键。
 */
public record CodeAvailability(boolean available) {
}

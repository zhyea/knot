package org.chobit.knot.common.model;

/**
 * 限流策略：只作用于模型与路由规则两层。
 *
 * 两个维度都是分钟级固定窗口：{@code rpm} 请求进入即计数、超出即拒；
 * {@code tpm} 请求成功后按真实用量累加，超限在后续请求上拦住。
 *
 * @param rpm 每分钟请求数上限；{@code <= 0} 表示这一维度不限
 * @param tpm 每分钟 token 上限；{@code <= 0} 表示这一维度不限
 */
public record RateLimitPolicy(int rpm, int tpm) {
}

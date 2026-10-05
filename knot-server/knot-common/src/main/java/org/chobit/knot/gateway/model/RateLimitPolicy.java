package org.chobit.knot.gateway.model;

/**
 * 限流策略：只作用于<b>模型</b>与<b>路由规则</b>两层。
 *
 * <p>两个维度都是分钟级固定窗口：</p>
 * <ul>
 *   <li>{@code rpm}（requests per minute）：请求进入即计数，超出即拒；</li>
 *   <li>{@code tpm}（tokens per minute）：请求成功后按真实用量累加——请求前算不出 token 数，
 *       只能事后记账，因此超限是在<b>后续请求</b>上拦住的。</li>
 * </ul>
 *
 * @param rpm 每分钟请求数上限；{@code <= 0} 表示这一维度不限
 * @param tpm 每分钟 token 上限；{@code <= 0} 表示这一维度不限
 */
public record RateLimitPolicy(int rpm, int tpm) {
}

package org.chobit.knot.gateway.model;

import java.math.BigDecimal;

/**
 * 限额策略：只作用于<b>应用</b>、<b>供应商账户</b>与<b>路由消费者</b>三层。
 *
 * <p>两个维度都是累计量，按 {@code window} 指定的窗口统计，<b>窗口结束即清零</b>；
 * 且都在请求成功后才记账——上游失败与 failover 重试不消耗额度。</p>
 *
 * @param maxTokens 窗口内 token 上限；{@code <= 0} 表示这一维度不限
 * @param costLimit 窗口内成本上限；{@code null} 或 {@code <= 0} 表示这一维度不限
 * @param currency  成本币种（USD / CNY）。计费结果币种与本值一致时才累加；
 *                  不做汇率换算——币种不符时跳过，避免混算出错账
 * @param window    统计窗口（MINUTE / HOUR / DAY / WEEK / MONTH），
 *                  空值或非法值按 {@code MONTH} 处理
 */
public record QuotaPolicy(long maxTokens, BigDecimal costLimit, String currency, String window) {
}

package org.chobit.knot.svc.dto.routing;

import org.chobit.knot.adapter.model.usage.NormalizedUsageDetail;

/**
 * 计费明细行（与网关 {@link NormalizedUsageDetail} 同构，金额为十进制普通字符串契约）。
 *
 * <p>{@code price} 是规则配置的<b>原始单价</b>，基准由同行 {@code unit} 声明
 * （{@code PER_SECOND} = 每秒价、{@code 1M_TOKENS} = 每百万 Token 价）。
 * 复算公式 {@code cost = quantity × price ÷ unitSize(unit)}，分母由 {@code unit} 推导，
 * 无需在契约里另传 {@code unitSize}。</p>
 *
 * @param type     明细类型（TOKEN: UNCACHED_INPUT / CACHED_READ / CACHED_WRITE / OUTPUT；媒体: 模式名）
 * @param quantity 计费量（token 数 / 张数 / 秒数）
 * @param unit     量纲（{@code BillingUnitEnum} 编码），与 quantity 同行给出
 * @param cost     本行金额（十进制普通字符串）
 * @param price    规则配置的原始单价，基准由 unit 声明（十进制普通字符串）
 */
public record BillingFactAmountLine(String type, Long quantity, String unit, String cost, String price) {

    public static BillingFactAmountLine from(NormalizedUsageDetail detail) {
        return new BillingFactAmountLine(detail.type(), detail.quantity(), detail.unit(),
                detail.cost(), detail.price());
    }
}

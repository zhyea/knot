package org.chobit.knot.gateway.constants.enums;

import java.util.List;

/**
 * 计费对账状态（{@code POST /api/billing/reconciliation}）。
 *
 * <p><b>本期只建枚举 + 单测，不注册 {@code EnumOptionRegistry}、不改接口返回。</b>
 * 该接口当前是桩实现（{@code BillingService#reconcile} 硬编码返回 0/0/"DONE"），
 * 且库中没有用量 / 账单流水表，比对数据源缺失。给它套数字 code 只会让桩看起来更正式，
 * 故等真实对账实现落地后再切换。</p>
 */
public enum ReconciliationStatusEnum implements NumericEnumOption {
    PENDING(1, "待对账"),
    DONE(2, "已完成"),
    FAILED(3, "失败");

    private final int code;
    private final String label;

    ReconciliationStatusEnum(int code, String label) {
        this.code = code;
        this.label = label;
    }

    @Override
    public int code() {
        return code;
    }

    @Override
    public String label() {
        return label;
    }

    public static ReconciliationStatusEnum fromCode(Integer code) {
        return NumericEnumOption.fromCode(values(), code);
    }

    public static ReconciliationStatusEnum requireCode(Integer code, String errorMessage) {
        return NumericEnumOption.requireCode(values(), code, errorMessage);
    }

    public static List<Integer> codes() {
        return NumericEnumOption.codes(values());
    }
}

package org.chobit.knot.gateway.constants.enums;

import java.util.List;

/**
 * 通知发送状态（{@code kb_notification_records.send_status}）。
 *
 * <p>RETRYING 是明确的中间态，不能折叠回 PENDING —— 重试次数与告警依赖该区分。
 * 发送记录不引入 {@code is_deleted}。</p>
 */
public enum NotificationSendStatusEnum implements NumericEnumOption {
    PENDING(1, "待发送"),
    SENT(2, "已发送"),
    FAILED(3, "失败"),
    RETRYING(4, "重试中");

    private final int code;
    private final String label;

    NotificationSendStatusEnum(int code, String label) {
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

    public static NotificationSendStatusEnum fromCode(Integer code) {
        return NumericEnumOption.fromCode(values(), code);
    }

    public static NotificationSendStatusEnum requireCode(Integer code, String errorMessage) {
        return NumericEnumOption.requireCode(values(), code, errorMessage);
    }

    public static List<Integer> codes() {
        return NumericEnumOption.codes(values());
    }
}

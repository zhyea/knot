package org.chobit.knot.common.constants.enums;

import java.util.List;

/**
 * 外部模型同步状态（{@code kx_model_items.sync_status}）。
 * 同步失败必须真实写入 {@code FAILED}；记录停在 {@code PENDING} 即表示待同步。
 */
public enum ExternalModelSyncStatusEnum implements NumericEnumOption {
    PENDING(1, "待同步"),
    SYNCED(2, "已同步"),
    FAILED(3, "失败");

    private final int code;
    private final String label;

    ExternalModelSyncStatusEnum(int code, String label) {
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

    public static ExternalModelSyncStatusEnum fromCode(Integer code) {
        return NumericEnumOption.fromCode(values(), code);
    }

    public static ExternalModelSyncStatusEnum requireCode(Integer code, String errorMessage) {
        return NumericEnumOption.requireCode(values(), code, errorMessage);
    }

    public static List<Integer> codes() {
        return NumericEnumOption.codes(values());
    }
}

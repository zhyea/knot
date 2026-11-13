package org.chobit.knot.common.moderation;

import org.chobit.knot.common.constants.enums.NumericEnumOption;

/**
 * 敏感词严重度：通过 {@code /api/enum-options} 以 {@code ModerationSeverityEnum} 键下发前端。
 * 严重度挂在词条上而非分类（见 {@link ModerationTerm}）；触发处置动作的阈值
 * {@code SEVERITY_THRESHOLD} 在 knot-plugin 的 {@code ModerationConstants}，不在此枚举。
 */
public enum ModerationSeverityEnum implements NumericEnumOption {
    LOW(1, "低"),
    MEDIUM(2, "中"),
    HIGH(3, "高");

    private final int code;
    private final String label;

    ModerationSeverityEnum(int code, String label) {
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

    public static ModerationSeverityEnum fromCode(Integer code) {
        return NumericEnumOption.fromCode(values(), code);
    }
}

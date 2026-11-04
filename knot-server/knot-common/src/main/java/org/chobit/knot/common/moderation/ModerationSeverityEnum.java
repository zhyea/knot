package org.chobit.knot.common.moderation;

import org.chobit.knot.common.constants.enums.NumericEnumOption;

/**
 * 敏感词严重度：从 ks_enum_configs 的通用严重度字典中拆出的领域枚举，
 * 通过 /api/enum-options 以 ModerationSeverityEnum 键下发前端。
 *
 * <p>严重度挂在词条上而非分类：同一分类下不同词严重度不同（见 {@link ModerationTerm}）。
 * 是否触发处置动作的阈值（SEVERITY_THRESHOLD）仍在 knot-plugin 的 ModerationConstants，不在此枚举。</p>
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

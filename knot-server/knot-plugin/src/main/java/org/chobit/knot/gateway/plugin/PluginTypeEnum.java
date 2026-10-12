package org.chobit.knot.gateway.plugin;

import org.chobit.knot.gateway.constants.enums.EnumOption;
import org.chobit.knot.gateway.error.BusinessException;
import org.chobit.knot.gateway.error.ErrorCode;

import java.util.Arrays;

/**
 * 插件执行类型：区分同步插件与异步插件两类。
 * <ul>
 *   <li>{@code SYNC}（同步插件）：实现 {@link PluginHandler}，嵌入网关/上游请求链
 *       （{@link PluginExtensionPoint} + {@link PluginStageCode}）内联执行，可观测/改写请求与响应。</li>
 *   <li>{@code ASYNC}（异步插件）：实现 {@link PluginEventSink}，消费 {@link PluginEvent}
 *       离链执行（如日志落盘、Kafka 上报），不参与请求关键路径。</li>
 * </ul>
 * 已通过 /api/common/enums 以 PluginTypeEnum 键下发前端。code 与枚举名一致，落库与校验均使用该 code。
 */
public enum PluginTypeEnum implements EnumOption {
    SYNC("SYNC", "同步插件"),
    ASYNC("ASYNC", "异步插件");

    private final String code;
    private final String label;

    PluginTypeEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    @Override
    public String code() {
        return code;
    }

    /** 前端展示名 */
    @Override
    public String label() {
        return label;
    }

    public static PluginTypeEnum fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String normalized = code.trim().toUpperCase();
        return Arrays.stream(values())
                .filter(item -> item.code.equals(normalized))
                .findFirst()
                .orElse(null);
    }

    /**
     * 校验插件类型编码并返回规范大写形式，非法值直接拒绝；null 视为 SYNC（默认同步）。
     */
    public static String requireCode(String code, String errorMessage) {
        if (code == null || code.isBlank()) {
            return SYNC.code();
        }
        PluginTypeEnum type = fromCode(code);
        if (type == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, errorMessage);
        }
        return type.code();
    }
}

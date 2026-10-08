package org.chobit.knot.gateway.constants.enums;

import java.util.List;

/**
 * 插件执行结果（{@code kr_plugin_execution_logs.result_status}）。
 *
 * <p>这是单次执行的**结果**，不是实体生命周期状态，与 {@link PluginInstanceStatusEnum} 无关。
 * 高频流水表只保留结果，不引入删除状态。</p>
 */
public enum PluginExecutionResultStatusEnum implements NumericEnumOption {
    SUCCESS(1, "成功"),
    SKIPPED(2, "跳过"),
    FAILED(3, "失败"),
    TIMEOUT(4, "超时"),
    OPEN_CIRCUIT(5, "熔断");

    private final int code;
    private final String label;

    PluginExecutionResultStatusEnum(int code, String label) {
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

    public static PluginExecutionResultStatusEnum fromCode(Integer code) {
        return NumericEnumOption.fromCode(values(), code);
    }

    public static PluginExecutionResultStatusEnum requireCode(Integer code, String errorMessage) {
        return NumericEnumOption.requireCode(values(), code, errorMessage);
    }

    public static List<Integer> codes() {
        return NumericEnumOption.codes(values());
    }
}

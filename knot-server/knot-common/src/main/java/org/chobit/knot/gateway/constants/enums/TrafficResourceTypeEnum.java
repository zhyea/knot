package org.chobit.knot.gateway.constants.enums;

import java.util.Arrays;

/**
 * 受控资源类型：决定该资源能配哪一类策略。
 *
 * <ul>
 *   <li>{@code MODEL} / {@code ROUTING_RULE} —— 只配<b>限流</b>（RPM + TPM）；</li>
 *   <li>{@code APP} / {@code PROVIDER} / {@code ROUTING_CONSUMER} —— 只配<b>限额</b>（最大 token + 成本上限）。</li>
 * </ul>
 */
public enum TrafficResourceTypeEnum {
    APP("APP", false, true),
    MODEL("MODEL", true, false),
    PROVIDER("PROVIDER", false, true),
    ROUTING_RULE("ROUTING_RULE", true, false),
    ROUTING_CONSUMER("ROUTING_CONSUMER", false, true);

    private final String code;
    private final boolean rateLimitSupported;
    private final boolean quotaSupported;

    TrafficResourceTypeEnum(String code, boolean rateLimitSupported, boolean quotaSupported) {
        this.code = code;
        this.rateLimitSupported = rateLimitSupported;
        this.quotaSupported = quotaSupported;
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    public String code() {
        return code;
    }

    /**
     * 该资源是否配置限流（RPM / TPM）。
     */
    public boolean supportsRateLimit() {
        return rateLimitSupported;
    }

    /**
     * 该资源是否配置限额（最大 token / 成本上限）。
     */
    public boolean supportsQuota() {
        return quotaSupported;
    }

    /**
     * 按 code 查找；未收录的类型返回 {@code null}（调用方按「不限制」处理）。
     */
    public static TrafficResourceTypeEnum ofCode(String code) {
        if (code == null) {
            return null;
        }
        return Arrays.stream(values())
                .filter(item -> item.code.equals(code))
                .findFirst()
                .orElse(null);
    }
}

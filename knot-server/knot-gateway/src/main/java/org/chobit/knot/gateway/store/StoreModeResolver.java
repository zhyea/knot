package org.chobit.knot.gateway.store;

import org.springframework.stereotype.Component;

/**
 * 业务域存储模式的唯一归一处：把 {@code knot.<domain>.store} 归一成一份运行时口径，各域装配类不再各写一套合法性判断。
 * 取值口径：未配置（{@code null} / 空白）回落 {@link StoreMode#CAFFEINE}；配了无法识别的值直接抛异常，「没配」与「配错」可区分。
 * 本类不归一故障口径：Redis 档一律 fail-hard，抛异常 + ERROR 日志。
 */
@Component
public class StoreModeResolver {

    /**
     * 归一业务域的存储模式。
     *
     * @param domain     业务域（traffic / load，新域直接传 code）
     * @param configured 该域 Properties 里的模式原文，{@code null} / 空白表示未显式设置
     * @return 归一后的存储模式
     * @throws IllegalStateException 模式值无法识别（宁可启动失败，也不要静默按单节点跑）
     */
    public StoreMode modeOf(String domain, String configured) {
        if (configured == null || configured.isBlank()) {
            return StoreMode.CAFFEINE;
        }
        StoreMode normalized = StoreMode.fromConfigured(configured);
        if (normalized == null) {
            throw new IllegalStateException("Unknown store mode for domain " + domain + ": " + configured
                    + "; expected one of caffeine / redis");
        }
        return normalized;
    }
}

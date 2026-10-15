package org.chobit.knot.gateway.store;

import org.springframework.stereotype.Component;

/**
 * 业务域存储模式的<b>唯一归一处</b>：把 {@code knot.<domain>.store}
 * 归一成一份运行时口径，各域装配类不再各写一套合法性判断。
 *
 * <p>存储选型是业务域自己的配置属性——traffic / load 各持有自己的
 * {@code store}，本类只提供跨域共用的归一与校验，不构成独立的「cache 层」。
 * 后续新业务域要加存储选型，直接 {@code knot.<新域>.store} 接入即可。</p>
 *
 * <p><b>取值口径</b>：未配置（{@code null} / 空白）回落 {@link StoreMode#CAFFEINE}；
 * 配了无法识别的值直接抛异常——「没配」与「配错」必须可区分，
 * 配错时宁可启动失败，也不静默按单节点跑。无历史键兼容层——本项目不做历史包袱，
 * 配置键一次定型。</p>
 *
 * <p>⚠ 本类<b>不</b>归一故障口径：fail-open 与降级机制已下线，
 * Redis 档一律 fail-hard，故障处置固定为「抛异常 + ERROR 日志」。</p>
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

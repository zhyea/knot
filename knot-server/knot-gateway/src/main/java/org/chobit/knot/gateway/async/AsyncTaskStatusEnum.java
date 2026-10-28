package org.chobit.knot.gateway.async;

import java.util.Arrays;

/**
 * 异步任务状态（落 {@code kb_async_tasks.status}）。
 *
 * <p><b>用字符串而不是数字</b>：任务状态是运维高频 {@code WHERE} 条件（查堆积、查卡单、查未接手），
 * 自解释的字符串省掉一层字典翻译；枚举仍在 Java 侧唯一收口，落库取值恒为 {@link #name()}。</p>
 *
 * <p>{@code PENDING} 与 {@code POLLING} 分开是刻意的：运维要能一眼分辨「创建后从未被轮询过」
 * （poller 没接手 / 抢占失败）与「正在轮询中」，两者处置方式完全不同。</p>
 */
public enum AsyncTaskStatusEnum {

    /** 上游创建成功，尚未发起首次查询 */
    PENDING,
    /** 已查询过至少一次，仍在等待终态 */
    POLLING,
    /** 终态：成功，已计费 */
    SUCCEEDED,
    /** 终态：上游失败，不计费 */
    FAILED,
    /** 终态：超过 knot 侧超时仍未出结果，不计费 */
    EXPIRED,
    /** 终态：主动取消（本轮未启用，语义先留） */
    CANCELLED;

    /** 是否在途：只有这两个状态会被扫描与抢占 */
    public boolean inFlight() {
        return this == PENDING || this == POLLING;
    }

    public static AsyncTaskStatusEnum fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String normalized = code.trim().toUpperCase();
        return Arrays.stream(values())
                .filter(item -> item.name().equals(normalized))
                .findFirst()
                .orElse(null);
    }
}

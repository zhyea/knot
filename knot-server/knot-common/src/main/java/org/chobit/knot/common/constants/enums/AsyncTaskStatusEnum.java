package org.chobit.knot.common.constants.enums;

import org.chobit.knot.common.error.BusinessException;
import org.chobit.knot.common.error.ErrorCode;

import java.util.Arrays;
import java.util.List;

/**
 * 异步任务状态，落 {@code kb_async_tasks.status}；对外即 0~5 的整型码，label 走 {@code /api/enum-options}。
 * 判终态用 {@link #terminal()}，在途态用 {@link #inFlightCodes()}；禁用 {@link #ordinal()}。
 */
public enum AsyncTaskStatusEnum implements NumericEnumOption {

    /** 上游创建成功，尚未发起首次查询 */
    PENDING(0, "待处理"),
    /** 已发起过至少一次查询（含当前这次），仍未到终态 */
    POLLING(1, "轮询中"),
    /** 终态：成功，已计费 */
    SUCCEEDED(2, "成功"),
    /** 终态：上游失败，不计费 */
    FAILED(3, "失败"),
    /** 终态：超过 knot 侧超时仍未出结果，不计费 */
    EXPIRED(4, "已超时"),
    /** 终态：主动取消（本轮未启用，语义先留） */
    CANCELLED(5, "已取消");

    /** 落库值。⚠ 只增不改、不复用废号，见类注释 */
    private final int code;

    /** 管理端展示名（{@code /api/enum-options} 与任务列表都用它） */
    private final String label;

    AsyncTaskStatusEnum(int code, String label) {
        this.code = code;
        this.label = label;
    }

    /** 落库值（{@code kb_async_tasks.status} 存的就是它） */
    @Override
    public int code() {
        return code;
    }

    @Override
    public String label() {
        return label;
    }

    /** 是否在途：只有这两个状态会被扫描与抢占 */
    public boolean inFlight() {
        return this == PENDING || this == POLLING;
    }

    /** 是否终态：{@code !inFlight()}。终态行不再被扫描，但仍可被「重试」重新打开 */
    public boolean terminal() {
        return !inFlight();
    }

    /**
     * 是否可被管理端「重试」重新打开：只含 {@link #FAILED} 与 {@link #EXPIRED}——两者都没出货、也没计费；
     * {@link #SUCCEEDED} 已计费、{@link #CANCELLED} 是人工作出的终止决定，均不重开。
     */
    public boolean retryable() {
        return this == FAILED || this == EXPIRED;
    }

    /** 可重试状态码集合，供 SQL 的 {@code status IN (...)} 使用（同 {@link #inFlightCodes()} 的理由：不写字面量） */
    public static List<Integer> retryableCodes() {
        return RETRYABLE_CODES;
    }

    /**
     * 在途状态码集合，供 SQL 的 {@code status IN (...)} 使用。
     */
    public static List<Integer> inFlightCodes() {
        return IN_FLIGHT_CODES;
    }

    /** 按落库值反查；未知值返回 {@code null}（由调用方决定是兜底还是报错，不在这里静默给默认值） */
    public static AsyncTaskStatusEnum fromCode(Integer code) {
        return NumericEnumOption.fromCode(values(), code);
    }

    /** 全部状态 code，供管理端查询下拉与批量校验使用 */
    public static List<Integer> codes() {
        return NumericEnumOption.codes(values());
    }

    /** 校验状态编码并返回其数字值，非法值直接拒绝（Service / Controller 边界用这个） */
    public static int requireCode(Integer code, String errorMessage) {
        AsyncTaskStatusEnum status = fromCode(code);
        if (status == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, errorMessage);
        }
        return status.code();
    }

    private static final List<Integer> IN_FLIGHT_CODES = Arrays.stream(values())
            .filter(AsyncTaskStatusEnum::inFlight)
            .map(AsyncTaskStatusEnum::code)
            .toList();

    private static final List<Integer> RETRYABLE_CODES = Arrays.stream(values())
            .filter(AsyncTaskStatusEnum::retryable)
            .map(AsyncTaskStatusEnum::code)
            .toList();
}

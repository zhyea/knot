package org.chobit.knot.common.constants.enums;

import org.chobit.knot.common.error.BusinessException;
import org.chobit.knot.common.error.ErrorCode;

import java.util.Arrays;
import java.util.List;

/**
 * 异步任务回调交付状态，落 {@code kb_async_tasks.delivery_status}，存 {@link #code()}。
 * 与 {@link AsyncTaskStatusEnum} 正交：前者答「任务成没成」，本枚举答「结果有没有送到
 * {@code callback_url}」，可组合出「生成成功 + 回调失败」。
 * code 只增不改、不复用废号；{@code 0} 是合法值（无 callback_url 无需交付），前端禁用 truthy 判断。
 */
public enum AsyncTaskDeliveryStatusEnum implements NumericEnumOption {

    /** 提交时没有 callback_url：无从交付，也永不进入扫描集 */
    NONE(0, "无需回调"),
    /** 任务已到终态，等待（或正在）向 callback_url 投递 */
    PENDING(1, "待回调"),
    /** 回调已成功送达（对端 2xx） */
    SUCCEEDED(2, "回调成功"),
    /**
     * 连续失败达到上限，放弃投递。交付维度的终态，不代表任务生成失败，客户仍可用任务句柄轮询到真实结果。
     */
    FAILED(3, "回调失败");

    /** 落库值。⚠ 只增不改、不复用废号，见类注释 */
    private final int code;

    /** 管理端展示名 */
    private final String label;

    AsyncTaskDeliveryStatusEnum(int code, String label) {
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

    /**
     * 是否在「待投递」集合：只有它会被扫描与抢占。
     */
    public boolean deliverable() {
        return this == PENDING;
    }

    /**
     * 待投递状态码集合，供 SQL 的 {@code delivery_status IN (...)} 使用。
     */
    public static List<Integer> deliverableCodes() {
        return DELIVERABLE_CODES;
    }

    /** 按落库值反查；未知值返回 {@code null}（由调用方决定兜底还是报错） */
    public static AsyncTaskDeliveryStatusEnum fromCode(Integer code) {
        return NumericEnumOption.fromCode(values(), code);
    }

    /** 全部 code，供管理端下拉与批量校验 */
    public static List<Integer> codes() {
        return NumericEnumOption.codes(values());
    }

    /** 校验编码并返回数字值，非法值直接拒绝（Service / Controller 边界用） */
    public static int requireCode(Integer code, String errorMessage) {
        AsyncTaskDeliveryStatusEnum status = fromCode(code);
        if (status == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, errorMessage);
        }
        return status.code();
    }

    private static final List<Integer> DELIVERABLE_CODES = Arrays.stream(values())
            .filter(AsyncTaskDeliveryStatusEnum::deliverable)
            .map(AsyncTaskDeliveryStatusEnum::code)
            .toList();
}

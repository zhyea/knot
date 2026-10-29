package org.chobit.knot.common.constants.enums;

import org.chobit.knot.common.error.BusinessException;
import org.chobit.knot.common.error.ErrorCode;

import java.util.Arrays;
import java.util.List;

/**
 * 异步任务<b>回调交付</b>状态（落 {@code kb_async_tasks.delivery_status}，存 {@link #code()}）。
 *
 * <p>⭐⭐ <b>为什么它与 {@link AsyncTaskStatusEnum} 是两个互相正交的维度</b>（2026-10-29）：
 * {@code task_status} 回答「任务本身成没成」，{@code delivery_status} 回答「结果有没有送到客户的
 * {@code callback_url}」。两者可以任意组合——<b>生成成功 + 回调失败</b>是合法状态，
 * 它既不等于任务失败，也不等于回调成功。</p>
 *
 * <p>把它们压进同一个枚举，就会出现「某个终态其实代表回调失败、不代表任务失败」这种自相矛盾的取值
 * （参考实现的 {@code 13=最终失败} 在注释里自己写着「不代表生成一定失败」）。
 * 一旦客户按这个终态做业务判断，就会把一批实际已生成成功的任务当成失败重跑——
 * 既有资损风险，也会让「成功率」这个指标永远算不准。</p>
 *
 * <p>⚠ code <b>只增不改、不复用废号</b>；{@code 0} 是合法值（提交时没有 callback_url，无需交付），
 * 前端禁用 truthy 判断。</p>
 */
public enum AsyncTaskDeliveryStatusEnum implements NumericEnumOption {

    /** 提交时没有 callback_url：无从交付，也永不进入扫描集 */
    NONE(0, "无需回调"),
    /** 任务已到终态，等待（或正在）向 callback_url 投递 */
    PENDING(1, "待回调"),
    /** 回调已成功送达（对端 2xx） */
    SUCCEEDED(2, "回调成功"),
    /**
     * 连续失败达到上限，放弃投递。
     *
     * <p>⚠ 这是<b>交付维度</b>的终态，<b>不代表任务生成失败</b>——
     * 客户仍可用任务句柄轮询到真实结果。</p>
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
     *
     * <p>⭐ 用<b>正向包含</b>（{@code delivery_status IN (1)}）而不是「排除 2 / 3」：
     * 将来加一个新的待投递子态（比如 {@code RETRYING}）时，负向排除会把新态静默当成在途扫不到；
     * 正向包含则是「加进集合就自动生效」。</p>
     */
    public boolean deliverable() {
        return this == PENDING;
    }

    /**
     * 待投递状态码集合，供 SQL 的 {@code delivery_status IN (...)} 使用。
     *
     * <p>理由同 {@link AsyncTaskStatusEnum#inFlightCodes()}：SQL 里出现魔法数字时，
     * 下一个往枚举里加待投递态的人不会想到来改 XML。</p>
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

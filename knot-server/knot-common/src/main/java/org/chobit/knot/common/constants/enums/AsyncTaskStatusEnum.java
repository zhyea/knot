package org.chobit.knot.common.constants.enums;

import org.chobit.knot.common.error.BusinessException;
import org.chobit.knot.common.error.ErrorCode;

import java.util.Arrays;
import java.util.List;

/**
 * 异步任务状态（落 {@code kb_async_tasks.status}，存 {@link #code()} 而非名字）。
 *
 * <p><b>2026-10-28 由「存 {@link #name()} 的 VARCHAR」改为「存 code 的 TINYINT」</b>（robin 拍板）。
 * 上一版的选择理由（运维 {@code WHERE status='POLLING'} 自解释）依然成立，翻案是因为另外两条更硬：</p>
 * <ul>
 *   <li><b>热点索引变窄</b>：{@code idx_async_tasks_due(status, next_poll_at)} 是轮询扫描的入口，
 *       status 是首列。{@code 'PENDING'} 连长度前缀要 9 字节，{@code TINYINT} 1 字节，
 *       同样 buffer pool 下索引页能装近两倍条目。</li>
 *   <li><b>全库风格统一</b>：本项目其它状态列（{@code EnabledStatusEnum}）都是 {@code TINYINT}。
 *       同一个库里两种状态存法，读代码的人要记两套规则。</li>
 * </ul>
 *
 * <p>⭐ <b>为什么住在 {@code common}（2026-10-29 二次搬迁）</b>：这套状态码有<b>三个互不依赖的消费方</b>
 * ——gateway 的编排与状态机、svc 的 SQL 在途态谓词（{@link #inFlightCodes()}）、admin 的任务查看页
 * （要 {@link #label()} 渲染）。放在任何一端都会让另外两端「反向依赖」或「复制一份枚举」，
 * 后者是同一状态集两份定义，必然漂移。common 是三者唯一的共同祖先。</p>
 *
 * <p>⚠ 由此它也进入 {@link NumericEnumOption} 的统一契约名单（{@code NumericStatusEnumContractTest}）：
 * {@code fromCode} / {@code requireCode} / {@code codes} 三件套必须齐备且与邻枚举行为一致。</p>
 *
 * <p>⭐ <b>代价与对策（整型真正危险的地方不是「不可读」，是「撞 code」）</b>：</p>
 * <ul>
 *   <li>两个常量给同一个 code，数据写进去就<b>不可区分、不可恢复</b>——信息已经丢了。
 *       字符串则撞不了名（编译器先挡住）。对策：code <b>显式声明、只增不改、不复用废号</b>。</li>
 *   <li>⚠ <b>严禁用 {@link #ordinal()}</b>：调整常量顺序（比如把 CANCELLED 挪到前面）会让历史数据
 *       整体错位，且没有任何编译信号。只认 {@link #code()}。</li>
 *   <li>数值本身就是<b>落库 wire format</b>：由 {@code AsyncTaskStatusEnumTest} 逐个钉死，
 *       改动即红。别处一律用枚举常量，不写裸数字。</li>
 *   <li>可读性靠 {@link #label()}（管理端展示）与列注释（{@code schema.sql} 里写全 0~5 映射）
 *       加 {@code CHECK} 约束补，不再靠「存字符串」。</li>
 * </ul>
 *
 * <p>{@code PENDING} 与 {@code POLLING} 分开是刻意的：运维要能一眼分辨「创建后从未被轮询过」
 * （poller 没接手 / 抢占失败）与「正在轮询中」，两者处置方式完全不同。</p>
 */
public enum AsyncTaskStatusEnum implements NumericEnumOption {

    /** 上游创建成功，尚未发起首次查询 */
    PENDING(0, "待处理"),
    /** 已查询过至少一次，仍在等待终态 */
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

    /**
     * 在途状态码集合，供 SQL 的 {@code status IN (...)} 使用。
     *
     * <p>⭐ 存在意义是<b>让 SQL 里不出现魔法数字</b>：{@code status in (0, 1)} 能跑通，但下一个
     * 往枚举里加在途态的人不会想到去改 XML，症状是「新状态的任务永远查不到」。
     * 由 Java 传参则加一个常量就自动生效。</p>
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
}

package org.chobit.knot.gateway.async;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

import static org.chobit.knot.gateway.async.AsyncTaskEvent.ATTEMPTS_EXHAUSTED;
import static org.chobit.knot.gateway.async.AsyncTaskEvent.CANCEL;
import static org.chobit.knot.gateway.async.AsyncTaskEvent.POLL_FAILED;
import static org.chobit.knot.gateway.async.AsyncTaskEvent.POLL_RUNNING;
import static org.chobit.knot.gateway.async.AsyncTaskEvent.POLL_SUCCEEDED;
import static org.chobit.knot.gateway.async.AsyncTaskEvent.QUERY_ERROR;
import static org.chobit.knot.gateway.async.AsyncTaskEvent.RESULT_MISSING;
import static org.chobit.knot.gateway.async.AsyncTaskEvent.SNAPSHOT_INVALID;
import static org.chobit.knot.gateway.async.AsyncTaskEvent.TIMEOUT;
import static org.chobit.knot.common.constants.enums.AsyncTaskStatusEnum.CANCELLED;
import static org.chobit.knot.common.constants.enums.AsyncTaskStatusEnum.EXPIRED;
import static org.chobit.knot.common.constants.enums.AsyncTaskStatusEnum.FAILED;
import static org.chobit.knot.common.constants.enums.AsyncTaskStatusEnum.POLLING;
import static org.chobit.knot.common.constants.enums.AsyncTaskStatusEnum.SUCCEEDED;
import static org.chobit.knot.gateway.async.AsyncTaskTransition.Action.RESCHEDULE;
import static org.chobit.knot.gateway.async.AsyncTaskTransition.Action.SETTLE;
import static org.chobit.knot.gateway.async.AsyncTaskTransition.Action.SETTLE_BILLED;
import org.chobit.knot.common.constants.enums.AsyncTaskStatusEnum;
/**
 * 异步任务状态机：{@code 当前状态 × 事件 → 下一状态 + 动作}；转移表 9 行，一个事件一行。
 * 终态冻结：{@code from} 不是在途态（含 null）一律 {@code REJECT}，与 SQL 终态 CAS（{@code WHERE status IN (在途码集合)}）是同一事实的两层执行。
 * 只有成功计费：{@code SETTLE_BILLED} 只挂在 {@link AsyncTaskEvent#POLL_SUCCEEDED} 上。
 * 构造期校验「每个事件都必须有规则」，漏登记直接启动失败。
 */
public final class AsyncTaskStateMachine {

    /** 事件 → 去向。在途态共享同一张表：PENDING 与 POLLING 的唯一差别只是首次查询后提升为后者。 */
    private static final Map<AsyncTaskEvent, Rule> RULES = rules();

    private AsyncTaskStateMachine() {
    }

    /**
     * 求一次转移。纯函数，无副作用、无依赖，可穷举测试。
     *
     * @param from  当前状态；null（落库值不可识别）视为非法来源
     * @param event 本次事件
     */
    public static AsyncTaskTransition transition(AsyncTaskStatusEnum from, AsyncTaskEvent event) {
        Objects.requireNonNull(event, "async task event must not be null");
        Rule rule = RULES.get(event);
        if (from == null || !from.inFlight()) {
            return new AsyncTaskTransition(from, event, from, AsyncTaskTransition.Action.REJECT);
        }
        return new AsyncTaskTransition(from, event, rule.to(), rule.action());
    }

    private static Map<AsyncTaskEvent, Rule> rules() {
        Map<AsyncTaskEvent, Rule> map = new EnumMap<>(AsyncTaskEvent.class);
        map.put(POLL_RUNNING, new Rule(POLLING, RESCHEDULE));
        map.put(QUERY_ERROR, new Rule(POLLING, RESCHEDULE));
        map.put(POLL_SUCCEEDED, new Rule(SUCCEEDED, SETTLE_BILLED));
        map.put(POLL_FAILED, new Rule(FAILED, SETTLE));
        map.put(RESULT_MISSING, new Rule(FAILED, SETTLE));
        map.put(SNAPSHOT_INVALID, new Rule(FAILED, SETTLE));
        map.put(ATTEMPTS_EXHAUSTED, new Rule(EXPIRED, SETTLE));
        map.put(TIMEOUT, new Rule(EXPIRED, SETTLE));
        map.put(CANCEL, new Rule(CANCELLED, SETTLE));
        for (AsyncTaskEvent event : AsyncTaskEvent.values()) {
            if (!map.containsKey(event)) {
                throw new IllegalStateException("Async task state machine has no rule for event: " + event);
            }
        }
        return Collections.unmodifiableMap(map);
    }

    /** 单个事件的去向 */
    private record Rule(AsyncTaskStatusEnum to, AsyncTaskTransition.Action action) {
    }
}

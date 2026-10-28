package org.chobit.knot.gateway.async;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.chobit.knot.gateway.async.AsyncTaskEvent.ATTEMPTS_EXHAUSTED;
import static org.chobit.knot.gateway.async.AsyncTaskEvent.CANCEL;
import static org.chobit.knot.gateway.async.AsyncTaskEvent.POLL_FAILED;
import static org.chobit.knot.gateway.async.AsyncTaskEvent.POLL_RUNNING;
import static org.chobit.knot.gateway.async.AsyncTaskEvent.POLL_SUCCEEDED;
import static org.chobit.knot.gateway.async.AsyncTaskEvent.QUERY_ERROR;
import static org.chobit.knot.gateway.async.AsyncTaskEvent.RESULT_MISSING;
import static org.chobit.knot.gateway.async.AsyncTaskEvent.SNAPSHOT_INVALID;
import static org.chobit.knot.gateway.async.AsyncTaskEvent.TIMEOUT;
import static org.chobit.knot.gateway.async.AsyncTaskStatusEnum.CANCELLED;
import static org.chobit.knot.gateway.async.AsyncTaskStatusEnum.EXPIRED;
import static org.chobit.knot.gateway.async.AsyncTaskStatusEnum.FAILED;
import static org.chobit.knot.gateway.async.AsyncTaskStatusEnum.PENDING;
import static org.chobit.knot.gateway.async.AsyncTaskStatusEnum.POLLING;
import static org.chobit.knot.gateway.async.AsyncTaskStatusEnum.SUCCEEDED;
import static org.chobit.knot.gateway.async.AsyncTaskTransition.Action.REJECT;
import static org.chobit.knot.gateway.async.AsyncTaskTransition.Action.RESCHEDULE;
import static org.chobit.knot.gateway.async.AsyncTaskTransition.Action.SETTLE;
import static org.chobit.knot.gateway.async.AsyncTaskTransition.Action.SETTLE_BILLED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 状态机穷举守门：事件 × 状态全量跑一遍，而不是抽样几个。
 *
 * <p>⭐ 这张表是<b>资损与"两条终态消息"的唯一防线</b>：转移表里任何一格写错
 * （比如把 {@code RESULT_MISSING} 写成 {@code SETTLE_BILLED}），抽样测试完全可能漏掉，
 * 而穷举能保证"每个事件都被问过一次"。</p>
 */
class AsyncTaskStateMachineTest {

    private static final Set<AsyncTaskStatusEnum> IN_FLIGHT = EnumSet.of(PENDING, POLLING);
    private static final Set<AsyncTaskStatusEnum> TERMINAL =
            EnumSet.of(SUCCEEDED, FAILED, EXPIRED, CANCELLED);

    @Test
    @DisplayName("每个事件都有规则：新增事件不登记转移表就跑不过")
    void everyEventHasRule() {
        for (AsyncTaskEvent event : AsyncTaskEvent.values()) {
            AsyncTaskTransition transition = AsyncTaskStateMachine.transition(POLLING, event);
            assertNotNull(transition.to(), "事件 " + event + " 没有去向");
            assertTrue(transition.action() != REJECT, "在途态收到 " + event + " 不该被拒");
        }
    }

    @Test
    @DisplayName("PENDING 与 POLLING 规则完全一致：两者差别只在「首次查询之前 / 之后」")
    void pendingAndPollingShareTheSameRules() {
        for (AsyncTaskEvent event : AsyncTaskEvent.values()) {
            AsyncTaskTransition fromPending = AsyncTaskStateMachine.transition(PENDING, event);
            AsyncTaskTransition fromPolling = AsyncTaskStateMachine.transition(POLLING, event);
            assertEquals(fromPolling.to(), fromPending.to(), "事件 " + event + " 在两种在途态去向不一致");
            assertEquals(fromPolling.action(), fromPending.action(), "事件 " + event + " 在两种在途态动作不一致");
        }
    }

    @Test
    @DisplayName("终态冻结：任何终态收到任何事件一律拒绝，且原地不动")
    void terminalStatesRejectEveryEvent() {
        for (AsyncTaskStatusEnum terminal : TERMINAL) {
            assertFalse(terminal.inFlight(), terminal + " 不该被当成在途态");
            for (AsyncTaskEvent event : AsyncTaskEvent.values()) {
                AsyncTaskTransition transition = AsyncTaskStateMachine.transition(terminal, event);
                assertEquals(REJECT, transition.action(), terminal + " 收到 " + event + " 必须拒绝");
                assertEquals(terminal, transition.to(), "拒绝时状态不得变化");
            }
        }
    }

    @Test
    @DisplayName("落库状态不可识别（null）时一律拒绝，不猜")
    void unknownStatusIsRejected() {
        assertEquals(REJECT, AsyncTaskStateMachine.transition(null, POLL_RUNNING).action());
    }

    @Test
    @DisplayName("⭐ 只有「上游成功」会计费：其余事件一律不计费")
    void onlySuccessBills() {
        Set<AsyncTaskEvent> billed = EnumSet.noneOf(AsyncTaskEvent.class);
        for (AsyncTaskEvent event : AsyncTaskEvent.values()) {
            if (AsyncTaskStateMachine.transition(POLLING, event).action() == SETTLE_BILLED) {
                billed.add(event);
            }
        }
        assertEquals(EnumSet.of(POLL_SUCCEEDED), billed,
                "计费事件多一条就是资损，少一条就是漏收");
    }

    @Test
    @DisplayName("非终态事件都留在 POLLING，且不落终态")
    void rescheduleStaysInFlight() {
        for (AsyncTaskEvent event : EnumSet.of(POLL_RUNNING, QUERY_ERROR)) {
            AsyncTaskTransition transition = AsyncTaskStateMachine.transition(PENDING, event);
            assertEquals(RESCHEDULE, transition.action());
            assertEquals(POLLING, transition.to(), "首次查询之后就该是 POLLING，PENDING 只表示从未查过");
            assertFalse(transition.settles());
        }
    }

    @Test
    @DisplayName("落终态必须自带错误码；计费事件不得带码；中间失败可以带码（只上报不落库）")
    void errorCodeMatchesAction() {
        for (AsyncTaskEvent event : AsyncTaskEvent.values()) {
            AsyncTaskTransition transition = AsyncTaskStateMachine.transition(POLLING, event);
            if (transition.action() == SETTLE) {
                assertNotNull(event.errorCode(), "事件 " + event + " 落终态却不带错误码，数仓无法归因");
                assertNotNull(event.defaultMessage(), "事件 " + event + " 缺少兜底话术");
            } else if (transition.action() == SETTLE_BILLED) {
                assertNull(event.errorCode(), "事件 " + event + " 计价却带错误码，语义矛盾");
            }
        }
        // RESCHEDULE 里只有 QUERY_ERROR 该带码：它代表一次真实发生的上游调用失败，
        // 不带码就等于「重试 5 次才成功」在数仓里看起来跟「一次成功」一样
        assertNotNull(QUERY_ERROR.errorCode(), "中间查询失败必须可归因");
        assertNull(POLL_RUNNING.errorCode(), "仍在跑不算错误");
    }

    @Test
    @DisplayName("落终态的事件去向必须是终态，留在途的必须不是终态")
    void targetStateIsConsistentWithAction() {
        for (AsyncTaskEvent event : AsyncTaskEvent.values()) {
            AsyncTaskTransition transition = AsyncTaskStateMachine.transition(POLLING, event);
            assertTrue(IN_FLIGHT.contains(transition.to()) || TERMINAL.contains(transition.to()),
                    "事件 " + event + " 的去向越界：" + transition.to());
            assertEquals(transition.settles(), !transition.to().inFlight(),
                    "事件 " + event + " 的动作与去向矛盾（落终态却仍标记在途，或反之）");
        }
    }
}

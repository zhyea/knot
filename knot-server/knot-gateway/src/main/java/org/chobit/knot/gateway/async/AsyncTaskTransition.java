package org.chobit.knot.gateway.async;

/**
 * 一次状态转移的结果：{@code from × event → to}，外加调用方要执行的动作。
 *
 * <p>⭐ 状态机只做判定、不碰副作用：转移结果里的 {@link Action} 告诉调用方「去排下一次」
 * 还是「去落终态」，至于写库、计费、发事件全在 {@code AsyncTaskPoller} 里。
 * 这样转移表可以被单测穷举，而副作用留在能被伪造协作者的地方。</p>
 *
 * @param from  转移前状态；非法转移时等于 {@code to}
 * @param event 触发事件
 * @param to    转移后状态
 * @param action 调用方动作
 */
public record AsyncTaskTransition(AsyncTaskStatusEnum from,
                                  AsyncTaskEvent event,
                                  AsyncTaskStatusEnum to,
                                  Action action) {

    /** 调用方要做什么 */
    public enum Action {

        /** 仍在途：安排下一次轮询（{@code PENDING} 首次查询后即转 {@code POLLING}） */
        RESCHEDULE,

        /** 落终态并计费——全表只有 {@link AsyncTaskEvent#POLL_SUCCEEDED} 一条路径 */
        SETTLE_BILLED,

        /** 落终态不计费 */
        SETTLE,

        /** 非法转移：终态不可再转移，写库前就该拦住 */
        REJECT
    }

    /** 是否要落终态（含计费与不计费） */
    public boolean settles() {
        return action == Action.SETTLE || action == Action.SETTLE_BILLED;
    }
}

package org.chobit.knot.gateway.async;

import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.adapter.usage.KnotExtendPayload;
import org.chobit.knot.common.constants.enums.AsyncTaskStatusEnum;
import org.chobit.knot.common.util.JsonKit;
import org.chobit.knot.gateway.model.vo.response.AsyncTaskFailureResponse;
import org.chobit.knot.gateway.model.vo.response.AsyncTaskQueryResponse;
import org.chobit.knot.svc.dto.async.AsyncTaskDto;

import java.util.Map;

/**
 * 异步任务对外视图的唯一投影：{@code AsyncTaskDto → AsyncTaskQueryResponse}。
 *
 * <p>⭐⭐ <b>为什么必须有这一层（2026-10-29）</b>：同一份对外 body 现在有两个出口——
 * 客户端<b>轮询</b>（{@link AsyncTaskLookupRunner}）与任务终态<b>回调推送</b>
 * （{@link AsyncTaskDeliveryRunner}）。两处各写一份投影，必然漂移：回调 body 少一个
 * {@code result} 键、或者 status 映射用了另一套词，客户端就要写两条解析分支。</p>
 *
 * <p>收口之后：<b>回调推的就是轮询会返回的那一份</b>。客户端的解析逻辑只有一套，
 * 「轮询拿到了什么」与「回调收到了什么」永远同形。</p>
 *
 * <p>⚠ {@code status} 是<b>对外词</b>（{@code queued/processing/completed/failed}），
 * 不是库里的 0~5 码。这个映射只在这里定义；落到库里的整型码不直接透出。</p>
 */
final class AsyncTaskViewSupport {

    private AsyncTaskViewSupport() {
    }

    /** 把一条任务投影成对外视图（轮询响应体与回调 body 共用） */
    static AsyncTaskQueryResponse toResponse(AsyncTaskDto task) {
        return toResponse(task, false);
    }

    /**
     * 带证据投影：{@code withEvidence} 为 true 时额外带上结算账与上游原文。
     *
     * <p>⭐ <b>两段只走测试头</b>：它们是内部账与上游原文，普通客户端不该看到（也不需要）。
     * 回调投递恒传 {@code false}——回推 body 与轮询 body 必须同形，多一个键就会让客户端
     * 「轮询拿到什么、回调收到什么」出现分叉，而那条契约是刻意收敛过的。</p>
     */
    static AsyncTaskQueryResponse toResponse(AsyncTaskDto task, boolean withEvidence) {
        return new AsyncTaskQueryResponse(task.taskCode(),
                displayStatus(task.status()),
                task.submittedAt(),
                task.completedAt(),
                task.resultExpiresAt(),
                resultOf(task),
                failureOf(task),
                withEvidence ? settlementOf(task) : null,
                withEvidence ? upstreamBodyOf(task) : null);
    }

    /**
     * 结算快照 → {@code knot_extend}；未结算（失败/超时/老任务）返回 null。
     *
     * <p>反序列化失败时 {@code JsonKit.fromJson} 自身记 ERROR 并返回 null，这里不再兜一层：
     * 多一层 catch 只会让「脏数据」与「本来没有」在日志里长得一样。</p>
     */
    private static KnotExtendPayload settlementOf(AsyncTaskDto task) {
        return JsonKit.fromJson(task.settlementJson(), KnotExtendPayload.class);
    }

    /** 上游终态响应原文 → 对象；无原文返回 null（在途任务、超时、老任务） */
    private static Map<String, Object> upstreamBodyOf(AsyncTaskDto task) {
        return JsonKit.fromJson(task.terminalBody(), new TypeReference<Map<String, Object>>() {
        });
    }

    /**
     * 对外状态沿用业界习惯：POLLING 归为 processing，PENDING 为 queued。
     *
     * <p>⭐ <b>对外协议与落库取值是两件事</b>：库里存 0~5 的码，客户端看到的是
     * {@code queued/processing/completed/failed}。无法识别的码落成 {@code unknown}：
     * 宁可让客户端看到一个陌生值，也不要把内部码当成状态文本发出去。</p>
     */
    static String displayStatus(Integer status) {
        AsyncTaskStatusEnum parsed = AsyncTaskStatusEnum.fromCode(status);
        if (parsed == null) {
            return "unknown";
        }
        return switch (parsed) {
            case PENDING -> "queued";
            case POLLING -> "processing";
            case SUCCEEDED -> "completed";
            case FAILED, EXPIRED, CANCELLED -> "failed";
        };
    }

    /**
     * 产物反序列化后回填：直接塞 {@code resultJson} 原文会变成「一个 JSON 字符串」，
     * 客户端还得再解一次。解析不出来（理论上不该发生）时回 null 而不是让查询/回调失败。
     */
    static Map<String, Object> resultOf(AsyncTaskDto task) {
        if (StringUtils.isBlank(task.resultJson())) {
            return null;
        }
        return JsonKit.fromJson(task.resultJson(), new TypeReference<Map<String, Object>>() {
        });
    }

    static AsyncTaskFailureResponse failureOf(AsyncTaskDto task) {
        if (StringUtils.isBlank(task.errorCode())) {
            return null;
        }
        return AsyncTaskFailureResponse.of(task.errorCode(), task.errorMessage());
    }
}

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
 * 同一份对外 body 有两个出口：客户端轮询（{@link AsyncTaskLookupRunner}）与任务终态回调推送（{@link AsyncTaskDeliveryRunner}），都经本类投影，保证两处同形。
 * 回调推的就是轮询会返回的那一份；唯一例外是测试头（回调没有请求头，故 {@code upstream_body} 只在单次轮询响应里出现），见 {@link #toResponse(AsyncTaskDto, boolean)}。
 * {@code status} 直接就是 {@link org.chobit.knot.common.constants.enums.AsyncTaskStatusEnum} 的落库码（0~5），与 {@code /api/enum-options} 下发的同一套。
 */
final class AsyncTaskViewSupport {

    private AsyncTaskViewSupport() {
    }

    /** 把一条任务投影成对外视图（轮询响应体与回调 body 共用） */
    static AsyncTaskQueryResponse toResponse(AsyncTaskDto task) {
        return toResponse(task, false);
    }

    /**
     * 投影：{@code testHeader} 为 true 时额外带上内部证据（含上游原文）。
     * 账目两键（{@code knot_extend} + 并列 {@code usage}）的下发条件：终态 + 提交时快照的 {@code returnUsageDetail}；测试头是叠加开关，只看它也能出账。
     * {@code upstream_body} 只跟测试头；消费者开了用量明细只拿账目，不收上游响应原文；回调没有请求头，测试头不叠加。
     */
    static AsyncTaskQueryResponse toResponse(AsyncTaskDto task, boolean testHeader) {
        boolean accounting = testHeader || (terminal(task) && returnUsageDetail(task));
        KnotExtendPayload knotExtend = accounting ? settlementOf(task) : null;
        // ⭐ 与同步链路同口径：账目不存在时并列 usage 也不出现，两键成对，不许单飞。
        Map<String, Object> rawUsage = knotExtend == null ? null : rawUsageOf(task);
        return new AsyncTaskQueryResponse(task.taskCode(),
                task.status(),
                task.submittedAt(),
                task.completedAt(),
                task.resultExpiresAt(),
                resultOf(task),
                failureOf(task),
                knotExtend,
                rawUsage,
                testHeader ? upstreamBodyOf(task) : null);
    }

    /** 终态判据走枚举，不写裸状态码（{@link AsyncTaskStatusEnum#terminal()} 即「非在途」） */
    private static boolean terminal(AsyncTaskDto task) {
        AsyncTaskStatusEnum status = AsyncTaskStatusEnum.fromCode(task.status());
        return status != null && status.terminal();
    }

    /**
     * 消费者配置取提交时的 {@code context_json} 快照，不是当前库里的路由配置。
     * 旧快照缺该字段时 Jackson 填 false，按「不带账目」处理。
     */
    private static boolean returnUsageDetail(AsyncTaskDto task) {
        if (StringUtils.isBlank(task.contextJson())) {
            return false;
        }
        AsyncTaskContext context = JsonKit.fromJson(task.contextJson(), AsyncTaskContext.class);
        return context != null && context.returnUsageDetail();
    }

    /**
     * 从终态查询响应原文取上游 usage 对象：配了 {@code usage_path} 就按档案表达式取，否则回落到顶层 {@code usage}（含 Responses 协议 {@code response.usage} 下钻）。
     * 只读 {@code terminal_body} 原文，不裁剪不回写；空对象与 null 一律省略（同同步链路口径）。
     */
    private static Map<String, Object> rawUsageOf(AsyncTaskDto task) {
        Map<String, Object> body = upstreamBodyOf(task);
        if (body == null || body.isEmpty()) {
            return null;
        }
        AsyncTaskSpec spec = StringUtils.isBlank(task.pollSpecJson())
                ? null : JsonKit.fromJson(task.pollSpecJson(), AsyncTaskSpec.class);
        Map<String, Object> usage = AsyncTaskSpecRules.usageOf(body, spec);
        return usage == null || usage.isEmpty() ? null : usage;
    }

    /**
     * 结算快照 → {@code knot_extend}；未结算（失败/超时/老任务）返回 {@code null}。
     * 反序列化失败时 {@code JsonKit.fromJson} 自身记 ERROR 并返回 {@code null}，这里不再兜一层。
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

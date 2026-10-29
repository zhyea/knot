package org.chobit.knot.gateway.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.common.constants.AuthConstants;
import org.chobit.knot.common.constants.GatewayHeaders;
import org.chobit.knot.gateway.async.AsyncTaskContext;
import org.chobit.knot.svc.dto.async.AsyncTaskDto;
import org.chobit.knot.common.constants.enums.AsyncTaskStatusEnum;
import org.chobit.knot.svc.service.AsyncTaskService;
import org.chobit.knot.gateway.exception.GatewayAuthException;
import org.chobit.knot.gateway.routing.RoutingResolver;
import org.chobit.knot.common.util.JsonKit;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;
/**
 * 异步任务查询端点（通用，不限于视频）。
 *
 * <p>客户端轮询 knot 而不是供应商：供应商地址与凭据不外泄、计费集中可控、
 * 未来换供应商客户端无感。路径刻意不挂 {@code /videos} —— 提交 + 轮询是一类通用能力，
 * 视频只是第一个使用者。</p>
 *
 * <p><b>鉴权</b>：与网关主链路同一套头部（{@code Authorization} + {@code Rule}），
 * 且规则码必须与任务归属一致。不匹配一律 404（而不是 403）——
 * 不泄漏「这个任务句柄是否存在」，避免跨规则枚举。</p>
 */
@Slf4j
@RestController
@RequestMapping("/openai/v1/tasks")
@RequiredArgsConstructor
public class GatewayTaskController {

    private final AsyncTaskService taskService;
    private final RoutingResolver routingResolver;

    /**
     * 查询一个异步任务。
     *
     * <p>⭐ 鉴权走 {@code RoutingResolver#requireRuleAccess}，与主链路<b>同一份</b>实现：
     * 只看 {@code Authorization} 是不是 {@code Bearer xxx} 是不够的——那样任何字符串都能查，
     * 任务句柄一泄露就是「谁都能读到别人的任务」。</p>
     *
     * <p>顺序是「先验 key，再查任务」：凭证不对直接 401，不进库。</p>
     */
    @GetMapping("/{taskCode}")
    public Mono<ResponseEntity<Object>> get(@RequestHeader(value = GatewayHeaders.AUTHORIZATION) String authorization,
                                            @RequestHeader(value = GatewayHeaders.RULE) String rule,
                                            @PathVariable("taskCode") String taskCode) {
        if (!StringUtils.startsWith(authorization, AuthConstants.BEARER_PREFIX)) {
            throw new GatewayAuthException("Invalid consumer API key");
        }
        routingResolver.requireRuleAccess(authorization.substring(AuthConstants.BEARER_PREFIX.length()), rule);
        AsyncTaskDto task = StringUtils.isBlank(taskCode) ? null : taskService.getByTaskCode(taskCode.trim());
        if (task == null || !ruleMatches(task, rule)) {
            return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "task not found: " + taskCode)));
        }
        return Mono.just(ResponseEntity.ok(body(task)));
    }

    /**
     * 规则归属校验：任务归属的路由规则在 {@code context_json} 里，解析后比对，
     * 不做字符串包含匹配（序列化格式变了就会静默失配）。上下文缺失时按「不匹配」处理 ——
     * 宁可查不到，不可越权可见。
     */
    private boolean ruleMatches(AsyncTaskDto task, String rule) {
        if (StringUtils.isBlank(task.contextJson()) || StringUtils.isBlank(rule)) {
            return false;
        }
        AsyncTaskContext context = JsonKit.fromJson(task.contextJson(), AsyncTaskContext.class);
        return context != null && StringUtils.equals(rule.trim(), context.ruleCode());
    }

    private Map<String, Object> body(AsyncTaskDto task) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("task_id", task.taskCode());
        body.put("status", displayStatus(task.status()));
        body.put("created_at", task.submittedAt());
        body.put("completed_at", task.completedAt());
        body.put("result_expires_at", task.resultExpiresAt());
        if (StringUtils.isNotBlank(task.resultJson())) {
            // 反序列化后回填：直接塞原文会变成「一个 JSON 字符串」，客户端还得再解一次
            body.put("result", JsonKit.fromJson(task.resultJson(),
                    new TypeReference<Map<String, Object>>() {
                    }));
        }
        if (StringUtils.isNotBlank(task.errorCode())) {
            body.put("error", Map.of("code", task.errorCode(),
                    "message", StringUtils.defaultString(task.errorMessage())));
        }
        return body;
    }

    /**
     * 对外状态沿用业界习惯：POLLING 归为 processing，PENDING 为 queued。
     *
     * <p>⭐ <b>对外协议与落库取值是两件事</b>：库里存 0~5 的码，客户端看到的是
     * {@code queued/processing/completed/failed}。所以把 status 改成整型<b>没有</b>改动任何对外契约——
     * 这里由枚举 switch 产生字符串，而不是把 DB 值直接透出去。</p>
     *
     * <p>无法识别的码（理论上不该出现，DB 有 CHECK 拦）落成 {@code unknown}：
     * 宁可让客户端看到一个陌生值，也不要把内部码 {@code 3} 当成状态文本发出去。</p>
     */
    private String displayStatus(Integer status) {
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
}

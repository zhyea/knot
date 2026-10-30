package org.chobit.knot.gateway.controller;

import lombok.RequiredArgsConstructor;
import org.chobit.knot.common.constants.GatewayHeaders;
import org.chobit.knot.gateway.async.AsyncTaskLookupRunner;
import org.chobit.knot.gateway.model.vo.response.AsyncTaskAcceptedResponse;
import org.chobit.knot.gateway.model.vo.response.AsyncTaskNotFoundResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

/**
 * 异步任务查询端点（通用，不限于视频）。
 *
 * <p>客户端轮询 knot 而不是供应商：供应商地址与凭据不外泄、计费集中可控、
 * 未来换供应商客户端无感。路径刻意不挂 {@code /videos} —— 提交 + 轮询是一类通用能力，
 * 视频只是第一个使用者。</p>
 *
 * <p>⭐ <b>本类只做三件事</b>（{@code 10-backend-java.md §3.3}）：绑定入参、委托给
 * {@link AsyncTaskLookupRunner}、把「查不到」翻译成 404。鉴权、查库、归属比对、
 * 状态文案映射、响应体投影全部在 Runner 里——controller 出现 {@code if} / {@code switch} /
 * {@code Map#put} 就是分层错位。</p>
 *
 * <p><b>鉴权</b>：与网关主链路同一套头部（{@code Authorization} + {@code Rule}），
 * 且规则码必须与任务归属一致。不匹配一律 404（而不是 403）——
 * 不泄漏「这个任务句柄是否存在」，避免跨规则枚举。</p>
 *
 * <p><b>为什么查询用 POST 而不是 GET</b>（2026-10-29 robin 拍板，规范见 {@code 10-backend-java.md §5.2}）：
 * 任务句柄在 URL 里，GET 会把它写进浏览器历史、代理与 CDN 的 access log、{@code Referer}，
 * 还会被中间层当成「可缓存、可预取、可重放」的只读请求自行代打（预取一次就是一次真实的库查询）。
 * 这条查询现在确实只读，但契约一旦以 GET 发出去，将来要加副作用（推进轮询态、计数）就改不回来了；
 * 而 POST 的代价只是客户端换个方法名。全文零 GET，见 {@code GatewayHttpMethodGuardTest}。</p>
 *
 * <p><b>测试头</b>：{@code X-Knot-Test-Return-Usage: true} 时响应额外带结算账
 * （{@code knot_extend}）与上游终态原文（{@code upstream_body}），供管理端调试抽屉做计费校验。
 * 这里只把原始头透传给 Runner，判定与投影都在 Runner/Support —— 控制器不知道「什么是证据」。</p>
 */
@RestController
@RequestMapping(AsyncTaskAcceptedResponse.TASKS_PATH)
@RequiredArgsConstructor
public class GatewayTaskController {

    private final AsyncTaskLookupRunner lookupRunner;

    /**
     * 查询一个异步任务（{@code POST}，无请求体：句柄是寻址信息，留在路径上客户端可直接
     * POST 回执里的 {@code query_url}，不必拼 body）。
     *
     * <p>200 / 404 的判定在这里，是因为<b>状态码是 HTTP 关切</b>：Runner 只表达
     * 「有没有这个任务」，把 404 塞进业务层会让它认识 {@code ResponseEntity}。</p>
     */
    @PostMapping("/{taskCode}")
    public Mono<ResponseEntity<Object>> query(@RequestHeader(value = GatewayHeaders.AUTHORIZATION) String authorization,
                                              @RequestHeader(value = GatewayHeaders.RULE) String rule,
                                              @PathVariable("taskCode") String taskCode,
                                              @RequestHeader(value = GatewayHeaders.X_KNOT_TEST_RETURN_USAGE, required = false)
                                              String testReturnUsage) {
        return lookupRunner.lookup(authorization, rule, taskCode, testReturnUsage)
                .<ResponseEntity<Object>>map(ResponseEntity::ok)
                .switchIfEmpty(Mono.fromSupplier(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(AsyncTaskNotFoundResponse.of(taskCode))));
    }
}

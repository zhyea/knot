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
 * 异步任务查询端点（通用，不限于视频），由客户端轮询 knot。
 * 职责：绑定入参、委托 {@link AsyncTaskLookupRunner}、把「查不到」翻译成 404。
 * 鉴权沿用网关主链路头部（{@code Authorization} + {@code Rule}），规则码须与任务归属一致，不匹配一律返回 404。
 * 测试头 {@code X-Knot-Test-Return-Usage: true} 时响应额外带结算账（{@code knot_extend}）与上游终态原文（{@code upstream_body}）。
 */
@RestController
@RequestMapping(AsyncTaskAcceptedResponse.TASKS_PATH)
@RequiredArgsConstructor
public class GatewayTaskController {

    private final AsyncTaskLookupRunner lookupRunner;

    /**
     * 查询一个异步任务（{@code POST}，无请求体：句柄留在路径上，客户端可直接 POST 回执里的 {@code query_url}）。
     * 200 / 404 的状态码判定在本方法，{@link AsyncTaskLookupRunner} 只表达任务是否存在。
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

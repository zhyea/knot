package org.chobit.knot.gateway.controller;

import org.chobit.knot.common.constants.GatewayHeaders;
import org.chobit.knot.common.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.gateway.runtime.GatewayRequestHandler;
import org.chobit.knot.gateway.model.vo.request.DecisionsRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Decisions 协议入口（{@code POST /v1/decisions}）。
 *
 * <p>与对话类协议形态不同：入参是 state + questions，出参是 answers 映射，
 * 上游侧路径由绑定配置的 {@code api_path} 决定（默认走协议默认路径）。</p>
 */
@RestController
@RequestMapping("/v1")
public class GatewayDecisionsController extends GatewayControllerSupport {

    public GatewayDecisionsController(GatewayRequestHandler requestHandler) {
        super(requestHandler);
    }

    /**
     * Handles decisions requests.
     */
    @PostMapping("/decisions")
    public Mono<ResponseEntity<Object>> decisions(
        @RequestHeader(value = GatewayHeaders.AUTHORIZATION) String authorization,
        @RequestHeader(value = GatewayHeaders.RULE) String rule,
        @RequestHeader(value = GatewayHeaders.TRACEPARENT, required = false) String traceparent,
        @RequestHeader(value = GatewayHeaders.X_KNOT_STICKY_KEY, required = false) String stickyKey,
        @RequestBody DecisionsRequest requestBody,
        ServerWebExchange exchange) {
        return handleRequest(authorization, rule, traceparent, stickyKey, requestBody, ModelApiProtocolEnum.DECISIONS, exchange);
    }
}

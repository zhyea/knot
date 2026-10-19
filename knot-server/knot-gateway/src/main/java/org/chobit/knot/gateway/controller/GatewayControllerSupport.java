package org.chobit.knot.gateway.controller;

import org.chobit.knot.gateway.config.GatewayUpstreamClientProperties;
import org.chobit.knot.gateway.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.gateway.runtime.GatewayRequestHandler;
import org.chobit.knot.gateway.vo.request.GatewayModelRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

public abstract class GatewayControllerSupport {

    private final GatewayRequestHandler requestHandler;

    /**
     * 网关异步请求处理线程池（B1 异步化承载池）：模板的同步处理链路在此执行，Tomcat worker 立即释放。
     */
    @Autowired
    @Qualifier("gatewayAsyncExecutor")
    private Executor gatewayAsyncExecutor;

    /**
     * 上游客户端配置：取 {@code requestBudget} 作为单条请求的总预算（B2 全局 deadline）。
     */
    @Autowired
    private GatewayUpstreamClientProperties upstreamProperties;

    protected GatewayControllerSupport(GatewayRequestHandler requestHandler) {
        this.requestHandler = requestHandler;
    }

    /**
     * Delegates a typed gateway API request to the shared request template.
     *
     * <p>B1 异步化：立即返回 {@link DeferredResult}，真正的上游代理链路（含失败重试的退避等待）在专用
     * {@code gatewayAsyncExecutor} 线程池上执行，Tomcat worker 线程立即释放，避免坏上游的重试/退避把
     * Web 容器线程吃干。请求上下文属性（{@link RequestContextHolder}）随异步任务传递，保证流式响应头、
     * 首 token 响应头等仍能在正确线程上写出。</p>
     */
    protected DeferredResult<Object> handleRequest(String authorization,
                                                   String rule,
                                                   String traceparent,
                                                   String stickyKey,
                                                   GatewayModelRequest request,
                                                   ModelApiProtocolEnum protocol) {
        return dispatch(() -> requestHandler.handle(
            authorization, rule, traceparent, stickyKey, request, protocol));
    }

    /**
     * Delegates a multipart gateway API request to the shared request template.
     */
    protected DeferredResult<Object> handleMultipartRequest(String authorization,
                                                            String rule,
                                                            String traceparent,
                                                            String stickyKey,
                                                            Map<String, Object> request,
                                                            ModelApiProtocolEnum protocol) {
        return dispatch(() -> requestHandler.handleMultipart(
            authorization, rule, traceparent, stickyKey, request, protocol));
    }

    /**
     * 把同步的模板处理搬到专用异步线程池执行，并以 {@link DeferredResult} 暴露给 Spring：
     * 异常时透传原始异常（解包 {@link CompletionException}），使 {@code GatewayExceptionHandler}
     * 的既有分支（502/429/401/503…）继续按原异常类型命中。
     */
    private DeferredResult<Object> dispatch(Supplier<Object> task) {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        CompletableFuture<Object> future = CompletableFuture.supplyAsync(() -> {
            RequestContextHolder.setRequestAttributes(requestAttributes);
            try {
                return task.get();
            } finally {
                RequestContextHolder.resetRequestAttributes();
            }
        }, gatewayAsyncExecutor);

        DeferredResult<Object> deferred = new DeferredResult<>(upstreamProperties.getRequestBudget().toMillis());
        future.whenComplete((value, ex) -> {
            if (ex != null) {
                Throwable cause = (ex instanceof CompletionException && ex.getCause() != null)
                    ? ex.getCause() : ex;
                deferred.setErrorResult(cause);
            } else {
                deferred.setResult(value);
            }
        });
        return deferred;
    }
}

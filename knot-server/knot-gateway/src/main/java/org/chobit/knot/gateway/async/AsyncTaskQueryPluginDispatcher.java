package org.chobit.knot.gateway.async;

import lombok.extern.slf4j.Slf4j;
import org.chobit.knot.adapter.upstream.UpstreamRequestContext;
import org.chobit.knot.gateway.upstream.support.UpstreamPluginContextSupport;
import org.chobit.knot.plugin.PluginDispatcher;
import org.chobit.knot.plugin.PluginExtensionPoint;
import org.chobit.knot.plugin.PluginStageCode;
import org.chobit.knot.plugin.upstream.UpstreamPluginContext;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 异步任务查询的三阶段 UPSTREAM_* 日志编排 + fail-open 故障隔离。
 * 查询动作以 {@link Supplier} 传入，本类在它之前 dispatch 请求、成功后 dispatch 响应、失败后 dispatch 异常。
 * 固定单尝试、自带 {@code queryTimeoutMs}（必须小于 claim TTL）、响应原文不可改写、不做每次响应的自动用量提取；只复用 {@link UpstreamPluginContextSupport} 这一个 dispatch 原语，不接管代理链。
 * 在调用侧 fail-open：日志插件抛的异常不改变查询结果、不吞掉上游异常，每个阶段单独 try/catch。
 */
@Slf4j
final class AsyncTaskQueryPluginDispatcher {

    private AsyncTaskQueryPluginDispatcher() {
    }

    /**
     * @param exchange 真正的上游查询；成功返回响应体原文，失败抛 {@code RuntimeException}
     * @return 上游响应体原文（不被插件改写）
     */
    static String execute(PluginDispatcher pluginDispatcher,
                          UpstreamRequestContext upstreamContext,
                          String upstreamUrl,
                          String taskCode,
                          Supplier<String> exchange) {
        dispatchQuietly(pluginDispatcher, PluginStageCode.UPSTREAM_REQUEST,
                upstreamContext, upstreamUrl, taskCode, null, null);
        final String body;
        try {
            body = exchange.get();
        } catch (RuntimeException upstreamEx) {
            dispatchQuietly(pluginDispatcher, PluginStageCode.UPSTREAM_ERROR,
                    upstreamContext, upstreamUrl, taskCode, null, upstreamEx);
            // 插件异常已在 dispatchQuietly 内被吞掉；这里必须原样抛出上游异常，
            // 不能被「插件异常」和「上游异常」两层搞混。
            throw upstreamEx;
        }
        // ⚠ 用 LinkedHashMap 而不是 Map.of：body 可能为 null（上游空响应），Map.of 不接受 null
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("responseBody", body);
        dispatchQuietly(pluginDispatcher, PluginStageCode.UPSTREAM_RESPONSE,
                upstreamContext, upstreamUrl, taskCode, snapshot, null);
        return body;
    }

    private static void dispatchQuietly(PluginDispatcher pluginDispatcher,
                                        PluginStageCode stageCode,
                                        UpstreamRequestContext upstreamContext,
                                        String upstreamUrl,
                                        String taskCode,
                                        Map<String, Object> responseSnapshot,
                                        Throwable error) {
        try {
            // 异步查询没有 ProxyResult，查询响应的供应商请求 id 未做归一，恒传 null。
            UpstreamPluginContext pluginContext = UpstreamPluginContextSupport.of(
                    upstreamContext, stageCode, responseSnapshot, error, null, upstreamUrl);
            pluginDispatcher.dispatch(PluginExtensionPoint.UPSTREAM_EXCHANGE, stageCode, pluginContext);
        } catch (RuntimeException pluginEx) {
            log.error("Async task query plugin dispatch failed: stage={} taskCode={}",
                    stageCode, taskCode, pluginEx);
        }
    }
}

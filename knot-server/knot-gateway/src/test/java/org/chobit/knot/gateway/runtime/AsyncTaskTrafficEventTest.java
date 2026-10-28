package org.chobit.knot.gateway.runtime;

import org.chobit.knot.common.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.gateway.kafka.KafkaPublisher;
import org.chobit.knot.gateway.kafka.msg.GatewayTrafficMsgDto;
import org.chobit.knot.gateway.kafka.msg.Msg;
import org.chobit.knot.gateway.model.ProxyResult;
import org.chobit.knot.gateway.model.ResolvedRouting;
import org.chobit.knot.gateway.upstream.stream.UpstreamStreamResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 异步任务的流量事件契约：<b>1 次提交 + N 次查询，逐次留痕，靠 task_* 字段归到同一笔任务</b>。
 *
 * <p>⭐ 本类取代了原来的 {@code TrafficEventSuppressionTest}。那个类守的是「异步提交时抑制事件」，
 * 而抑制这件事在 2026-10-28 被整体废除：它把「1 次提交 + N 次查询」压缩成「1 条终态事件」，
 * 于是提交那次上游调用、以及中间每一次查询在数仓里全部蒸发——上游成本算不出、
 * 产物延迟分布算不出、「重试 5 次才成功」与「一次成功」长得一模一样。</p>
 *
 * <p>现在的守门点是两组：</p>
 * <ol>
 *   <li><b>不能少发</b>：提交、异常、流式收尾三个出口都照发，且提交那条带
 *       {@code task_phase=submit} —— 少发就是丢上游调用；</li>
 *   <li><b>不能污染同步</b>：同步请求的四个 {@code task_*} 字段必须全部缺席，
 *       否则数仓「客户端请求数」口径（{@code task_phase is null or task_phase='submit'}）会算错。</li>
 * </ol>
 */
class AsyncTaskTrafficEventTest {

    private static final String TOPIC = "knot.traffic";
    private static final String BODY = "{\"id\":\"chat-1\"}";
    private static final ResolvedRouting ROUTING =
            new ResolvedRouting(1L, "rule-a", 10L, "sk-routing-key", false, List.of(), null, null, null);
    private static final String TRACEPARENT = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

    // ---------- 一、事件字段契约（直接压 DTO） ----------

    @Test
    @DisplayName("同步请求：四个 task_* 字段全部缺席（数仓口径靠这一点区分同步与异步）")
    void syncRequestCarriesNoTaskFields() {
        GatewayTrafficMsgDto msg = GatewayTrafficMsgDto.of(context(null), exchange(), null, null, null);

        assertNull(msg.taskCode());
        assertNull(msg.taskPhase());
        assertNull(msg.taskTerminal());
        assertNull(msg.taskQuerySeq());
    }

    @Test
    @DisplayName("提交事件：task_phase=submit，terminal=false，没有 query_seq（此时还没查过）")
    void submitStamp() {
        GatewayTrafficMsgDto msg = GatewayTrafficMsgDto.of(
                context(GatewayTrafficMsgDto.TaskTrace.submit("task-1")), exchange(), null, null, null);

        assertEquals("task-1", msg.taskCode());
        assertEquals("submit", msg.taskPhase());
        assertFalse(msg.taskTerminal(), "提交不是终态——钱还没发生");
        assertNull(msg.taskQuerySeq(), "提交阶段还没查询过，给 0 会被数仓当成「查了 0 次」");
    }

    @Test
    @DisplayName("中间查询事件：terminal=false 且 seq 递增")
    void queryStamp() {
        GatewayTrafficMsgDto msg = GatewayTrafficMsgDto.of(
                context(GatewayTrafficMsgDto.TaskTrace.query("task-1", 7, false)), exchange(), null, null, null);

        assertEquals("query", msg.taskPhase());
        assertFalse(msg.taskTerminal());
        assertEquals(7, msg.taskQuerySeq());
    }

    @Test
    @DisplayName("终态事件：terminal=true —— 计费与失败率统计只看这一个字段")
    void terminalStamp() {
        GatewayTrafficMsgDto msg = GatewayTrafficMsgDto.of(
                context(GatewayTrafficMsgDto.TaskTrace.query("task-1", 12, true)), exchange(), null, null, null);

        assertEquals("query", msg.taskPhase(), "终态也是一次查询，phase 不该另造一个值");
        assertTrue(msg.taskTerminal());
        assertEquals(12, msg.taskQuerySeq(), "12 次才出结果，这个数字就是延迟分布的唯一来源");
    }

    // ---------- 二、三个出口都照发（不再抑制） ----------

    @Test
    @DisplayName("异步提交：事件照发且带 submit 标记（改动前这里会被抑制成 0 条）")
    void submitIsStillPublished() {
        RecordingPublisher publisher = new RecordingPublisher();
        StubTemplate subject = new StubTemplate(publisher, null);
        subject.submitStamp = GatewayTrafficMsgDto.TaskTrace.submit("task-1");

        subject.handleMultipart("Bearer key", "rule-a", null, null,
                Map.of("model", "x"), ModelApiProtocolEnum.CHAT_COMPLETIONS, GatewayHttpExchange.noop()).block();

        assertEquals(1, publisher.count(), "提交那次上游调用必须留痕，它是「1 + N」里的那个 1");
        GatewayTrafficMsgDto msg = (GatewayTrafficMsgDto) publisher.messages.get(0);
        assertEquals("submit", msg.taskPhase());
        assertEquals("task-1", msg.taskCode());
    }

    @Test
    @DisplayName("异步提交后响应层抛异常：仍然恰好一条，且仍带 submit 标记")
    void terminalErrorStillCarriesSubmitStamp() {
        RecordingPublisher publisher = new RecordingPublisher();
        StubTemplate subject = new StubTemplate(publisher, new IllegalStateException("boom"));
        subject.submitStamp = GatewayTrafficMsgDto.TaskTrace.submit("task-1");

        assertThrows(IllegalStateException.class, () -> subject.handleMultipart("Bearer key", "rule-a",
                null, null, Map.of("model", "x"), ModelApiProtocolEnum.CHAT_COMPLETIONS,
                GatewayHttpExchange.noop()).block());

        assertEquals(1, publisher.count());
        assertEquals("submit", ((GatewayTrafficMsgDto) publisher.messages.get(0)).taskPhase());
    }

    @Test
    @DisplayName("同步请求的异常出口：恰好一条，且不带任何 task 标记")
    void syncErrorHasNoStamp() {
        RecordingPublisher publisher = new RecordingPublisher();
        StubTemplate subject = new StubTemplate(publisher, new IllegalStateException("boom"));

        assertThrows(IllegalStateException.class, () -> subject.handleMultipart("Bearer key", "rule-a",
                null, null, Map.of("model", "x"), ModelApiProtocolEnum.CHAT_COMPLETIONS,
                GatewayHttpExchange.noop()).block());

        assertEquals(1, publisher.count());
        assertNull(((GatewayTrafficMsgDto) publisher.messages.get(0)).taskPhase());
    }

    @Test
    @DisplayName("流式收尾：恰好一条事件，且带上请求上下文里的 task 标记")
    void streamCompletionCarriesStamp() {
        RecordingPublisher publisher = new RecordingPublisher();
        GatewayRequestHandler handler = new GatewayRequestHandler(null, null, null, null, null, null,
                null, null, null, null, publisher, TOPIC, null, null, null);

        GatewayRequestContext context = context(null);
        context.putAttribute(GatewayTrafficMsgDto.TaskTrace.ATTRIBUTE,
                GatewayTrafficMsgDto.TaskTrace.query("task-1", 3, true));
        GatewayExchange exchange = new GatewayExchange(Map.of("model", "x"),
                MediaType.APPLICATION_JSON, new RecordingHttpExchange());
        exchange.proxyResult(new ProxyResult(null, 1L, 2L, null,
                new UpstreamStreamResponse(Flux.empty(), MediaType.APPLICATION_OCTET_STREAM,
                        null, null, null, null), null, null));

        Object response = handler.applyUsageAccounting(context, exchange);
        assertNotNull(response);
        assertTrue(response instanceof GatewayStreamingResponse, "非 event-stream 上游仍走流式转发");
        Flux.from(((GatewayStreamingResponse) response).body()).then().block();

        assertEquals(1, publisher.count());
        GatewayTrafficMsgDto msg = (GatewayTrafficMsgDto) publisher.messages.get(0);
        assertEquals("query", msg.taskPhase());
        assertTrue(msg.taskTerminal());
        assertEquals(3, msg.taskQuerySeq());
    }

    // ---------- 三、开关 ----------

    @Test
    @DisplayName("投递器缺失（kafka 关闭）时静默跳过，提交照样返回业务体")
    void noPublisherIsSilent() {
        StubTemplate subject = new StubTemplate(null, null);
        subject.submitStamp = GatewayTrafficMsgDto.TaskTrace.submit("task-1");

        Object response = subject.handleMultipart("Bearer key", "rule-a", null, null,
                Map.of("model", "x"), ModelApiProtocolEnum.CHAT_COMPLETIONS, GatewayHttpExchange.noop()).block();

        assertEquals(BODY, response);
    }

    // ---------- 辅助 ----------

    private static GatewayRequestContext context(GatewayTrafficMsgDto.TaskTrace trace) {
        GatewayRequestContext context = new GatewayRequestContext("Bearer sk", "rule-a",
                GatewayTraceContext.fromTraceparent(TRACEPARENT),
                ModelApiProtocolEnum.CHAT_COMPLETIONS, null);
        context.routing(ROUTING);
        if (trace != null) {
            context.putAttribute(GatewayTrafficMsgDto.TaskTrace.ATTRIBUTE, trace);
        }
        return context;
    }

    private static GatewayExchange exchange() {
        return new GatewayExchange(Map.of("model", "x"), MediaType.APPLICATION_JSON);
    }

    private static final class RecordingPublisher extends KafkaPublisher {

        private final List<String> topics = new ArrayList<>();
        private final List<Msg> messages = new ArrayList<>();

        private RecordingPublisher() {
            super(null, null);
        }

        @Override
        public void publish(String topic, Msg msg) {
            topics.add(topic);
            messages.add(msg);
        }

        private int count() {
            return messages.size();
        }
    }

    /**
     * 最小模板：代理与计费都退化到常量 / 指定失败，只关心「事件发不发、带什么标记」。
     *
     * <p>{@code submitStamp} 模拟 {@code GatewayRequestHandler} 在异步提交成功时写入的属性——
     * 本类测的是模板层<b>不再判断</b>，标记由上层写、事件原样带走。</p>
     */
    private static final class StubTemplate extends AbstractGatewayRequestTemplate {

        private final RuntimeException failure;
        private GatewayTrafficMsgDto.TaskTrace submitStamp;

        private StubTemplate(KafkaPublisher publisher, RuntimeException failure) {
            super(null, null, publisher, TOPIC);
            this.failure = failure;
        }

        @Override
        protected ResolvedRouting resolveRouting(GatewayRequestContext context) {
            return ROUTING;
        }

        @Override
        protected Mono<ProxyResult> proxy(GatewayRequestContext context, GatewayExchange exchange) {
            // 标记必须在任何出口之前写：成功与异常两条路径都要能读到它
            if (submitStamp != null) {
                context.putAttribute(GatewayTrafficMsgDto.TaskTrace.ATTRIBUTE, submitStamp);
            }
            return failure == null ? Mono.just(new ProxyResult(BODY, 1L, 2L, null)) : Mono.error(failure);
        }

        @Override
        protected Object applyUsageAccounting(GatewayRequestContext context, GatewayExchange exchange) {
            return exchange.proxyResult().responseBody();
        }
    }
}

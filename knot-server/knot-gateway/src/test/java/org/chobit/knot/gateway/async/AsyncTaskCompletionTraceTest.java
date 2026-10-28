package org.chobit.knot.gateway.async;

import org.chobit.knot.dal.entity.AsyncTaskEntity;
import org.chobit.knot.dal.mapper.AppMapper;
import org.chobit.knot.dal.mapper.BillingRuleMapper;
import org.chobit.knot.dal.mapper.ModelApiBindingMapper;
import org.chobit.knot.dal.mapper.ModelMapper;
import org.chobit.knot.dal.mapper.ModelPoolMapper;
import org.chobit.knot.dal.mapper.ProviderAccountMapper;
import org.chobit.knot.dal.mapper.ProviderCredentialMapper;
import org.chobit.knot.dal.mapper.RoutingConsumerMapper;
import org.chobit.knot.dal.mapper.RoutingRuleMapper;
import org.chobit.knot.dal.mapper.RoutingRuleTargetMapper;
import org.chobit.knot.gateway.kafka.KafkaPublisher;
import org.chobit.knot.gateway.kafka.msg.GatewayTrafficMsgDto;
import org.chobit.knot.gateway.kafka.msg.Msg;
import org.chobit.knot.gateway.runtime.GatewayTraceContext;
import org.chobit.knot.gateway.service.GatewayEntityCacheService;
import org.chobit.knot.svc.service.support.ResourceTrafficPolicySupport;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 异步任务「1 + N 条事件共用同一追踪坐标」的<b>实现层</b>守门。
 *
 * <p>⭐ 为什么必须有这个测试（{@code GatewayTraceContextTest} 已经验过 {@code replay} 本身）：
 * {@code replay} 是对的，不等于 {@link AsyncTaskCompletion#publish} 用了它。上一版的实现调的是
 * {@code fromTraceparent}，那句代码在单元测试里毫无破绽——它会正常返回一个 traceId 相同的上下文，
 * 只有 spanId 每次悄悄换一个。**唯一能抓住这件事的地方就是这里**：跑一遍 {@code publish}，
 * 看真实产出的消息同不同意。</p>
 *
 * <p>探针方式：把 {@code publish} 里的 {@code replay(...)} 改回 {@code fromTraceparent(...)}，
 * 本类必须变红（且 {@code GatewayTraceContextTest} 全绿）——那正是这个测试存在的全部理由。</p>
 */
class AsyncTaskCompletionTraceTest {

    private static final String SUBMIT_TRACEPARENT =
            "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

    private final RecordingPublisher publisher = new RecordingPublisher();

    private final AsyncTaskCompletion completion = new AsyncTaskCompletion(
            null, null, new StubEntityCache(), publisher, "knot.gateway.traffic");

    /**
     * N 次查询 + 终态必须与提交那次拿到<b>完全一致</b>的 {@code traceId}/{@code spanId}/{@code requestId}。
     *
     * <p>这是「让 N 次请求使用相同 traceid + span_id」这条要求的直接断言：
     * 数仓按 {@code (trace_id, span_id)} 就能把一笔任务的 1 + N 条捞成一组。</p>
     */
    @Test
    void everyEventSharesSubmitTraceCoordinate() {
        // 提交那次的追踪上下文：网关解析入站 traceparent 后为本次请求生成了自己的 span
        GatewayTraceContext submit = GatewayTraceContext.fromTraceparent(SUBMIT_TRACEPARENT);
        AsyncTaskContext context = contextOf(submit);
        AsyncTaskEntity task = taskOf("tk_share");

        completion.publishQuery(task, context, 1, null);
        completion.publishQuery(task, context, 2, null);
        completion.publishQuery(task, context, 3, "UPSTREAM_QUERY_ERROR");
        completion.publishTerminal(task, context, 4, null, null);

        assertEquals(4, publisher.messages.size(), "1 次提交之外，2 次成功查询 + 1 次失败查询 + 终态共 4 条");
        for (GatewayTrafficMsgDto msg : publisher.messages) {
            assertEquals(submit.traceId(), msg.traceId(), "traceId 必须沿用提交那次");
            assertEquals(submit.spanId(), msg.spanId(),
                    "spanId 必须沿用提交那次的 span —— 用 fromTraceparent 重建会每次新生成一个");
            assertEquals(submit.requestId(), msg.requestId(), "requestId 必须沿用提交那次");
            assertEquals("tk_share", msg.taskCode());
        }
    }

    /**
     * 区分 N 次查询靠的是 {@code task_query_seq}，不是 trace 坐标。
     *
     * <p>顺带把「共用坐标会不会丢信息」这个疑问钉死：坐标相同、序号递进，
     * 数仓仍能把第几次查询排出来。</p>
     */
    @Test
    void querySeqIsWhatDistinguishesQueries() {
        GatewayTraceContext submit = GatewayTraceContext.fromTraceparent(SUBMIT_TRACEPARENT);
        AsyncTaskContext context = contextOf(submit);
        AsyncTaskEntity task = taskOf("tk_seq");

        completion.publishQuery(task, context, 1, null);
        completion.publishQuery(task, context, 2, null);

        GatewayTrafficMsgDto first = publisher.messages.get(0);
        GatewayTrafficMsgDto second = publisher.messages.get(1);

        assertEquals(1, first.taskQuerySeq());
        assertEquals(2, second.taskQuerySeq());
        // 坐标一致、序号不同 —— 两条事件既归得进一组，又排得出先后
        assertEquals(first.spanId(), second.spanId());
    }

    /**
     * 中间查询失败只带错误码、不落终态；终态那条才置 {@code task_terminal}。
     *
     * <p>与 {@code AsyncTaskStateMachine} 的「有码 ≠ 落错误终态」是同一条口径在消息层的体现。</p>
     */
    @Test
    void terminalFlagSeparatesRetryFromFailure() {
        AsyncTaskContext context = contextOf(GatewayTraceContext.fromTraceparent(SUBMIT_TRACEPARENT));
        AsyncTaskEntity task = taskOf("tk_terminal");

        completion.publishQuery(task, context, 1, "UPSTREAM_QUERY_ERROR");
        completion.publishTerminal(task, context, 2, null, null);

        GatewayTrafficMsgDto retried = publisher.messages.get(0);
        assertNotEquals(Boolean.TRUE, retried.taskTerminal(), "中间查询失败不是终态");
        assertNotNull(retried.error(), "但错误码要进事件，否则上游抖动在数仓里看不见");

        GatewayTrafficMsgDto settled = publisher.messages.get(1);
        assertEquals(Boolean.TRUE, settled.taskTerminal());
        assertNull(settled.error());
    }

    /**
     * 提交时没留下可解析的 traceparent（脏数据 / 老任务）时，坐标退化为新生成——
     * 但 requestId 仍然沿用，不能让同一笔任务连「我方请求编号」都分裂。
     */
    @Test
    void unreadableTraceparentStillKeepsRequestId() {
        AsyncTaskContext context = new AsyncTaskContext("tk_dirty", null, null,
                "not-a-traceparent", "0123456789abcdef0123456789abcdef",
                "VIDEO_GENERATIONS", "m", "up", "p", "pool", "https://x");
        AsyncTaskEntity task = taskOf("tk_dirty");

        completion.publishQuery(task, context, 1, null);
        completion.publishQuery(task, context, 2, null);

        for (GatewayTrafficMsgDto msg : publisher.messages) {
            assertEquals("0123456789abcdef0123456789abcdef", msg.requestId());
        }
    }

    private static AsyncTaskContext contextOf(GatewayTraceContext submit) {
        return new AsyncTaskContext("tk", "rule-1", "sticky-1",
                submit.traceparent(), submit.requestId(),
                "VIDEO_GENERATIONS", "kling-v3", "kling-v3-turbo", "pa-1", "pool-1",
                "https://upstream.example/v1/videos");
    }

    private static AsyncTaskEntity taskOf(String taskCode) {
        AsyncTaskEntity task = new AsyncTaskEntity();
        task.setTaskCode(taskCode);
        task.setRequestJson("{\"model\":\"kling-v3\",\"prompt\":\"x\"}");
        return task;
    }

    /**
     * 投递器桩：只记录下发的消息。
     *
     * <p>用子类覆写而不是 mock：{@code KafkaPublisher} 是具体类（JDK 25 下 Mockito 不能 mock 具体类），
     * 且 {@link #publish} 非 final。构造器的两个依赖在本类覆写后永不被用到，传 null 即可。</p>
     */
    private static final class RecordingPublisher extends KafkaPublisher {

        private final List<GatewayTrafficMsgDto> messages = new ArrayList<>();

        private RecordingPublisher() {
            super(null, null);
        }

        @Override
        public void publish(String topic, Msg msg) {
            messages.add((GatewayTrafficMsgDto) msg);
        }
    }

    /**
     * 数据桩：{@code getModelByCode} 返回 null —— {@link AsyncTaskCompletion#publish} 对 null
     * 模型是容忍的（消息里的模型 id 落空，但 trace/task 字段照发），正好够本类断言追踪坐标。
     *
     * <p>构造器不能吃 null mapper：javac 对绑定方法引用（{@code modelMapper::getByCode}）的接收者
     * 会插入 {@code Objects.requireNonNull}，故用动态代理占位。</p>
     */
    private static final class StubEntityCache extends GatewayEntityCacheService {

        private StubEntityCache() {
            super(nullProxy(AppMapper.class),
                    nullProxy(RoutingConsumerMapper.class),
                    nullProxy(RoutingRuleMapper.class),
                    nullProxy(RoutingRuleTargetMapper.class),
                    nullProxy(ModelMapper.class),
                    nullProxy(ModelPoolMapper.class),
                    nullProxy(ProviderAccountMapper.class),
                    nullProxy(ProviderCredentialMapper.class),
                    nullProxy(ModelApiBindingMapper.class),
                    new ResourceTrafficPolicySupport(null, null, null, null),
                    nullProxy(BillingRuleMapper.class));
        }

        @Override
        public org.chobit.knot.dal.entity.ModelEntity getModelByCode(String modelCode) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T nullProxy(Class<T> type) {
        return (T) java.lang.reflect.Proxy.newProxyInstance(
                type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> null);
    }
}

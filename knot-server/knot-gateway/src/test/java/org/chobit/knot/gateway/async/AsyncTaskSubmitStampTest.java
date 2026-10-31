package org.chobit.knot.gateway.async;

import org.chobit.knot.common.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.svc.dto.async.AsyncTaskDraftDto;
import org.chobit.knot.svc.service.AsyncTaskService;
import org.chobit.knot.dal.entity.ModelApiBindingEntity;
import org.chobit.knot.svc.converter.GatewayConfigConverter;
import org.chobit.knot.svc.service.GatewayConfigQueryService;
import org.chobit.knot.svc.vo.gateway.ModelApiBindingConfigVo;
import org.mapstruct.factory.Mappers;
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
import org.chobit.knot.gateway.kafka.msg.GatewayTrafficMsgDto.TaskTrace;
import org.chobit.knot.gateway.model.ProxyResult;
import org.chobit.knot.gateway.model.vo.response.AsyncTaskAcceptedResponse;
import org.chobit.knot.gateway.runtime.GatewayRequestContext;
import org.chobit.knot.gateway.runtime.GatewayTraceContext;
import org.chobit.knot.gateway.service.GatewayConfigCacheService;
import org.chobit.knot.svc.service.support.ResourceTrafficPolicySupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 提交事件的 {@code task} 标记归属：由 {@link AsyncTaskSupport} 写，不由请求链路写。
 *
 * <p>⭐ 为什么标记要在这里而不是 {@code GatewayRequestHandler}：请求链路只回答
 * 「这次是不是异步」。一旦它也开始拼 {@code TaskTrace}，同步链路就重新耦合上事件格式，
 * 而事件格式是会变的（`task_*` 这组字段 2026-10-28 才加）。放这里还有一层好处——
 * 提交标记与轮询侧发的查询/终态标记出自同一个包，口径不可能漂移。</p>
 *
 * <p>探针：删掉 {@code submitIfAsync} 里那行 {@code putAttribute}，本类 2 例必红。</p>
 */
class AsyncTaskSubmitStampTest {

    private final RecordingStore store = new RecordingStore();

    private final AsyncTaskSupport support = new StubSupport(store);

    /**
     * 判定为异步并成功落库时，context 上必须已经带上 {@code phase=submit} 的标记。
     *
     * <p>这条标记就是请求结束、模板发布那一刻事件里 {@code task_code} 的来源；
     * 丢了它，提交事件在数仓里就没有 {@code task_code}，1 + N 会被拆成「1 个孤儿 + N 个一组」。</p>
     */
    @Test
    @DisplayName("提交成功：context 上带 phase=submit 标记，task_code 与回执一致")
    void submitStampsContext() {
        GatewayRequestContext context = context();
        AsyncTaskAcceptedResponse accepted = support.submitIfAsync(context, null, createResult());

        assertNotNull(accepted, "档案齐全 + 上游返回 task_id，应判定为异步");
        TaskTrace trace = context.attribute(TaskTrace.ATTRIBUTE);
        assertNotNull(trace, "提交事件必须带任务标记，否则数仓无法把它归入这笔任务");
        assertEquals(TaskTrace.PHASE_SUBMIT, trace.phase());
        assertEquals(false, trace.terminal());
        assertNull(trace.querySeq(), "提交还没有查询过，序号必须是空而不是 0");
        assertEquals(accepted.taskCode(), trace.taskCode(), "标记里的 task_code 必须与回给客户端的回执同一个");
        assertEquals(1, store.inserted.size());
    }

    /**
     * 判定不成立（绑定上没配档案）时走同步链路，**不能留下任何标记**。
     *
     * <p>反方向守门：标记一旦漏到同步请求上，那条事件的 {@code task_phase} 就有值，
     * 数仓按 {@code task_phase is null or = 'submit'} 统计客户端请求数时会静默算错。</p>
     */
    @Test
    @DisplayName("非异步：不写任何标记，同步事件保持 task_phase 为空")
    void syncLeavesNoStamp() {
        GatewayRequestContext context = context();
        AsyncTaskSupport syncOnly = new AsyncTaskSupport(null, null, store, new AsyncTaskProperties()) {
            @Override
            public ModelApiBindingConfigVo bindingOf(Long modelId, ModelApiProtocolEnum protocol) {
                return CONVERTER.toApiBindingVo(new ModelApiBindingEntity());
            }
        };

        assertNull(syncOnly.submitIfAsync(context, null, createResult()));
        assertNull(context.attribute(TaskTrace.ATTRIBUTE), "同步请求不得带 task 标记");
        assertEquals(0, store.inserted.size());
    }

    private static GatewayRequestContext context() {
        return new GatewayRequestContext(null, "rule-1",
                GatewayTraceContext.fromTraceparent("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"),
                ModelApiProtocolEnum.VIDEO_GENERATIONS, null);
    }

    private static ProxyResult createResult() {
        return new ProxyResult("{\"data\":{\"id\":\"up-1\",\"status\":\"submitted\"}}", 1L, 2L, null);
    }

    /**
     * 只替身两个判定钩子（它们本就是 public 的厂商知识边界），其余走真实实现——
     * 这样 {@code submitIfAsync} 的内部流程（构信封、落库、打标记）都是被测代码本身。
     */
    private static final class StubSupport extends AsyncTaskSupport {

        private StubSupport(AsyncTaskService store) {
            // properties 必须是真的：submitIfAsync 会读回调白名单（即使本次没有 callback_url）
            super(null, new StubEntityCache(), store, new AsyncTaskProperties());
        }

        @Override
        public ModelApiBindingConfigVo bindingOf(Long modelId, ModelApiProtocolEnum protocol) {
            ModelApiBindingEntity binding = new ModelApiBindingEntity();
            binding.setAsyncProfileCode("KLING_TASK");
            return CONVERTER.toApiBindingVo(binding);
        }

        @Override
        public AsyncTaskSpec loadSpec(String profileCode, ModelApiProtocolEnum protocol) {
            return new AsyncTaskSpec("GET", "/v1/tasks/{task_id}", "data.id", "data.status",
                    "status == 'succeed'", "status == 'failed'", null, "data.url", null, null,
                    7, 5000, 1000, 30000, 360, 1_800_000L);
        }
    }

    /**
     * 数据桩：全方法返回 null。{@code submitIfAsync} 只用 {@code getModelByCode} 取计费规则，
     * 取不到是合法路径（消息里模型 id 落空，但任务照落库）。
     */
    private static final GatewayConfigConverter CONVERTER =
            Mappers.getMapper(GatewayConfigConverter.class);

    private static final class StubEntityCache extends GatewayConfigCacheService {

        private StubEntityCache() {
            super(new GatewayConfigQueryService(
                            nullProxy(AppMapper.class),
                            nullProxy(RoutingConsumerMapper.class),
                            nullProxy(RoutingRuleMapper.class),
                            nullProxy(RoutingRuleTargetMapper.class),
                            nullProxy(ModelMapper.class),
                            nullProxy(ModelPoolMapper.class),
                            nullProxy(ProviderAccountMapper.class),
                            nullProxy(ProviderCredentialMapper.class),
                            nullProxy(ModelApiBindingMapper.class),
                            nullProxy(BillingRuleMapper.class),
                            CONVERTER),
                    new ResourceTrafficPolicySupport(null, null, null, null));
        }
    }

    /**
     * 存储桩：只记下 {@code insert} 的任务，其余方法不会被本类触达。
     *
     * <p>⭐ 用<b>继承覆写</b>而不是实现接口：svc 合并后 {@code AsyncTaskService} 是具体类
     * （与 {@code BillingService} 同构，直连 Mapper），单实现接口那一层已经拆掉了。
     * 传 {@code null} 给构造器是安全的——被覆写的 {@code insert} 不碰 Mapper，
     * 其余方法本用例不触达。</p>
     */
    private static final class RecordingStore extends AsyncTaskService {

        private final List<AsyncTaskDraftDto> inserted = new ArrayList<>();

        private RecordingStore() {
            super(null);
        }

        @Override
        public void insert(AsyncTaskDraftDto task) {
            inserted.add(task);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T nullProxy(Class<T> type) {
        return (T) java.lang.reflect.Proxy.newProxyInstance(
                type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> null);
    }
}

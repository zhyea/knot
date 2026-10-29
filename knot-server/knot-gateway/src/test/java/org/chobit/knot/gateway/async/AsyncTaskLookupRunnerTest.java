package org.chobit.knot.gateway.async;

import org.chobit.knot.common.constants.enums.AsyncTaskStatusEnum;
import org.chobit.knot.common.util.JsonKit;
import org.chobit.knot.gateway.exception.GatewayAuthException;
import org.chobit.knot.gateway.exception.GatewayInvalidRequestException;
import org.chobit.knot.gateway.model.vo.response.AsyncTaskFailureResponse;
import org.chobit.knot.gateway.model.vo.response.AsyncTaskQueryResponse;
import org.chobit.knot.gateway.routing.RoutingResolver;
import org.chobit.knot.gateway.service.GatewayEntityCacheService;
import org.chobit.knot.svc.dto.async.AsyncTaskDto;
import org.chobit.knot.svc.service.AsyncTaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 异步任务查询的<b>业务契约</b>（{@link AsyncTaskLookupRunner}）。
 *
 * <p>⭐ 这些断言原本写在 {@code GatewayTaskControllerTest} 里——业务在 controller 里时，
 * 要验「鉴权没过就不查库」「跨规则不可见」「status 到对外文案的映射」只能顺手透过控制器测。
 * 业务搬进 Runner 后测试跟着搬（能力搬家而测试不搬＝留下一个测不到实现的空壳）：
 * 同一个行为现在直接打在承载它的类上，控制器只剩 200/404 的映射另行守。</p>
 *
 * <p>守三条：</p>
 * <ol>
 *   <li><b>鉴权不能只看 header 前缀</b>——必须走与主链路同一份的 {@code RoutingResolver#requireRuleAccess}；</li>
 *   <li><b>句柄不外泄</b>——不存在与越权都返回空 {@code Mono}（controller 统一翻 404），
 *       任何「403 或不同文案」的区分都等于确认句柄存在；</li>
 *   <li><b>对外状态不是落库码</b>——库里 0~5，客户端看到 queued/processing/completed/failed。</li>
 * </ol>
 */
class AsyncTaskLookupRunnerTest {

    private RecordingTaskService taskService;
    private RecordingRoutingResolver resolver;
    private AsyncTaskLookupRunner runner;

    @BeforeEach
    void setUp() {
        taskService = new RecordingTaskService();
        resolver = new RecordingRoutingResolver();
        // 调度器传 null：走 MdcContextSupport 的兜底 boundedElastic，与生产同语义
        runner = new AsyncTaskLookupRunner(taskService, resolver, null);
    }

    @Test
    @DisplayName("非 Bearer 凭据直接 401，不查库")
    void rejectsNonBearer() {
        assertThrows(GatewayAuthException.class, () -> runner.lookup("Basic abc", "rule-1", "tc-1"));
        assertNull(resolver.seenSecretKey, "鉴权没过就不该往下走");
        assertEquals(0, taskService.lookups, "不该进库查任务");
    }

    @Test
    @DisplayName("鉴权交给 RoutingResolver，且传的是去掉 Bearer 前缀的裸 key")
    void delegatesAuthorization() {
        taskService.put("tc-1", task(AsyncTaskStatusEnum.PENDING, "rule-1", null, null));
        runner.lookup("Bearer sk-abc", "rule-1", "tc-1").block();
        assertEquals("sk-abc", resolver.seenSecretKey);
        assertEquals("rule-1", resolver.seenRuleCode);
    }

    @Test
    @DisplayName("key 无效 / 规则不属于该消费者：401，且不进库查任务")
    void unauthorizedKeyIsRejected() {
        resolver.error = new GatewayAuthException("Consumer API key not found");
        // ⚠ 鉴权段现在跑在调度器线程上，异常以 onError 形式到达 → 必须 block() 才拿得到
        assertThrows(GatewayAuthException.class,
                () -> runner.lookup("Bearer sk-bad", "rule-1", "tc-1").block());
        assertEquals(0, taskService.lookups, "不该进库查任务");
    }

    @Test
    @DisplayName("规则头为空：400（与主链路同口径，不是查不到）")
    void blankRuleIsBadRequest() {
        resolver.error = new GatewayInvalidRequestException("Rule header must not be blank");
        assertThrows(GatewayInvalidRequestException.class,
                () -> runner.lookup("Bearer sk-abc", " ", "tc-1").block());
    }

    @Test
    @DisplayName("探针：鉴权与查库必须离开调用线程（event loop 上做阻塞 JDBC 会卡死连接）")
    void blockingSectionLeavesCallerThread() {
        Scheduler probe = Schedulers.newSingle("probe-blocking");
        try {
            AsyncTaskLookupRunner offloaded = new AsyncTaskLookupRunner(taskService, resolver, probe);
            taskService.put("tc-1", task(AsyncTaskStatusEnum.PENDING, "rule-1", null, null));

            offloaded.lookup("Bearer sk-abc", "rule-1", "tc-1").block();

            assertTrue(resolver.seenThreadName != null && resolver.seenThreadName.startsWith("probe-blocking"),
                    "鉴权（查库）仍留在调用线程上：" + resolver.seenThreadName);
            assertTrue(taskService.seenThreadName != null && taskService.seenThreadName.startsWith("probe-blocking"),
                    "查任务仍留在调用线程上：" + taskService.seenThreadName);
        } finally {
            probe.dispose();
        }
    }

    @Test
    @DisplayName("任务不存在或规则不匹配：一律空 Mono（由 controller 翻成同一个 404）")
    void unknownOrForeignTaskIsEmpty() {
        taskService.put("missing", null);
        assertTrue(runner.lookup("Bearer sk-abc", "rule-1", "missing").blockOptional().isEmpty(),
                "查不到必须回空 Mono，不能回 null 值让调用方自己猜");

        taskService.put("tc-1", task(AsyncTaskStatusEnum.PENDING, "rule-1", null, null));
        assertTrue(runner.lookup("Bearer sk-abc", "rule-other", "tc-1").blockOptional().isEmpty(),
                "跨规则查询必须同样查不到：区分「存在但无权」会泄漏句柄存在性");
    }

    @Test
    @DisplayName("上下文缺失时按不匹配处理：宁可查不到，不可越权可见")
    void missingContextIsEmpty() {
        taskService.put("tc-1", task(AsyncTaskStatusEnum.PENDING, null, null, null));
        assertTrue(runner.lookup("Bearer sk-abc", "rule-1", "tc-1").blockOptional().isEmpty());
    }

    @Test
    @DisplayName("在途任务：对外状态 queued / processing")
    void inFlightStatusMapping() {
        taskService.put("tc-1", task(AsyncTaskStatusEnum.PENDING, "rule-1", null, null));
        assertEquals("queued", statusOf("tc-1"));

        taskService.put("tc-2", task(AsyncTaskStatusEnum.POLLING, "rule-1", null, null));
        assertEquals("processing", statusOf("tc-2"));
    }

    @Test
    @DisplayName("终态：completed 带产物对象（不是 JSON 字符串），failed 带错误码")
    void terminalStatusMapping() {
        taskService.put("tc-1", task(AsyncTaskStatusEnum.SUCCEEDED, "rule-1",
                "{\"result_url\":\"https://x/a.mp4\"}", null));
        AsyncTaskQueryResponse done = runner.lookup("Bearer sk-abc", "rule-1", "tc-1").block();

        assertEquals("completed", done.status());
        // 产物必须是对象：塞原文会让客户端再解一次字符串
        assertEquals(Map.of("result_url", "https://x/a.mp4"), done.result());
        assertNull(done.error());

        taskService.put("tc-2", task(AsyncTaskStatusEnum.FAILED, "rule-1", null,
                "UPSTREAM_TASK_FAILED", "prompt rejected"));
        AsyncTaskQueryResponse failed = runner.lookup("Bearer sk-abc", "rule-1", "tc-2").block();

        assertEquals("failed", failed.status());
        assertEquals(new AsyncTaskFailureResponse("UPSTREAM_TASK_FAILED", "prompt rejected"), failed.error());
        assertNull(failed.result());
    }

    @Test
    @DisplayName("探针：规则名带前后空格应视为同一条规则，不得因空格而查不到")
    void ruleIsTrimmed() {
        taskService.put("tc-1", task(AsyncTaskStatusEnum.PENDING, "rule-1", null, null));
        assertTrue(runner.lookup("Bearer sk-abc", " rule-1 ", "tc-1").blockOptional().isPresent());
    }

    private String statusOf(String taskCode) {
        return runner.lookup("Bearer sk-abc", "rule-1", taskCode).block().status();
    }

    private static AsyncTaskDto task(AsyncTaskStatusEnum status, String ruleCode, String resultJson, String errorCode) {
        return task(status, ruleCode, resultJson, errorCode, null);
    }

    /** {@code errorMessage} 与「有没有上下文」都是显式参数：record 造完不能改，用例得一次说清 */
    private static AsyncTaskDto task(AsyncTaskStatusEnum status, String ruleCode, String resultJson,
                                     String errorCode, String errorMessage) {
        String contextJson = ruleCode == null ? null
                : JsonKit.toJson(new AsyncTaskContext("tc", ruleCode, null, null, "req",
                "VIDEO_GENERATIONS", "m", "up", "p", "pool", "https://x"));
        return new AsyncTaskDto(null, "tc", null, null, null,
                status.code(), null, null, null, null,
                null, null, contextJson, resultJson, errorCode, errorMessage,
                null, null, null, null, null, null,
                null, null, null, null, null, null);
    }

    /**
     * 任务表桩：{@link AsyncTaskService} 是具体类（直连 Mapper），故子类覆写而非 mock。
     *
     * <p>⚠ 本机 JDK 25 下 Mockito 的 inline mock maker 无法 instrument 具体类
     * （{@code Byte Buddy could not instrument all classes within the mock's type hierarchy}），
     * 同 {@link RecordingRoutingResolver} 的处理方式。</p>
     *
     * <p>{@code lookups} 不是为了好看：{@code verifyNoInteractions} 的语义是「鉴权没过就别查库」，
     * 换过来必须有一个计数才守得住——少了它就是「测了鉴权、没测不查库」。</p>
     */
    private static final class RecordingTaskService extends AsyncTaskService {

        private final Map<String, AsyncTaskDto> tasks = new HashMap<>();
        private int lookups;
        /** 执行查库的线程名：由 {@link #blockingSectionLeavesCallerThread} 断言「已离开调用线程」 */
        private String seenThreadName;

        private RecordingTaskService() {
            // 覆写掉的 getByTaskCode 不碰 Mapper，传 null 安全
            super(null);
        }

        private void put(String taskCode, AsyncTaskDto task) {
            tasks.put(taskCode, task);
        }

        @Override
        public AsyncTaskDto getByTaskCode(String taskCode) {
            lookups++;
            seenThreadName = Thread.currentThread().getName();
            return tasks.get(taskCode);
        }
    }

    /**
     * 只记录入参 / 按测试意愿抛错：{@code RoutingResolver} 是具体类，
     * 且构造器会用 dataService 建方法引用（不能传 null），故子类覆写而非 mock。
     */
    private static final class RecordingRoutingResolver extends RoutingResolver {

        private String seenSecretKey;
        private String seenRuleCode;
        /** 执行鉴权的线程名：同上，用于断言阻塞段已离开调用线程 */
        private String seenThreadName;
        private RuntimeException error;

        private RecordingRoutingResolver() {
            super(new GatewayEntityCacheService(
                            nullReturningProxy(org.chobit.knot.dal.mapper.AppMapper.class),
                            nullReturningProxy(org.chobit.knot.dal.mapper.RoutingConsumerMapper.class),
                            nullReturningProxy(org.chobit.knot.dal.mapper.RoutingRuleMapper.class),
                            nullReturningProxy(org.chobit.knot.dal.mapper.RoutingRuleTargetMapper.class),
                            nullReturningProxy(org.chobit.knot.dal.mapper.ModelMapper.class),
                            nullReturningProxy(org.chobit.knot.dal.mapper.ModelPoolMapper.class),
                            nullReturningProxy(org.chobit.knot.dal.mapper.ProviderAccountMapper.class),
                            nullReturningProxy(org.chobit.knot.dal.mapper.ProviderCredentialMapper.class),
                            nullReturningProxy(org.chobit.knot.dal.mapper.ModelApiBindingMapper.class),
                            new org.chobit.knot.svc.service.support.ResourceTrafficPolicySupport(null, null, null, null),
                            nullReturningProxy(org.chobit.knot.dal.mapper.BillingRuleMapper.class)),
                    null, null, null, null);
        }

        @Override
        public void requireRuleAccess(String secretKey, String ruleCode) {
            this.seenSecretKey = secretKey;
            this.seenRuleCode = ruleCode;
            this.seenThreadName = Thread.currentThread().getName();
            if (error != null) {
                throw error;
            }
        }
    }

    /**
     * mapper 占位：{@code GatewayEntityCacheService} 的构造器对每个 mapper 建方法引用，
     * 传 null 会在构造期 NPE（javac 对方法引用接收者有显式空值检查）——同 {@code RoutingResolverTest} 的做法。
     */
    @SuppressWarnings("unchecked")
    private static <T> T nullReturningProxy(Class<T> type) {
        return (T) java.lang.reflect.Proxy.newProxyInstance(
                type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> null);
    }
}

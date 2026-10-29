package org.chobit.knot.gateway.controller;

import org.chobit.knot.common.util.JsonKit;
import org.chobit.knot.gateway.exception.GatewayAuthException;
import org.chobit.knot.gateway.async.AsyncTaskContext;
import org.chobit.knot.svc.dto.async.AsyncTaskDto;
import org.chobit.knot.common.constants.enums.AsyncTaskStatusEnum;
import org.chobit.knot.svc.service.AsyncTaskService;
import org.chobit.knot.gateway.exception.GatewayInvalidRequestException;
import org.chobit.knot.gateway.routing.RoutingResolver;
import org.chobit.knot.gateway.service.GatewayEntityCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 契约测试：异步任务查询端点（{@link GatewayTaskController}）。
 *
 * <p>⭐ 守两条：</p>
 * <ol>
 *   <li><b>鉴权不能只看 header 前缀</b>——「Bearer + 任意字符串」都能查就是漏了一个洞，
 *       必须走与主链路同一份的 {@code RoutingResolver#requireRuleAccess}；</li>
 *   <li><b>句柄不外泄</b>——规则不匹配一律 404 而不是 403，403 等于确认句柄存在，可被枚举。</li>
 * </ol>
 */
class GatewayTaskControllerTest {

    private RecordingTaskService taskService;
    private RecordingRoutingResolver resolver;
    private GatewayTaskController controller;

    @BeforeEach
    void setUp() {
        taskService = new RecordingTaskService();
        resolver = new RecordingRoutingResolver();
        controller = new GatewayTaskController(taskService, resolver);
    }

    @Test
    @DisplayName("非 Bearer 凭据直接 401，不查库")
    void rejectsNonBearer() {
        assertThrows(GatewayAuthException.class, () -> controller.query("Basic abc", "rule-1", "tc-1"));
        assertNull(resolver.seenSecretKey, "鉴权没过就不该往下走");
        assertEquals(0, taskService.lookups, "不该进库查任务");
    }

    @Test
    @DisplayName("鉴权交给 RoutingResolver，且传的是去掉 Bearer 前缀的裸 key")
    void delegatesAuthorization() {
        taskService.put("tc-1", task(AsyncTaskStatusEnum.PENDING, "rule-1", null, null));
        controller.query("Bearer sk-abc", "rule-1", "tc-1").block();
        assertEquals("sk-abc", resolver.seenSecretKey);
        assertEquals("rule-1", resolver.seenRuleCode);
    }

    @Test
    @DisplayName("key 无效 / 规则不属于该消费者：401，且不进库查任务")
    void unauthorizedKeyIsRejected() {
        resolver.error = new GatewayAuthException("Consumer API key not found");
        assertThrows(GatewayAuthException.class, () -> controller.query("Bearer sk-bad", "rule-1", "tc-1"));
        assertEquals(0, taskService.lookups, "不该进库查任务");
    }

    @Test
    @DisplayName("规则头为空：400（与主链路同口径，不是 404）")
    void blankRuleIsBadRequest() {
        resolver.error = new GatewayInvalidRequestException("Rule header must not be blank");
        assertThrows(GatewayInvalidRequestException.class, () -> controller.query("Bearer sk-abc", " ", "tc-1"));
    }

    @Test
    @DisplayName("任务不存在或规则不匹配一律 404（不泄漏句柄是否存在）")
    void unknownOrForeignTaskIsNotFound() {
        taskService.put("missing", null);
        assertEquals(HttpStatus.NOT_FOUND, controller.query("Bearer sk-abc", "rule-1", "missing").block().getStatusCode());

        taskService.put("tc-1", task(AsyncTaskStatusEnum.PENDING, "rule-1", null, null));
        assertEquals(HttpStatus.NOT_FOUND, controller.query("Bearer sk-abc", "rule-other", "tc-1").block().getStatusCode(),
                "跨规则查询必须 404：403 会确认句柄存在");
    }

    @Test
    @DisplayName("上下文缺失时按不匹配处理：宁可查不到，不可越权可见")
    void missingContextIsNotFound() {
        AsyncTaskDto task = task(AsyncTaskStatusEnum.PENDING, null, null, null);
        taskService.put("tc-1", task);
        assertEquals(HttpStatus.NOT_FOUND, controller.query("Bearer sk-abc", "rule-1", "tc-1").block().getStatusCode());
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
        AsyncTaskDto done = task(AsyncTaskStatusEnum.SUCCEEDED, "rule-1", "{\"result_url\":\"https://x/a.mp4\"}", null);
        taskService.put("tc-1", done);
        ResponseEntity<Object> response = controller.query("Bearer sk-abc", "rule-1", "tc-1").block();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertEquals("completed", body.get("status"));
        // 产物必须是对象：塞原文会让客户端再解一次字符串
        assertEquals(Map.of("result_url", "https://x/a.mp4"), body.get("result"));
        assertNull(body.get("error"));

        AsyncTaskDto failed = task(AsyncTaskStatusEnum.FAILED, "rule-1", null, "UPSTREAM_TASK_FAILED", "prompt rejected");
        taskService.put("tc-2", failed);
        ResponseEntity<Object> failResponse = controller.query("Bearer sk-abc", "rule-1", "tc-2").block();
        Map<?, ?> failBody = (Map<?, ?>) failResponse.getBody();
        assertEquals("failed", failBody.get("status"));
        assertEquals(Map.of("code", "UPSTREAM_TASK_FAILED", "message", "prompt rejected"), failBody.get("error"));
        assertNull(failBody.get("result"));
    }

    @Test
    @DisplayName("探针：规则名带前后空格应视为同一条规则，不得因空格而查不到")
    void ruleIsTrimmed() {
        taskService.put("tc-1", task(AsyncTaskStatusEnum.PENDING, "rule-1", null, null));
        assertEquals(HttpStatus.OK, controller.query("Bearer sk-abc", " rule-1 ", "tc-1").block().getStatusCode());
    }

    private String statusOf(String taskCode) {
        ResponseEntity<Object> response = controller.query("Bearer sk-abc", "rule-1", taskCode).block();
        return (String) ((Map<?, ?>) response.getBody()).get("status");
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
                status.code(), null, null, null,
                null, null, contextJson, resultJson, errorCode, errorMessage,
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

        private final Map<String, AsyncTaskDto> tasks = new java.util.HashMap<>();
        private int lookups;

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

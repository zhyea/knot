package org.chobit.knot.gateway.controller;

import org.chobit.knot.common.util.JsonKit;
import org.chobit.knot.dal.entity.AsyncTaskEntity;
import org.chobit.knot.gateway.async.AsyncTaskContext;
import org.chobit.knot.gateway.async.AsyncTaskStore;
import org.chobit.knot.gateway.exception.GatewayAuthException;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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

    private AsyncTaskStore store;
    private RecordingRoutingResolver resolver;
    private GatewayTaskController controller;

    @BeforeEach
    void setUp() {
        store = mock(AsyncTaskStore.class);
        resolver = new RecordingRoutingResolver();
        controller = new GatewayTaskController(store, resolver);
    }

    @Test
    @DisplayName("非 Bearer 凭据直接 401，不查库")
    void rejectsNonBearer() {
        assertThrows(GatewayAuthException.class, () -> controller.get("Basic abc", "rule-1", "tc-1"));
        assertNull(resolver.seenSecretKey, "鉴权没过就不该往下走");
        verifyNoInteractions(store);
    }

    @Test
    @DisplayName("鉴权交给 RoutingResolver，且传的是去掉 Bearer 前缀的裸 key")
    void delegatesAuthorization() {
        when(store.getByTaskCode("tc-1")).thenReturn(task("PENDING", "rule-1", null, null));
        controller.get("Bearer sk-abc", "rule-1", "tc-1").block();
        assertEquals("sk-abc", resolver.seenSecretKey);
        assertEquals("rule-1", resolver.seenRuleCode);
    }

    @Test
    @DisplayName("key 无效 / 规则不属于该消费者：401，且不进库查任务")
    void unauthorizedKeyIsRejected() {
        resolver.error = new GatewayAuthException("Consumer API key not found");
        assertThrows(GatewayAuthException.class, () -> controller.get("Bearer sk-bad", "rule-1", "tc-1"));
        verifyNoInteractions(store);
    }

    @Test
    @DisplayName("规则头为空：400（与主链路同口径，不是 404）")
    void blankRuleIsBadRequest() {
        resolver.error = new GatewayInvalidRequestException("Rule header must not be blank");
        assertThrows(GatewayInvalidRequestException.class, () -> controller.get("Bearer sk-abc", " ", "tc-1"));
    }

    @Test
    @DisplayName("任务不存在或规则不匹配一律 404（不泄漏句柄是否存在）")
    void unknownOrForeignTaskIsNotFound() {
        when(store.getByTaskCode("missing")).thenReturn(null);
        assertEquals(HttpStatus.NOT_FOUND, controller.get("Bearer sk-abc", "rule-1", "missing").block().getStatusCode());

        when(store.getByTaskCode("tc-1")).thenReturn(task("PENDING", "rule-1", null, null));
        assertEquals(HttpStatus.NOT_FOUND, controller.get("Bearer sk-abc", "rule-other", "tc-1").block().getStatusCode(),
                "跨规则查询必须 404：403 会确认句柄存在");
    }

    @Test
    @DisplayName("上下文缺失时按不匹配处理：宁可查不到，不可越权可见")
    void missingContextIsNotFound() {
        AsyncTaskEntity task = task("PENDING", null, null, null);
        task.setContextJson(null);
        when(store.getByTaskCode("tc-1")).thenReturn(task);
        assertEquals(HttpStatus.NOT_FOUND, controller.get("Bearer sk-abc", "rule-1", "tc-1").block().getStatusCode());
    }

    @Test
    @DisplayName("在途任务：对外状态 queued / processing")
    void inFlightStatusMapping() {
        when(store.getByTaskCode("tc-1")).thenReturn(task("PENDING", "rule-1", null, null));
        assertEquals("queued", statusOf("tc-1"));

        when(store.getByTaskCode("tc-2")).thenReturn(task("POLLING", "rule-1", null, null));
        assertEquals("processing", statusOf("tc-2"));
    }

    @Test
    @DisplayName("终态：completed 带产物对象（不是 JSON 字符串），failed 带错误码")
    void terminalStatusMapping() {
        AsyncTaskEntity done = task("SUCCEEDED", "rule-1", "{\"result_url\":\"https://x/a.mp4\"}", null);
        when(store.getByTaskCode("tc-1")).thenReturn(done);
        ResponseEntity<Object> response = controller.get("Bearer sk-abc", "rule-1", "tc-1").block();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertEquals("completed", body.get("status"));
        // 产物必须是对象：塞原文会让客户端再解一次字符串
        assertEquals(Map.of("result_url", "https://x/a.mp4"), body.get("result"));
        assertNull(body.get("error"));

        AsyncTaskEntity failed = task("FAILED", "rule-1", null, "UPSTREAM_TASK_FAILED");
        failed.setErrorMessage("prompt rejected");
        when(store.getByTaskCode("tc-2")).thenReturn(failed);
        ResponseEntity<Object> failResponse = controller.get("Bearer sk-abc", "rule-1", "tc-2").block();
        Map<?, ?> failBody = (Map<?, ?>) failResponse.getBody();
        assertEquals("failed", failBody.get("status"));
        assertEquals(Map.of("code", "UPSTREAM_TASK_FAILED", "message", "prompt rejected"), failBody.get("error"));
        assertNull(failBody.get("result"));
    }

    @Test
    @DisplayName("探针：规则名带前后空格应视为同一条规则，不得因空格而查不到")
    void ruleIsTrimmed() {
        when(store.getByTaskCode("tc-1")).thenReturn(task("PENDING", "rule-1", null, null));
        assertEquals(HttpStatus.OK, controller.get("Bearer sk-abc", " rule-1 ", "tc-1").block().getStatusCode());
    }

    private String statusOf(String taskCode) {
        ResponseEntity<Object> response = controller.get("Bearer sk-abc", "rule-1", taskCode).block();
        return (String) ((Map<?, ?>) response.getBody()).get("status");
    }

    private static AsyncTaskEntity task(String status, String ruleCode, String resultJson, String errorCode) {
        AsyncTaskEntity task = new AsyncTaskEntity();
        task.setTaskCode("tc");
        task.setStatus(status);
        task.setResultJson(resultJson);
        task.setErrorCode(errorCode);
        if (ruleCode != null) {
            task.setContextJson(JsonKit.toJson(new AsyncTaskContext("tc", ruleCode, null, null, "req",
                    "VIDEO_GENERATIONS", "m", "up", "p", "pool", "https://x")));
        }
        return task;
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

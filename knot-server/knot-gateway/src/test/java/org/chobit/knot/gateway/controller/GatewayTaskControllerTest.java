package org.chobit.knot.gateway.controller;

import org.chobit.knot.gateway.async.AsyncTaskLookupRunner;
import org.chobit.knot.gateway.model.vo.response.AsyncTaskNotFoundResponse;
import org.chobit.knot.gateway.model.vo.response.AsyncTaskQueryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * 控制器只剩<b>一层皮</b>，所以这里也只测那层皮：入参原样透传、200/404 的映射。
 *
 * <p>⭐ <b>业务断言不在这里</b>（鉴权顺序、句柄存在性、状态文案、产物对象化）——
 * 它们随实现搬到了 {@code AsyncTaskLookupRunnerTest}。留着两份等于一份测壳一份测实现，
 * 改实现时两处都要动，而壳那份的断言其实恒真。</p>
 *
 * <p>⚠ 这里守的恰恰是最容易悄悄坏掉的两件：①参数被「顺手加工」过（trim / 取前缀）——
 * 那属于业务，散回 controller 就又厚了；②查不到被翻成 200 空 body——
 * 客户端会把 null 当「任务还在跑」无限轮询。</p>
 */
class GatewayTaskControllerTest {

    private RecordingLookupRunner runner;
    private GatewayTaskController controller;

    @BeforeEach
    void setUp() {
        runner = new RecordingLookupRunner();
        controller = new GatewayTaskController(runner);
    }

    @Test
    @DisplayName("命中：原样返回 200 + Runner 给出的响应体（不二次包装）")
    void returnsOkWithRunnerResult() {
        AsyncTaskQueryResponse body = new AsyncTaskQueryResponse("tc-1", "queued",
                null, null, null, null, null);
        runner.result = Mono.just(body);

        ResponseEntity<Object> response = controller.query("Bearer sk-abc", "rule-1", "tc-1").block();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(body, response.getBody(), "响应体必须原样透出，controller 不再加工字段");
    }

    @Test
    @DisplayName("查不到：404，且 body 是同一份「task not found」形状（不区分不存在与越权）")
    void mapsEmptyToNotFound() {
        runner.result = Mono.empty();

        ResponseEntity<Object> response = controller.query("Bearer sk-abc", "rule-1", "tc-1").block();

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals(new AsyncTaskNotFoundResponse("task not found: tc-1"), response.getBody());
    }

    @Test
    @DisplayName("入参原样透传给 Runner：路径上的句柄不做 trim、Authorization 不去前缀")
    void passesArgumentsThrough() {
        runner.result = Mono.empty();

        controller.query("Bearer sk-abc", "rule-1", " tc-1 ").block();

        assertEquals("Bearer sk-abc", runner.seenAuthorization, "去 Bearer 前缀是业务，不该在壳里做");
        assertEquals("rule-1", runner.seenRule);
        assertEquals(" tc-1 ", runner.seenTaskCode, "trim 属于业务（Runner 里做），壳原样传");
    }

    /**
     * Runner 桩：真实实现要 {@code AsyncTaskService} 与 {@code RoutingResolver}，
     * 而本类只验委托，故子类覆写 {@code lookup}（传 null 安全——父实现不会被调用）。
     */
    private static final class RecordingLookupRunner extends AsyncTaskLookupRunner {

        private Mono<AsyncTaskQueryResponse> result = Mono.empty();
        private String seenAuthorization;
        private String seenRule;
        private String seenTaskCode;

        private RecordingLookupRunner() {
            super(null, null, null);
        }

        @Override
        public Mono<AsyncTaskQueryResponse> lookup(String authorization, String rule, String taskCode) {
            this.seenAuthorization = authorization;
            this.seenRule = rule;
            this.seenTaskCode = taskCode;
            return result;
        }
    }
}

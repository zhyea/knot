package org.chobit.knot.gateway.async;

import org.chobit.knot.common.constants.enums.GatewayErrorTypeEnum;
import org.chobit.knot.common.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.common.constants.enums.ProxyErrorCodeEnum;
import org.chobit.knot.common.util.JsonKit;
import org.chobit.knot.dal.entity.ModelApiBindingEntity;
import org.chobit.knot.svc.converter.GatewayConfigConverter;
import org.chobit.knot.svc.vo.gateway.ModelApiBindingConfigVo;
import org.mapstruct.factory.Mappers;
import org.chobit.knot.gateway.async.AsyncTaskSupport.AsyncTaskCreation;
import org.chobit.knot.gateway.exception.GatewayUpstreamException;
import org.chobit.knot.gateway.model.ProxyResult;
import org.chobit.knot.gateway.runtime.GatewayRequestContext;
import org.chobit.knot.gateway.runtime.GatewayRequestHandler;
import org.chobit.knot.gateway.runtime.GatewayTraceContext;
import org.chobit.knot.svc.dto.routing.RoutingRuleTargetDto;
import org.chobit.knot.svc.dto.async.AsyncTaskDraftDto;
import org.chobit.knot.svc.dto.async.AsyncTaskProfileDto;
import org.chobit.knot.svc.service.AsyncTaskProfileService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
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
 * 契约测试：异步任务的<b>提交侧</b>（{@link AsyncTaskSupport}）。
 *
 * <p>⭐ 判定、产物取值、退避三组用例已随代码搬到 {@code AsyncTaskSpecRulesTest}
 * （2026-10-29）。这里只剩「提交那一刻」才成立的事实：异步与否看绑定、任务行怎么组装、
 * 非异步时如何回退同步。回退错了的表现是「同步调用被当成异步挂住」，客户端一直拿不到结果。</p>
 */
class AsyncTaskSupportTest {

    private final AsyncTaskSupport support = new AsyncTaskSupport(null, null, null, null);

    private static AsyncTaskSpec spec() {
        return new AsyncTaskSpec("GET", "/v1/tasks/{task_id}", "data.id", "data[0].status",
                "['succeed','SUCCEEDED'] contains status", "status == 'failed'", null,
                "data[0].outputs[0].url", null, null,
                7, 5000, 1000, 30000, 360, 30L, null);
    }

    @Test
    @DisplayName("异步与否只看绑定上的档案编码：为空/空白一律同步")
    void asyncIsBindingProperty() {
        // 载体换成不可变 record 后，原来「改同一个 binding 三次」的写法必须改成构造三个：
        // 这反而更贴合用例语义——每次断言的是「给定这样一份配置，判出什么」。
        assertNull(support.profileCodeOf(binding(null)));
        assertNull(support.profileCodeOf(null));
        assertNull(support.profileCodeOf(binding("  ")), "空白档案编码等同未配置");
        assertEquals("KLING_TASK", support.profileCodeOf(binding(" KLING_TASK ")));
    }

    @Test
    @DisplayName("任务行组装：三个 JSON 段齐全、产物时效按天、下一次轮询与寿命按档案")
    void buildsPendingRow() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 27, 12, 0, 0);
        AsyncTaskCreation creation = new AsyncTaskCreation(
                "tc-1", "KLING_TASK", spec(), null,
                Map.of("prompt", "a cat"),
                new AsyncTaskContext("tc-1", "rule-1", null, null, "req-1",
                        "VIDEO_GENERATIONS", "kling-3", "kling-3-turbo", "kling", "pool-1", "https://x/videos",
                        1L, 2L, 3L, 4L, false),
                "up-1", "RULE-1", "V1", now, "https://client.example/callback");
        AsyncTaskDraftDto task = support.build(creation);

        assertEquals("tc-1", task.taskCode());
        assertEquals("KLING_TASK", task.profileCode());
        assertEquals("up-1", task.upstreamTaskId());
        // ⭐ 「新任务必定是 PENDING、查询次数必定是 0」这两条不在这里断言了：
        //    初值的赋予权随数据访问一起搬到了 svc 的 AsyncTaskConverter，
        //    钉 wire 值的断言也随之搬进 AsyncTaskConverterTest（能力搬家，测试必须跟着搬，
        //    否则删了能力测试还绿，等于没测）。草稿里根本没有这两个字段。
        assertEquals(now.plusNanos(5_000_000_000L), task.nextPollAt());
        assertEquals(now.plusMinutes(30), task.expiresAt());
        assertEquals(now.plusDays(7), task.resultExpiresAt());
        assertEquals("RULE-1", task.billingRuleCode());
        assertEquals("V1", task.billingRuleVersionCode());
        assertEquals("https://client.example/callback", task.callbackUrl(),
                "回调地址必须原样落到草稿：它是终态时投递的唯一目标");
        assertNotNull(task.requestJson());
        assertNotNull(task.pollSpecJson());
        assertNotNull(task.contextJson());
        // 探针：快照必须能反序列化回同样的口径，否则「改档案不影响在途任务」是空话
        assertEquals(spec(), JsonKit.fromJson(task.pollSpecJson(), AsyncTaskSpec.class));
    }

    @Test
    @DisplayName("产物时效未配置时不写 result_expires_at")
    void noResultTtl() {
        AsyncTaskSpec noTtl = new AsyncTaskSpec("GET", "/t/{task_id}", "data.id", "data.status",
                "status == 'succeed'", "status == 'failed'", null, "data.url", null, null,
                null, 5000, 1000, 30000, 360, 30L, null);
        AsyncTaskDraftDto task = support.build(new AsyncTaskCreation("tc", "P", noTtl, null,
                Map.of(), new AsyncTaskContext("tc", "r", null, null, "req", null, "m", null, null, null, null,
                        null, null, null, null, false),
                "up", null, null, LocalDateTime.now(), null));
        assertNull(task.resultExpiresAt());
    }

    @Test
    @DisplayName("未判定为异步时上游结果为空不落任务（回退同步；已判定异步的空响应见下一例）")
    void submitFallsBackToSync() {
        assertNull(support.submitIfAsync(null, null, null), "无上游结果时不判定");
        assertNull(support.submitIfAsync(null, null, new ProxyResult("  ", 1L, 1L, null)),
                "空响应体不是一次成功的任务创建");
    }

    @Test
    @DisplayName("档案已声明异步但创建响应为空：抛 502，绝不回退同步")
    void blankCreateBodyForAsyncThrows() {
        AsyncTaskSupport configured = new AsyncTaskSupport(null, null, null, null) {
            @Override
            public ModelApiBindingConfigVo bindingOf(Long modelId, ModelApiProtocolEnum protocol) {
                return binding("KLING_TASK");
            }

            @Override
            public AsyncTaskSpec loadSpec(String profileCode, ModelApiProtocolEnum protocol) {
                return spec();
            }
        };
        GatewayRequestContext context = new GatewayRequestContext("Bearer sk", "rule-a",
                GatewayTraceContext.fromTraceparent(null), ModelApiProtocolEnum.VIDEO_GENERATIONS, null);
        context.putAttribute(GatewayRequestHandler.HIT_TARGET_ATTRIBUTE,
                new RoutingRuleTargetDto("model-a", 7L, "model-a", "upstream-a",
                        "模型", "VIDEO", "pa-1", 1, true, "pool-1"));

        GatewayUpstreamException error = assertThrows(GatewayUpstreamException.class,
                () -> configured.submitIfAsync(context, null, new ProxyResult("  ", 1L, 1L, null)));

        assertTrue(error.getMessage().contains("KLING_TASK"), "报错必须带档案编码：" + error.getMessage());
        assertEquals(ProxyErrorCodeEnum.UPSTREAM_ERROR.code(), error.code());
        assertEquals(GatewayErrorTypeEnum.UPSTREAM_ERROR.code(), error.type());
    }

    @Test
    @DisplayName("档案已声明异步但创建响应取不到任务 ID：抛 502 UPSTREAM_ERROR，绝不回退同步")
    void missingUpstreamTaskIdThrows() {
        // 上游「任务已创建」的响应里没有档案约定的 ID：回退同步会让客户端把它当最终结果，
        // 而本地不入任务行 → 既查不到也不计费。所以此处必须响（2026-11-01 定）。
        GatewayUpstreamException error = assertThrows(GatewayUpstreamException.class,
                () -> support.requireUpstreamTaskId("video-kling", Map.of("task_id", "up-1"), spec()),
                "task_id_path 是 data.id，响应顶层只有 task_id → 取不到，必须抛");
        assertTrue(error.getMessage().contains("video-kling"), "报错必须带档案编码：" + error.getMessage());
        assertTrue(error.getMessage().contains("data.id"), "报错必须带表达式原文：" + error.getMessage());
        assertEquals(ProxyErrorCodeEnum.UPSTREAM_ERROR.code(), error.code());
        assertEquals(GatewayErrorTypeEnum.UPSTREAM_ERROR.code(), error.type());

        assertEquals("up-9", support.requireUpstreamTaskId("video-kling",
                Map.of("data", Map.of("id", "up-9")), spec()), "取得到就正常返回，不误伤");
    }

    @Test
    @DisplayName("任务句柄：32 位小写 hex，不暴露上游 ID")
    void taskCodeShape() {
        String code = AsyncTaskSupport.newTaskCode();
        assertEquals(32, code.length());
        assertTrue(code.matches("[0-9a-f]{32}"), code);
        assertFalse(code.contains("-"));
    }

    @Test
    @DisplayName("⭐ 流式协议上的异步档案一律不生效：手改 SQL 写出的行也挡在这里")
    void streamingProtocolNeverRunsAsync() {
        // 失效方式最难查的一种：上游回 SSE → 请求链路走流式分支 → 异步提交被静默跳过，
        // 于是既没有任务行去补记用量、链路又因为「自认为异步」跳过了频控记账，
        // 这笔调用既不计数也不计费，且日志里没有任何痕迹（2026-11-12 定位）。
        int streamable = 0;
        for (ModelApiProtocolEnum protocol : ModelApiProtocolEnum.values()) {
            if (!protocol.streamSupported()) {
                continue;
            }
            streamable++;
            List<String> queried = new ArrayList<>();
            // ⚠ 档案协议必须与「绑定的协议」一致，否则「协议不匹配」那条会先把它拦住，
            // 用例就变成在测另一条规则（第一版夹具踩过这个坑：探针去掉 guard 仍然绿）。
            AsyncTaskSupport guarded = supportWith(profileOf(protocol.code()), queried);
            assertNull(guarded.loadSpec("KLING_TASK", protocol), protocol.code() + " 是流式协议，必须判为不可用");
            assertTrue(queried.isEmpty(), protocol.code() + " 应在查档案之前短路，实际查了：" + queried);
        }
        assertTrue(streamable > 0, "枚举里必须有流式协议，否则本用例形同虚设");

        // 非流式协议照旧生效：证明这条 guard 只拦流式，不是把异步整体关掉
        List<String> queried = new ArrayList<>();
        AsyncTaskSupport normal = supportWith(profileOf("VIDEO_GENERATIONS"), queried);
        assertNotNull(normal.loadSpec("KLING_TASK", ModelApiProtocolEnum.VIDEO_GENERATIONS));
        assertEquals(List.of("KLING_TASK"), queried);
    }

    /** 夹具走「构造实体 → 真 Converter」：record 12 个位置参数手写易错位 */
    private static final GatewayConfigConverter CONVERTER =
            Mappers.getMapper(GatewayConfigConverter.class);

    /** 档案服务替身：用子类覆写而非 mock（JDK 25 下 Mockito 不动具体类），并记录「查过哪些编码」 */
    private static AsyncTaskSupport supportWith(AsyncTaskProfileDto profile, List<String> queried) {
        AsyncTaskProfileService profiles = new AsyncTaskProfileService(null, null) {
            @Override
            public AsyncTaskProfileDto getByProfileCode(String profileCode) {
                queried.add(profileCode);
                return profile;
            }

            @Override
            public AsyncTaskProfileDto getEnabledByProfileCode(String profileCode) {
                return profile;
            }
        };
        return new AsyncTaskSupport(profiles, null, null, null);
    }

    private static AsyncTaskProfileDto profileOf(String protocol) {
        return new AsyncTaskProfileDto("KLING_TASK", protocol, "GET", "/v1/tasks/{task_id}",
                "data.id", "data.status", "status == 'succeed'", "status == 'failed'", null,
                "data.url", null, null, 7, 5000, 1000, 30000, 360, 30L, null);
    }

    private static ModelApiBindingConfigVo binding(String asyncProfileCode) {
        ModelApiBindingEntity entity = new ModelApiBindingEntity();
        entity.setAsyncProfileCode(asyncProfileCode);
        return CONVERTER.toApiBindingVo(entity);
    }

    @Test
    @DisplayName("context_json 快照：returnUsageDetail 往返不丢（提交时快照消费者是否要看用量明细）")
    void contextJsonSnapshotsReturnUsageDetail() {
        AsyncTaskContext detail = new AsyncTaskContext("tc", "r", null, null, "req", null,
                "m", null, null, null, null, null, null, null, null, true);
        String json = JsonKit.toJson(detail);
        assertTrue(json.contains("returnUsageDetail"), "快照必须带这个键，否则旧快照反序列化恒为 false");
        assertTrue(JsonKit.fromJson(json, AsyncTaskContext.class).returnUsageDetail());
        AsyncTaskContext plain = new AsyncTaskContext("tc", "r", null, null, "req", null,
                "m", null, null, null, null, null, null, null, null, false);
        assertFalse(JsonKit.fromJson(JsonKit.toJson(plain), AsyncTaskContext.class).returnUsageDetail(),
                "显式 false / 旧快照缺字段都按 false");
    }
}

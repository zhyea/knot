package org.chobit.knot.gateway.model.vo.response;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.chobit.knot.adapter.model.usage.NormalizedUsage;
import org.chobit.knot.adapter.usage.KnotExtendPayload;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 查询响应体的<b>对外契约</b>：键名、条件出现的键、嵌套形状。
 *
 * <p>⭐ 为什么这份必须单独存在：body 从 {@code LinkedHashMap} 换成 record 之后，wire 上的键名
 * 由 {@code @JsonProperty} 决定，而<b>删掉注解不会有任何编译或测试信号</b>——
 * Jackson 会按组件名输出 {@code taskCode} / {@code createdAt}，客户端拿到的线上 JSON 就变了，
 * 服务端两个端点却都干净。所以断言<b>序列化结果</b>，不是 Java 字段名。</p>
 *
 * <p>⚠ <b>不能用 {@code JsonKit.toTree} 验这份契约</b>：线上响应体是 WebFlux 的
 * {@code HttpMessageConverter} 用 <b>Spring 的</b> ObjectMapper 序列化的，而 {@code JsonKit}
 * 的默认 mapper 与它<b>两处不同</b>——默认把日期写成时间戳数组、全局 {@code NON_NULL}。
 * 拿 {@code JsonKit} 断言会把「线上输出 ISO 字符串 + 显式 null」验成「数组 + 缺键」，
 * 于是测试绿而契约已经漂了。这里直接用与 Spring Boot 默认等价的 mapper。
 * （{@code knot-gateway} 里也确实没有 {@code JsonKit.init}，即网关的 JsonKit 从未被 Spring 覆盖。）</p>
 *
 * <p>双向探针：①删掉任一 {@code @JsonProperty} → {@code camelCaseFieldsAreAbsent} 必红；
 * ②把字段级 {@code @JsonInclude(NON_NULL)} 提到类上 → {@code absentFieldsStayAbsent} 必红
 * （{@code completed_at} 会跟着消失）。</p>
 */
class AsyncTaskQueryResponseTest {

    /** 与 Spring Boot 默认的 {@code Jackson2ObjectMapperBuilder} 等价：ISO 日期 + 保留 null */
    private static final ObjectMapper WIRE_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    @DisplayName("全字段：恰好七个键，蛇形与原名都对得上")
    void wireFormatIsPinned() {
        AsyncTaskQueryResponse body = new AsyncTaskQueryResponse("tc-1", "completed",
                LocalDateTime.of(2026, 10, 29, 12, 0, 0),
                LocalDateTime.of(2026, 10, 29, 12, 0, 5),
                LocalDateTime.of(2026, 10, 30, 12, 0, 0),
                Map.of("result_url", "https://x/a.mp4"),
                new AsyncTaskFailureResponse("UPSTREAM_TASK_FAILED", "boom"),
                null, null);

        JsonNode json = toWire(body);

        assertNotNull(json);
        assertEquals(Set.of("task_id", "status", "created_at", "completed_at",
                        "result_expires_at", "result", "error"), fieldNames(json),
                "键名是客户端解析依据，多一个少一个都是破坏契约");
        assertEquals("tc-1", json.get("task_id").asText(), "task_id 是 knot 自己的句柄，不是上游 ID");
        assertEquals("completed", json.get("status").asText());
        assertEquals("2026-10-29T12:00:00", json.get("created_at").asText(),
                "时间字段是 ISO 字符串（Spring 默认关 timestamps），不是数组/数字");
        // 产物是对象：塞原文会让客户端再解一次字符串
        assertEquals("https://x/a.mp4", json.get("result").get("result_url").asText());
        // 失败信息是 {code, message} 两个键，不是字符串
        assertEquals(Set.of("code", "message"), fieldNames(json.get("error")));
    }

    /**
     * 测试头下多出来的两个证据键。
     *
     * <p>⭐ 这两键<b>不带测试头时必须缺键</b>（见 {@link #nullFieldsAreOmitted}）：
     * 它们是排障用的内部证据（结算账 + 上游原文），常规客户端不该看见，
     * 也不该被当成对外契约的一部分去解析。</p>
     */
    @Test
    @DisplayName("带测试头：多出 knot_extend / upstream_body 两键，形状分别是账目与上游原文")
    void evidenceKeysAppearOnlyWithTestHead() {
        NormalizedUsage billing = new NormalizedUsage(1500L, "0.004", "USD", "v1", List.of());
        AsyncTaskQueryResponse body = new AsyncTaskQueryResponse("tc-1", "completed",
                LocalDateTime.of(2026, 10, 29, 12, 0, 0),
                LocalDateTime.of(2026, 10, 29, 12, 0, 5),
                LocalDateTime.of(2026, 10, 30, 12, 0, 0),
                Map.of("videos", List.of(Map.of("duration", "5"))),
                null,
                new KnotExtendPayload(null, billing),
                Map.of("data", Map.of("task_status", "succeed")));

        JsonNode json = toWire(body);

        assertTrue(json.has("knot_extend"), "结算账必须能读出来，否则异步计费无从复核");
        assertEquals("0.004", json.get("knot_extend").get("billing").get("totalCost").asText());
        assertTrue(json.has("upstream_body"), "上游终态原文是离线重算的唯一输入");
        assertEquals("succeed",
                json.get("upstream_body").get("data").get("task_status").asText());
    }

    @Test
    @DisplayName("在途任务：result / error 缺键，但 completed_at 保留为 null")
    void nullFieldsAreOmitted() {
        JsonNode json = toWire(new AsyncTaskQueryResponse("tc-1", "processing",
                LocalDateTime.of(2026, 10, 29, 12, 0, 0), null, null, null, null, null, null));

        assertFalse(json.has("result"), "在途不该出现 result：null 会被客户端当结果读");
        assertFalse(json.has("error"), "在途不该出现 error");
        assertTrue(json.has("completed_at"), "completed_at 缺键与值为 null 是两条客户端解析路径，旧契约一直在发 null");
        assertTrue(json.get("completed_at").isNull());
        assertTrue(json.has("result_expires_at"));
        // 证据两键同样 NON_NULL：没给就没有，绝不输出 "knot_extend": null
        assertFalse(json.has("knot_extend"));
        assertFalse(json.has("upstream_body"));
    }

    @Test
    @DisplayName("驼峰名一个都不许漏出去（注解被删的信号）")
    void camelCaseFieldsAreAbsent() {
        JsonNode json = toWire(new AsyncTaskQueryResponse("tc-1", "queued", null, null, null,
                null, null, new KnotExtendPayload(null, null), Map.of("k", "v")));

        for (String camel : Set.of("taskCode", "createdAt", "completedAt", "resultExpiresAt",
                "knotExtend", "upstreamBody")) {
            assertFalse(json.has(camel), "wire 上出现了 Java 字段名 " + camel + "：@JsonProperty 掉了");
        }
    }

    private static JsonNode toWire(AsyncTaskQueryResponse body) {
        try {
            return WIRE_MAPPER.readTree(WIRE_MAPPER.writeValueAsString(body));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("响应体序列化失败", e);
        }
    }

    private static Set<String> fieldNames(JsonNode json) {
        Set<String> names = new LinkedHashSet<>();
        for (Iterator<String> it = json.fieldNames(); it.hasNext(); ) {
            names.add(it.next());
        }
        return names;
    }
}


package org.chobit.knot.gateway.controller;

import org.chobit.knot.gateway.dto.routing.RoutingRuleDto;
import org.chobit.knot.gateway.dto.routing.RoutingRuleTargetDto;
import org.chobit.knot.gateway.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.gateway.rw.ApiResponseWrapperAdvice;
import org.chobit.knot.gateway.rw.RwProperties;
import org.chobit.knot.gateway.service.RoutingRuleService;
import org.chobit.knot.gateway.service.RoutingTestPreparation;
import org.chobit.knot.gateway.service.RoutingTestStreamHandle;
import org.chobit.knot.gateway.util.JsonKit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 流式路由规则测试端点的集成验证。
 *
 * <p>用桩服务而非真实网关：验证重点是「管理端把网关响应翻译成 meta/chunk/complete/error 事件」
 * 以及「统一响应包装不会把 SSE 包成 ApiResponse JSON」。</p>
 *
 * <p>用桩类而非 Mockito mock：JDK 25 下 inline mock maker 无法改写具体类。</p>
 */
class RoutingRuleStreamControllerTest {

    private StubRoutingRuleService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        JsonKit.init(new com.fasterxml.jackson.databind.ObjectMapper());
        service = new StubRoutingRuleService();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new RoutingRuleController(service, null))
                .setControllerAdvice(new ApiResponseWrapperAdvice(new RwProperties()),
                        new org.chobit.knot.gateway.GlobalExceptionHandler(new RwProperties()))
                .build();
    }

    @Test
    void textStreamEmitsMetaThenChunksThenComplete() throws Exception {
        service.setUpstream("你", "好", "世界");

        MvcResult result = mockMvc.perform(post("/api/routing-rules/1/test/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"secretKey":"sk-x","prompt":"hi","protocol":"CHAT_COMPLETIONS",
                                 "requestBody":{"stream":true}}"""))
                .andExpect(status().isOk())
                // SSE 头：禁缓存 + 禁代理缓冲
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE,
                        MediaType.TEXT_EVENT_STREAM_VALUE))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-cache"))
                .andExpect(header().string("X-Accel-Buffering", "no"))
                .andExpect(request().asyncStarted())
                .andReturn();

        String body = awaitStream(result);

        // 关键断言：整体不是 ApiResponse JSON（统一包装必须跳过）
        assertFalse(body.trim().startsWith("{"),
                "SSE 响应被统一包装成了 JSON: " + body);

        // meta 只出现一次，且在所有 chunk 之前
        assertEquals(1, countEvent(body, "meta"), "meta 事件应恰好出现一次: " + body);
        assertTrue(body.indexOf("event: meta") < body.indexOf("event: chunk"),
                "meta 必须先于首个 chunk: " + body);

        // complete 只出现一次，且在最后
        assertEquals(1, countEvent(body, "complete"), "complete 事件应恰好出现一次: " + body);
        assertTrue(body.indexOf("event: complete") > body.lastIndexOf("event: chunk"),
                "complete 必须在所有 chunk 之后: " + body);

        // chunk 边界由「一次 8KiB 读取」决定，不等于上游的逻辑事件数：
        // 这里断言的是拼接后的完整文本不丢字节、顺序正确
        List<String> chunks = extractData(body, "chunk");
        assertFalse(chunks.isEmpty(), "应至少产出一个 chunk: " + body);
        String joined = String.join("", chunks);
        assertTrue(joined.contains("你"), "应保留「你」: " + joined);
        assertTrue(joined.contains("好"), "应保留「好」: " + joined);
        assertTrue(joined.contains("世界"), "应保留「世界」: " + joined);
        assertTrue(joined.indexOf("你") < joined.indexOf("好"), "片段顺序应保持: " + joined);
        assertTrue(joined.indexOf("好") < joined.indexOf("世界"), "片段顺序应保持: " + joined);
        assertTrue(joined.contains("[DONE]") || joined.contains("data:"),
                "应原样透传上游 SSE 行: " + joined);

        // meta 携带命中模型等元数据
        String meta = extractData(body, "meta").get(0);
        assertTrue(meta.contains("\"modelCode\":\"gpt-4o-mini\""), "meta: " + meta);
        assertTrue(meta.contains("\"protocol\":\"CHAT_COMPLETIONS\""), "meta: " + meta);
        assertTrue(meta.contains("curl"), "meta 应含 curl: " + meta);

        // complete 携带真实上游状态
        String complete = extractData(body, "complete").get(0);
        assertTrue(complete.contains("\"status\":\"SUCCESS\""), "complete: " + complete);
        assertTrue(complete.contains("\"httpStatus\":200"), "complete: " + complete);
        assertTrue(complete.contains("\"bytes\""), "complete 应含字节数: " + complete);
    }

    @Test
    void upstream4xxBecomesErrorEventCarryingRealStatus() throws Exception {
        service.setUpstreamStatus(HttpStatus.UNAUTHORIZED);
        service.setUpstream("{\"error\":\"invalid api key\"}");

        MvcResult result = mockMvc.perform(post("/api/routing-rules/1/test/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"secretKey":"sk-x","protocol":"CHAT_COMPLETIONS",
                                 "requestBody":{"stream":true}}"""))
                // SSE 外层恒为 200：真实上游状态由事件内的 httpStatus 承载
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();

        String body = awaitStream(result);
        assertTrue(body.contains("event: error"), "应产出 error 事件: " + body);
        assertFalse(body.contains("event: chunk"), "错误响应不应产出 chunk: " + body);
        String error = extractData(body, "error").get(0);
        assertTrue(error.contains("\"httpStatus\":401"), "error 应携带真实状态: " + error);
        assertTrue(error.contains("invalid api key"), "error 应保留响应体: " + error);
    }

    @Test
    void binaryStreamUsesBase64Encoding() throws Exception {
        service.setUpstreamContentType(MediaType.APPLICATION_OCTET_STREAM);
        service.setUpstreamBytes(new byte[]{0x00, 0x01, 0x02});

        MvcResult result = mockMvc.perform(post("/api/routing-rules/1/test/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"secretKey":"sk-x","protocol":"AUDIO_SPEECH",
                                 "requestBody":{"stream":true}}"""))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();

        String body = awaitStream(result);
        String chunk = extractData(body, "chunk").get(0);
        assertTrue(chunk.contains("\"encoding\":\"base64\""), "二进制应走 base64: " + chunk);
        assertTrue(chunk.contains("\"contentType\":\"application/octet-stream\""),
                "应携带 contentType: " + chunk);
    }

    @Test
    void handleIsClosedAfterStreamEnds() throws Exception {
        service.setUpstream("a");

        MvcResult result = mockMvc.perform(post("/api/routing-rules/1/test/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"secretKey":"sk-x","protocol":"CHAT_COMPLETIONS",
                                 "requestBody":{"stream":true}}"""))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();

        awaitStream(result);
        assertTrue(service.handleClosed, "流结束后句柄必须被关闭（连接归还连接池）");
    }

    @Test
    void validationFailureBeforeStreamKeepsApiResponseError() throws Exception {
        service.setFailOnOpen(true);

        MvcResult result = mockMvc.perform(post("/api/routing-rules/1/test/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"secretKey":"sk-x","protocol":"CHAT_COMPLETIONS",
                                 "requestBody":{"stream":true}}"""))
                // 建立 SSE 之前的校验失败仍走既有 ApiResponse 错误格式。
                // 注意 BusinessException 按项目既有约定返回 HTTP 200 + success:false，
                // 这里断言的是「没有混进 SSE 事件」这一实质。
                // 不对 charset 作断言：standaloneSetup 下默认响应不带 charset，
                // 中文按 UTF-8 字节显式解码后校验内容即可。
                .andExpect(request().asyncNotStarted())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();

        String json = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(json.contains("消费者不存在或未启用"), "错误信息应原样返回: " + json);
        assertTrue(json.contains("\"success\":false"), "应保持 ApiResponse 结构: " + json);
        assertFalse(json.contains("event:"), "校验失败不应产出 SSE 事件: " + json);
    }

    /**
     * {@code StreamingResponseBody} 在 MockMvc 中以异步任务写出，perform() 返回时内容尚未落盘。
     * 这里显式等待异步结果，保证读到的是完整事件流。
     */
    private String awaitStream(MvcResult result) throws Exception {
        result.getAsyncResult();
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private int countEvent(String body, String event) {
        return (int) body.lines().filter(line -> line.equals("event: " + event)).count();
    }

    private List<String> extractData(String body, String event) {
        List<String> result = new java.util.ArrayList<>();
        String[] lines = body.split("\n");
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].equals("event: " + event) && i + 1 < lines.length
                    && lines[i + 1].startsWith("data: ")) {
                result.add(lines[i + 1].substring("data: ".length()));
            }
        }
        return result;
    }

    /**
     * 桩服务：直接返回构造好的流句柄，绕开 DB 与真实网关。
     */
    static class StubRoutingRuleService extends RoutingRuleService {

        private String[] upstreamChunks = {"chunk"};
        private byte[] upstreamBytes;
        private HttpStatusCode upstreamStatus = HttpStatus.OK;
        private MediaType upstreamContentType = MediaType.TEXT_EVENT_STREAM;
        private boolean failOnOpen;
        boolean handleClosed;

        StubRoutingRuleService() {
            super(null, null, null, null, null, null, null, null, null, null, null);
        }

        void setUpstream(String... chunks) {
            this.upstreamChunks = chunks;
        }

        void setUpstreamStatus(HttpStatusCode status) {
            this.upstreamStatus = status;
        }

        void setUpstreamContentType(MediaType contentType) {
            this.upstreamContentType = contentType;
        }

        void setUpstreamBytes(byte[] bytes) {
            this.upstreamBytes = bytes;
        }

        void setFailOnOpen(boolean failOnOpen) {
            this.failOnOpen = failOnOpen;
        }

        @Override
        public RoutingTestStreamHandle openTestStream(Long ruleId, String secretKey, String prompt,
                                                     String protocolCode, String targetType, Long targetId,
                                                     Map<String, Object> requestBody) {
            if (failOnOpen) {
                throw new org.chobit.knot.gateway.error.BusinessException(
                        org.chobit.knot.gateway.error.ErrorCode.VALIDATION_ERROR, "消费者不存在或未启用");
            }
            RoutingTestPreparation prep = new RoutingTestPreparation(
                    new RoutingRuleDto(1L, "rule-a", "规则A", null, List.of(1L), List.of("消费者"),
                            1L, "应用", null, true, List.of(), null, null, null),
                    secretKey,
                    new RoutingRuleTargetDto("MODEL", 10L, "gpt-4o-mini", "gpt-4o-mini",
                            "模型A", "CHAT", "prov-1", 1, true),
                    ModelApiProtocolEnum.CHAT_COMPLETIONS,
                    "gpt-4o-mini",
                    Map.of("stream", true),
                    "http://127.0.0.1:19999",
                    "/openai/v1/chat/completions",
                    "curl -X POST 'http://127.0.0.1:19999/openai/v1/chat/completions'"
            );
            return new RoutingTestStreamHandle(
                    new FakeClientHttpResponse(upstreamStatus, upstreamContentType, bodyBytes()), prep) {
                @Override
                public void close() throws IOException {
                    handleClosed = true;
                    super.close();
                }
            };
        }

        private byte[] bodyBytes() {
            if (upstreamBytes != null) {
                return upstreamBytes;
            }
            StringBuilder sb = new StringBuilder();
            for (String chunk : upstreamChunks) {
                sb.append("data: ").append(chunk).append("\n\n");
            }
            return sb.toString().getBytes(StandardCharsets.UTF_8);
        }
    }

    /**
     * 最小可用的 ClientHttpResponse：只需提供状态、Content-Type 与响应体流。
     */
    static class FakeClientHttpResponse implements ClientHttpResponse {

        private final HttpStatusCode status;
        private final MediaType contentType;
        private final byte[] body;
        private boolean closed;

        FakeClientHttpResponse(HttpStatusCode status, MediaType contentType, byte[] body) {
            this.status = status;
            this.contentType = contentType;
            this.body = body;
        }

        @Override
        public HttpStatusCode getStatusCode() {
            return status;
        }

        @Override
        public String getStatusText() {
            return status.toString();
        }

        @Override
        public void close() {
            closed = true;
        }

        @Override
        public InputStream getBody() {
            return new ByteArrayInputStream(body);
        }

        @Override
        public HttpHeaders getHeaders() {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(contentType);
            return headers;
        }
    }
}

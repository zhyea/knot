package org.chobit.knot.gateway.controller;

import jakarta.validation.Valid;
import org.chobit.knot.gateway.annotation.OperationLog;
import org.chobit.knot.gateway.converter.RoutingRuleConverter;
import org.chobit.knot.gateway.dto.routing.RoutingRuleDto;
import org.chobit.knot.gateway.model.PageQuery;
import org.chobit.knot.gateway.model.PageRequest;
import org.chobit.knot.gateway.model.PageResult;
import org.chobit.knot.gateway.service.RoutingRuleService;
import org.chobit.knot.gateway.service.RoutingTestPreparation;
import org.chobit.knot.gateway.service.RoutingTestStreamHandle;
import org.chobit.knot.gateway.util.JsonKit;
import org.chobit.knot.gateway.vo.common.CodeAvailability;
import org.chobit.knot.gateway.vo.common.EnabledStatusRequest;
import org.chobit.knot.gateway.vo.routing.ProtocolDebugCapabilityItem;
import org.chobit.knot.gateway.vo.routing.RoutingRule;
import org.chobit.knot.gateway.vo.routing.RoutingTestRequest;
import org.chobit.knot.gateway.vo.routing.RoutingTestResult;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/routing-rules")
public class RoutingRuleController {

    /** 流式读取缓冲区：够小以保证首片段低延迟，够大以避免事件数过多 */
    private static final int STREAM_BUFFER_SIZE = 8 * 1024;

    private final RoutingRuleService routingRuleService;
    private final RoutingRuleConverter routingRuleConverter;

    /**
     * Constructs a new instance.
     */
    public RoutingRuleController(RoutingRuleService routingRuleService, RoutingRuleConverter routingRuleConverter) {
        this.routingRuleService = routingRuleService;
        this.routingRuleConverter = routingRuleConverter;
    }

    /**
     * Lists debug capabilities per protocol: gateway path, hint, request template and prompt field.
     */
    @GetMapping("/debug-capabilities")
    public List<ProtocolDebugCapabilityItem> debugCapabilities() {
        return routingRuleService.listDebugCapabilities();
    }

    /**
     * Lists routing rules with pagination.
     */
    @PostMapping("/list")
    public PageResult<RoutingRule> list(@RequestBody(required = false) PageQuery query) {
        PageResult<RoutingRuleDto> page = routingRuleService.list(
                query == null ? PageRequest.of(1, 20) : query.toPageRequest(),
                query == null ? null : query.keyword()
        );
        return page.mapList(routingRuleConverter::toVOList);
    }

    /**
     * Checks whether the rule code is available.
     */
    @GetMapping("/check-code")
    public CodeAvailability checkCode(@RequestParam String code,
                                      @RequestParam(required = false) Long excludeId) {
        return new CodeAvailability(routingRuleService.isRuleCodeAvailable(code, excludeId));
    }

    /**
     * Creates a routing rule.
     */
    @OperationLog(module = "routing", operation = "CREATE", entityType = "RoutingRule",
            entityIdAfter = "#result.id()",
            entityNameAfter = "#result.name()",
            description = "'创建路由规则'",
            newValueSpel = "@routingRuleService.routingRuleAuditSnapshot(#result.id())")
    @PostMapping
    public RoutingRule create(@RequestBody @Valid RoutingRule request) {
        RoutingRuleDto created = routingRuleService.create(routingRuleConverter.toDto(request));
        return routingRuleConverter.toVO(created);
    }

    /**
     * Updates a routing rule.
     */
    @OperationLog(module = "routing", operation = "UPDATE", entityType = "RoutingRule",
            entityId = "#p0",
            entityNameAfter = "#result.name()",
            description = "'更新路由规则'",
            oldValueSpel = "@routingRuleService.routingRuleAuditSnapshot(#p0)",
            newValueSpel = "@routingRuleService.routingRuleAuditSnapshot(#p0)")
    @PutMapping("/{id}")
    public RoutingRule update(@PathVariable Long id, @RequestBody @Valid RoutingRule request) {
        RoutingRuleDto updated = routingRuleService.update(id, routingRuleConverter.toDto(request));
        return routingRuleConverter.toVO(updated);
    }

    /**
     * Updates the routing rule enabled status.
     */
    @OperationLog(module = "routing", operation = "UPDATE", entityType = "RoutingRule",
            entityId = "#p0",
            entityNameAfter = "#result.name()",
            description = "'更新路由规则状态'",
            oldValueSpel = "@routingRuleService.routingRuleAuditSnapshot(#p0)",
            newValueSpel = "@routingRuleService.routingRuleAuditSnapshot(#p0)")
    @PutMapping("/{id}/status")
    public RoutingRule updateStatus(@PathVariable Long id, @RequestBody @Valid EnabledStatusRequest request) {
        RoutingRuleDto updated = routingRuleService.updateStatus(id, Boolean.TRUE.equals(request.enabled()));
        return routingRuleConverter.toVO(updated);
    }

    /**
     * Executes routing rule test.
     */
    @PostMapping("/{id}/test")
    public RoutingTestResult test(@PathVariable Long id, @RequestBody @Valid RoutingTestRequest request) {
        return routingRuleService.testInvoke(id,
                request.secretKey(),
                request.prompt(),
                request.protocol(),
                request.targetType(),
                request.targetId(),
                request.requestBody());
    }

    /**
     * 流式路由规则测试：以专用 SSE 事件流承载测试元数据、响应片段与结束状态。
     *
     * <p>返回 {@link ResponseEntity} 有两个目的：一是设置 SSE 响应头（禁缓存、禁代理缓冲），
     * 二是命中统一响应包装的 {@code ResponseEntity} 跳过分支，避免流对象被包成 JSON。
     * 校验失败发生在建立 SSE 之前，仍由全局异常处理器返回既有 4xx {@code ApiResponse}。</p>
     */
    @PostMapping(value = "/{id}/test/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> testStream(@PathVariable Long id,
                                                            @RequestBody @Valid RoutingTestRequest request) {
        RoutingTestStreamHandle handle = routingRuleService.openTestStream(id,
                request.secretKey(),
                request.prompt(),
                request.protocol(),
                request.targetType(),
                request.targetId(),
                request.requestBody());
        StreamingResponseBody body = out -> writeStream(out, handle);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                .header("X-Accel-Buffering", "no")
                .body(body);
    }

    /**
     * 逐块读取网关响应并翻译为管理端 SSE 事件。
     * 句柄在 finally 中关闭，覆盖正常结束、上游异常与浏览器取消三条路径。
     */
    private void writeStream(OutputStream out, RoutingTestStreamHandle handle) {
        long startedAt = System.currentTimeMillis();
        try (handle) {
            RoutingTestPreparation prep = handle.preparation();
            // meta 必须先于首个 chunk：此时网关响应头已到手，命中模型 / 协议 / curl 已确定
            // 注意 targetModelId 可能为 null，Map.of 不接受 null value，故用 LinkedHashMap
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("matchedRuleId", prep.rule().id());
            meta.put("targetProviderAccountCode", nullSafe(prep.target().providerAccountCode()));
            meta.put("targetModelId", prep.target().targetId());
            meta.put("modelCode", nullSafe(prep.model()));
            meta.put("protocol", prep.protocol().code());
            meta.put("curl", nullSafe(prep.curl()));
            writeEvent(out, "meta", meta);

            HttpStatusCode upstreamStatus = handle.statusCode();
            if (upstreamStatus.isError()) {
                // 网关在首字节前就返回了 4xx/5xx：SSE 外层已是 200，真实状态由事件内的 httpStatus 承载
                String errorBody = readAllText(handle.bodyStream());
                writeEvent(out, "error", Map.of(
                        "status", "FAILED",
                        "httpStatus", upstreamStatus.value(),
                        "errorMessage", "网关返回 HTTP " + upstreamStatus.value(),
                        "responseBody", errorBody
                ));
                return;
            }

            MediaType contentType = handle.contentType();
            boolean textual = isTextual(contentType);
            byte[] buffer = new byte[STREAM_BUFFER_SIZE];
            long bytes = 0;
            try (InputStream in = handle.bodyStream()) {
                int read;
                while ((read = in.read(buffer)) != -1) {
                    bytes += read;
                    writeEvent(out, "chunk", textual
                            ? Map.of("text", new String(buffer, 0, read, StandardCharsets.UTF_8))
                            : Map.of("encoding", "base64",
                                    "content", Base64.getEncoder().encodeToString(
                                            Arrays.copyOfRange(buffer, 0, read)),
                                    "contentType", contentType == null ? "" : contentType.toString()));
                }
            }
            writeEvent(out, "complete", Map.of(
                    "status", "SUCCESS",
                    "httpStatus", upstreamStatus.value(),
                    "bytes", bytes,
                    "durationMs", System.currentTimeMillis() - startedAt
            ));
        } catch (IOException | RuntimeException ex) {
            // 浏览器取消时写不出去属正常现象：此时只能保证资源已被 try-with-resources 释放
            writeErrorQuietly(out, ex);
        }
    }

    /**
     * 写单个 SSE 事件：event 行 + data 行 + 空行分隔，随后立即 flush 保证增量可见。
     */
    private void writeEvent(OutputStream out, String event, Map<String, Object> data) throws IOException {
        out.write(("event: " + event + "\n").getBytes(StandardCharsets.UTF_8));
        out.write(("data: " + JsonKit.toJsonOrThrow(data) + "\n").getBytes(StandardCharsets.UTF_8));
        out.write('\n');
        out.flush();
    }

    private void writeErrorQuietly(OutputStream out, Exception ex) {
        try {
            writeEvent(out, "error", Map.of(
                    "status", "FAILED",
                    "httpStatus", 0,
                    "errorMessage", String.valueOf(ex.getMessage()),
                    "responseBody", ""
            ));
        } catch (IOException | RuntimeException ignored) {
            // 连接已断开（用户主动停止 / 上游异常），无法再向客户端补发错误事件
        }
    }

    private String readAllText(InputStream in) throws IOException {
        try (in) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * 文本类响应按原始顺序透传；二进制（如音频）走 base64 通道。
     */
    private boolean isTextual(MediaType contentType) {
        if (contentType == null) {
            return true;
        }
        String type = contentType.getType();
        if ("text".equalsIgnoreCase(type)) {
            return true;
        }
        if (!"application".equalsIgnoreCase(type)) {
            return false;
        }
        String subtype = contentType.getSubtype();
        return subtype == null
                || subtype.endsWith("json")
                || subtype.endsWith("+json")
                || "x-ndjson".equalsIgnoreCase(subtype)
                || "xml".equalsIgnoreCase(subtype);
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }
}

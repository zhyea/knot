package org.chobit.knot.admin.controller;

import jakarta.validation.Valid;
import org.chobit.knot.common.annotation.OperationLog;
import org.chobit.knot.admin.config.GatewayRuntimeProperties;
import org.chobit.knot.svc.converter.RoutingRuleConverter;
import org.chobit.knot.svc.dto.routing.RoutingRuleDto;
import org.chobit.knot.common.model.PageQuery;
import org.chobit.knot.common.model.PageResult;
import org.chobit.knot.svc.service.RoutingRuleDebugService;
import org.chobit.knot.svc.service.RoutingRuleService;
import org.chobit.knot.svc.service.RoutingTestBillingVerdictService;
import org.chobit.knot.admin.service.support.RoutingTestStreamWriter;
import org.chobit.knot.svc.dto.routing.RoutingTestStreamHandle;
import org.chobit.knot.svc.vo.common.CodeAvailability;
import org.chobit.knot.svc.vo.common.EnabledStatusRequest;
import org.chobit.knot.svc.vo.routing.ProtocolDebugCapabilityItem;
import org.chobit.knot.svc.vo.routing.RoutingRule;
import org.chobit.knot.svc.vo.routing.RoutingRuleDebugContext;
import org.chobit.knot.svc.vo.routing.RoutingRuleItem;
import org.chobit.knot.svc.vo.routing.RoutingTestRequest;
import org.chobit.knot.svc.vo.routing.RoutingTestResult;
import org.chobit.knot.svc.vo.routing.RoutingBillingVerdictRequest;
import org.chobit.knot.svc.vo.routing.RoutingBillingVerdictResult;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.List;

@RestController
@RequestMapping("/api/routing-rules")
public class RoutingRuleController {

    /**
     * 流式读取缓冲区大小（字节）。
     */
    private static final int STREAM_BUFFER_SIZE = 8 * 1024;

    private final RoutingRuleService routingRuleService;
    private final RoutingRuleDebugService routingRuleDebugService;
    private final RoutingRuleConverter routingRuleConverter;
    private final GatewayRuntimeProperties gatewayRuntimeProperties;
    private final RoutingTestBillingVerdictService routingTestBillingVerdictService;

    public RoutingRuleController(RoutingRuleService routingRuleService,
                                 RoutingRuleDebugService routingRuleDebugService,
                                 RoutingRuleConverter routingRuleConverter,
                                 GatewayRuntimeProperties gatewayRuntimeProperties,
                                 RoutingTestBillingVerdictService routingTestBillingVerdictService) {
        this.routingRuleService = routingRuleService;
        this.routingRuleDebugService = routingRuleDebugService;
        this.routingRuleConverter = routingRuleConverter;
        this.gatewayRuntimeProperties = gatewayRuntimeProperties;
        this.routingTestBillingVerdictService = routingTestBillingVerdictService;
    }

    /**
     * Lists debug capabilities per protocol: gateway path, hint, request template and prompt field.
     */
    @GetMapping("/debug-capabilities")
    public List<ProtocolDebugCapabilityItem> debugCapabilities() {
        return routingRuleDebugService.listDebugCapabilities();
    }

    /**
     * 调试面板的上下文：逐目标可调试协议 + 该作用域下已按模型族（优先）与协议筛选过的预设请求。
     * 数据源与调试准入校验同源 —— 目标协议由 {@code supportedProtocolsForTarget} 计算，
     * 前端不再自行解析模型 / 模型池的协议绑定，也不自行筛预设。
     */
    @GetMapping("/{id}/debug-context")
    public RoutingRuleDebugContext debugContext(@PathVariable Long id) {
        return routingRuleDebugService.listDebugContext(id);
    }

    /**
     * Lists routing rules with pagination. 列表只含列表页要渲染的字段，
     * 限流 / 额度、重试、摘除与目标选择策略由详情接口按需下发。
     */
    @PostMapping("/list")
    public PageResult<RoutingRuleItem> list(@RequestBody(required = false) PageQuery query) {
        return routingRuleService.list(
            PageQuery.toPageRequest(query),
            query == null ? null : query.keyword()
        );
    }

    /**
     * 规则详情：编辑抽屉打开时按 id 取完整配置（限流 / 额度、重试、摘除、目标选择策略、绑定处数）。
     */
    @GetMapping("/{id}")
    public RoutingRule get(@PathVariable Long id) {
        return routingRuleConverter.toVO(routingRuleService.getById(id));
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
        return routingRuleDebugService.testInvoke(gatewayRuntimeProperties.normalizedBaseUrl(),
            id,
            request.secretKey(),
            request.prompt(),
            request.protocol(),
            request.targetType(),
            request.targetId(),
            request.requestBody());
    }

    /**
     * 流式路由规则测试：以专用 SSE 事件流承载测试元数据、响应片段与结束状态。
     * 返回 {@link ResponseEntity} 以设置 SSE 响应头（禁缓存、禁代理缓冲），并命中统一响应包装的跳过分支。
     * 校验失败发生在建立 SSE 之前，仍由全局异常处理器返回 4xx {@code ApiResponse}。
     */
    @PostMapping(value = "/{id}/test/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> testStream(@PathVariable Long id,
                                                            @RequestBody @Valid RoutingTestRequest request) {
        RoutingTestStreamHandle handle = routingRuleDebugService.openTestStream(gatewayRuntimeProperties.normalizedBaseUrl(),
            id,
            request.secretKey(),
            request.prompt(),
            request.protocol(),
            request.targetType(),
            request.targetId(),
            request.requestBody());
        StreamingResponseBody body = out -> RoutingTestStreamWriter.write(out, handle);
        return ResponseEntity.ok()
            .contentType(MediaType.TEXT_EVENT_STREAM)
            .header(HttpHeaders.CACHE_CONTROL, "no-cache")
            .header("X-Accel-Buffering", "no")
            .body(body);
    }

    /**
     * 路由规则测试·计费裁判：抽屉在测试响应到手后自动回传当次调用产出，做双盲校验。
     * 与 {@code /test} 同权限（routing:rule:test）；被告账缺失时由前端短路、不发起本请求，编排层不抛异常。
     */
    @PostMapping("/{id}/test/billing-verdict")
    public RoutingBillingVerdictResult billingVerdict(@PathVariable Long id,
                                                      @RequestBody RoutingBillingVerdictRequest request) {
        return routingTestBillingVerdictService.verdict(request,
            gatewayRuntimeProperties.normalizedBaseUrl(), request.sessionId());
    }

}

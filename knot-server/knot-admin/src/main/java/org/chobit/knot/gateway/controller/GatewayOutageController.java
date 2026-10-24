package org.chobit.knot.gateway.controller;

import jakarta.validation.Valid;
import org.chobit.knot.gateway.annotation.OperationLog;
import org.chobit.knot.gateway.config.GatewayRuntimeProperties;
import org.chobit.knot.gateway.dto.outage.GatewayOutageAck;
import org.chobit.knot.gateway.dto.outage.GatewayOutageEjectRequest;
import org.chobit.knot.gateway.dto.outage.GatewayOutageItem;
import org.chobit.knot.gateway.dto.outage.GatewayOutageReleaseRequest;
import org.chobit.knot.gateway.service.support.GatewayOutageRunner;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 网关摘除管理面（admin 侧）：把管理端操作透传到网关 {@code /_admin/outages*}。
 *
 * <p>三个端点：</p>
 * <ul>
 *   <li>{@code GET /api/gateway/outages?ruleCode=&modelCode=}：当前非健康条目列表；</li>
 *   <li>{@code POST /api/gateway/outages/release}：手动恢复；</li>
 *   <li>{@code POST /api/gateway/outages/eject}：手动摘除（可带冷却与人工原因）。</li>
 * </ul>
 *
 * <p>实际 HTTP 调用在 {@link GatewayOutageRunner} 完成，本控制器只负责鉴权（接口级授权）、
 * 参数校验与操作日志。网关基地址 / 令牌由 {@link GatewayRuntimeProperties} 透传。</p>
 */
@RestController
@RequestMapping("/api/gateway/outages")
public class GatewayOutageController {

    private final GatewayOutageRunner gatewayOutageRunner;
    private final GatewayRuntimeProperties gatewayRuntimeProperties;

    /**
     * Constructs a new instance.
     */
    public GatewayOutageController(GatewayOutageRunner gatewayOutageRunner,
                                   GatewayRuntimeProperties gatewayRuntimeProperties) {
        this.gatewayOutageRunner = gatewayOutageRunner;
        this.gatewayRuntimeProperties = gatewayRuntimeProperties;
    }

    /**
     * 当前非健康条目列表（摘除中 + 探活中），可按规则 / 模型过滤。
     */
    @GetMapping
    public List<GatewayOutageItem> list(@RequestParam(required = false) String ruleCode,
                                        @RequestParam(required = false) String modelCode) {
        return gatewayOutageRunner.listOutages(gatewayRuntimeProperties.normalizedBaseUrl(),
                gatewayRuntimeProperties.getAdminToken(), ruleCode, modelCode);
    }

    /**
     * 手动恢复：清除「规则 × 模型」的摘除状态，目标立即重新参与候选链。
     */
    @OperationLog(module = "routing", operation = "UPDATE", entityType = "RoutingTarget",
            entityName = "'路由目标摘除恢复 ' + #p0.ruleCode + '/' + #p0.modelCode",
            description = "'手动恢复路由目标摘除'",
            recordOldValue = false,
            newValueSpel = "#p0")
    @PostMapping("/release")
    public GatewayOutageAck release(@RequestBody @Valid GatewayOutageReleaseRequest request) {
        return gatewayOutageRunner.release(gatewayRuntimeProperties.normalizedBaseUrl(),
                gatewayRuntimeProperties.getAdminToken(), request);
    }

    /**
     * 手动摘除：直接进入冷却，不依赖窗口计数。
     */
    @OperationLog(module = "routing", operation = "UPDATE", entityType = "RoutingTarget",
            entityName = "'路由目标手动摘除 ' + #p0.ruleCode + '/' + #p0.modelCode",
            description = "'手动摘除路由目标'",
            recordOldValue = false,
            newValueSpel = "#p0")
    @PostMapping("/eject")
    public GatewayOutageAck eject(@RequestBody @Valid GatewayOutageEjectRequest request) {
        return gatewayOutageRunner.eject(gatewayRuntimeProperties.normalizedBaseUrl(),
                gatewayRuntimeProperties.getAdminToken(), request);
    }
}

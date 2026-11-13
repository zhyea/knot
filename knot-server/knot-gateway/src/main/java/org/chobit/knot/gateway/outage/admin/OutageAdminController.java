package org.chobit.knot.gateway.outage.admin;

import lombok.extern.slf4j.Slf4j;
import org.chobit.knot.gateway.admin.GatewayAdminAccessSupport;
import org.chobit.knot.gateway.outage.OutageKeys;
import org.chobit.knot.gateway.outage.TargetOutageStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import org.springframework.http.server.reactive.ServerHttpRequest;

import java.util.List;

/**
 * 摘除管理面 API（网关侧，路径前缀 {@code /_admin}）。
 * 端点：{@code GET /_admin/outages?ruleCode=&modelCode=} 列当前非健康条目（摘除中 + 探活中）；{@code POST /_admin/outages/release} 手动恢复；{@code POST /_admin/outages/eject} 手动摘除（可带冷却时长与人工原因）。
 * 准入：配置令牌时校验请求头 {@code X-Knot-Admin-Token}，未配置时仅放行 loopback；{@code knot.outage.admin-api.enabled=false} 时整体不注册（404）。
 * 摘除状态存进程内 Caffeine，列表只反映被轮询到的那个实例，多节点下各节点各摘各的。
 */
@Slf4j
@RestController
@RequestMapping("/_admin/outages")
@ConditionalOnProperty(name = "knot.outage.admin-api.enabled", havingValue = "true", matchIfMissing = true)
public class OutageAdminController {

    private final TargetOutageStore store;
    private final OutageKeys keys;
    private final OutageAdminProperties properties;

    /**
     * @param store      摘除状态存储（当前生效档位）
     * @param keys       键工厂
     * @param properties 管理面准入配置
     */
    public OutageAdminController(TargetOutageStore store,
                                 OutageKeys keys,
                                 OutageAdminProperties properties) {
        this.store = store;
        this.keys = keys;
        this.properties = properties;
    }

    /**
     * 当前非健康条目列表（摘除中 + 探活中），可按规则 / 模型过滤。
     */
    @GetMapping
    public List<OutageAdminItem> list(@RequestParam(required = false) String ruleCode,
                                      @RequestParam(required = false) String modelCode,
                                      ServerHttpRequest request) {
        authorize(request);
        long now = System.currentTimeMillis();
        return store.list(now).stream()
                .filter(entry -> isBlank(ruleCode) || ruleCode.equals(entry.ruleCode()))
                .filter(entry -> isBlank(modelCode) || modelCode.equals(entry.modelCode()))
                .map(OutageAdminItem::of)
                .toList();
    }

    /**
     * 手动恢复：清除「规则 × 模型」的摘除状态，目标立即重新参与候选链。
     */
    @PostMapping("/release")
    public OutageAdminAck release(@RequestBody OutageAdminReleaseRequest body,
                                  ServerHttpRequest request) {
        authorize(request);
        String ruleCode = requireText(body.ruleCode(), "ruleCode");
        String modelCode = requireText(body.modelCode(), "modelCode");
        store.release(keys.of(ruleCode, modelCode));
        log.warn("Outage target manually released: rule={}, model={}", ruleCode, modelCode);
        return OutageAdminAck.released(ruleCode, modelCode);
    }

    /**
     * 手动摘除：直接进入冷却，不依赖窗口计数。
     */
    @PostMapping("/eject")
    public OutageAdminAck eject(@RequestBody OutageAdminEjectRequest body,
                                ServerHttpRequest request) {
        authorize(request);
        String ruleCode = requireText(body.ruleCode(), "ruleCode");
        String modelCode = requireText(body.modelCode(), "modelCode");
        long now = System.currentTimeMillis();
        long cooldownMs = body.cooldownMillis();
        store.forceEject(keys.of(ruleCode, modelCode), cooldownMs, now);
        log.warn("Outage target manually ejected: rule={}, model={}, cooldownSec={}, reason={}",
                ruleCode, modelCode, cooldownMs / 1000L, body.reason());
        return OutageAdminAck.ejected(ruleCode, modelCode, now + cooldownMs);
    }

    /**
     * 准入：配了令牌就比对请求头；没配只放行 loopback。
     * 判定集中在 {@link GatewayAdminAccessSupport}，异步任务管理面共用同一份实现。
     */
    private void authorize(ServerHttpRequest request) {
        GatewayAdminAccessSupport.requireAccess(request, properties.getToken());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String requireText(String value, String field) {
        if (isBlank(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " must not be blank");
        }
        return value.trim();
    }
}

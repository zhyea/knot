package org.chobit.knot.gateway.outage.admin;

import lombok.extern.slf4j.Slf4j;
import org.chobit.knot.common.constants.GatewayHeaders;
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

import java.net.InetSocketAddress;
import java.util.List;

/**
 * 摘除管理面 API（网关侧，路径前缀 {@code /_admin}，避开 OpenAI 兼容路径）。
 *
 * <p>三个端点：</p>
 * <ul>
 *   <li>{@code GET /_admin/outages?ruleCode=&modelCode=}：当前非健康条目（摘除中 + 探活中）；</li>
 *   <li>{@code POST /_admin/outages/release}：手动恢复；</li>
 *   <li>{@code POST /_admin/outages/eject}：手动摘除（可带冷却时长与人工原因）。</li>
 * </ul>
 *
 * <p><b>准入</b>：网关没有全局鉴权拦截器（鉴权在请求 handler 内按 key/rule 做），故本控制器
 * 自带保护——配置了令牌时校验请求头 {@code X-Knot-Admin-Token}；未配置时仅放行 loopback。
 * {@code knot.outage.admin-api.enabled=false} 时整体不注册（404）。</p>
 *
 * <p><b>多节点口径</b>：摘除状态一律存进程内 Caffeine，因此本控制器的列表
 * <b>只反映被轮询到的那个实例</b>——各节点各摘各的。这是 2026-10-15 的有意取舍：
 * 摘除是本节点自己的异常观察，放 Redis 会让 Redis 故障同时打掉网关的自我保护能力。</p>
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
     * Constructs a new instance.
     *
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
     */
    private void authorize(ServerHttpRequest request) {
        if (properties.hasToken()) {
            String actual = request.getHeaders().getFirst(GatewayHeaders.X_KNOT_ADMIN_TOKEN);
            if (!properties.getToken().equals(actual)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "invalid or missing X-Knot-Admin-Token");
            }
            return;
        }
        if (!isLoopback(request.getRemoteAddress())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "outage admin api requires X-Knot-Admin-Token or loopback access");
        }
    }

    /**
     * loopback 判定：Netty 下拿不到字符串形式的对端地址，改为判定 {@link InetSocketAddress} 的地址本身
     * （2026-10-20 WebFlux 批2；原实现比对 {@code 127.0.0.1}/{@code ::1}/localhost 三种字符串）。
     * 无对端地址（如本地 in-process 调用）按非 loopback 处理——宁可拒绝也不放宽准入。
     */
    private static boolean isLoopback(InetSocketAddress remoteAddress) {
        if (remoteAddress == null) {
            return false;
        }
        return remoteAddress.getAddress() != null && remoteAddress.getAddress().isLoopbackAddress();
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

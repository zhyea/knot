package org.chobit.knot.svc.service.support;

import org.chobit.knot.svc.dto.outage.GatewayOutageAck;
import org.chobit.knot.svc.dto.outage.GatewayOutageEjectRequest;
import org.chobit.knot.svc.dto.outage.GatewayOutageItem;
import org.chobit.knot.svc.dto.outage.GatewayOutageReleaseRequest;
import org.chobit.knot.gateway.error.BusinessException;
import org.chobit.knot.gateway.error.ErrorCode;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Optional;

/**
 * 网关摘除管理面的转发执行器：把 admin 后端的查询 / 恢复 / 手动摘除请求透传到网关
 * {@code /_admin/outages*}，网关自带令牌 / loopback 准入（见网关侧 {@code OutageAdminController}）。
 *
 * <p>2026-10-24 改进清单 3.3：本类原为 {@code service} 包下的 {@code GatewayOutageService}——
 * 它没有业务编排，只是「建 RestClient + 发三个请求 + 翻译异常」，挂在 {@code @Service} 上
 * 既违反「Service 层只做编排」也把自建外部客户端固化进了业务类。现按
 * {@code RestClientDecisionsTransport} 的同款形态收进 {@code service.support}，
 * 以 {@code Runner} 后缀标识「执行器」角色。</p>
 *
 * <p>网关基地址由 admin 侧 {@code GatewayRuntimeProperties.normalizedBaseUrl()} 透传；
 * 可选 {@code adminToken} 作为 {@code X-Knot-Admin-Token} 头下发，与网关侧
 * {@code knot.outage.admin-api.token} 同源（同一部署共享 {@code KNOT_GATEWAY_ADMIN_TOKEN}）。</p>
 */
@Component
public class GatewayOutageRunner {

    /**
     * 网关管理面准入令牌头（与网关侧 {@code GatewayHeaders.X_KNOT_ADMIN_TOKEN} 一致；此处硬编码避免反向依赖网关模块）。
     */
    private static final String ADMIN_TOKEN_HEADER = "X-Knot-Admin-Token";
    /**
     * 转发连接超时（网关与本管理端同机/同网段，短超时即可）
     */
    private static final int CONNECT_TIMEOUT_MS = 5_000;
    /**
     * 转发读取超时（摘除列表/动作响应都很小，10s 足够）
     */
    private static final int READ_TIMEOUT_MS = 10_000;

    private final RestClient restClient;

    public GatewayOutageRunner() {
        this.restClient = RestClientSupport.withTimeouts(CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS);
    }

    /**
     * 查询当前非健康条目（摘除中 + 探活中），可按规则 / 模型过滤。
     *
     * @param gatewayBaseUrl 网关基地址（已归一化）
     * @param adminToken     管理面令牌（空则不下发，依赖网关 loopback 放行）
     * @param ruleCode       规则过滤（空则不过滤）
     * @param modelCode      模型过滤（空则不过滤）
     */
    public List<GatewayOutageItem> listOutages(String gatewayBaseUrl,
                                               String adminToken,
                                               String ruleCode,
                                               String modelCode) {
        String uri = UriComponentsBuilder.fromUriString(gatewayBaseUrl + "/_admin/outages")
            .queryParamIfPresent("ruleCode", Optional.ofNullable(blankToNull(ruleCode)))
            .queryParamIfPresent("modelCode", Optional.ofNullable(blankToNull(modelCode)))
            .build()
            .toUriString();
        RestClient.RequestHeadersSpec<?> base = restClient.get().uri(uri);
        RestClient.RequestHeadersSpec<?> req = adminToken != null && !adminToken.isBlank()
            ? base.header(ADMIN_TOKEN_HEADER, adminToken.trim()) : base;
        return call(() -> req.retrieve()
            .body(new ParameterizedTypeReference<List<GatewayOutageItem>>() {
            }), "查询摘除目标列表");
    }

    /**
     * 手动恢复：清除「规则 × 模型」的摘除状态，目标立即重新参与候选链。
     */
    public GatewayOutageAck release(String gatewayBaseUrl,
                                    String adminToken,
                                    GatewayOutageReleaseRequest request) {
        RestClient.RequestBodySpec base = restClient.post()
            .uri(gatewayBaseUrl + "/_admin/outages/release");
        RestClient.RequestBodySpec req = adminToken != null && !adminToken.isBlank()
            ? base.header(ADMIN_TOKEN_HEADER, adminToken.trim()) : base;
        return call(() -> req.contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .retrieve()
            .body(GatewayOutageAck.class), "手动恢复摘除目标");
    }

    /**
     * 手动摘除：直接进入冷却，不依赖窗口计数。
     */
    public GatewayOutageAck eject(String gatewayBaseUrl,
                                  String adminToken,
                                  GatewayOutageEjectRequest request) {
        RestClient.RequestBodySpec base = restClient.post()
            .uri(gatewayBaseUrl + "/_admin/outages/eject");
        RestClient.RequestBodySpec req = adminToken != null && !adminToken.isBlank()
            ? base.header(ADMIN_TOKEN_HEADER, adminToken.trim()) : base;
        return call(() -> req.contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .retrieve()
            .body(GatewayOutageAck.class), "手动摘除目标");
    }

    /**
     * 统一异常翻译：网关 4xx 复用既有错误码；连接/5xx 归为网关不可用（503）。
     */
    private static <T> T call(ThrowingSupplier<T> action, String op) {
        try {
            return action.get();
        } catch (HttpStatusCodeException ex) {
            HttpStatusCode status = ex.getStatusCode();
            ErrorCode code = switch (status.value()) {
                case 400 -> ErrorCode.VALIDATION_ERROR;
                case 401 -> ErrorCode.UNAUTHORIZED;
                case 403 -> ErrorCode.FORBIDDEN;
                case 404 -> ErrorCode.NOT_FOUND;
                default -> ErrorCode.GATEWAY_UNAVAILABLE;
            };
            throw new BusinessException(code,
                op + " 失败：网关返回 HTTP " + status.value() + " " + ex.getResponseBodyAsString());
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.GATEWAY_UNAVAILABLE,
                op + " 失败：无法连接网关（" + ex.getMessage() + "）");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * 受检异常包装的函数式接口，供 {@link #call} 内统一翻译网关调用异常。
     */
    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}

package org.chobit.knot.admin.service.support;

import org.chobit.knot.common.error.BusinessException;
import org.chobit.knot.common.error.ErrorCode;
import org.chobit.knot.svc.service.support.GatewayCallSupport;
import org.chobit.knot.svc.dto.async.AsyncTaskOperationResponse;
import org.chobit.knot.svc.service.support.RestClientSupport;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;

/**
 * 异步任务运维动作的转发执行器：把管理端的终止 / 重试 / 清理请求透传到网关
 * {@code /_admin/async-tasks*}，准入由网关侧 {@code AsyncTaskAdminController} 自带。
 *
 * <p>⭐ <b>为什么管理端不直接改库</b>：状态迁移的判定与终态事件的发布都在网关
 * （{@code AsyncTaskStateMachine} / {@code AsyncTaskRunner}）。管理端直接 UPDATE 会绕过终态事件，
 * 那笔任务在数仓里就永远缺 {@code task_terminal=true} 的那一条——按终态口径算的金额与成功率
 * 都会漏掉它，而库里看起来一切正常。这与摘除管理面（{@code GatewayOutageRunner}）是同一套形态：
 * <b>运行时状态归运行时进程，管理端只做透传</b>。</p>
 *
 * <p>⚠ 形态刻意与 {@code GatewayOutageRunner} 对齐（RestClient + 短超时 + 统一异常翻译），
 * 但<b>不复用那个类</b>：它绑定的是摘除的路径与报文，合并会让两个不相干的管理面互相牵制。</p>
 *
 * <p>⚠ 在 {@code admin.service.support} 而不是 {@code svc.service.support}：消费方只有管理端
 * （网关自己就是服务端），放进两 app 共用的 svc 只会让网关也背一个用不到的 HTTP 客户端。</p>
 */
@Component
public class GatewayAsyncTaskRunner {

    /** 网关管理面准入令牌头（与网关侧同一常量名；此处硬编码避免反向依赖网关模块） */
    private static final String ADMIN_TOKEN_HEADER = "X-Knot-Admin-Token";
    /** 网关与管理端同机/同网段，短超时即可 */
    private static final int CONNECT_TIMEOUT_MS = 5_000;
    /** 终止要走一次抢占 + 落终态 + 发事件；清理是一次批量 DELETE。10s 足够 */
    private static final int READ_TIMEOUT_MS = 10_000;

    private static final String BASE_PATH = "/_admin/async-tasks";

    private final RestClient restClient;

    public GatewayAsyncTaskRunner() {
        this.restClient = RestClientSupport.withTimeouts(CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS);
    }

    /** 终止一条在途任务（网关侧落 {@code CANCELLED} 并发终态事件） */
    public AsyncTaskOperationResponse cancel(String gatewayBaseUrl, String adminToken, String taskCode) {
        return GatewayCallSupport.call(() -> post(gatewayBaseUrl, adminToken, "/" + taskCode + "/cancel")
                .retrieve()
                .body(AsyncTaskOperationResponse.class), "终止异步任务");
    }

    /** 重开一条失败 / 超时的任务（网关侧 CAS 到 {@code PENDING}） */
    public AsyncTaskOperationResponse retry(String gatewayBaseUrl, String adminToken, String taskCode) {
        return GatewayCallSupport.call(() -> post(gatewayBaseUrl, adminToken, "/" + taskCode + "/retry")
                .retrieve()
                .body(AsyncTaskOperationResponse.class), "重试异步任务");
    }

    /** 立即执行一次「按保留窗口清理终态行」，返回删除行数 */
    public int cleanup(String gatewayBaseUrl, String adminToken) {
        Integer removed = GatewayCallSupport.call(() -> post(gatewayBaseUrl, adminToken, "/cleanup")
                .retrieve()
                .body(Integer.class), "清理异步任务终态行");
        return removed == null ? 0 : removed;
    }

    /**
     * 三个端点共用同一段装配：无请求体的 POST（句柄在路径上）。
     *
     * <p>⚠ 令牌为空时<b>不下发</b>该头，交给网关的 loopback 判定——
     * 发一个空令牌头等于把一个「看起来配了」的信号送过去。</p>
     */
    private RestClient.RequestHeadersSpec<?> post(String gatewayBaseUrl, String adminToken, String path) {
        RestClient.RequestHeadersSpec<?> base = restClient.post().uri(gatewayBaseUrl + BASE_PATH + path);
        return adminToken != null && !adminToken.isBlank()
                ? base.header(ADMIN_TOKEN_HEADER, adminToken.trim())
                : base;
    }


}

package org.chobit.knot.gateway.async.admin;

import org.chobit.knot.gateway.admin.GatewayAdminAccessSupport;
import org.chobit.knot.gateway.runtime.MdcContextSupport;
import org.chobit.knot.svc.dto.async.AsyncTaskOperationResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import java.util.function.Supplier;

/**
 * 异步任务管理面（网关侧，{@code /_admin/async-tasks}），三个端点均为 {@code POST}：
 * {@code /_admin/async-tasks/{taskCode}/cancel} 终止在途任务（落 {@code CANCELLED}）；
 * {@code /_admin/async-tasks/{taskCode}/retry} 重开失败 / 超时任务（{@code PENDING}）；{@code /_admin/async-tasks/cleanup} 按保留窗口清理终态行。
 * 本类负责准入、委托 {@link AsyncTaskAdminRunner}、把「任务不存在」翻成 404；阻塞段一律 {@code onBlockingScheduler}。
 */
@RestController
@RequestMapping("/_admin/async-tasks")
@ConditionalOnProperty(name = "knot.gateway.async-task.admin-api.enabled",
        havingValue = "true", matchIfMissing = true)
public class AsyncTaskAdminController {

    private final AsyncTaskAdminRunner runner;
    private final AsyncTaskAdminProperties properties;

    /**
     * 阻塞段调度器（{@code gatewayBlockingScheduler}）；单测可为 {@code null}，
     * 退化为 Reactor 的 {@code boundedElastic}。
     */
    @Nullable
    private final Scheduler blockingScheduler;

    @Autowired
    public AsyncTaskAdminController(AsyncTaskAdminRunner runner,
                                    AsyncTaskAdminProperties properties,
                                    @Nullable Scheduler blockingScheduler) {
        this.runner = runner;
        this.properties = properties;
        this.blockingScheduler = blockingScheduler;
    }

    /**
     * 终止一条在途任务。
     * 404 仅在任务不存在（或已清理）时返回；「已是终态」「正被轮询节点处理」均为 200 + {@code changed=false}。
     */
    @PostMapping("/{taskCode}/cancel")
    public Mono<ResponseEntity<AsyncTaskOperationResponse>> cancel(@PathVariable("taskCode") String taskCode,
                                                               ServerHttpRequest request) {
        GatewayAdminAccessSupport.requireAccess(request, properties.getToken());
        return respond(() -> runner.cancel(taskCode));
    }

    /** 重开一条失败 / 超时的任务（{@code FAILED} / {@code EXPIRED} → 在途）。 */
    @PostMapping("/{taskCode}/retry")
    public Mono<ResponseEntity<AsyncTaskOperationResponse>> retry(@PathVariable("taskCode") String taskCode,
                                                              ServerHttpRequest request) {
        GatewayAdminAccessSupport.requireAccess(request, properties.getToken());
        return respond(() -> runner.retry(taskCode));
    }

    /** 立即执行一次「按保留窗口清理终态行」，返回删除行数。 */
    @PostMapping("/cleanup")
    public Mono<ResponseEntity<Integer>> cleanup(ServerHttpRequest request) {
        GatewayAdminAccessSupport.requireAccess(request, properties.getToken());
        return MdcContextSupport.onBlockingScheduler(Mono.fromSupplier(runner::cleanup), blockingScheduler)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.ok(0));
    }

    /**
     * 统一的两段式：阻塞段换调度器 → 空结果翻 404。
     * {@code Mono.empty()} 只表示「任务不存在」（Runner 返回 {@code null}），不要给「没做成」补 4xx。
     */
    private Mono<ResponseEntity<AsyncTaskOperationResponse>> respond(Supplier<AsyncTaskOperationResponse> action) {
        return MdcContextSupport.onBlockingScheduler(Mono.fromSupplier(action), blockingScheduler)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }
}

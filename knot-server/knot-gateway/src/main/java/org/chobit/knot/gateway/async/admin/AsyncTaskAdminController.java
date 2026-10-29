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
 * 异步任务管理面（网关侧，{@code /_admin/async-tasks}）。
 *
 * <p>三个端点，全部 {@code POST}（与主链路同一口径：管理动作不是幂等的「读」，
 * 也没有任何理由让中间层缓存或预取）：</p>
 * <ul>
 *   <li>{@code POST /_admin/async-tasks/{taskCode}/cancel}：终止在途任务（落 {@code CANCELLED}）；</li>
 *   <li>{@code POST /_admin/async-tasks/{taskCode}/retry}：重开失败 / 超时任务（{@code PENDING}）；</li>
 *   <li>{@code POST /_admin/async-tasks/cleanup}：立即按保留窗口清理终态行。</li>
 * </ul>
 *
 * <p>⭐ <b>为什么管理动作在网关侧而不在管理端直连数据库</b>：状态迁移与终态事件发布都在网关
 * （{@code AsyncTaskStateMachine} / {@code AsyncTaskRunner#finish}）。管理端直接改库会跳过
 * 终态事件，那笔任务在数仓里就永远没有 {@code task_terminal=true} 的记录。管理端只做 HTTP 透传，
 * 与摘除管理面（{@code /_admin/outages}）同一套形态。</p>
 *
 * <p>⭐ <b>本类只做三件事</b>：准入、把动作委托给 {@link AsyncTaskAdminRunner}、把「任务不存在」
 * 翻成 404。业务判断（能不能终止、能不能重试）全在 Runner 里——controller 出现
 * {@code if (status == ...)} 就是分层错位（{@code 10-backend-java.md §3.3}）。</p>
 *
 * <p>⚠ <b>阻塞段换调度器</b>：Runner 里是 MyBatis 与状态机写库，跑在 Netty event loop 上会
 * 卡死同线程的连接。口径与 {@code AsyncTaskLookupRunner} 一致：一律 {@code onBlockingScheduler}。</p>
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
     *
     * <p>404 只在「没有这个任务」（或它已被清理掉）时返回；「已经是终态」「正被轮询节点处理」
     * 都是 200 + {@code changed=false}——运维要看到的是事实，不是又一个红条。</p>
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
     *
     * <p>⚠ 不要在这里给「没做成」补 4xx：{@code Mono.empty()} 在这条链路上只有一个含义——
     * 「没有这个任务」，它来自 Runner 返回 {@code null}。把两种语义混在一个信号上，
     * 前端就分不清「任务没了」与「任务还在、但这次没动」。</p>
     */
    private Mono<ResponseEntity<AsyncTaskOperationResponse>> respond(Supplier<AsyncTaskOperationResponse> action) {
        return MdcContextSupport.onBlockingScheduler(Mono.fromSupplier(action), blockingScheduler)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }
}

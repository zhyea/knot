package org.chobit.knot.admin.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import lombok.RequiredArgsConstructor;
import org.chobit.knot.admin.config.GatewayRuntimeProperties;
import org.chobit.knot.admin.service.support.GatewayAsyncTaskRunner;
import org.chobit.knot.common.constants.enums.AsyncTaskStatusEnum;
import org.chobit.knot.common.error.BusinessException;
import org.chobit.knot.common.error.ErrorCode;
import org.chobit.knot.common.model.PageRequest;
import org.chobit.knot.common.model.PageResult;
import org.chobit.knot.svc.dto.async.AsyncTaskDto;
import org.chobit.knot.svc.dto.async.AsyncTaskOperationResponse;
import org.chobit.knot.svc.dto.async.AsyncTaskQuery;
import org.chobit.knot.svc.service.AsyncTaskService;
import org.springframework.stereotype.Service;

/**
 * 异步任务管理面的编排：读走 svc 直连库，写全部透传给网关。
 *
 * <p>⭐ <b>读与写为什么走两条完全不同的路</b>：</p>
 * <ul>
 *   <li><b>读</b>（列表 / 详情）是纯查询，管理端自己查库最快也最稳，网关挂了也能排查——
 *       出事的场景恰恰需要它还能看；</li>
 *   <li><b>写</b>（终止 / 重试 / 清理）必须经过网关：状态迁移由 {@code AsyncTaskStateMachine}
 *       判定，终态事件由 {@code AsyncTaskRunner} 发布。管理端直接 UPDATE 会绕过后者，
 *       那笔任务在数仓里就永远没有 {@code task_terminal=true} 的记录，而库里看起来一切正常。</li>
 * </ul>
 *
 * <p>⚠ <b>本类不碰 Mapper</b>（分层红线 {@code §3.1}）：数据访问全部经
 * {@link AsyncTaskService}。判据是一条 grep——{@code admin} 下不应出现
 * {@code org.chobit.knot.dal}，由 {@code LayeringGuardTest} 守着。</p>
 */
@Service
@RequiredArgsConstructor
public class AsyncTaskAdminService {

    private final AsyncTaskService taskService;
    private final GatewayAsyncTaskRunner gatewayRunner;
    private final GatewayRuntimeProperties gatewayProperties;

    /**
     * 分页检索。
     *
     * <p>⚠ 返回的记录里四个 JSON 段是 {@code null}（列表 SQL 刻意没取），前端也只展示摘要字段。
     * 详情请走 {@link #detail(String)}。</p>
     */
    public PageResult<AsyncTaskDto> list(AsyncTaskQuery query) {
        PageRequest pageRequest = query == null ? PageRequest.of(1, 20) : query.toPageRequest();
        Integer status = query == null || query.status() == null
                ? null
                : AsyncTaskStatusEnum.requireCode(query.status(), "无效的任务状态：" + query.status());
        try (Page<?> ignored = PageHelper.startPage(pageRequest.pageNum(), pageRequest.pageSize())) {
            PageInfo<AsyncTaskDto> pageInfo = new PageInfo<>(taskService.search(statusQuery(query, status)));
            return PageResult.of(pageInfo.getList(), pageInfo.getTotal(),
                    pageRequest.pageNum(), pageRequest.pageSize());
        }
    }

    /**
     * 任务详情（含四段 JSON 原文）。
     *
     * <p>⚠ 四个 JSON 段<b>按原文返回</b>，不在后端反序列化：它们是排障素材（请求体、查询口径快照、
     * 计费上下文、终态产物），前端按需折叠展示。后端解析再重组会丢掉「原文长什么样」这个信息——
     * 而排查口径问题时，原文才是最有力的证据。</p>
     */
    public AsyncTaskDto detail(String taskCode) {
        String normalized = taskCode == null ? null : taskCode.trim();
        AsyncTaskDto task = normalized == null || normalized.isEmpty()
                ? null
                : taskService.getByTaskCode(normalized);
        if (task == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "异步任务不存在：" + taskCode);
        }
        return task;
    }

    /** 终止在途任务（透传网关，由网关落 {@code CANCELLED} 并发终态事件） */
    public AsyncTaskOperationResponse cancel(String taskCode) {
        return gatewayRunner.cancel(gatewayProperties.normalizedBaseUrl(),
                gatewayProperties.getAdminToken(), taskCode);
    }

    /** 重试失败 / 超时任务（透传网关，由网关 CAS 回 {@code PENDING}） */
    public AsyncTaskOperationResponse retry(String taskCode) {
        return gatewayRunner.retry(gatewayProperties.normalizedBaseUrl(),
                gatewayProperties.getAdminToken(), taskCode);
    }

    /** 立即清理已过保留窗口的终态行，返回删除行数 */
    public int cleanup() {
        return gatewayRunner.cleanup(gatewayProperties.normalizedBaseUrl(),
                gatewayProperties.getAdminToken());
    }

    /**
     * 用校验过的状态码重建查询条件。
     *
     * <p>为什么不就地改：{@code AsyncTaskQuery} 是 record（不可变），而这里要先做一次
     * 「非法码 → 400」的边界校验。重建成本是一行，比让 mapper 去处理一个不存在的状态码便宜得多
     * ——把非法值塞进 SQL 的后果是「查不到任何行」，而那与「这个状态确实没有任务」无法区分。</p>
     */
    private static AsyncTaskQuery statusQuery(AsyncTaskQuery query, Integer status) {
        if (query == null) {
            return null;
        }
        return new AsyncTaskQuery(query.pageNum(), query.pageSize(), query.keyword(),
                query.profileCode(), query.protocol(), status,
                query.submittedFrom(), query.submittedTo());
    }
}

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
 * 读（列表 / 详情）直连库，网关挂了也能排查；写（终止 / 重试 / 清理）必须经过网关。
 * 状态迁移由 {@code AsyncTaskStateMachine} 判定，终态事件由 {@code AsyncTaskRunner} 发布。
 * 本类不碰 Mapper，数据访问全部经 {@link AsyncTaskService}。
 */
@Service
@RequiredArgsConstructor
public class AsyncTaskAdminService {

    private final AsyncTaskService taskService;
    private final GatewayAsyncTaskRunner gatewayRunner;
    private final GatewayRuntimeProperties gatewayProperties;

    /**
     * 分页检索。返回的记录里四个 JSON 段是 {@code null}（列表 SQL 未取），详情请走 {@link #detail(String)}。
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
     * 任务详情（含请求体、查询口径快照、计费上下文、终态产物四段 JSON 原文）。
     * 四个 JSON 段按原文返回，不在后端反序列化。
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
     * 用校验过的状态码重建查询条件（{@code AsyncTaskQuery} 是 record，不可变）；非法码在此报 400。
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

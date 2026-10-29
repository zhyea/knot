package org.chobit.knot.svc.service;

import lombok.RequiredArgsConstructor;
import org.chobit.knot.dal.mapper.AsyncTaskProfileMapper;
import org.chobit.knot.svc.converter.AsyncTaskConverter;
import org.chobit.knot.svc.dto.async.AsyncTaskProfileDto;
import org.springframework.stereotype.Service;

/**
 * 异步任务查询口径档案（{@code kb_async_task_profiles}）的访问服务。
 *
 * <p>⚠ <b>本服务当前只提供读取</b>：档案由管理端维护，运行时没有写档案的场景。
 * 先给写方法等于先开一扇没人走的门。管理面的增删改将来加在这个类里，
 * 而不是散到网关或另起一个仓储。</p>
 *
 * <p>⭐ 只做「按编码取一行 + 转成模型」，<b>不做任何默认值归一</b>：档案里的
 * {@code poll_interval_ms} 等字段允许为 null（列有 DEFAULT），归一与兜底是消费方
 * （{@code AsyncTaskSpec}）的事。在这里补一层兜底，默认值就会有两个出处。</p>
 *
 * <p>为什么它和 {@link AsyncTaskService} 是两个类：见 {@code AsyncTaskService} 的类注释——
 * 判据是生命周期与「谁能改」，不是表数量。</p>
 */
@Service
@RequiredArgsConstructor
public class AsyncTaskProfileService {

    private final AsyncTaskProfileMapper mapper;

    /** 按档案编码读取；不存在返回 {@code null}（由调用方判为「配置不完整」） */
    public AsyncTaskProfileDto getByProfileCode(String profileCode) {
        return AsyncTaskConverter.toProfileRecord(mapper.getByProfileCode(profileCode));
    }
}

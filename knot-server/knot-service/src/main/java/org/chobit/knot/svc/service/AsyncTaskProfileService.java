package org.chobit.knot.svc.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.common.constants.enums.EnabledStatusEnum;
import org.chobit.knot.common.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.common.error.BusinessException;
import org.chobit.knot.common.error.ErrorCode;
import org.chobit.knot.dal.entity.AsyncTaskProfileEntity;
import org.chobit.knot.dal.mapper.AsyncTaskProfileMapper;
import org.chobit.knot.dal.mapper.ModelApiBindingMapper;
import org.chobit.knot.svc.converter.AsyncTaskConverter;
import org.chobit.knot.svc.dto.async.AsyncTaskProfileDryRunDto;
import org.chobit.knot.svc.dto.async.AsyncTaskProfileDto;
import org.chobit.knot.svc.dto.async.AsyncTaskProfileQuery;
import org.chobit.knot.svc.dto.async.AsyncTaskProfileSaveDto;
import org.chobit.knot.svc.service.support.AsyncTaskProfileSupport;
import org.chobit.knot.svc.vo.async.AsyncTaskProfileDetailVo;
import org.chobit.knot.svc.vo.async.AsyncTaskProfileDryRunVo;
import org.chobit.knot.svc.vo.common.OptionItem;
import org.chobit.knot.svc.vo.common.meta.AsyncTaskProfileMeta;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * 异步任务查询口径档案（{@code kb_async_task_profiles}）的读写与管理面动作。
 * 只负责一行的落库与校验，不解释表达式语义：求值在 {@code AsyncTaskExpressionSupport}、
 * 判定在 {@code AsyncTaskTerminalSupport}、静态校验与试跑在 {@code AsyncTaskProfileSupport}。
 * 档案列允许为 {@code null}，不做默认值兜底，归一由消费方 {@code AsyncTaskSpec} 完成。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncTaskProfileService {

    private final AsyncTaskProfileMapper profileMapper;
    private final ModelApiBindingMapper bindingMapper;

    /** 按档案编码读取（运行时口径）；不存在返回 {@code null}（由调用方判为「配置不完整」） */
    public AsyncTaskProfileDto getByProfileCode(String profileCode) {
        return AsyncTaskConverter.toProfileRecord(profileMapper.getByProfileCode(profileCode));
    }

    /**
     * 按编码读取启用档案（运行时用）：停用或不存在都返回 {@code null}。
     * 停用后已提交的任务仍按 {@code poll_spec_json} 快照跑完。
     */
    public AsyncTaskProfileDto getEnabledByProfileCode(String profileCode) {
        return AsyncTaskConverter.toProfileRecord(
                profileMapper.getEnabledByProfileCode(StringUtils.trimToNull(profileCode)));
    }

    /**
     * 管理面检索；状态码先经 {@link EnabledStatusEnum#requireCode} 校验再进 SQL。
     */
    public List<AsyncTaskProfileDetailVo> search(AsyncTaskProfileQuery query) {
        AsyncTaskProfileQuery q = query == null
                ? new AsyncTaskProfileQuery(null, null, null, null, null)
                : query;
        Integer status = q.status() == null
                ? null
                : EnabledStatusEnum.requireCode(q.status(), "无效的启用状态：" + q.status()).code();
        return profileMapper.search(StringUtils.trimToNull(q.keyword()),
                        StringUtils.upperCase(StringUtils.trimToNull(q.protocol())), status)
                .stream()
                .map(AsyncTaskConverter::toProfileDetailVo)
                .toList();
    }

    /** 档案详情（含被引用行数）；不存在抛 404 */
    public AsyncTaskProfileDetailVo detail(String profileCode) {
        return AsyncTaskConverter.toProfileDetailVo(require(profileCode));
    }

    /**
     * 新建或更新（按 {@code profileCode} 判断）。{@code profileCode} 更新时不可修改，它是绑定表的引用键。
     * 已被引用的档案不允许改协议（绑定行有「协议 = 档案协议」的一致性约束）。
     */
    public AsyncTaskProfileDetailVo save(AsyncTaskProfileSaveDto command) {
        AsyncTaskProfileSaveDto dto = AsyncTaskProfileSupport.normalize(command);
        AsyncTaskProfileSupport.validate(dto);

        AsyncTaskProfileEntity existing = profileMapper.getByProfileCode(dto.profileCode());
        if (existing != null && !Objects.equals(existing.getProtocol(), dto.protocol())) {
            int bindings = bindingMapper.countByAsyncProfileCode(dto.profileCode());
            if (bindings > 0) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "档案 " + dto.profileCode() + " 已被 " + bindings + " 个模型引用，不能修改所属协议"
                                + "（" + existing.getProtocol() + " → " + dto.protocol() + "）；"
                                + "请新建一条档案并逐条改绑");
            }
        }

        AsyncTaskProfileEntity entity = AsyncTaskConverter.toProfileEntity(dto);
        if (existing == null) {
            profileMapper.insert(entity);
        } else {
            profileMapper.update(entity);
        }
        log.info("Async task profile saved: code={} protocol={} status={}",
                dto.profileCode(), dto.protocol(), dto.status());
        return detail(dto.profileCode());
    }

    /**
     * 删除档案；被引用时拒删，不做级联清空绑定。
     */
    public void delete(String profileCode) {
        AsyncTaskProfileEntity existing = profileMapper.getByProfileCode(profileCode);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "异步任务档案不存在：" + profileCode);
        }
        int bindings = bindingMapper.countByAsyncProfileCode(profileCode);
        if (bindings > 0) {
            List<String> models = bindingMapper.listModelCodesByAsyncProfileCode(profileCode);
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "档案 " + profileCode + " 仍被 " + bindings + " 个模型引用（" + String.join("、", models)
                            + "），请先在模型表单里改为同步或换绑其他档案");
        }
        profileMapper.deleteByProfileCode(profileCode);
        log.info("Async task profile deleted: code={}", profileCode);
    }

    /**
     * 任务协议下拉候选项，只含启用档案。
     * {@code disabled} 由后端算：流式协议（{@code ModelApiProtocolEnum#streamSupported}）不能走异步。
     * 停用档案不返回。
     */
    public List<OptionItem<AsyncTaskProfileMeta>> options() {
        return profileMapper.listEnabled().stream()
                .map(entity -> new OptionItem<>(
                        entity.getProfileCode(),
                        entity.getProfileCode(),
                        disabledOf(entity),
                        new AsyncTaskProfileMeta(entity.getProtocol(), entity.getQueryMethod(),
                                entity.getQueryPath(), entity.getPollIntervalMs(), entity.getTimeoutMinutes())))
                .toList();
    }

    /** 试跑：按档案表达式读两段真实响应（{@link AsyncTaskProfileSupport#dryRun} 收集全部错误，不抛） */
    public AsyncTaskProfileDryRunVo dryRun(AsyncTaskProfileDryRunDto command) {
        return AsyncTaskProfileSupport.dryRun(command);
    }

    private static Boolean disabledOf(AsyncTaskProfileEntity entity) {
        ModelApiProtocolEnum protocol = ModelApiProtocolEnum.fromCode(entity.getProtocol());
        return protocol == null || protocol.streamSupported();
    }

    private AsyncTaskProfileEntity require(String profileCode) {
        String normalized = StringUtils.trimToNull(profileCode);
        AsyncTaskProfileEntity entity = normalized == null ? null : profileMapper.getByProfileCode(normalized);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "异步任务档案不存在：" + profileCode);
        }
        return entity;
    }
}

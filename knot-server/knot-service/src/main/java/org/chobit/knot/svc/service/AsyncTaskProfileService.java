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
 *
 * <p>⭐⭐ <b>档案的消费方是两个 app，所以它住在 svc</b>（判据 {@code 10-backend-java.md §3.2}）：
 * 网关按它轮询（只读），管理端按它增删改与试跑。放在任一端，另一端就只能复制一份口径。</p>
 *
 * <p>⭐ <b>读写职责的边界</b>：本类只管「一行配置怎么落库、怎么校验」，
 * <b>不解释表达式语义</b>——表达式求值在 {@code AsyncTaskExpressionSupport}、
 * 判定在 {@code AsyncTaskTerminalSupport}、静态校验与试跑在 {@code AsyncTaskProfileSupport}。
 * 否则这个类会同时知道「表结构」与「某家上游的响应长什么样」。</p>
 *
 * <p>⚠ <b>不在这里做默认值兜底</b>：档案列允许为 null（列有 DEFAULT），归一与兜底是消费方
 * （{@code AsyncTaskSpec}）的事。在这里补一层，默认值就会有两个出处。</p>
 *
 * <p>⚠ <b>为什么它和 {@link AsyncTaskService} 是两个类</b>：判据不是表数量，而是生命周期与
 * 「谁能改」——档案是低频配置（管理面写、网关读），任务实例是运行热数据（只有网关写）。</p>
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
     * 按编码读取<b>启用</b>档案（运行时用）：停用或不存在都返回 {@code null}。
     *
     * <p>⭐ 为什么运行时必须过滤停用态：档案是排障时的第一把开关——「先停掉这家的轮询，别再烧钱」。
     * 若运行时仍按停用档案轮询，这个开关就只是装饰。停用后已提交的任务仍按
     * {@code poll_spec_json} 快照跑完（快照不查档案），这是刻意的：在途任务不该被中途换口径。</p>
     */
    public AsyncTaskProfileDto getEnabledByProfileCode(String profileCode) {
        return AsyncTaskConverter.toProfileRecord(
                profileMapper.getEnabledByProfileCode(StringUtils.trimToNull(profileCode)));
    }

    /**
     * 管理面检索。
     *
     * <p>⚠ 状态码先经 {@link EnabledStatusEnum#requireCode} 校验再进 SQL：把非法码塞进 SQL 的后果是
     * 「查不到任何行」，而那与「这个状态确实没有档案」无法区分。</p>
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
     * 新建或更新（按 {@code profileCode} 判断）。
     *
     * <p>⭐ {@code profileCode} 更新时不可修改：它是绑定表的引用键，改它等于让所有引用
     * 瞬间指向不存在的档案。要换编码只能新建 + 迁移绑定。</p>
     *
     * <p>⚠ <b>已被引用的档案不允许改协议</b>：绑定行上有「协议 = 档案协议」的一致性约束
     * （绑定侧保存时会校验），改了协议会造出一批「协议不匹配」的现行配置，而它们只会在
     * 运行时被兜底回落同步——表现为「异步莫名其妙不生效」。</p>
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
     * 删除档案。
     *
     * <p>⚠ <b>被引用时拒删</b>，不做「级联清空绑定」：清空绑定等于把那些模型静默改回同步，
     * 而使用方只会看到「异步结果不再回推」。这个动作必须由人在模型表单里显式做。</p>
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
     * 任务协议下拉候选项（只含<b>启用</b>档案）。
     *
     * <p>⭐ {@code disabled} 由后端算：流式协议（{@code ModelApiProtocolEnum#streamSupported}）
     * 不可能走异步——一次提交 + N 次查询意味着响应会先结束。把这条事实算一次，
     * 前端就不会出现第二份「哪些协议能异步」的常量。</p>
     *
     * <p>⚠ 停用档案<b>不返回</b>而不是返回 disabled：绑定侧保存时也会因「不存在或已停用」被拒，
     * 若下拉里还留着它，用户会选中一个必然失败的选项。</p>
     */
    public List<OptionItem<AsyncTaskProfileMeta>> options() {
        return profileMapper.listEnabled().stream()
                .map(entity -> new OptionItem<>(
                        entity.getProfileCode(),
                        entity.getProfileCode(),
                        disabledOf(entity),
                        new AsyncTaskProfileMeta(entity.getProtocol(), entity.getQueryMethod(),
                                entity.getQueryPath(), entity.getPollIntervalMs(), entity.getTimeoutMs())))
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

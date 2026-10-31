package org.chobit.knot.svc.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.common.error.BusinessException;
import org.chobit.knot.common.error.ErrorCode;
import org.chobit.knot.common.model.PageRequest;
import org.chobit.knot.common.model.PageResult;
import org.chobit.knot.svc.converter.PluginConverter;
import org.chobit.knot.svc.dto.plugin.PluginDto;
import org.chobit.knot.dal.entity.PluginInstanceEntity;
import org.chobit.knot.dal.mapper.PluginInstanceMapper;
import org.chobit.knot.plugin.PluginBindingProvider;
import org.chobit.knot.plugin.PluginBindingView;
import org.chobit.knot.plugin.PluginExtensionPoint;
import org.chobit.knot.plugin.PluginStageCode;
import org.chobit.knot.plugin.PluginTypeEnum;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
public class PluginService implements PluginBindingProvider {
    private final PluginInstanceMapper pluginMapper;
    private final PluginConverter pluginConverter;

    /**
     * Constructs a new instance.
     */
    public PluginService(PluginInstanceMapper pluginMapper, PluginConverter pluginConverter) {
        this.pluginMapper = pluginMapper;
        this.pluginConverter = pluginConverter;
    }

    /**
     * Lists matching results. Executes the public operation.
     */
    public PageResult<PluginDto> list(PageRequest pageRequest) {
        return list(pageRequest, null, null, null);
    }

    /**
     * Lists matching results. Executes the public operation.
     */
    public PageResult<PluginDto> list(PageRequest pageRequest, String keyword, String status, String pluginType) {
        try (Page<?> ignored = PageHelper.startPage(pageRequest.pageNum(), pageRequest.pageSize())) {
            PageInfo<PluginInstanceEntity> pageInfo = new PageInfo<>(pluginMapper.list(StringUtils.trimToNull(keyword), toStatusCode(status), normalizePluginType(pluginType)));
            return PageResult.fromPage(pageInfo, pluginConverter::toDtoList, pageRequest);
        }
    }

    /**
     * Returns the requested value. Executes the public operation.
     */
    public PluginDto getById(Long id) {
        PluginInstanceEntity entity = pluginMapper.getById(id);
        if (entity == null) throw new BusinessException(ErrorCode.NOT_FOUND, "plugin not found");
        return pluginConverter.toDto(entity);
    }

    /**
     * 逻辑删除（死行不展示）。reconcile 标记 orphaned 的插件行，由前端删除按钮触发。
     */
    @Transactional
    public PluginDto markDeleted(Long id) {
        getById(id); // ensure exists
        pluginMapper.updateDeleted(id, 1);
        return getById(id);
    }

    /**
     * Updates the target resource. Executes the public operation.
     */
    @Transactional
    public PluginDto updateStatus(Long id, Integer status) {
        getById(id); // ensure exists
        pluginMapper.updateStatus(id, status);
        return getById(id);
    }

    @Override
    public List<PluginBindingView> listBindings(PluginExtensionPoint extensionPoint, PluginStageCode stageCode) {
        return pluginMapper.listActiveBindings(extensionPoint.code(), stageCode.code()).stream()
                .map(this::toBindingView)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * 单表视图构造：枚举列按 code 解析；存量非法 code 直接跳过，避免 valueOf 抛异常打挂整条链路。
     * scope 维度已随单表收口移除（2026-10-13），配置改由 Nacos 下发，故视图不再携带 scope/config。
     */
    private PluginBindingView toBindingView(PluginInstanceEntity entity) {
        PluginExtensionPoint point = PluginExtensionPoint.fromCode(entity.getExtensionPoint());
        PluginStageCode stage = PluginStageCode.fromCode(entity.getStageCode());
        if (point == null || stage == null) {
            return null;
        }
        return new PluginBindingView(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getCapabilityCode(),
                point,
                stage,
                entity.getOrderNo(),
                entity.getTimeoutMs()
        );
    }

    /** 查询参数归一：空串视为不过滤；非 SYNC 的非法值同样不过滤（避免误吞全部结果） */
    private static String normalizePluginType(String pluginType) {
        String value = pluginType == null ? "" : pluginType.trim().toUpperCase();
        if (value.isEmpty()) {
            return null;
        }
        return PluginTypeEnum.fromCode(value) == null ? null : value;
    }

    /** 查询参数归一：数字字符串 -> EnabledStatusEnum code；非法值不过滤 */
    private static Integer toStatusCode(String status) {
        String value = status == null ? "" : status.trim();
        if (value.isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

}

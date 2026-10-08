package org.chobit.knot.gateway.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.chobit.knot.gateway.error.BusinessException;
import org.chobit.knot.gateway.error.ErrorCode;
import org.chobit.knot.gateway.model.PageRequest;
import org.chobit.knot.gateway.model.PageResult;
import org.chobit.knot.gateway.converter.PluginConverter;
import org.chobit.knot.gateway.dto.plugin.PluginDto;
import org.chobit.knot.gateway.entity.PluginBindingEntity;
import org.chobit.knot.gateway.entity.PluginInstanceEntity;
import org.chobit.knot.gateway.mapper.PluginInstanceMapper;
import org.chobit.knot.gateway.plugin.*;
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
        return list(pageRequest, null, null);
    }

    /**
     * Lists matching results. Executes the public operation.
     */
    public PageResult<PluginDto> list(PageRequest pageRequest, String keyword, String status) {
        try (Page<?> ignored = PageHelper.startPage(pageRequest.pageNum(), pageRequest.pageSize())) {
            PageInfo<PluginInstanceEntity> pageInfo = new PageInfo<>(pluginMapper.list(normalizeKeyword(keyword), toStatusCode(status)));
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
     * Creates a new resource. Executes the public operation.
     */
    @Transactional
    public PluginDto create(PluginDto request) {
        PluginInstanceEntity e = new PluginInstanceEntity();
        e.setCode(request.code());
        e.setName(request.name());
        e.setPackageCode(request.packageCode());
        e.setCapabilityCode(request.capabilityCode());
        // 扩展点与执行阶段由代码枚举唯一定义，非法 code 直接拒绝，不再依赖 DB 字典
        e.setExtensionPoint(PluginExtensionPoint.requireCode(request.extensionPoint(),
                "unsupported plugin extension point: " + request.extensionPoint()));
        e.setStageCode(PluginStageCode.requireCode(request.stageCode(),
                "unsupported plugin stage code: " + request.stageCode()));
        e.setStatus(request.status());
        e.setTimeoutMs(request.timeoutMs());
        e.setConfigJson(request.configJson());
        pluginMapper.insert(e);
        return getById(e.getId());
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
     * 绑定行的枚举列一律按 code 解析；存量非法 code 直接跳过，避免 valueOf 抛异常打挂整条链路。
     */
    private PluginBindingView toBindingView(PluginBindingEntity entity) {
        PluginExtensionPoint point = PluginExtensionPoint.fromCode(entity.getExtensionPoint());
        PluginStageCode stage = PluginStageCode.fromCode(entity.getStageCode());
        PluginScopeType scope = PluginScopeType.fromCode(entity.getScopeType());
        if (point == null || stage == null || scope == null) {
            return null;
        }
        return new PluginBindingView(
                entity.getId(),
                entity.getInstanceCode(),
                entity.getInstanceName(),
                entity.getPackageCode(),
                entity.getPackageName(),
                entity.getCapabilityCode(),
                point,
                stage,
                scope,
                entity.getScopeRefId(),
                entity.getOrderNo(),
                entity.getConfigJson(),
                entity.getTimeoutMs()
        );
    }

    private static String normalizeKeyword(String keyword) {
        String value = keyword == null ? "" : keyword.trim();
        return value.isEmpty() ? null : value;
    }

    /** 查询参数归一：数字字符串 -> PluginInstanceStatusEnum code；非法值不过滤 */
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

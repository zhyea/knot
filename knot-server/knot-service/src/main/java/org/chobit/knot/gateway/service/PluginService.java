package org.chobit.knot.gateway.service;

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
        PageHelper.startPage(pageRequest.pageNum(), pageRequest.pageSize());
        PageInfo<PluginInstanceEntity> pageInfo = new PageInfo<>(pluginMapper.list(normalizeKeyword(keyword), normalizeStatus(status)));
        return PageResult.fromPage(pageInfo, pluginConverter::toDtoList, pageRequest);
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
        e.setStatus(request.status());
        e.setFailMode(request.failMode());
        e.setTimeoutMs(request.timeoutMs());
        e.setConfigJson(request.configJson());
        pluginMapper.insert(e);
        return getById(e.getId());
    }

    /**
     * Updates the target resource. Executes the public operation.
     */
    @Transactional
    public PluginDto updateStatus(Long id, String status) {
        getById(id); // ensure exists
        pluginMapper.updateStatus(id, status);
        return getById(id);
    }

    @Override
    public List<PluginBindingView> listBindings(PluginExtensionPoint extensionPoint, PluginStageCode stageCode) {
        return pluginMapper.listActiveBindings(extensionPoint.name(), stageCode.code()).stream()
                .map(this::toBindingView)
                .toList();
    }

    private PluginBindingView toBindingView(PluginBindingEntity entity) {
        return new PluginBindingView(
                entity.getId(),
                entity.getInstanceId(),
                entity.getInstanceCode(),
                entity.getInstanceName(),
                entity.getPackageCode(),
                entity.getPackageName(),
                entity.getCapabilityCode(),
                PluginExtensionPoint.valueOf(entity.getExtensionPoint()),
                PluginStageCode.fromCode(entity.getStageCode()),
                PluginScopeType.valueOf(entity.getScopeType()),
                entity.getScopeRefId(),
                entity.getOrderNo(),
                entity.getConfigJson(),
                entity.getFailMode(),
                entity.getTimeoutMs()
        );
    }

    private static String normalizeKeyword(String keyword) {
        String value = keyword == null ? "" : keyword.trim();
        return value.isEmpty() ? null : value;
    }

    private static String normalizeStatus(String status) {
        String value = status == null ? "" : status.trim();
        return value.isEmpty() ? null : value;
    }

}

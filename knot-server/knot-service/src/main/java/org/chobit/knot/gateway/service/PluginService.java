package org.chobit.knot.gateway.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.chobit.knot.gateway.error.BusinessException;
import org.chobit.knot.gateway.error.ErrorCode;
import org.chobit.knot.gateway.model.PageRequest;
import org.chobit.knot.gateway.model.PageResult;
import org.chobit.knot.gateway.converter.PluginConverter;
import org.chobit.knot.gateway.dto.plugin.PluginDto;
import org.chobit.knot.gateway.entity.PluginEntity;
import org.chobit.knot.gateway.mapper.PluginMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PluginService {
    private final PluginMapper pluginMapper;
    private final PluginConverter pluginConverter;

    public PluginService(PluginMapper pluginMapper, PluginConverter pluginConverter) {
        this.pluginMapper = pluginMapper;
        this.pluginConverter = pluginConverter;
    }

    public PageResult<PluginDto> list(PageRequest pageRequest) {
        PageHelper.startPage(pageRequest.pageNum(), pageRequest.pageSize());
        PageInfo<PluginEntity> pageInfo = new PageInfo<>(pluginMapper.list());
        return PageResult.fromPage(pageInfo, pluginConverter::toDtoList, pageRequest);
    }

    public PluginDto getById(Long id) {
        PluginEntity entity = pluginMapper.getById(id);
        if (entity == null) throw new BusinessException(ErrorCode.NOT_FOUND, "plugin not found");
        return pluginConverter.toDto(entity);
    }

    @Transactional
    public PluginDto create(PluginDto request) {
        PluginEntity e = new PluginEntity();
        e.setCode(request.code());
        e.setName(request.name());
        e.setPluginType(request.pluginType());
        e.setVersion(request.version());
        e.setStatus(request.status());
        pluginMapper.insert(e);
        return pluginConverter.toDto(e);
    }

    @Transactional
    public PluginDto updateStatus(Long id, String status) {
        getById(id); // ensure exists
        PluginEntity e = new PluginEntity();
        e.setId(id);
        e.setStatus(status);
        pluginMapper.updateStatus(e);
        return getById(id);
    }

}

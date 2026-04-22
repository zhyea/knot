package com.knot.gateway.service;

import com.knot.gateway.entity.PluginEntity;
import com.knot.gateway.mapper.PluginMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PluginService {
    private final PluginMapper pluginMapper;

    public PluginService(PluginMapper pluginMapper) {
        this.pluginMapper = pluginMapper;
    }

    public List<PluginDto> list() {
        return pluginMapper.list().stream()
                .map(p -> new PluginDto(p.getId(), p.getCode(), p.getName(), p.getPluginType(), p.getVersion(), p.getStatus()))
                .toList();
    }

    public PluginDto create(PluginDto request) {
        PluginEntity e = new PluginEntity();
        e.setCode(request.code());
        e.setName(request.name());
        e.setPluginType(request.pluginType());
        e.setVersion(request.version());
        e.setStatus(request.status());
        pluginMapper.insert(e);
        return new PluginDto(e.getId(), e.getCode(), e.getName(), e.getPluginType(), e.getVersion(), e.getStatus());
    }

    public PluginDto updateStatus(Long id, String status) {
        PluginEntity e = new PluginEntity();
        e.setId(id);
        e.setStatus(status);
        pluginMapper.updateStatus(e);
        return list().stream().filter(i -> i.id().equals(id)).findFirst()
                .orElse(new PluginDto(id, "unknown", "unknown", "PRE", "0.0.0", status));
    }

    public record PluginDto(Long id, String code, String name, String pluginType, String version, String status) {}
}

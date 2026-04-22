package com.knot.gateway.controller;

import com.knot.gateway.common.ApiResponse;
import com.knot.gateway.service.PluginService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/plugins")
public class PluginController {
    private final PluginService pluginService;

    public PluginController(PluginService pluginService) {
        this.pluginService = pluginService;
    }

    @GetMapping
    public ApiResponse<List<PluginItem>> list() {
        List<PluginItem> result = pluginService.list().stream()
                .map(p -> new PluginItem(p.id(), p.code(), p.name(), p.pluginType(), p.version(), p.status()))
                .toList();
        return ApiResponse.ok(result);
    }

    @PostMapping
    public ApiResponse<PluginItem> create(@RequestBody PluginItem request) {
        PluginService.PluginDto created = pluginService.create(
                new PluginService.PluginDto(null, request.code(), request.name(), request.pluginType(), request.version(), request.status())
        );
        return ApiResponse.ok(new PluginItem(created.id(), created.code(), created.name(), created.pluginType(), created.version(), created.status()));
    }

    @PutMapping("/{id}/status")
    public ApiResponse<PluginItem> updateStatus(@PathVariable Long id, @RequestBody PluginStatusRequest request) {
        PluginService.PluginDto updated = pluginService.updateStatus(id, request.status());
        return ApiResponse.ok(new PluginItem(updated.id(), updated.code(), updated.name(), updated.pluginType(), updated.version(), updated.status()));
    }

    public record PluginItem(Long id, String code, String name, String pluginType, String version, String status) {
    }

    public record PluginStatusRequest(String status) {
    }
}

package com.knot.gateway.controller;

import com.knot.gateway.common.ApiResponse;
import com.knot.gateway.common.model.PageRequest;
import com.knot.gateway.common.model.PageResult;
import com.knot.gateway.converter.PluginConverter;
import com.knot.gateway.service.PluginService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/plugins")
public class PluginController {
    private final PluginService pluginService;
    private final PluginConverter pluginConverter;

    public PluginController(PluginService pluginService, PluginConverter pluginConverter) {
        this.pluginService = pluginService;
        this.pluginConverter = pluginConverter;
    }

    @GetMapping
    public ApiResponse<PageResult<PluginItem>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        PageResult<PluginService.PluginDto> page = pluginService.list(PageRequest.of(pageNum, pageSize));
        return ApiResponse.ok(page.mapList(pluginConverter::toVOList));
    }

    @PostMapping
    public ApiResponse<PluginItem> create(@RequestBody @Valid PluginItem request) {
        PluginService.PluginDto created = pluginService.create(pluginConverter.toDto(request));
        return ApiResponse.ok(pluginConverter.toVO(created));
    }

    @PutMapping("/{id}/status")
    public ApiResponse<PluginItem> updateStatus(@PathVariable Long id, @RequestBody @Valid PluginStatusRequest request) {
        PluginService.PluginDto updated = pluginService.updateStatus(id, request.status());
        return ApiResponse.ok(pluginConverter.toVO(updated));
    }

    public record PluginItem(Long id, String code, String name, String pluginType, String version, String status) {
    }

    public record PluginStatusRequest(String status) {
    }
}

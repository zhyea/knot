package org.chobit.knot.gateway.controller;

import org.chobit.knot.gateway.ApiResponse;
import org.chobit.knot.gateway.model.PageQuery;
import org.chobit.knot.gateway.model.PageRequest;
import org.chobit.knot.gateway.model.PageResult;
import org.chobit.knot.gateway.converter.PluginConverter;
import org.chobit.knot.gateway.dto.plugin.PluginDto;
import org.chobit.knot.gateway.service.PluginService;
import org.chobit.knot.gateway.vo.plugin.*;
import jakarta.validation.Valid;
import org.chobit.knot.gateway.vo.plugin.PluginItem;
import org.chobit.knot.gateway.vo.plugin.PluginStatusRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/plugins")
public class PluginController {
    private final PluginService pluginService;
    private final PluginConverter pluginConverter;

    public PluginController(PluginService pluginService, PluginConverter pluginConverter) {
        this.pluginService = pluginService;
        this.pluginConverter = pluginConverter;
    }

    @PostMapping
    public ApiResponse<PageResult<PluginItem>> list(@RequestBody(required = false) PageQuery query) {
        PageResult<PluginDto> page = pluginService.list(query == null ? PageRequest.of(1, 20) : query.toPageRequest());
        return ApiResponse.ok(page.mapList(pluginConverter::toVOList));
    }

    @PostMapping
    public ApiResponse<PluginItem> create(@RequestBody @Valid PluginItem request) {
        PluginDto created = pluginService.create(pluginConverter.toDto(request));
        return ApiResponse.ok(pluginConverter.toVO(created));
    }

    @PutMapping("/{id}/status")
    public ApiResponse<PluginItem> updateStatus(@PathVariable Long id, @RequestBody @Valid PluginStatusRequest request) {
        PluginDto updated = pluginService.updateStatus(id, request.status());
        return ApiResponse.ok(pluginConverter.toVO(updated));
    }

}

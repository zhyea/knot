package com.knot.gateway.controller;

import com.knot.gateway.common.ApiResponse;
import com.knot.gateway.common.model.PageRequest;
import com.knot.gateway.common.model.PageResult;
import com.knot.gateway.common.model.QuotaPolicy;
import com.knot.gateway.common.model.RateLimitPolicy;
import com.knot.gateway.converter.ModelConverter;
import com.knot.gateway.dto.model.ModelDto;
import com.knot.gateway.dto.model.ModelTestResultDto;
import com.knot.gateway.dto.model.ModelVersionSwitchResultDto;
import com.knot.gateway.service.ModelService;
import com.knot.gateway.vo.model.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/models")
public class ModelController {
    private final ModelService modelService;
    private final ModelConverter modelConverter;

    public ModelController(ModelService modelService, ModelConverter modelConverter) {
        this.modelService = modelService;
        this.modelConverter = modelConverter;
    }

    @GetMapping
    public ApiResponse<PageResult<ModelItem>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        PageResult<ModelDto> page = modelService.list(PageRequest.of(pageNum, pageSize));
        return ApiResponse.ok(page.mapList(modelConverter::toVOList));
    }

    @PostMapping
    public ApiResponse<ModelItem> create(@RequestBody @Valid ModelItem request) {
        ModelDto created = modelService.create(modelConverter.toDto(request));
        return ApiResponse.ok(modelConverter.toVO(created));
    }

    @PutMapping("/{id}")
    public ApiResponse<ModelItem> update(@PathVariable Long id, @RequestBody @Valid ModelItem request) {
        ModelDto updated = modelService.update(id, modelConverter.toDto(request));
        return ApiResponse.ok(modelConverter.toVO(updated));
    }

    @PostMapping("/{id}/test")
    public ApiResponse<ModelTestResult> test(@PathVariable Long id, @RequestBody @Valid ModelTestRequest request) {
        ModelTestResultDto result = modelService.testModel(id, request.prompt());
        return ApiResponse.ok(new ModelTestResult(result.output(), result.latencyMs(), result.tokenUsage()));
    }

    @PostMapping("/{id}/versions/switch")
    public ApiResponse<ModelVersionSwitchResult> switchVersion(@PathVariable Long id, @RequestBody @Valid ModelVersionSwitchRequest request) {
        ModelVersionSwitchResultDto result = modelService.switchVersion(id, request.targetVersion());
        return ApiResponse.ok(new ModelVersionSwitchResult(result.modelId(), result.activeVersion(), result.status()));
    }

}

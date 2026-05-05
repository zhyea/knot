package org.chobit.knot.gateway.controller;

import org.chobit.knot.gateway.ApiResponse;
import org.chobit.knot.gateway.model.PageQuery;
import org.chobit.knot.gateway.model.PageRequest;
import org.chobit.knot.gateway.model.PageResult;
import org.chobit.knot.gateway.converter.ModelConverter;
import org.chobit.knot.gateway.dto.model.ModelDto;
import org.chobit.knot.gateway.dto.model.ModelTestResultDto;
import org.chobit.knot.gateway.dto.model.ModelVersionSwitchResultDto;
import org.chobit.knot.gateway.service.ModelService;
import org.chobit.knot.gateway.vo.model.*;
import jakarta.validation.Valid;
import org.chobit.knot.gateway.vo.model.*;
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

    @PostMapping("/list")
    public ApiResponse<PageResult<ModelItem>> list(@RequestBody(required = false) PageQuery query) {
        PageResult<ModelDto> page = modelService.list(query == null ? PageRequest.of(1, 20) : query.toPageRequest());
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

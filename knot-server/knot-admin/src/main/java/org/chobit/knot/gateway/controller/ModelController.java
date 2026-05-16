package org.chobit.knot.gateway.controller;

import org.chobit.knot.gateway.annotation.OperationLog;
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
    public PageResult<ModelItem> list(@RequestBody(required = false) PageQuery query) {
        PageResult<ModelDto> page = modelService.list(query == null ? PageRequest.of(1, 20) : query.toPageRequest());
        return page.mapList(modelConverter::toVOList);
    }

    @OperationLog(module = "model", operation = "CREATE", entityType = "Model",
            entityIdAfter = "#result.id()",
            entityNameAfter = "#result.name()",
            description = "'新建模型'",
            recordNewValue = true,
            newValueSpel = "@modelService.modelAuditSnapshot(#result.id())")
    @PostMapping
    public ModelItem create(@RequestBody @Valid ModelItem request) {
        ModelDto created = modelService.create(modelConverter.toDto(request));
        return modelConverter.toVO(created);
    }

    @OperationLog(module = "model", operation = "UPDATE", entityType = "Model",
            entityId = "#p0",
            entityNameAfter = "#result.name()",
            description = "'更新模型'",
            recordOldValue = true,
            oldValueSpel = "@modelService.modelAuditSnapshot(#p0)",
            recordNewValue = true,
            newValueSpel = "@modelService.modelAuditSnapshot(#p0)")
    @PutMapping("/{id}")
    public ModelItem update(@PathVariable Long id, @RequestBody @Valid ModelItem request) {
        ModelDto updated = modelService.update(id, modelConverter.toDto(request));
        return modelConverter.toVO(updated);
    }

    @PostMapping("/{id}/test")
    public ModelTestResult test(@PathVariable Long id, @RequestBody @Valid ModelTestRequest request) {
        ModelTestResultDto result = modelService.testModel(id, request.prompt());
        return new ModelTestResult(result.output(), result.latencyMs(), result.tokenUsage());
    }

    @PostMapping("/{id}/versions/switch")
    public ModelVersionSwitchResult switchVersion(@PathVariable Long id, @RequestBody @Valid ModelVersionSwitchRequest request) {
        ModelVersionSwitchResultDto result = modelService.switchVersion(id, request.targetVersion());
        return new ModelVersionSwitchResult(result.modelId(), result.activeVersion(), result.status());
    }
}

package org.chobit.knot.gateway.controller;

import jakarta.validation.Valid;
import org.chobit.knot.gateway.annotation.OperationLog;
import org.chobit.knot.gateway.converter.ModelConverter;
import org.chobit.knot.gateway.dto.model.ModelDto;
import org.chobit.knot.gateway.model.PageQuery;
import org.chobit.knot.gateway.model.PageRequest;
import org.chobit.knot.gateway.model.PageResult;
import org.chobit.knot.gateway.service.ModelService;
import org.chobit.knot.gateway.vo.common.EnabledStatusRequest;
import org.chobit.knot.gateway.vo.model.ModelItem;
import org.chobit.knot.gateway.vo.model.UsageExtractorItem;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/models")
public class ModelController {
    private final ModelService modelService;
    private final ModelConverter modelConverter;

    /**
     * Constructs a new instance.
     */
    public ModelController(ModelService modelService, ModelConverter modelConverter) {
        this.modelService = modelService;
        this.modelConverter = modelConverter;
    }

    /**
     * Checks whether the model code is available.
     */
    @GetMapping("/check-code")
    public Map<String, Boolean> checkCode(
            @RequestParam String code,
            @RequestParam(required = false) Long excludeId) {
        return Map.of("available", modelService.isModelCodeAvailable(code, excludeId));
    }

    /**
     * Lists usage extractors available for model API bindings.
     */
    @GetMapping("/usage-extractors")
    public List<UsageExtractorItem> usageExtractors() {
        return modelService.listUsageExtractors();
    }

    /**
     * Returns the model detail.
     */
    @GetMapping("/{id}")
    public ModelItem get(@PathVariable Long id) {
        return modelConverter.toVO(modelService.getById(id));
    }

    /**
     * Lists models with pagination.
     */
    @PostMapping("/list")
    public PageResult<ModelItem> list(@RequestBody(required = false) PageQuery query) {
        PageResult<ModelDto> page = modelService.list(
                query == null ? PageRequest.of(1, 20) : query.toPageRequest(),
                query == null ? null : query.keyword(),
                query == null ? null : query.modelTypes()
        );
        return page.mapList(modelConverter::toVOList);
    }

    /**
     * Creates a model.
     */
    @OperationLog(module = "model", operation = "CREATE", entityType = "Model",
            entityIdAfter = "#result.id()",
            entityNameAfter = "#result.name()",
            description = "'创建模型'",
            recordNewValue = true,
            newValueSpel = "@modelService.modelAuditSnapshot(#result.id())")
    @PostMapping
    public ModelItem create(@RequestBody @Valid ModelItem request) {
        ModelDto created = modelService.create(modelConverter.toDto(request));
        return modelConverter.toVO(created);
    }

    /**
     * Updates a model.
     */
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

    /**
     * Updates the model enabled status.
     */
    @OperationLog(module = "model", operation = "UPDATE", entityType = "Model",
            entityId = "#p0",
            entityNameAfter = "#result.name()",
            description = "'更新模型状态'",
            recordOldValue = true,
            oldValueSpel = "@modelService.modelAuditSnapshot(#p0)",
            recordNewValue = true,
            newValueSpel = "@modelService.modelAuditSnapshot(#p0)")
    @PutMapping("/{id}/status")
    public ModelItem updateStatus(@PathVariable Long id, @RequestBody @Valid EnabledStatusRequest request) {
        ModelDto updated = modelService.updateStatus(id, Boolean.TRUE.equals(request.enabled()));
        return modelConverter.toVO(updated);
    }
}

package org.chobit.knot.gateway.controller;

import org.chobit.knot.gateway.annotation.OperationLog;
import org.chobit.knot.gateway.model.PageQuery;
import org.chobit.knot.gateway.model.PageRequest;
import org.chobit.knot.gateway.model.PageResult;
import org.chobit.knot.gateway.converter.ModelConverter;
import org.chobit.knot.gateway.dto.model.ModelDto;
import org.chobit.knot.gateway.dto.model.ModelTestResultDto;
import org.chobit.knot.gateway.service.ModelService;
import org.chobit.knot.gateway.vo.model.*;
import jakarta.validation.Valid;
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
     * Checks whether the requested condition is satisfied. Executes the public operation.
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
     * Returns the requested value. Executes the public operation.
     */
    @GetMapping("/{id}")
    public ModelItem get(@PathVariable Long id) {
        return modelConverter.toVO(modelService.getById(id));
    }

    /**
     * Lists matching results. Executes the public operation.
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
     * Creates a new resource. Executes the public operation.
     */
    @OperationLog(module = "model", operation = "CREATE", entityType = "Model",
            entityIdAfter = "#result.id()",
            entityNameAfter = "#result.name()",
            description = "'鏂板缓妯″瀷'",
            recordNewValue = true,
            newValueSpel = "@modelService.modelAuditSnapshot(#result.id())")
    @PostMapping
    /**
     * Creates a new resource.
     */
    public ModelItem create(@RequestBody @Valid ModelItem request) {
        ModelDto created = modelService.create(modelConverter.toDto(request));
        return modelConverter.toVO(created);
    }

    /**
     * Updates the target resource. Executes the public operation.
     */
    @OperationLog(module = "model", operation = "UPDATE", entityType = "Model",
            entityId = "#p0",
            entityNameAfter = "#result.name()",
            description = "'鏇存柊妯″瀷'",
            recordOldValue = true,
            oldValueSpel = "@modelService.modelAuditSnapshot(#p0)",
            recordNewValue = true,
            newValueSpel = "@modelService.modelAuditSnapshot(#p0)")
    @PutMapping("/{id}")
    /**
     * Updates the target resource.
     */
    public ModelItem update(@PathVariable Long id, @RequestBody @Valid ModelItem request) {
        ModelDto updated = modelService.update(id, modelConverter.toDto(request));
        return modelConverter.toVO(updated);
    }

    /**
     * Executes a test operation and returns the result. Executes the public operation.
     */
    @PostMapping("/{id}/test")
    public ModelTestResult test(@PathVariable Long id, @RequestBody @Valid ModelTestRequest request) {
        ModelTestResultDto result = modelService.testModel(id, request.prompt());
        return new ModelTestResult(result.output(), result.latencyMs(), result.tokenUsage());
    }

}

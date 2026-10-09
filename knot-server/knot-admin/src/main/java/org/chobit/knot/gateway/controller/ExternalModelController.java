package org.chobit.knot.gateway.controller;

import org.chobit.knot.gateway.annotation.OperationLog;
import org.chobit.knot.gateway.dto.model.ExternalModelItemQuery;
import org.chobit.knot.gateway.dto.model.ExternalModelSyncResult;
import org.chobit.knot.gateway.dto.model.LogicalModelDto;
import org.chobit.knot.gateway.entity.ExternalModelItemEntity;
import org.chobit.knot.gateway.entity.ExternalModelSourceEntity;
import org.chobit.knot.gateway.model.PageResult;
import org.chobit.knot.gateway.service.ExternalModelService;
import org.chobit.knot.gateway.vo.model.LogicalModelItem;
import org.chobit.knot.gateway.converter.LogicalModelConverter;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/external-models")
public class ExternalModelController {

    private final ExternalModelService externalModelService;
    private final LogicalModelConverter logicalModelConverter;

    /**
     * Constructs a new instance.
     */
    public ExternalModelController(ExternalModelService externalModelService,
                                   LogicalModelConverter logicalModelConverter) {
        this.externalModelService = externalModelService;
        this.logicalModelConverter = logicalModelConverter;
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    @GetMapping("/sources")
    public List<ExternalModelSourceEntity> sources() {
        return externalModelService.listSources();
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    @PostMapping("/items/list")
    public PageResult<ExternalModelItemEntity> items(@RequestBody(required = false) ExternalModelItemQuery query) {
        return externalModelService.listItems(query);
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    @GetMapping("/items/{id}")
    public ExternalModelItemEntity item(@PathVariable Long id) {
        return externalModelService.getItem(id);
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    @OperationLog(module = "external-model", operation = "SYNC", entityType = "ExternalModelSource",
            entityName = "#p0",
            description = "'同步外部模型清单'",
            recordOldValue = false,
            newValueSpel = "#result")
    @PostMapping("/sources/{sourceCode}/sync")
    public ExternalModelSyncResult sync(@PathVariable String sourceCode) {
        return externalModelService.sync(sourceCode);
    }

    /**
     * Creates a new resource. Executes the public operation.
     */
    @OperationLog(module = "external-model", operation = "CREATE", entityType = "LogicalModel",
            entityId = "#p0",
            entityNameAfter = "#result.modelName",
            description = "'从外部模型创建统一模型'",
            oldValueSpel = "@externalModelService.getItem(#p0)",
            newValueSpel = "#result")
    @PostMapping("/items/{id}/logical-model")
    public LogicalModelItem createLogicalModel(@PathVariable Long id) {
        LogicalModelDto created = externalModelService.createLogicalModel(id);
        return logicalModelConverter.toVO(created);
    }

    /**
     * Creates a new resource. Executes the public operation.
     */
    @OperationLog(module = "external-model", operation = "CREATE", entityType = "LogicalModel",
            description = "'批量从外部模型创建统一模型'",
            recordOldValue = false,
            newValueSpel = "#result")
    @PostMapping("/items/logical-models")
    public ExternalModelSyncResult createLogicalModels(@RequestBody(required = false) ExternalModelItemQuery query) {
        return externalModelService.createLogicalModels(query);
    }

    /**
     * Deletes the target resource. Executes the public operation.
     */
    @OperationLog(module = "external-model", operation = "DELETE", entityType = "ExternalModelItem",
            entityId = "#p0",
            description = "'删除外部模型'",
            oldValueSpel = "@externalModelService.getItem(#p0)",
            recordNewValue = false)
    @DeleteMapping("/items/{id}")
    public void deleteItem(@PathVariable Long id) {
        externalModelService.deleteItem(id);
    }

    /**
     * Deletes the target resource. Executes the public operation.
     */
    @OperationLog(module = "external-model", operation = "DELETE", entityType = "ExternalModelItem",
            description = "'批量删除外部模型'",
            recordOldValue = false,
            newValueSpel = "#p0")
    @PostMapping("/items/batch-delete")
    public int deleteItems(@RequestBody List<Long> ids) {
        return externalModelService.deleteItems(ids);
    }

    @OperationLog(module = "external-model", operation = "UPDATE", entityType = "ExternalModelItem",
            entityId = "#p0",
            description = "'设置外部模型忽略状态'",
            oldValueSpel = "@externalModelService.getItem(#p0)",
            newValueSpel = "#p1")
    @PostMapping("/items/{id}/ignored")
    public void setIgnored(@PathVariable Long id, @RequestParam boolean ignored) {
        externalModelService.setIgnored(id, ignored);
    }
}

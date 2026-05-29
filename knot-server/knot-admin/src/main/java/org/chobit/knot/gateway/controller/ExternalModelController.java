package org.chobit.knot.gateway.controller;

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

    public ExternalModelController(ExternalModelService externalModelService,
                                   LogicalModelConverter logicalModelConverter) {
        this.externalModelService = externalModelService;
        this.logicalModelConverter = logicalModelConverter;
    }

    @GetMapping("/sources")
    public List<ExternalModelSourceEntity> sources() {
        return externalModelService.listSources();
    }

    @PostMapping("/items/list")
    public PageResult<ExternalModelItemEntity> items(@RequestBody(required = false) ExternalModelItemQuery query) {
        return externalModelService.listItems(query);
    }

    @GetMapping("/items/{id}")
    public ExternalModelItemEntity item(@PathVariable Long id) {
        return externalModelService.getItem(id);
    }

    @PostMapping("/sources/{sourceCode}/sync")
    public ExternalModelSyncResult sync(@PathVariable String sourceCode) {
        return externalModelService.sync(sourceCode);
    }

    @PostMapping("/items/{id}/logical-model")
    public LogicalModelItem createLogicalModel(@PathVariable Long id) {
        LogicalModelDto created = externalModelService.createLogicalModel(id);
        return logicalModelConverter.toVO(created);
    }

    @PostMapping("/items/logical-models")
    public ExternalModelSyncResult createLogicalModels(@RequestBody(required = false) ExternalModelItemQuery query) {
        return externalModelService.createLogicalModels(query);
    }

    @DeleteMapping("/items/{id}")
    public void deleteItem(@PathVariable Long id) {
        externalModelService.deleteItem(id);
    }

    @PostMapping("/items/batch-delete")
    public int deleteItems(@RequestBody List<Long> ids) {
        return externalModelService.deleteItems(ids);
    }
}

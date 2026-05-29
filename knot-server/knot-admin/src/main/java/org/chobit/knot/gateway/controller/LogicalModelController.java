package org.chobit.knot.gateway.controller;

import jakarta.validation.Valid;
import org.chobit.knot.gateway.annotation.OperationLog;
import org.chobit.knot.gateway.converter.LogicalModelConverter;
import org.chobit.knot.gateway.dto.model.LogicalModelDto;
import org.chobit.knot.gateway.model.PageQuery;
import org.chobit.knot.gateway.model.PageRequest;
import org.chobit.knot.gateway.model.PageResult;
import org.chobit.knot.gateway.service.LogicalModelService;
import org.chobit.knot.gateway.vo.model.LogicalModelItem;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/logical-models")
public class LogicalModelController {
    private final LogicalModelService logicalModelService;
    private final LogicalModelConverter logicalModelConverter;

    public LogicalModelController(LogicalModelService logicalModelService,
                                  LogicalModelConverter logicalModelConverter) {
        this.logicalModelService = logicalModelService;
        this.logicalModelConverter = logicalModelConverter;
    }

    @GetMapping("/check-code")
    public Map<String, Boolean> checkCode(
            @RequestParam String code,
            @RequestParam(required = false) Long excludeId) {
        return Map.of("available", logicalModelService.isModelCodeAvailable(code, excludeId));
    }

    @GetMapping("/{id}")
    public LogicalModelItem get(@PathVariable Long id) {
        return logicalModelConverter.toVO(logicalModelService.getById(id));
    }

    @PostMapping("/list")
    public PageResult<LogicalModelItem> list(@RequestBody(required = false) PageQuery query) {
        PageResult<LogicalModelDto> page = logicalModelService.list(
                query == null ? PageRequest.of(1, 20) : query.toPageRequest(),
                query == null ? null : query.keyword()
        );
        return page.mapList(logicalModelConverter::toVOList);
    }

    @OperationLog(module = "logical-model", operation = "CREATE", entityType = "LogicalModel",
            entityIdAfter = "#result.id()",
            entityNameAfter = "#result.modelName()",
            description = "'create logical model'",
            recordNewValue = true,
            newValueSpel = "@logicalModelService.logicalModelAuditSnapshot(#result.id())")
    @PostMapping
    public LogicalModelItem create(@RequestBody @Valid LogicalModelItem request) {
        return logicalModelConverter.toVO(logicalModelService.create(logicalModelConverter.toDto(request)));
    }

    @OperationLog(module = "logical-model", operation = "UPDATE", entityType = "LogicalModel",
            entityId = "#p0",
            entityNameAfter = "#result.modelName()",
            description = "'update logical model'",
            recordOldValue = true,
            oldValueSpel = "@logicalModelService.logicalModelAuditSnapshot(#p0)",
            recordNewValue = true,
            newValueSpel = "@logicalModelService.logicalModelAuditSnapshot(#p0)")
    @PutMapping("/{id}")
    public LogicalModelItem update(@PathVariable Long id, @RequestBody @Valid LogicalModelItem request) {
        return logicalModelConverter.toVO(logicalModelService.update(id, logicalModelConverter.toDto(request)));
    }

    @OperationLog(module = "logical-model", operation = "DELETE", entityType = "LogicalModel",
            entityId = "#p0",
            description = "'delete logical model'",
            recordOldValue = true,
            oldValueSpel = "@logicalModelService.logicalModelAuditSnapshot(#p0)")
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        logicalModelService.delete(id);
    }

}

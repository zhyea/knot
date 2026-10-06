package org.chobit.knot.gateway.controller;

import jakarta.validation.Valid;
import org.chobit.knot.gateway.annotation.OperationLog;
import org.chobit.knot.gateway.converter.LogicalModelConverter;
import org.chobit.knot.gateway.dto.model.LogicalModelDto;
import org.chobit.knot.gateway.model.PageQuery;
import org.chobit.knot.gateway.model.PageRequest;
import org.chobit.knot.gateway.model.PageResult;
import org.chobit.knot.gateway.service.LogicalModelService;
import org.chobit.knot.gateway.vo.common.CodeAvailability;
import org.chobit.knot.gateway.vo.common.EnabledStatusRequest;
import org.chobit.knot.gateway.vo.model.LogicalModelItem;
import org.chobit.knot.gateway.model.LogicalModelOptionQuery;
import org.chobit.knot.gateway.service.OptionsService;
import org.chobit.knot.gateway.vo.common.OptionItem;
import org.chobit.knot.gateway.vo.common.OptionPage;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/logical-models")
public class LogicalModelController {
    private final LogicalModelService logicalModelService;
    private final LogicalModelConverter logicalModelConverter;
    private final OptionsService optionsService;

    /**
     * Constructs a new instance.
     */
    public LogicalModelController(LogicalModelService logicalModelService,
                                  LogicalModelConverter logicalModelConverter, OptionsService optionsService) {
        this.logicalModelService = logicalModelService;
        this.logicalModelConverter = logicalModelConverter;
        this.optionsService = optionsService;
    }

    @PostMapping("/options")
    public OptionPage<OptionItem> listOptions(@RequestBody(required = false) LogicalModelOptionQuery query) {
        return optionsService.listLogicalModelOptions(query);
    }

    /**
     * Checks whether the requested condition is satisfied. Executes the public operation.
     */
    @GetMapping("/check-code")
    public CodeAvailability checkCode(
            @RequestParam String code,
            @RequestParam(required = false) Long excludeId) {
        return new CodeAvailability(logicalModelService.isModelCodeAvailable(code, excludeId));
    }

    /**
     * Returns the requested value. Executes the public operation.
     */
    @GetMapping("/{id}")
    public LogicalModelItem get(@PathVariable Long id) {
        return logicalModelConverter.toVO(logicalModelService.getById(id));
    }

    /**
     * Lists matching results. Executes the public operation.
     */
    @PostMapping("/list")
    public PageResult<LogicalModelItem> list(@RequestBody(required = false) PageQuery query) {
        PageResult<LogicalModelDto> page = logicalModelService.list(
                query == null ? PageRequest.of(1, 20) : query.toPageRequest(),
                query == null ? null : query.keyword(),
                query == null ? null : query.modelTypes(),
                query == null ? null : query.includeDeleted()
        );
        return page.mapList(logicalModelConverter::toVOList);
    }

    /**
     * Creates a new resource. Executes the public operation.
     */
    @OperationLog(module = "logical-model", operation = "CREATE", entityType = "LogicalModel",
            entityIdAfter = "#result.id()",
            entityNameAfter = "#result.modelName()",
            description = "'新建统一模型'",
            newValueSpel = "@logicalModelService.logicalModelAuditSnapshot(#result.id())")
    @PostMapping
    /**
     * Creates a new resource.
     */
    public LogicalModelItem create(@RequestBody @Valid LogicalModelItem request) {
        return logicalModelConverter.toVO(logicalModelService.create(logicalModelConverter.toDto(request)));
    }

    /**
     * Updates the target resource. Executes the public operation.
     */
    @OperationLog(module = "logical-model", operation = "UPDATE", entityType = "LogicalModel",
            entityId = "#p0",
            entityNameAfter = "#result.modelName()",
            description = "'更新统一模型'",
            oldValueSpel = "@logicalModelService.logicalModelAuditSnapshot(#p0)",
            newValueSpel = "@logicalModelService.logicalModelAuditSnapshot(#p0)")
    @PutMapping("/{id}")
    /**
     * Updates the target resource.
     */
    public LogicalModelItem update(@PathVariable Long id, @RequestBody @Valid LogicalModelItem request) {
        return logicalModelConverter.toVO(logicalModelService.update(id, logicalModelConverter.toDto(request)));
    }

    /**
     * Updates the logical model enabled status.
     */
    @OperationLog(module = "logical-model", operation = "UPDATE", entityType = "LogicalModel",
            entityId = "#p0",
            description = "'更新统一模型状态'",
            oldValueSpel = "@logicalModelService.logicalModelAuditSnapshot(#p0)",
            newValueSpel = "@logicalModelService.logicalModelAuditSnapshot(#p0)")
    @PutMapping("/{id}/status")
    public LogicalModelItem updateStatus(@PathVariable Long id,
                                         @RequestBody @Valid EnabledStatusRequest request) {
        return logicalModelConverter.toVO(
                logicalModelService.updateStatus(id, Boolean.TRUE.equals(request.enabled()))
        );
    }

    /**
     * Deletes the target resource. Executes the public operation.
     */
    @OperationLog(module = "logical-model", operation = "DELETE", entityType = "LogicalModel",
            entityId = "#p0",
            description = "'删除统一模型'",
            oldValueSpel = "@logicalModelService.logicalModelAuditSnapshot(#p0)")
    @DeleteMapping("/{id}")
    /**
     * Deletes the target resource.
     */
    public void delete(@PathVariable Long id) {
        logicalModelService.delete(id);
    }

    /**
     * 恢复已逻辑删除的统一模型。编码唯一性按物理行判定，删除后同 model_code 无法新建，
     * 只能通过本接口恢复。
     */
    @OperationLog(module = "logical-model", operation = "UPDATE", entityType = "LogicalModel",
            entityId = "#p0",
            entityNameAfter = "#result.modelName()",
            description = "'恢复统一模型'",
            oldValueSpel = "@logicalModelService.logicalModelAuditSnapshot(#p0)",
            newValueSpel = "@logicalModelService.logicalModelAuditSnapshot(#p0)")
    @PutMapping("/{id}/restore")
    public LogicalModelItem restore(@PathVariable Long id) {
        return logicalModelConverter.toVO(logicalModelService.restore(id));
    }

}

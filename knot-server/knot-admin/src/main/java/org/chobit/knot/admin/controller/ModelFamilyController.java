package org.chobit.knot.admin.controller;

import org.chobit.knot.common.annotation.OperationLog;
import org.chobit.knot.common.model.PageQuery;
import org.chobit.knot.common.model.PageRequest;
import org.chobit.knot.common.model.PageResult;
import org.chobit.knot.svc.service.ModelFamilyService;
import org.chobit.knot.svc.vo.common.CodeAvailability;
import org.chobit.knot.svc.vo.common.EnabledStatusRequest;
import org.chobit.knot.svc.vo.model.ModelFamilyItem;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 模型族维护。
 *
 * <p>数据仍存枚举表 {@code ks_enum_configs}（category='model_family'），这里只是按模型域
 * 单独开一套接口，使权限可以只授予「模型族」而不必开放全量枚举读写。</p>
 */
@RestController
@RequestMapping("/api/model-families")
public class ModelFamilyController {

    private final ModelFamilyService modelFamilyService;

    /**
     * Constructs a new instance.
     */
    public ModelFamilyController(ModelFamilyService modelFamilyService) {
        this.modelFamilyService = modelFamilyService;
    }

    /**
     * Checks whether the requested condition is satisfied. Executes the public operation.
     */
    @GetMapping("/check-code")
    public CodeAvailability checkCode(@RequestParam String code,
                                      @RequestParam(required = false) Long excludeId) {
        return new CodeAvailability(modelFamilyService.isCodeAvailable(code, excludeId));
    }

    /**
     * Returns the requested value. Executes the public operation.
     */
    @GetMapping("/{id}")
    public ModelFamilyItem get(@PathVariable Long id) {
        return modelFamilyService.getById(id);
    }

    /**
     * Lists matching results. Executes the public operation.
     */
    @PostMapping("/list")
    public PageResult<ModelFamilyItem> list(@RequestBody(required = false) PageQuery query) {
        return modelFamilyService.list(
                query == null ? PageRequest.of(1, 20) : query.toPageRequest(),
                query == null ? null : query.keyword()
        );
    }

    /**
     * Creates a new resource. Executes the public operation.
     */
    @OperationLog(module = "model-family", operation = "CREATE", entityType = "ModelFamily",
            entityIdAfter = "#result.id()",
            entityNameAfter = "#result.name()",
            description = "'新建模型族'",
            newValueSpel = "#result")
    @PostMapping
    public ModelFamilyItem create(@RequestBody ModelFamilyItem request) {
        return modelFamilyService.create(request);
    }

    /**
     * Updates the target resource. Executes the public operation.
     */
    @OperationLog(module = "model-family", operation = "UPDATE", entityType = "ModelFamily",
            entityId = "#p0",
            entityNameAfter = "#result.name()",
            description = "'更新模型族'",
            oldValueSpel = "@modelFamilyService.getById(#p0)",
            newValueSpel = "#result")
    @PutMapping("/{id}")
    public ModelFamilyItem update(@PathVariable Long id, @RequestBody ModelFamilyItem request) {
        return modelFamilyService.update(id, request);
    }

    /**
     * Updates the model family enabled status. Executes the public operation.
     */
    @OperationLog(module = "model-family", operation = "UPDATE", entityType = "ModelFamily",
            entityId = "#p0",
            entityNameAfter = "#result.name()",
            description = "'更新模型族状态'",
            oldValueSpel = "@modelFamilyService.getById(#p0)",
            newValueSpel = "#result")
    @PutMapping("/{id}/status")
    public ModelFamilyItem updateStatus(@PathVariable Long id, @RequestBody EnabledStatusRequest request) {
        return modelFamilyService.updateStatus(id, request.enabled() != null && request.enabled());
    }

    /**
     * Deletes the target resource. Executes the public operation.
     */
    @OperationLog(module = "model-family", operation = "DELETE", entityType = "ModelFamily",
            entityId = "#p0",
            description = "'删除模型族'",
            oldValueSpel = "@modelFamilyService.getById(#p0)")
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        modelFamilyService.delete(id);
    }
}

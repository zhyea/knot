package org.chobit.knot.gateway.controller;

import jakarta.validation.Valid;
import org.chobit.knot.gateway.annotation.OperationLog;
import org.chobit.knot.gateway.converter.ModelConverter;
import org.chobit.knot.gateway.dto.model.ModelDto;
import org.chobit.knot.gateway.dto.provider.DiscountPolicyDto;
import org.chobit.knot.gateway.model.PageQuery;
import org.chobit.knot.gateway.model.PageRequest;
import org.chobit.knot.gateway.model.PageResult;
import org.chobit.knot.gateway.service.ModelService;
import org.chobit.knot.gateway.vo.common.CodeAvailability;
import org.chobit.knot.gateway.vo.common.EnabledStatusRequest;
import org.chobit.knot.gateway.vo.model.ModelItem;
import org.chobit.knot.gateway.vo.model.ModelApiProtocolItem;
import org.chobit.knot.gateway.vo.model.ModelTypeItem;
import org.chobit.knot.gateway.vo.model.RequestAdapterItem;
import org.chobit.knot.gateway.vo.model.UsageExtractorItem;
import org.chobit.knot.gateway.vo.provider.DiscountPolicy;
import org.chobit.knot.gateway.model.ModelOptionQuery;
import org.chobit.knot.gateway.service.OptionsService;
import org.chobit.knot.gateway.vo.common.OptionItem;
import org.chobit.knot.gateway.vo.common.OptionPage;
import org.chobit.knot.gateway.vo.common.meta.ModelMeta;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/models")
public class ModelController {
    private final ModelService modelService;
    private final ModelConverter modelConverter;
    private final OptionsService optionsService;

    /**
     * Constructs a new instance.
     */
    public ModelController(ModelService modelService, ModelConverter modelConverter, OptionsService optionsService) {
        this.modelService = modelService;
        this.modelConverter = modelConverter;
        this.optionsService = optionsService;
    }

    @PostMapping("/options")
    public OptionPage<OptionItem<ModelMeta>> listOptions(@RequestBody(required = false) ModelOptionQuery query) {
        return optionsService.listModelOptions(query);
    }

    /**
     * Checks whether the model code is available.
     */
    @GetMapping("/check-code")
    public CodeAvailability checkCode(
            @RequestParam String code,
            @RequestParam(required = false) Long excludeId) {
        return new CodeAvailability(modelService.isModelCodeAvailable(code, excludeId));
    }

    /**
     * Lists usage extractors available for model API bindings.
     */
    @GetMapping("/usage-extractors")
    public List<UsageExtractorItem> usageExtractors() {
        return modelService.listUsageExtractors();
    }

    /**
     * Lists request adapters available for model API bindings.
     */
    @GetMapping("/request-adapters")
    public List<RequestAdapterItem> requestAdapters() {
        return modelService.listRequestAdapters();
    }

    /**
     * Lists model API protocols defined in code.
     */
    @GetMapping("/api-protocols")
    public List<ModelApiProtocolItem> apiProtocols() {
        return modelService.listApiProtocols();
    }

    /**
     * Lists model types defined in code.
     */
    @GetMapping("/types")
    public List<ModelTypeItem> types() {
        return modelService.listModelTypes();
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
                query == null ? null : query.modelTypes(),
                query == null ? null : query.logicalModelCode(),
                query == null ? null : query.status(),
                query == null ? null : query.includeDeleted()
        );
        return page.mapList(modelConverter::toVOList);
    }

    /**
     * Creates a model.
     */
    @OperationLog(module = "model", operation = "CREATE", entityType = "Model",
            entityIdAfter = "#result.id()",
            entityNameAfter = "#result.modelCode()",
            description = "'创建模型'",
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
            entityNameAfter = "#result.modelCode()",
            description = "'更新模型'",
            oldValueSpel = "@modelService.modelAuditSnapshot(#p0)",
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
            entityNameAfter = "#result.modelCode()",
            description = "'更新模型状态'",
            oldValueSpel = "@modelService.modelAuditSnapshot(#p0)",
            newValueSpel = "@modelService.modelAuditSnapshot(#p0)")
    @PutMapping("/{id}/status")
    public ModelItem updateStatus(@PathVariable Long id, @RequestBody @Valid EnabledStatusRequest request) {
        ModelDto updated = modelService.updateStatus(id, Boolean.TRUE.equals(request.enabled()));
        return modelConverter.toVO(updated);
    }

    /**
     * 逻辑删除供应商模型（被路由规则引用时返回 409）。不物理删除，可通过恢复接口还原。
     */
    @OperationLog(module = "model", operation = "DELETE", entityType = "Model",
            entityId = "#p0",
            description = "'删除供应商模型'",
            oldValueSpel = "@modelService.modelAuditSnapshot(#p0)")
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        modelService.delete(id);
    }

    /**
     * 恢复已逻辑删除的供应商模型。model_code 唯一性按物理行判定，删除后同 model_code 无法新建，
     * 只能通过本接口恢复。
     */
    @OperationLog(module = "model", operation = "UPDATE", entityType = "Model",
            entityId = "#p0",
            entityNameAfter = "#result.modelCode()",
            description = "'恢复供应商模型'",
            oldValueSpel = "@modelService.modelAuditSnapshot(#p0)",
            newValueSpel = "@modelService.modelAuditSnapshot(#p0)")
    @PutMapping("/{id}/restore")
    public ModelItem restore(@PathVariable Long id) {
        ModelDto restored = modelService.restore(id);
        return modelConverter.toVO(restored);
    }

    // ==================== 折扣策略（绑定供应商模型 model_code） ====================

    @PostMapping("/{modelCode}/discount-policies/list")
    public List<DiscountPolicy> listDiscountPolicies(@PathVariable String modelCode) {
        return modelService.listDiscountPolicies(modelCode).stream()
                .map(ModelController::toDiscountPolicyVO)
                .toList();
    }

    @OperationLog(module = "model", operation = "CREATE", entityType = "Model",
            entityId = "#p0",
            entityNameAfter = "#p0",
            description = "'新增折扣策略'",
            newValueSpel = "#result")
    @PostMapping("/{modelCode}/discount-policies")
    public DiscountPolicy createDiscountPolicy(@PathVariable String modelCode,
                                               @RequestBody @Valid DiscountPolicy request) {
        return toDiscountPolicyVO(modelService.createDiscountPolicy(modelCode, toDiscountPolicyDto(request)));
    }

    @OperationLog(module = "model", operation = "UPDATE", entityType = "Model",
            entityId = "#p0",
            entityNameAfter = "#p0",
            description = "'更新折扣策略'",
            oldValueSpel = "@modelService.discountPolicyAuditSnapshot(#p1)",
            newValueSpel = "#result")
    @PutMapping("/{modelCode}/discount-policies/{policyId}")
    public DiscountPolicy updateDiscountPolicy(@PathVariable String modelCode,
                                               @PathVariable Long policyId,
                                               @RequestBody @Valid DiscountPolicy request) {
        return toDiscountPolicyVO(modelService.updateDiscountPolicy(modelCode, policyId, toDiscountPolicyDto(request)));
    }

    private static DiscountPolicyDto toDiscountPolicyDto(DiscountPolicy vo) {
        return new DiscountPolicyDto(
                null, vo.policyName(), vo.scopeType(), vo.scopeRefId(),
                vo.discountType(), vo.discountValue(), vo.priority(), vo.status());
    }

    private static DiscountPolicy toDiscountPolicyVO(DiscountPolicyDto dto) {
        return new DiscountPolicy(dto.id(), dto.policyName(), dto.scopeType(), dto.scopeRefId(),
                dto.discountType(), dto.discountValue(), dto.priority(), dto.status());
    }
}

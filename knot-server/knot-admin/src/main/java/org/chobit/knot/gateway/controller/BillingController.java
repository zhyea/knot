package org.chobit.knot.gateway.controller;

import jakarta.validation.Valid;
import org.chobit.knot.gateway.annotation.OperationLog;
import org.chobit.knot.gateway.converter.BillingConverter;
import org.chobit.knot.gateway.dto.billing.BillingRuleDto;
import org.chobit.knot.gateway.dto.billing.ReconciliationResultDto;
import org.chobit.knot.gateway.model.PageQuery;
import org.chobit.knot.gateway.model.PageRequest;
import org.chobit.knot.gateway.model.PageResult;
import org.chobit.knot.gateway.service.BillingService;
import org.chobit.knot.gateway.vo.billing.BillingCapabilities;
import org.chobit.knot.gateway.vo.billing.BillingReportSummary;
import org.chobit.knot.gateway.vo.billing.BillingRule;
import org.chobit.knot.gateway.vo.billing.BillingRuleListItem;
import org.chobit.knot.gateway.vo.common.EnabledStatusRequest;
import org.chobit.knot.gateway.vo.billing.ReconciliationRequest;
import org.chobit.knot.gateway.vo.billing.ReconciliationResult;
import org.chobit.knot.gateway.vo.billing.PricingPreviewRequest;
import org.chobit.knot.gateway.vo.billing.PricingPreviewResult;
import org.chobit.knot.gateway.model.BillingRuleOptionQuery;
import org.chobit.knot.gateway.service.OptionsService;
import org.chobit.knot.gateway.vo.common.OptionItem;
import org.chobit.knot.gateway.vo.common.OptionPage;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/billing")
public class BillingController {
    private final BillingService billingService;
    private final BillingConverter billingConverter;
    private final OptionsService optionsService;

    /**
     * Constructs a new instance.
     */
    public BillingController(BillingService billingService, BillingConverter billingConverter, OptionsService optionsService) {
        this.billingService = billingService;
        this.billingConverter = billingConverter;
        this.optionsService = optionsService;
    }

    @PostMapping("/options")
    public OptionPage<OptionItem<Void>> listOptions(@RequestBody(required = false) BillingRuleOptionQuery query) {
        return optionsService.listBillingRuleOptions(query);
    }

    /**
     * Lists matching results. Executes the public operation.
     */
    @PostMapping("/rules")
    public PageResult<BillingRuleListItem> listRules(@RequestBody(required = false) PageQuery query) {
        return billingService.listRules(
                query == null ? PageRequest.of(1, 20) : query.toPageRequest(),
                query == null ? null : query.keyword(),
                query == null ? null : query.modelFamilyCode(),
                query == null ? null : query.code(),
                query == null ? null : query.includeDeleted()
        );
    }

    /**
     * 规则详情（含 configJson）：编辑抽屉打开时按 id 从后端取全量记录，列表只走轻量 VO。
     */
    @GetMapping("/rules/{id}")
    public BillingRule getRule(@PathVariable Long id) {
        return billingConverter.toRuleVO(billingService.getRuleById(id));
    }

    /**
     * 计费能力矩阵：模式（量）与进阶方案（价）两层能力。
     */
    @GetMapping("/mode-capabilities")
    public BillingCapabilities modeCapabilities() {
        return billingService.listModeCapabilities();
    }

    /**
     * 计费报表汇总（配置维度）：规则状态计数与统一模型/模式/方案/币种分布。
     */
    @GetMapping("/report/summary")
    public BillingReportSummary reportSummary() {
        return billingService.getReportSummary();
    }

    /**
     * Creates a new resource. Executes the public operation.
     */
    @OperationLog(module = "billing", operation = "CREATE", entityType = "BillingRule",
            entityIdAfter = "#result.id()",
            entityNameAfter = "#result.code()",
            description = "'创建计费规则'",
            newValueSpel = "@billingService.billingRuleAuditSnapshot(#result.id())")
    @PostMapping()
    public BillingRule createRule(@RequestBody @Valid BillingRule request) {
        BillingRuleDto created = billingService.createRule(billingConverter.toRuleDto(request));
        return billingConverter.toRuleVO(created);
    }

    /**
     * Updates the target resource. Executes the public operation.
     */
    @OperationLog(module = "billing", operation = "UPDATE", entityType = "BillingRule",
            entityId = "#p0",
            entityNameAfter = "#result.code()",
            description = "'更新计费规则'",
            oldValueSpel = "@billingService.billingRuleAuditSnapshot(#p0)",
            newValueSpel = "@billingService.billingRuleAuditSnapshot(#p0)")
    @PutMapping("/rules/{id}")
    public BillingRule updateRule(@PathVariable Long id, @RequestBody @Valid BillingRule request) {
        BillingRuleDto updated = billingService.updateRule(id, billingConverter.toRuleDto(request));
        return billingConverter.toRuleVO(updated);
    }

    /**
     * Updates the target resource status. Executes the public operation.
     */
    @OperationLog(module = "billing", operation = "UPDATE", entityType = "BillingRule",
            entityId = "#p0",
            entityNameAfter = "#result.code()",
            description = "'更新计费规则状态'",
            oldValueSpel = "@billingService.billingRuleAuditSnapshot(#p0)",
            newValueSpel = "@billingService.billingRuleAuditSnapshot(#p0)")
    @PutMapping("/rules/{id}/status")
    public BillingRule updateRuleStatus(@PathVariable Long id, @RequestBody @Valid EnabledStatusRequest request) {
        BillingRuleDto updated = billingService.updateStatus(id, Boolean.TRUE.equals(request.enabled()));
        return billingConverter.toRuleVO(updated);
    }

    /**
     * Deletes the target resource. Executes the public operation.
     */
    @OperationLog(module = "billing", operation = "DELETE", entityType = "BillingRule",
            entityId = "#p0",
            description = "'删除计费规则'",
            oldValueSpel = "@billingService.billingRuleAuditSnapshot(#p0)")
    @DeleteMapping("/rules/{id}")
    public void deleteRule(@PathVariable Long id) {
        billingService.deleteRule(id);
    }

    /**
     * 恢复已逻辑删除的计费规则：仅清 {@code is_deleted}，{@code status} 保持停用，需显式启用才生效。
     */
    @OperationLog(module = "billing", operation = "UPDATE", entityType = "BillingRule",
            entityId = "#p0",
            entityNameAfter = "#result.code()",
            description = "'恢复计费规则'",
            newValueSpel = "@billingService.billingRuleAuditSnapshot(#p0)")
    @PutMapping("/rules/{id}/restore")
    public BillingRule restoreRule(@PathVariable Long id) {
        return billingConverter.toRuleVO(billingService.restoreRule(id));
    }

    /**
     * 计费方案试算：给定规则与时点，返回相位判定与最终单价（高低峰排障/页面自测用）。
     */
    @PostMapping("/rules/{id}/preview")
    public PricingPreviewResult previewRule(@PathVariable Long id,
                                            @RequestBody(required = false) PricingPreviewRequest request) {
        return billingService.previewPricing(id, request);
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    @PostMapping("/reconciliation")
    public ReconciliationResult reconciliation(@RequestBody @Valid ReconciliationRequest request) {
        ReconciliationResultDto result = billingService.reconcile(request.providerCode(), request.billDate());
        return billingConverter.toReconciliationVO(result);
    }
}

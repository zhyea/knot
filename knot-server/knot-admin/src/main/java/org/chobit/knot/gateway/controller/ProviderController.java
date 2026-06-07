package org.chobit.knot.gateway.controller;

import org.chobit.knot.gateway.annotation.OperationLog;
import org.chobit.knot.gateway.model.PageQuery;
import org.chobit.knot.gateway.model.PageRequest;
import org.chobit.knot.gateway.model.PageResult;
import org.chobit.knot.gateway.converter.ProviderConverter;
import org.chobit.knot.gateway.dto.provider.DiscountPolicyDto;
import org.chobit.knot.gateway.dto.provider.ProviderDto;
import org.chobit.knot.gateway.service.ProviderService;
import org.chobit.knot.gateway.vo.common.EnabledStatusRequest;
import org.chobit.knot.gateway.vo.provider.DiscountPolicy;
import org.chobit.knot.gateway.vo.provider.ProviderItem;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/providers")
public class ProviderController {
    private final ProviderService providerService;
    private final ProviderConverter providerConverter;

    /**
     * Constructs a new instance.
     */
    public ProviderController(ProviderService providerService, ProviderConverter providerConverter) {
        this.providerService = providerService;
        this.providerConverter = providerConverter;
    }

    /**
     * Lists matching results. Executes the public operation.
     */
    @PostMapping("/list")
    public PageResult<ProviderItem> list(@RequestBody(required = false) PageQuery query) {
        PageResult<ProviderDto> page = providerService.list(
                query == null ? PageRequest.of(1, 20) : query.toPageRequest(),
                query == null ? null : query.keyword()
        );
        return page.mapList(providerConverter::toVOList);
    }

    /**
     * Returns a suggested value. Executes the public operation.
     */
    @GetMapping("/suggest-code")
    public String suggestCode() {
        return providerService.suggestCode();
    }

    /**
     * Checks whether the requested condition is satisfied. Executes the public operation.
     */
    @GetMapping("/check-code")
    public Map<String, Boolean> checkCode(
            @RequestParam String code,
            @RequestParam(required = false) Long excludeId) {
        return Map.of("available", providerService.isCodeAvailable(code, excludeId));
    }

    /**
     * Returns the requested value. Executes the public operation.
     */
    @GetMapping("/{id}")
    public ProviderItem get(@PathVariable Long id) {
        return providerConverter.toVO(providerService.getById(id));
    }

    /**
     * Creates a new resource. Executes the public operation.
     */
    @OperationLog(module = "provider", operation = "CREATE", entityType = "Provider",
            entityIdAfter = "#result.id()",
            entityNameAfter = "#result.name()",
            description = "'鏂板缓渚涘簲鍟?",
            recordNewValue = true,
            newValueSpel = "@providerService.providerAuditSnapshot(#result.id())")
    @PostMapping
    /**
     * Creates a new resource.
     */
    public ProviderItem create(@RequestBody @Valid ProviderItem request) {
        ProviderDto created = providerService.create(providerConverter.toDto(request));
        return providerConverter.toVO(created);
    }

    /**
     * Updates the target resource. Executes the public operation.
     */
    @OperationLog(module = "provider", operation = "UPDATE", entityType = "Provider",
            entityId = "#p0",
            entityNameAfter = "#result.name()",
            description = "'鏇存柊渚涘簲鍟?",
            recordOldValue = true,
            oldValueSpel = "@providerService.providerAuditSnapshot(#p0)",
            recordNewValue = true,
            newValueSpel = "@providerService.providerAuditSnapshot(#p0)")
    @PutMapping("/{id}")
    /**
     * Updates the target resource.
     */
    public ProviderItem update(@PathVariable Long id, @RequestBody @Valid ProviderItem request) {
        ProviderDto updated = providerService.update(id, providerConverter.toDto(request));
        return providerConverter.toVO(updated);
    }

    /**
     * Updates the target resource status. Executes the public operation.
     */
    @OperationLog(module = "provider", operation = "UPDATE", entityType = "Provider",
            entityId = "#p0",
            entityNameAfter = "#result.name()",
            description = "'鏇存柊渚涘簲鍟嗙姸鎬?,'",
            recordOldValue = true,
            oldValueSpel = "@providerService.providerAuditSnapshot(#p0)",
            recordNewValue = true,
            newValueSpel = "@providerService.providerAuditSnapshot(#p0)")
    @PutMapping("/{id}/status")
    public ProviderItem updateStatus(@PathVariable Long id, @RequestBody @Valid EnabledStatusRequest request) {
        ProviderDto updated = providerService.updateStatus(id, Boolean.TRUE.equals(request.enabled()));
        return providerConverter.toVO(updated);
    }

    /**
     * Lists matching results. Executes the public operation.
     */
    @PostMapping("/{id}/discount-policies/list")
    public List<DiscountPolicy> listDiscountPolicies(@PathVariable Long id) {
        return providerService.listDiscountPolicies(id).stream()
                .map(this::toDiscountPolicyVO).toList();
    }

    /**
     * Creates a new resource. Executes the public operation.
     */
    @OperationLog(module = "provider", operation = "CREATE", entityType = "Provider",
            entityId = "#p0",
            entityNameAfter = "@providerService.getById(#p0).name()",
            description = "'鏂板鎶樻墸绛栫暐'",
            recordNewValue = true,
            newValueSpel = "#result")
    @PostMapping("/{id}/discount-policies")
    /**
     * Creates a new resource.
     */
    public DiscountPolicy createDiscountPolicy(@PathVariable Long id, @RequestBody @Valid DiscountPolicy request) {
        DiscountPolicyDto created = providerService.createDiscountPolicy(id, toDiscountPolicyDto(request));
        return toDiscountPolicyVO(created);
    }

    /**
     * Updates the target resource. Executes the public operation.
     */
    @OperationLog(module = "provider", operation = "UPDATE", entityType = "Provider",
            entityId = "#p0",
            entityNameAfter = "@providerService.getById(#p0).name()",
            description = "'鏇存柊鎶樻墸绛栫暐'",
            recordOldValue = true,
            oldValueSpel = "@providerService.discountPolicyAuditSnapshot(#p1)",
            recordNewValue = true,
            newValueSpel = "#result")
    @PutMapping("/{id}/discount-policies/{policyId}")
    public DiscountPolicy updateDiscountPolicy(
            @PathVariable Long id,
            @PathVariable Long policyId,
            @RequestBody @Valid DiscountPolicy request) {
        DiscountPolicyDto updated = providerService.updateDiscountPolicy(id, policyId, toDiscountPolicyDto(request));
        return toDiscountPolicyVO(updated);
    }

    private DiscountPolicyDto toDiscountPolicyDto(DiscountPolicy vo) {
        return new DiscountPolicyDto(
                null, vo.policyName(), vo.scopeType(), vo.scopeRefId(),
                vo.discountType(), vo.discountValue(), vo.priority(), vo.status()
        );
    }

    private DiscountPolicy toDiscountPolicyVO(DiscountPolicyDto dto) {
        return new DiscountPolicy(dto.id(), dto.policyName(), dto.scopeType(), dto.scopeRefId(),
                dto.discountType(), dto.discountValue(), dto.priority(), dto.status());
    }
}

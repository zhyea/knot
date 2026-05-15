package org.chobit.knot.gateway.controller;

import org.chobit.knot.gateway.model.PageQuery;
import org.chobit.knot.gateway.model.PageRequest;
import org.chobit.knot.gateway.model.PageResult;
import org.chobit.knot.gateway.converter.ProviderConverter;
import org.chobit.knot.gateway.dto.provider.DiscountPolicyDto;
import org.chobit.knot.gateway.dto.provider.ProviderDto;
import org.chobit.knot.gateway.service.ProviderService;
import org.chobit.knot.gateway.vo.provider.DiscountPolicy;
import org.chobit.knot.gateway.vo.provider.ProviderItem;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/providers")
public class ProviderController {
    private final ProviderService providerService;
    private final ProviderConverter providerConverter;

    public ProviderController(ProviderService providerService, ProviderConverter providerConverter) {
        this.providerService = providerService;
        this.providerConverter = providerConverter;
    }

    @PostMapping("/list")
    public PageResult<ProviderItem> list(@RequestBody(required = false) PageQuery query) {
        PageResult<ProviderDto> page = providerService.list(query == null ? PageRequest.of(1, 20) : query.toPageRequest());
        return page.mapList(providerConverter::toVOList);
    }

    @PostMapping
    public ProviderItem create(@RequestBody @Valid ProviderItem request) {
        ProviderDto created = providerService.create(providerConverter.toDto(request));
        return providerConverter.toVO(created);
    }

    @PutMapping("/{id}")
    public ProviderItem update(@PathVariable Long id, @RequestBody @Valid ProviderItem request) {
        ProviderDto updated = providerService.update(id, providerConverter.toDto(request));
        return providerConverter.toVO(updated);
    }

    @PostMapping("/{id}/discount-policies/list")
    public List<DiscountPolicy> listDiscountPolicies(@PathVariable Long id) {
        return providerService.listDiscountPolicies(id).stream()
                .map(this::toDiscountPolicyVO).toList();
    }

    @PostMapping("/{id}/discount-policies")
    public DiscountPolicy createDiscountPolicy(@PathVariable Long id, @RequestBody @Valid DiscountPolicy request) {
        DiscountPolicyDto created = providerService.createDiscountPolicy(id, toDiscountPolicyDto(request));
        return toDiscountPolicyVO(created);
    }

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

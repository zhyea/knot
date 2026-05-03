package com.knot.gateway.controller;

import com.knot.gateway.common.ApiResponse;
import com.knot.gateway.common.model.PageRequest;
import com.knot.gateway.common.model.PageResult;
import com.knot.gateway.common.model.QuotaPolicy;
import com.knot.gateway.common.model.RateLimitPolicy;
import com.knot.gateway.converter.ProviderConverter;
import com.knot.gateway.dto.provider.DiscountPolicyDto;
import com.knot.gateway.dto.provider.ProviderDto;
import com.knot.gateway.service.ProviderService;
import com.knot.gateway.vo.provider.DiscountPolicy;
import com.knot.gateway.vo.provider.ProviderItem;
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

    @GetMapping
    public ApiResponse<PageResult<ProviderItem>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        PageResult<ProviderDto> page = providerService.list(PageRequest.of(pageNum, pageSize));
        return ApiResponse.ok(page.mapList(providerConverter::toVOList));
    }

    @PostMapping
    public ApiResponse<ProviderItem> create(@RequestBody @Valid ProviderItem request) {
        ProviderDto created = providerService.create(providerConverter.toDto(request));
        return ApiResponse.ok(providerConverter.toVO(created));
    }

    @PutMapping("/{id}")
    public ApiResponse<ProviderItem> update(@PathVariable Long id, @RequestBody @Valid ProviderItem request) {
        ProviderDto updated = providerService.update(id, providerConverter.toDto(request));
        return ApiResponse.ok(providerConverter.toVO(updated));
    }

    @GetMapping("/{id}/discount-policies")
    public ApiResponse<List<DiscountPolicy>> listDiscountPolicies(@PathVariable Long id) {
        return ApiResponse.ok(providerService.listDiscountPolicies(id).stream()
                .map(this::toDiscountPolicyVO).toList());
    }

    @PostMapping("/{id}/discount-policies")
    public ApiResponse<DiscountPolicy> createDiscountPolicy(@PathVariable Long id, @RequestBody @Valid DiscountPolicy request) {
        DiscountPolicyDto created = providerService.createDiscountPolicy(id, toDiscountPolicyDto(request));
        return ApiResponse.ok(toDiscountPolicyVO(created));
    }

    @PutMapping("/{id}/discount-policies/{policyId}")
    public ApiResponse<DiscountPolicy> updateDiscountPolicy(
            @PathVariable Long id,
            @PathVariable Long policyId,
            @RequestBody @Valid DiscountPolicy request) {
        DiscountPolicyDto updated = providerService.updateDiscountPolicy(id, policyId, toDiscountPolicyDto(request));
        return ApiResponse.ok(toDiscountPolicyVO(updated));
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

package com.knot.gateway.controller;

import com.knot.gateway.common.ApiResponse;
import com.knot.gateway.common.error.BusinessException;
import com.knot.gateway.common.error.ErrorCode;
import com.knot.gateway.common.model.QuotaPolicy;
import com.knot.gateway.common.model.RateLimitPolicy;
import com.knot.gateway.service.ProviderService;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping("/api/providers")
public class ProviderController {
    private final ProviderService providerService;

    private final Map<Long, List<DiscountPolicy>> discountStore = new LinkedHashMap<>();
    private final AtomicLong discountIdGenerator = new AtomicLong(1);

    public ProviderController(ProviderService providerService) {
        this.providerService = providerService;
    }

    @GetMapping
    public ApiResponse<List<ProviderItem>> list() {
        List<ProviderItem> result = providerService.list().stream()
                .map(d -> new ProviderItem(d.id(), d.name(), d.type(), d.baseUrl(), d.enabled(), d.rateLimitPolicy(), d.quotaPolicy()))
                .toList();
        return ApiResponse.ok(result);
    }

    @PostMapping
    public ApiResponse<ProviderItem> create(@RequestBody ProviderItem request) {
        ProviderService.ProviderDto created = providerService.create(
                new ProviderService.ProviderDto(null, null, request.name(), request.type(), request.baseUrl(),
                        request.enabled(), request.rateLimitPolicy(), request.quotaPolicy())
        );
        return ApiResponse.ok(new ProviderItem(created.id(), created.name(), created.type(), created.baseUrl(),
                created.enabled(), created.rateLimitPolicy(), created.quotaPolicy()));
    }

    @PutMapping("/{id}")
    public ApiResponse<ProviderItem> update(@PathVariable Long id, @RequestBody ProviderItem request) {
        ProviderService.ProviderDto updated = providerService.update(
                id,
                new ProviderService.ProviderDto(id, null, request.name(), request.type(), request.baseUrl(),
                        request.enabled(), request.rateLimitPolicy(), request.quotaPolicy())
        );
        return ApiResponse.ok(new ProviderItem(updated.id(), updated.name(), updated.type(), updated.baseUrl(),
                updated.enabled(), updated.rateLimitPolicy(), updated.quotaPolicy()));
    }

    @GetMapping("/{id}/discount-policies")
    public ApiResponse<List<DiscountPolicy>> listDiscountPolicies(@PathVariable Long id) {
        ensureProviderExists(id);
        return ApiResponse.ok(discountStore.getOrDefault(id, List.of()));
    }

    @PostMapping("/{id}/discount-policies")
    public ApiResponse<DiscountPolicy> createDiscountPolicy(@PathVariable Long id, @RequestBody DiscountPolicy request) {
        ensureProviderExists(id);
        DiscountPolicy created = new DiscountPolicy(
                discountIdGenerator.getAndIncrement(),
                request.policyName(),
                request.scopeType(),
                request.scopeRefId(),
                request.discountType(),
                request.discountValue(),
                request.priority(),
                request.status()
        );
        discountStore.computeIfAbsent(id, key -> new ArrayList<>()).add(created);
        return ApiResponse.ok(created);
    }

    @PutMapping("/{id}/discount-policies/{policyId}")
    public ApiResponse<DiscountPolicy> updateDiscountPolicy(
            @PathVariable Long id,
            @PathVariable Long policyId,
            @RequestBody DiscountPolicy request
    ) {
        ensureProviderExists(id);
        List<DiscountPolicy> policies = discountStore.getOrDefault(id, new ArrayList<>());
        for (int i = 0; i < policies.size(); i++) {
            if (policies.get(i).id().equals(policyId)) {
                DiscountPolicy updated = new DiscountPolicy(
                        policyId,
                        request.policyName(),
                        request.scopeType(),
                        request.scopeRefId(),
                        request.discountType(),
                        request.discountValue(),
                        request.priority(),
                        request.status()
                );
                policies.set(i, updated);
                discountStore.put(id, policies);
                return ApiResponse.ok(updated);
            }
        }
        throw new BusinessException(ErrorCode.NOT_FOUND, "discount policy not found");
    }

    private void ensureProviderExists(Long id) {
        boolean exists = providerService.list().stream().anyMatch(item -> item.id().equals(id));
        if (!exists) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "provider not found");
        }
    }

    public record ProviderItem(Long id, String name, String type, String baseUrl, boolean enabled,
                               RateLimitPolicy rateLimitPolicy, QuotaPolicy quotaPolicy) {
    }

    public record DiscountPolicy(
            Long id,
            String policyName,
            String scopeType,
            Long scopeRefId,
            String discountType,
            double discountValue,
            int priority,
            String status
    ) {
    }
}

package com.knot.gateway.controller;

import com.knot.gateway.common.ApiResponse;
import com.knot.gateway.common.error.BusinessException;
import com.knot.gateway.common.error.ErrorCode;
import com.knot.gateway.common.model.QuotaPolicy;
import com.knot.gateway.common.model.RateLimitPolicy;
import com.knot.gateway.service.ModelService;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/models")
public class ModelController {
    private final ModelService modelService;

    private final Map<Long, String> activeVersionStore = new LinkedHashMap<>();

    public ModelController(ModelService modelService) {
        this.modelService = modelService;
    }

    @GetMapping
    public ApiResponse<List<ModelItem>> list() {
        List<ModelItem> result = modelService.list().stream()
                .map(d -> new ModelItem(d.id(), d.name(), d.providerId(), d.modelType(), d.version(),
                        d.enabled(), d.rateLimitPolicy(), d.quotaPolicy()))
                .toList();
        return ApiResponse.ok(result);
    }

    @PostMapping
    public ApiResponse<ModelItem> create(@RequestBody ModelItem request) {
        ModelService.ModelDto created = modelService.create(new ModelService.ModelDto(
                null, null, request.name(), request.providerId(), request.modelType(), request.version(),
                request.enabled(), request.rateLimitPolicy(), request.quotaPolicy()
        ));
        activeVersionStore.put(created.id(), created.version());
        return ApiResponse.ok(new ModelItem(created.id(), created.name(), created.providerId(), created.modelType(),
                created.version(), created.enabled(), created.rateLimitPolicy(), created.quotaPolicy()));
    }

    @PutMapping("/{id}")
    public ApiResponse<ModelItem> update(@PathVariable Long id, @RequestBody ModelItem request) {
        ModelService.ModelDto updated = modelService.update(id, new ModelService.ModelDto(
                id, null, request.name(), request.providerId(), request.modelType(), request.version(),
                request.enabled(), request.rateLimitPolicy(), request.quotaPolicy()
        ));
        return ApiResponse.ok(new ModelItem(updated.id(), updated.name(), updated.providerId(), updated.modelType(),
                updated.version(), updated.enabled(), updated.rateLimitPolicy(), updated.quotaPolicy()));
    }

    @PostMapping("/{id}/test")
    public ApiResponse<ModelTestResult> test(@PathVariable Long id, @RequestBody ModelTestRequest request) {
        ensureModelExists(id);
        String output = "echo: " + request.prompt();
        return ApiResponse.ok(new ModelTestResult(output, 120, Math.max(1, request.prompt().length() / 2)));
    }

    @PostMapping("/{id}/versions/switch")
    public ApiResponse<ModelVersionSwitchResult> switchVersion(@PathVariable Long id, @RequestBody ModelVersionSwitchRequest request) {
        ensureModelExists(id);
        activeVersionStore.put(id, request.targetVersion());
        return ApiResponse.ok(new ModelVersionSwitchResult(id, request.targetVersion(), "ACTIVE"));
    }

    private void ensureModelExists(Long id) {
        boolean exists = modelService.list().stream().anyMatch(item -> item.id().equals(id));
        if (!exists) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "model not found");
        }
    }

    public record ModelItem(Long id, String name, Long providerId, String modelType, String version, boolean enabled,
                            RateLimitPolicy rateLimitPolicy, QuotaPolicy quotaPolicy) {
    }

    public record ModelTestRequest(String prompt, Double temperature, Integer maxTokens) {
    }

    public record ModelTestResult(String output, int latencyMs, int tokenUsage) {
    }

    public record ModelVersionSwitchRequest(String targetVersion) {
    }

    public record ModelVersionSwitchResult(Long modelId, String activeVersion, String status) {
    }
}

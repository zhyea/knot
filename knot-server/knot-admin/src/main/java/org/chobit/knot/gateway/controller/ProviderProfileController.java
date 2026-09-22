package org.chobit.knot.gateway.controller;

import jakarta.validation.Valid;
import org.chobit.knot.gateway.annotation.OperationLog;
import org.chobit.knot.gateway.model.PageQuery;
import org.chobit.knot.gateway.model.PageRequest;
import org.chobit.knot.gateway.model.PageResult;
import org.chobit.knot.gateway.service.ProviderProfileService;
import org.chobit.knot.gateway.vo.provider.ProviderProfileItem;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/provider-profiles")
public class ProviderProfileController {

    private final ProviderProfileService providerProfileService;

    public ProviderProfileController(ProviderProfileService providerProfileService) {
        this.providerProfileService = providerProfileService;
    }

    @PostMapping("/list")
    public PageResult<ProviderProfileItem> list(@RequestBody(required = false) PageQuery query) {
        PageQuery actual = query == null
                ? new PageQuery(null, null, null, null, null, null, null, null, null)
                : query;
        return providerProfileService.list(actual.keyword(), actual.category(), actual.toPageRequest());
    }

    @GetMapping("/{id}")
    public ProviderProfileItem getById(@PathVariable Long id) {
        return providerProfileService.getById(id);
    }

    @GetMapping("/check-code")
    public Map<String, Boolean> checkCode(@RequestParam String code,
                                          @RequestParam(required = false) Long excludeId) {
        return Map.of("available", providerProfileService.isCodeAvailable(code, excludeId));
    }

    @OperationLog(module = "provider-profile", operation = "CREATE", entityType = "ProviderProfile",
            entityIdAfter = "#result.id()",
            entityNameAfter = "#result.name()",
            description = "'新建供应商信息'",
            recordNewValue = true,
            newValueSpel = "@providerProfileService.providerProfileAuditSnapshot(#result.id())")
    @PostMapping
    public ProviderProfileItem create(@RequestBody @Valid ProviderProfileItem request) {
        return providerProfileService.create(request);
    }

    @OperationLog(module = "provider-profile", operation = "UPDATE", entityType = "ProviderProfile",
            entityId = "#p0",
            entityNameAfter = "#result.name()",
            description = "'更新供应商信息'",
            recordNewValue = true,
            newValueSpel = "@providerProfileService.providerProfileAuditSnapshot(#p0)")
    @PutMapping("/{id}")
    public ProviderProfileItem update(@PathVariable Long id,
                                      @RequestBody @Valid ProviderProfileItem request) {
        return providerProfileService.update(id, request);
    }

    @OperationLog(module = "provider-profile", operation = "DELETE", entityType = "ProviderProfile",
            entityId = "#p0",
            entityName = "@providerProfileService.providerProfileAuditSnapshot(#p0)?.get('name')",
            description = "'删除供应商信息'",
            recordOldValue = true,
            oldValueSpel = "@providerProfileService.providerProfileAuditSnapshot(#p0)")
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        providerProfileService.delete(id);
    }
}
